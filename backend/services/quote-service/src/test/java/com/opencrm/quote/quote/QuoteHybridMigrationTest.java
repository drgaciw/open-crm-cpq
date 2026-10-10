package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.quote.client.*;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.ramp.*;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class QuoteHybridMigrationTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @Test void persistedDecliningMigrationQuantityCannotIncreaseInsideItsWindow() throws Exception {
        var response=json.readValue(getClass().getResourceAsStream("/ramp/r4-engine-golden-response.json"),PricingExecuteView.class);
        var declining=response.getLineResults().getFirst();
        declining.setPricedPeriods(List.of(
                PricedPeriodView.builder().sequence(1).startMonth(1).months(6).quantity(10).netUnitPrice(new BigDecimal("100"))
                        .netMonthly(new BigDecimal("1000")).grossPeriodAmount(new BigDecimal("6000"))
                        .migrationCredit(new BigDecimal("3000")).periodAmount(new BigDecimal("3000")).build(),
                PricedPeriodView.builder().sequence(2).startMonth(7).months(6).quantity(11).netUnitPrice(new BigDecimal("100"))
                        .netMonthly(new BigDecimal("1100")).periodAmount(new BigDecimal("6600")).build(),
                PricedPeriodView.builder().sequence(3).startMonth(13).months(24).quantity(0).netUnitPrice(new BigDecimal("100"))
                        .netMonthly(BigDecimal.ZERO).periodAmount(BigDecimal.ZERO).build()));
        Quote quote=Quote.builder().currency("USD").durationInMonths(36).build();
        var lines=persistedMigrationLines(quote,response);
        assertThatThrownBy(() -> AcceptedRampSnapshots.validatePairs(quote,lines)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException)ex).getErrorCode()).isEqualTo("ACCEPTED_RAMP_SNAPSHOT_INVALID");
    }
    @Test void persistedRisingMigrationQuantityCannotDecreaseAfterItsWindow() throws Exception {
        var response=json.readValue(getClass().getResourceAsStream("/ramp/r4-engine-golden-response.json"),PricingExecuteView.class);
        var last=response.getLineResults().getLast().getPricedPeriods().getLast();
        last.setQuantity(9);last.setNetMonthly(new BigDecimal("540"));last.setPeriodAmount(new BigDecimal("12960"));
        Quote quote=Quote.builder().currency("USD").durationInMonths(36).build();
        var lines=persistedMigrationLines(quote,response);
        assertThatThrownBy(() -> AcceptedRampSnapshots.validatePairs(quote,lines)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException)ex).getErrorCode()).isEqualTo("ACCEPTED_RAMP_SNAPSHOT_INVALID");
    }
    private List<QuoteLineItem> persistedMigrationLines(Quote quote,PricingExecuteView response) throws Exception {
        List<QuoteLineItem> lines=new ArrayList<>();
        for(var priced:response.getLineResults()) {
            QuoteLineItem line=QuoteLineItem.builder().quote(quote).productExternalId(priced.getProductExternalId())
                    .productType(priced.getLineItemType()).lineItemType(priced.getLineItemType()).billingFrequency("MONTHLY")
                    .hybridMigrationJson(json.writeValueAsString(priced.getMetadata().get("hybridMigration")))
                    .pricingWaterfallJson(json.writeValueAsString(Map.of("authoritativeProductType",priced.getMetadata().get("authoritativeProductType"),
                            "pricedPeriods",priced.getPricedPeriods()))).build();
            line.getRampSteps().add(QuoteRampStep.builder().lineItem(line).sequence(1).startMonth(1).months(36).quantity(10).build());
            lines.add(line);
        }
        return lines;
    }
    @Test void realEngineGoldenSurvivesCalculateReloadAndAcceptedSnapshot() throws Exception {
        // Captured real R4 engine response with server product authority, SHA256 1C859E882F638B85ECAFC38964480784643DB5DF56A33DBD2E0B05CA631346CA.
        PricingExecuteView response=json.readValue(getClass().getResourceAsStream("/ramp/r4-engine-golden-response.json"),PricingExecuteView.class);
        Quote quote=Quote.builder().id(1L).uuid("00000000-0000-4000-8000-000000000001").quoteNumber("Q-R4").customerExternalId("synthetic-r4-customer")
                .currency("USD").durationInMonths(36).startDate(LocalDate.of(2027,1,1)).endDate(LocalDate.of(2029,12,31)).build();
        List<QuoteLineItem> items=new ArrayList<>();
        long id=2;
        for(var priced:response.getLineResults()) {
            QuoteLineItem line=QuoteLineItem.builder().id(id++).uuid("00000000-0000-4000-8000-"+String.format("%012d",id)).quote(quote).productExternalId(priced.getProductExternalId())
                    .productType(priced.getLineItemType()).lineItemType(priced.getLineItemType()).quantity(priced.getQuantity())
                    .billingFrequency("MONTHLY").listPrice(priced.getListPrice()).sortOrder(items.size())
                    .hybridMigrationJson(json.writeValueAsString(priced.getMetadata().get("hybridMigration"))).build();
            for(var period:priced.getPricedPeriods()) line.getRampSteps().add(QuoteRampStep.builder().lineItem(line).sequence(period.getSequence())
                    .startMonth(period.getStartMonth()).months(period.getMonths()).quantity(period.getQuantity()).priceBasis("ABSOLUTE")
                    .unitPrice(period.getNetUnitPrice()).build());
            items.add(line);
        }
        quote.getLineItems().addAll(items);
        var repository=mock(QuoteRepository.class);var lineRepository=mock(QuoteLineItemRepository.class);var pricing=mock(PricingServiceClient.class);
        when(repository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(lineRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(items);
        when(pricing.executePricing(anyMap())).thenAnswer(inv -> {
            Map<String,Object> context=inv.getArgument(0);
            var requestLines=(List<Map<String,Object>>)context.get("lineItems");
            assertThat(((Map<?,?>)requestLines.getFirst().get("metadata")).get("hybridMigration")).isInstanceOf(HybridMigrationContext.class);
            return ApiResponse.success(response);
        });
        QuoteService service=new QuoteService(repository,Mappers.getMapper(QuoteMapper.class),mock(QuoteNumberGenerator.class),mock(QuoteEventPublisher.class),
                lineRepository,pricing,mock(ProductServiceClient.class),json,mock(OutboxEventPublisher.class),new CurrentActor("service:quote-service",false),mock(RampScheduleGuard.class));
        var dto=service.calculate(1L);
        assertThat(dto.getTcv()).isEqualByComparingTo("43800");assertThat(dto.getAcv()).isEqualByComparingTo("14600");
        assertThat(dto.getArr()).isEqualByComparingTo("18000");assertThat(dto.getExitArr()).isEqualByComparingTo("14400");
        assertThat(service.getById(1L).getLineItems().getFirst().getPricedPeriods().getFirst().getMigrationCredit()).isEqualByComparingTo("3000");
        assertThat(RampPricingSnapshot.read(items.getFirst().getPricingWaterfallJson()).path("migrationCreditAudit").path("effectiveCredit").decimalValue()).isEqualByComparingTo("3000");
        assertThat(RampPricingSnapshot.read(items.getFirst().getPricingWaterfallJson()).path("authoritativeProductType").asText()).isEqualTo("ONPREM_SUPPORT");
        assertThat(RampPricingSnapshot.fingerprint(quote)).isEqualTo(RampPricingSnapshot.fingerprint(quote,items));
        quote.setStatus(QuoteStatus.ACCEPTED);quote.setAcceptedAt(java.time.Instant.parse("2026-10-10T15:00:00Z"));
        var event=QuoteAcceptedKafkaEventFactory.from(quote,items);
        var decline=event.getLineItems().getFirst();
        assertThat(decline.getExtendedAmount()).isEqualByComparingTo("1000");
        assertThat(decline.getRamp().getFirst().getGrossPeriodAmount()).isEqualByComparingTo("12000");
        assertThat(decline.getRamp().getFirst().getMigrationCredit()).isEqualByComparingTo("3000");
        assertThat(decline.getRamp().getFirst().getPeriodAmount()).isEqualByComparingTo("9000");
        assertThat(decline.getRamp().getLast().getMigrationCredit()).isNull();
        assertThat(event.getLineItems().getLast().getRamp().getFirst().getGrossPeriodAmount()).isNull();
        assertThat(decline.getHybridMigration().getBookValue()).isEqualByComparingTo("10000");
        String capture = System.getProperty("quote.r4.accepted.capture");
        if (capture != null) java.nio.file.Files.writeString(java.nio.file.Path.of(capture), json.writerWithDefaultPrettyPrinter().writeValueAsString(event));
        String originalContext=items.getFirst().getHybridMigrationJson();
        items.getFirst().setHybridMigrationJson(originalContext.replace("10000.0","9000.0"));
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,items)).isInstanceOf(BusinessException.class);
        items.getFirst().setHybridMigrationJson(originalContext);items.getFirst().setLineItemType("SUBSCRIPTION");
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,items)).isInstanceOf(BusinessException.class);
        items.getFirst().setLineItemType("ONPREM_SUPPORT");items.getLast().setProductExternalId(items.getFirst().getProductExternalId());
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,items)).isInstanceOf(BusinessException.class);
    }
    @Test void unknownHybridAuthoringFieldIsRejectedRatherThanDiscarded() {
        assertThatThrownBy(() -> json.readValue("{\"pairId\":\"pair\",\"role\":\"DECLINING\",\"startMonth\":1,\"months\":12,\"bookValue\":10000,\"credit\":999}",HybridMigrationContext.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
    @Test void authoredHybridWindowCannotExceedAcceptedTwelveMonthCeiling() {
        assertThatThrownBy(() -> new HybridMigrationContext("pair","DECLINING",1,13,new BigDecimal("10000")))
                .isInstanceOf(BusinessException.class);
    }
    @Test void typedHybridMonthAndBookValuesDoNotCoerceStringsOrFractionalMonths() {
        for(String body:List.of("{\"pairId\":\"pair\",\"role\":\"DECLINING\",\"startMonth\":\"1\",\"months\":12,\"bookValue\":10000}",
                "{\"pairId\":\"pair\",\"role\":\"DECLINING\",\"startMonth\":1,\"months\":1.5,\"bookValue\":10000}",
                "{\"pairId\":\"pair\",\"role\":\"DECLINING\",\"startMonth\":1,\"months\":12,\"bookValue\":\"10000\"}"))
            assertThatThrownBy(() -> json.readValue(body,HybridMigrationContext.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
    @Test void boot4Jackson3AuthoringAlsoRejectsUnknownFieldsAndNumericStrings() {
        var httpJson=tools.jackson.databind.json.JsonMapper.builder().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        for(String body:List.of("{\"pairId\":\"pair\",\"role\":\"DECLINING\",\"startMonth\":1,\"months\":12,\"bookValue\":10000,\"credit\":999}",
                "{\"pairId\":\"pair\",\"role\":\"DECLINING\",\"startMonth\":\"1\",\"months\":12,\"bookValue\":10000}"))
            assertThatThrownBy(() -> httpJson.readValue(body,HybridMigrationContext.class)).isInstanceOf(tools.jackson.core.JacksonException.class);
    }
    @Test void boot4HttpLineAuthoringPreservesValidTypesAndRejectsInvalidHybridBeforeService() throws Exception {
        var lines=mock(QuoteLineItemService.class);
        when(lines.addLineItem(anyLong(),any())).thenReturn(QuoteLineItemDto.builder().id(2L).build());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new QuoteLineItemController(lines))
                .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter()).build();
        String prefix="{\"productExternalId\":\"synthetic-support\",\"listPrice\":100,\"quantity\":10,\"hybridMigration\":";
        String valid="{\"pairId\":\"pair\",\"role\":\"DECLINING\",\"startMonth\":1,\"months\":12,\"bookValue\":10000}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/quotes/1/line-items")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(prefix+valid+"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        var captured=org.mockito.ArgumentCaptor.forClass(LineItemCreateRequest.class);
        verify(lines).addLineItem(eq(1L),captured.capture());
        assertThat(captured.getValue().getHybridMigration().bookValue()).isEqualByComparingTo("10000");
        clearInvocations(lines);
        for(String invalid:List.of(valid.replace("\"startMonth\":1","\"startMonth\":\"1\""),
                valid.replace("\"months\":12","\"months\":1.5"),valid.replace("10000}","10000,\"credit\":999}"),
                "{\"pairId\":\"pair\",\"role\":\"RISING\",\"startMonth\":1,\"months\":12,\"bookValue\":null}")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/quotes/1/line-items")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(prefix+invalid+"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
        verifyNoInteractions(lines);
    }
}
