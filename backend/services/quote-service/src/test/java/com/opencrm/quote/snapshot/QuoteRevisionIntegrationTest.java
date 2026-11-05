package com.opencrm.quote.snapshot;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers @Tag("docker") @Tag("integration")
@SpringBootTest(classes={AcceptedSnapshotIntegrationTest.App.class,QuoteRevisionIntegrationTest.CorrectionConfig.class},webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"oms.quote.enabled=true","oms.quote.scope-id=pilot","oms.quote.customer-ids[0]=a","spring.cache.type=none"})
class QuoteRevisionIntegrationTest {
    @Configuration @Import({QuoteRevisionService.class,SourceTermsService.class}) static class CorrectionConfig {}
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->PG.getJdbcUrl()+"&currentSchema=quote");r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @Autowired QuoteRepository quotes; @Autowired QuoteLineItemRepository lines; @Autowired QuoteService service;
    @Autowired QuoteRevisionService corrections; @Autowired SourceTermsService terms;
    @Autowired AcceptedSnapshotRepository snapshots; @Autowired TransactionTemplate tx; @Autowired JdbcTemplate db;
    @Autowired com.opencrm.quote.client.PricingServiceClient pricing;
    @Autowired ProductServiceClient products; @Autowired ObjectMapper mapper;
    @BeforeEach void clean() { db.execute("TRUNCATE quote_revision_inbox,quote_revision_operations,accepted_order_snapshots,outbox_events,quote_line_items,quotes RESTART IDENTITY CASCADE"); }
    Quote accepted() {
        Long id=tx.execute(t->{var q=Quote.builder().quoteNumber("Q-REV").name("source").customerExternalId("a").status(QuoteStatus.SENT)
            .subtotal(new BigDecimal("50.00")).totalAmount(new BigDecimal("50.00")).build();
            q.getLineItems().add(QuoteLineItem.builder().quote(q).productExternalId("1").quantity(1).listPrice(new BigDecimal("50.00"))
                .unitPrice(new BigDecimal("50.00")).extendedAmount(new BigDecimal("50.00")).build());return quotes.saveAndFlush(q).getId();});
        service.accept(id);return quotes.findById(id).orElseThrow();
    }
    QuoteRevisionRequest request(Quote q,String event,String operation) {return new QuoteRevisionRequest(1,event,operation,"order-1",q.getUuid(),q.getAcceptedRevision(),"pilot","a","Correct missing source terms");}
    @Test void freshApprovalCreatesSecondSnapshotAndReplayNeverReopensIt() {
        var q=accepted();var original=snapshots.findAll().getFirst();String lineId=lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(q.getId()).getFirst().getUuid();
        var request=request(q,"event-1","op-1");corrections.reopen(request);
        var draft=quotes.findById(q.getId()).orElseThrow();
        assertThat(draft.getStatus()).isEqualTo(QuoteStatus.DRAFT);assertThat(draft.getAcceptedRevision()).isEqualTo(1);
        assertThat(draft.getApprovalState()).isEqualTo(ApprovalState.NONE);assertThat(draft.getOmsApprovalReference()).isNull();
        assertThatThrownBy(()->service.accept(q.getId())).hasMessageContaining("Only sent quotes");
        draft=quotes.findById(q.getId()).orElseThrow();
        var auth=new AcceptedSnapshotControllerTest().auth("oms:snapshot:write",List.of("pilot"),List.of("a"));
        terms.capture(q.getId(),new SourceTermsRequest(draft.getVersion(),"address","tax-ref","config-ref",List.of(new SourceTermsRequest.LineTerms(lineId,"DIGITAL","ONE_TIME","0.00",null,null))),auth);
        var repriced=com.opencrm.quote.client.PricingExecuteView.builder().success(true).lineResults(List.of(
            com.opencrm.quote.client.PricingLineResultView.builder().productExternalId("1").quantity(1).listPrice(new BigDecimal("60.00"))
                .unitPrice(new BigDecimal("60.00")).discountAmount(BigDecimal.ZERO).finalPrice(new BigDecimal("60.00")).build())).build();
        when(pricing.executePricing(anyMap())).thenReturn(com.opencrm.common.dto.ApiResponse.success(repriced));
        service.calculate(q.getId());
        var validation=new ConfigurationValidationResult();validation.setValid(true);when(products.validateConfiguration(anyList())).thenReturn(validation);
        service.submitForReview(q.getId());service.reject(q.getId(),"Tax evidence needs correction");
        var rejected=quotes.findById(q.getId()).orElseThrow();
        terms.capture(q.getId(),new SourceTermsRequest(rejected.getVersion(),"address","corrected-tax-ref","config-ref",List.of(new SourceTermsRequest.LineTerms(lineId,"DIGITAL","ONE_TIME","0.00",null,null))),auth);
        assertThat(quotes.findById(q.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.DRAFT);
        service.submitForReview(q.getId());service.approve(q.getId());service.send(q.getId());service.accept(q.getId());
        var revised=quotes.findById(q.getId()).orElseThrow();assertThat(revised.getAcceptedRevision()).isEqualTo(2);assertThat(revised.getUuid()).isEqualTo(q.getUuid());
        assertThat(revised.getTotalAmount()).isEqualByComparingTo("60.00");
        assertThat(snapshots.count()).isEqualTo(2);assertThat(snapshots.findById(original.getId()).orElseThrow().getPayload()).isEqualTo(original.getPayload());
        assertThat(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(q.getId()).getFirst().getUuid()).isEqualTo(lineId);
        corrections.reopen(request);corrections.reopen(request(q,"event-2","op-1"));
        assertThat(quotes.findById(q.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(db.queryForObject("select count(*) from quote_revision_inbox",Integer.class)).isEqualTo(2);
    }
    @Test void competingCorrectionsReopenOnlyOnce() throws Exception {
        var q=accepted();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var attempts=new ArrayList<java.util.concurrent.Callable<Boolean>>();
            for(int i=0;i<20;i++) {final int index=i;attempts.add(()->{try {corrections.reopen(request(q,"event-"+index,"op-"+index));return true;}catch(RuntimeException ex){return false;}});}
            int successes=0;for(var f:pool.invokeAll(attempts))if(f.get())successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(db.queryForObject("select count(*) from quote_revision_operations",Integer.class)).isEqualTo(1);
        assertThat(quotes.findById(q.getId()).orElseThrow().getAcceptedRevision()).isEqualTo(1);
    }
    @Test void rejectedReceiptRollsBackReopen() {
        var q=accepted();db.execute("ALTER TABLE quote_revision_inbox ADD CONSTRAINT reject_test_receipt CHECK(event_id<>'fail')");
        try {assertThatThrownBy(()->corrections.reopen(request(q,"fail","op-fail"))).isInstanceOf(RuntimeException.class);}
        finally {db.execute("ALTER TABLE quote_revision_inbox DROP CONSTRAINT reject_test_receipt");}
        assertThat(quotes.findById(q.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(db.queryForObject("select count(*) from quote_revision_operations",Integer.class)).isZero();
    }
    @Test void rejectsScopeCustomerAndRevisionAndBindsBothReplayIdentities() {
        var q=accepted();var r=request(q,"event","op");
        for(var bad:List.of(new QuoteRevisionRequest(1,"bad1","bad1",r.orderId(),r.quoteId(),1,"other","a",r.reason()),
                new QuoteRevisionRequest(1,"bad2","bad2",r.orderId(),r.quoteId(),1,"pilot","other",r.reason()),
                new QuoteRevisionRequest(1,"bad3","bad3",r.orderId(),r.quoteId(),2,"pilot","a",r.reason())))
            assertThatThrownBy(()->corrections.reopen(bad)).isInstanceOf(RuntimeException.class);
        corrections.reopen(r);
        assertThatThrownBy(()->corrections.reopen(new QuoteRevisionRequest(1,"event","different",r.orderId(),r.quoteId(),1,"pilot","a",r.reason()))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->corrections.reopen(new QuoteRevisionRequest(1,"new","op",r.orderId(),r.quoteId(),1,"pilot","a","different body"))).isInstanceOf(RuntimeException.class);
    }
}
