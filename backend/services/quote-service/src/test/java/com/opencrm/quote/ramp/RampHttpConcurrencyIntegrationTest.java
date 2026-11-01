package com.opencrm.quote.ramp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.config.GlobalExceptionHandler;
import com.opencrm.quote.config.QuoteLineConcurrencyAdvice;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.quote.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = RampHttpConcurrencyIntegrationTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "opencrm.security.mode=dev", "opencrm.pricing.ramp.enabled=true", "opencrm.kafka.inbox.enabled=false",
                "opencrm.outbox.relay.enabled=false", "spring.kafka.listener.auto-startup=false",
                "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=quote",
                "spring.flyway.enabled=true", "spring.flyway.schemas=quote", "spring.flyway.default-schema=quote",
                "spring.datasource.hikari.maximum-pool-size=5",
                "spring.autoconfigure.exclude=com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"})
class RampHttpConcurrencyIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?currentSchema=quote");
        props.add("spring.datasource.username", POSTGRES::getUsername);
        props.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @SpringBootConfiguration @EnableAutoConfiguration @EnableJpaAuditing
    @EntityScan(basePackages="com.opencrm.quote")
    @EnableJpaRepositories(basePackages={"com.opencrm.quote.quote", "com.opencrm.quote.lineitem", "com.opencrm.quote.ramp"})
    @Import({QuoteLineItemController.class, RampScheduleController.class, QuoteController.class, GlobalExceptionHandler.class, QuoteLineConcurrencyAdvice.class})
    static class App {
        @Bean RampPricingProperties rampProperties() { var p = new RampPricingProperties(); p.setEnabled(true); return p; }
        @Bean RampScheduleValidator validator() { return new RampScheduleValidator(); }
        @Bean RampEscalatorExpander expander() { return new RampEscalatorExpander(); }
        @Bean RampScheduleAttacher attacher(RampPricingProperties p, RampScheduleValidator v, RampEscalatorExpander e) { return new RampScheduleAttacher(p,v,e); }
        @Bean RampScheduleGuard guard(QuoteLineItemRepository l, QuoteRampStepRepository s, RampScheduleValidator v, RampPricingProperties p) {
            return new RampScheduleGuard(l,s,v,p,mock(ApprovalRoutingClient.class),mock(OutboxEventPublisher.class));
        }
        @Bean QuoteLineItemService lineService(QuoteLineItemRepository l, QuoteRepository q, RampScheduleAttacher a, RampScheduleGuard g) { return new QuoteLineItemService(l,q,a,g); }
        @Bean com.opencrm.quote.client.PricingServiceClient pricing() { return mock(com.opencrm.quote.client.PricingServiceClient.class); }
        @Bean com.opencrm.quote.quote.QuoteContinuationService continuation() { return mock(com.opencrm.quote.quote.QuoteContinuationService.class); }
        @Bean QuoteService quotesService(QuoteRepository q, QuoteLineItemRepository l, com.opencrm.quote.client.PricingServiceClient p,
                RampScheduleGuard g, ObjectMapper mapper) {
            var qm=org.mapstruct.factory.Mappers.getMapper(QuoteMapper.class);
            var actor=mock(com.opencrm.security.CurrentActor.class);
            org.mockito.Mockito.when(actor.unverifiedCallersAllowed()).thenReturn(true);
            return new QuoteService(q,qm,mock(QuoteNumberGenerator.class),mock(com.opencrm.quote.event.QuoteEventPublisher.class),l,p,
                    mock(com.opencrm.quote.client.ProductServiceClient.class),mapper,mock(OutboxEventPublisher.class),actor,g);
        }
        @Bean RampScheduleQueryService rampService(QuoteRepository q, RampPricingProperties p, RampEscalatorExpander e, QuoteLineItemService l) { return new RampScheduleQueryService(q,p,e,l); }
    }
    @LocalServerPort int port;
    @Autowired QuoteRepository quotes;
    @Autowired QuoteLineItemService lineService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired com.opencrm.quote.client.PricingServiceClient pricing;

    @Test void concurrentLineEditAndExtensionReturnOneSuccessAndOneConflict() throws Exception {
        var tx = new TransactionTemplate(manager);
        long[] ids=tx.execute(status -> {
            Quote q=quotes.saveAndFlush(Quote.builder().quoteNumber("HTTP-RACE").name("Race").customerExternalId("customer").currency("USD").durationInMonths(36).build());
            long line=lineService.addLineItem(q.getId(), lineRequest("40")).getId();
            em.flush();
            return new long[]{q.getId(),line};
        });
        Long version=tx.execute(status -> quotes.findById(ids[0]).orElseThrow().getVersion());
        jdbc.execute("create function quote.test_slow_ramp_write() returns trigger language plpgsql as $$ begin perform pg_advisory_xact_lock(750123); return new; end $$");
        jdbc.execute("create trigger test_slow_ramp_write before insert or update on quote.quote_ramp_steps for each row execute function quote.test_slow_ramp_write()");
        try {
            String edit=new ObjectMapper().writeValueAsString(lineRequest("41"));
            String extend=new ObjectMapper().writeValueAsString(Map.of("steps",List.of(step("40")),"durationInMonths",48,
                    "escalator",Map.of("startPrice",40,"mode","AMOUNT","amount",1,"everyMonths",12),
                    "lineItemId",ids[1],"expectedQuoteVersion",version));
            var client=HttpClient.newHttpClient(); var start=new CountDownLatch(1);
            var locked=new CountDownLatch(1); var release=new CountDownLatch(1);
            try (var pool=Executors.newFixedThreadPool(3)) {
                var blocker=pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                    jdbc.query("select pg_advisory_xact_lock(750123)", rs -> null);
                    locked.countDown();
                    try { if (!release.await(20,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }));
                assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
                var b=pool.submit(() -> send(client,start,"POST","/api/v1/quotes/"+ids[0]+"/ramp-schedule/extend",extend));
                start.countDown();
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                while (System.nanoTime()<until && waitingRampUpdates()<1 && !b.isDone()) Thread.sleep(20);
                if (b.isDone()) System.out.println("extension completed before barrier " + b.get().statusCode() + " " + b.get().body());
                assertThat(waitingRampUpdates()).isGreaterThanOrEqualTo(1);
                var a=pool.submit(() -> send(client,start,"PUT","/api/v1/quotes/"+ids[0]+"/line-items/"+ids[1],edit));
                try {
                    until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                    while (System.nanoTime()<until && waitingLocks()<2) Thread.sleep(20);
                    assertThat(waitingLocks()).isGreaterThanOrEqualTo(2);
                } finally { release.countDown(); }
                blocker.get(10,TimeUnit.SECONDS);
                var editResponse=a.get(30,TimeUnit.SECONDS);
                var extendResponse=b.get(30,TimeUnit.SECONDS);
                assertThat(extendResponse.statusCode()).isEqualTo(200);
                assertThat(editResponse.statusCode()).isEqualTo(409);
                assertThat(editResponse.body()).contains("QUOTE_VERSION_CONFLICT");
            }
        } finally {
            jdbc.execute("drop trigger test_slow_ramp_write on quote.quote_ramp_steps");
            jdbc.execute("drop function quote.test_slow_ramp_write()");
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void calculateVersusExtensionHasNoUnclassifiedFailure(boolean calculationFirst) throws Exception {
        var tx=new TransactionTemplate(manager);
        long[] ids=tx.execute(status -> {
            Quote q=quotes.saveAndFlush(Quote.builder().quoteNumber("HTTP-CALC-RACE-" + calculationFirst).name("Race").customerExternalId("customer").currency("USD").durationInMonths(36).build());
            long line=lineService.addLineItem(q.getId(),lineRequest("40")).getId(); em.flush(); return new long[]{q.getId(),line};
        });
        Long version=tx.execute(status -> quotes.findById(ids[0]).orElseThrow().getVersion());
        var period=com.opencrm.quote.client.PricedPeriodView.builder().sequence(1).startMonth(1).months(36).quantity(50)
                .netUnitPrice(new BigDecimal("40")).netMonthly(new BigDecimal("2000")).periodAmount(new BigDecimal("72000")).build();
        var line=com.opencrm.quote.client.PricingLineResultView.builder().productExternalId("seats").lineItemType("SAAS_SEAT_NAMED")
                .quantity(50).listPrice(new BigDecimal("50")).unitPrice(new BigDecimal("40")).finalPrice(new BigDecimal("2000"))
                .pricedPeriods(List.of(period)).metadata(Map.of("rampGovernanceEvaluated",true,"rampPolicyContext","test","authoritativeProductType","SAAS_SEAT_NAMED")).build();
        org.mockito.Mockito.when(pricing.executePricing(org.mockito.ArgumentMatchers.anyMap())).thenReturn(com.opencrm.common.dto.ApiResponse.success(
                com.opencrm.quote.client.PricingExecuteView.builder().success(true).lineResults(List.of(line)).build()));
        jdbc.execute("create function quote.test_block_calc_line() returns trigger language plpgsql as $$ begin perform pg_advisory_xact_lock(750124); return new; end $$");
        String blockedTable = calculationFirst ? "quote_line_items" : "quote_ramp_steps";
        jdbc.execute("create trigger test_block_calc_line before insert or update on quote." + blockedTable + " for each row execute function quote.test_block_calc_line()");
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1); var start=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(3)) {
            var blocker=pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                jdbc.query("select pg_advisory_xact_lock(750124)", rs -> null); locked.countDown();
                try { if (!release.await(25,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue(); var client=HttpClient.newHttpClient(); start.countDown();
            String extend=new ObjectMapper().writeValueAsString(Map.of("steps",List.of(step("40")),"durationInMonths",48,
                    "escalator",Map.of("startPrice",40,"mode","AMOUNT","amount",1,"everyMonths",12),
                    "lineItemId",ids[1],"expectedQuoteVersion",version));
            java.util.concurrent.Future<HttpResponse<String>> first = calculationFirst
                    ? pool.submit(() -> send(client,start,"POST","/api/v1/quotes/"+ids[0]+"/calculate","{}"))
                    : pool.submit(() -> send(client,start,"POST","/api/v1/quotes/"+ids[0]+"/ramp-schedule/extend",extend));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while (System.nanoTime()<until && (calculationFirst ? waitingLineUpdates() : waitingRampUpdates())<1 && !first.isDone()) Thread.sleep(20);
            if(first.isDone()) System.out.println("FIRST EARLY " + first.get().statusCode() + " " + first.get().body());
            assertThat(calculationFirst ? waitingLineUpdates() : waitingRampUpdates()).isGreaterThanOrEqualTo(1);
            java.util.concurrent.Future<HttpResponse<String>> second = calculationFirst
                    ? pool.submit(() -> send(client,start,"POST","/api/v1/quotes/"+ids[0]+"/ramp-schedule/extend",extend))
                    : pool.submit(() -> send(client,start,"POST","/api/v1/quotes/"+ids[0]+"/calculate","{}"));
            try {
                until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                while (System.nanoTime()<until && waitingLocks()<2 && !second.isDone()) Thread.sleep(20);
                assertThat(waitingLocks()>=2 || second.isDone()).isTrue();
            } finally { release.countDown(); }
            blocker.get(10,TimeUnit.SECONDS);
            var winner=first.get(30,TimeUnit.SECONDS); var loser=second.get(30,TimeUnit.SECONDS);
            assertThat(winner.statusCode()).isEqualTo(200);
            assertThat(loser.statusCode()).isEqualTo(409);
            assertThat(loser.body()).contains("QUOTE_VERSION_CONFLICT");
        } finally {
            release.countDown(); jdbc.execute("drop trigger test_block_calc_line on quote." + blockedTable);
            jdbc.execute("drop function quote.test_block_calc_line()");
        }
    }
    @Test void calculateResponseVersionCanDriveNextExtension() throws Exception {
        var tx=new TransactionTemplate(manager);
        long[] ids=tx.execute(status -> {
            Quote q=quotes.saveAndFlush(Quote.builder().quoteNumber("HTTP-VERSION").name("Version").customerExternalId("customer").currency("USD").durationInMonths(36).build());
            long id=lineService.addLineItem(q.getId(),lineRequest("40")).getId(); em.flush(); return new long[]{q.getId(),id};
        });
        var period=com.opencrm.quote.client.PricedPeriodView.builder().sequence(1).startMonth(1).months(36).quantity(50)
                .netUnitPrice(new BigDecimal("40")).netMonthly(new BigDecimal("2000")).periodAmount(new BigDecimal("72000")).build();
        var line=com.opencrm.quote.client.PricingLineResultView.builder().productExternalId("seats").lineItemType("SAAS_SEAT_NAMED")
                .quantity(50).listPrice(new BigDecimal("50")).unitPrice(new BigDecimal("40")).finalPrice(new BigDecimal("2000"))
                .pricedPeriods(List.of(period)).metadata(Map.of("rampGovernanceEvaluated",true,"rampPolicyContext","test","authoritativeProductType","SAAS_SEAT_NAMED")).build();
        org.mockito.Mockito.when(pricing.executePricing(org.mockito.ArgumentMatchers.anyMap())).thenReturn(com.opencrm.common.dto.ApiResponse.success(
                com.opencrm.quote.client.PricingExecuteView.builder().success(true).lineResults(List.of(line)).build()));
        var open=new CountDownLatch(0); var client=HttpClient.newHttpClient();
        var response=send(client,open,"POST","/api/v1/quotes/"+ids[0]+"/calculate","{}");
        assertThat(response.statusCode()).isEqualTo(200);
        long returned=new ObjectMapper().readTree(response.body()).path("data").path("version").longValue();
        Long committed=tx.execute(status -> quotes.findById(ids[0]).orElseThrow().getVersion());
        assertThat(returned).isEqualTo(committed);
        String extend=new ObjectMapper().writeValueAsString(Map.of("steps",List.of(step("40")),"durationInMonths",48,
                "escalator",Map.of("startPrice",40,"mode","AMOUNT","amount",1,"everyMonths",12),
                "lineItemId",ids[1],"expectedQuoteVersion",returned));
        var extension=send(client,open,"POST","/api/v1/quotes/"+ids[0]+"/ramp-schedule/extend",extend);
        assertThat(extension.statusCode()).isEqualTo(200);
    }

    @Test void rejectedQuoteResponseVersionCanDriveNextExtension() throws Exception {
        var tx=new TransactionTemplate(manager);
        long[] ids=tx.execute(status -> {
            Quote q=quotes.saveAndFlush(Quote.builder().quoteNumber("HTTP-REJECT-VERSION").name("Version").customerExternalId("customer").currency("USD").durationInMonths(36).build());
            long id=lineService.addLineItem(q.getId(),lineRequest("40")).getId();
            q.setStatus(QuoteStatus.IN_REVIEW); em.flush(); return new long[]{q.getId(),id};
        });
        var open=new CountDownLatch(0); var client=HttpClient.newHttpClient();
        var response=send(client,open,"POST","/api/v1/quotes/"+ids[0]+"/reject?reason=fix","{}");
        assertThat(response.statusCode()).isEqualTo(200);
        long returned=new ObjectMapper().readTree(response.body()).path("data").path("version").longValue();
        Long committed=tx.execute(status -> quotes.findById(ids[0]).orElseThrow().getVersion());
        assertThat(returned).isEqualTo(committed);
        String extend=new ObjectMapper().writeValueAsString(Map.of("steps",List.of(step("40")),"durationInMonths",48,
                "escalator",Map.of("startPrice",40,"mode","AMOUNT","amount",1,"everyMonths",12),
                "lineItemId",ids[1],"expectedQuoteVersion",returned));
        assertThat(send(client,open,"POST","/api/v1/quotes/"+ids[0]+"/ramp-schedule/extend",extend).statusCode()).isEqualTo(200);
    }

    private long waitingLineUpdates() { return jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%quote_line_items%'",Long.class); }

    private long waitingLocks() {
        return jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",Long.class);
    }
    private long waitingRampUpdates() {
        return jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%quote_ramp_steps%'",Long.class);
    }
    private HttpResponse<String> send(HttpClient c, CountDownLatch start, String method, String path, String body) throws Exception {
        start.await(); var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json")
                .method(method,HttpRequest.BodyPublishers.ofString(body)).build();
        return c.send(req,HttpResponse.BodyHandlers.ofString());
    }
    private static LineItemCreateRequest lineRequest(String price) {
        return LineItemCreateRequest.builder().productExternalId("seats").productName("Seats").productType("SAAS_SEAT_NAMED")
                .lineItemType("SAAS_SEAT_NAMED").quantity(50).listPrice(new BigDecimal("50")).billingFrequency("MONTHLY")
                .schedule(RampScheduleDto.builder().steps(List.of(step(price))).build()).build();
    }
    private static RampStepDto step(String price) { return RampStepDto.builder().sequence(1).startMonth(1).months(36).quantity(50)
            .priceBasis(RampPriceBasis.ABSOLUTE).unitPrice(new BigDecimal(price)).build(); }
}
