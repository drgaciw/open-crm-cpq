package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.security.CurrentActor;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemDto;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuoteService.clone copies line items (Wave M3)")
class QuoteServiceCloneTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private QuoteMapper quoteMapper;
    @Mock
    private QuoteNumberGenerator quoteNumberGenerator;
    @Mock
    private QuoteEventPublisher eventPublisher;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepository;
    @Mock
    private PricingServiceClient pricingServiceClient;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(
                quoteRepository,
                quoteMapper,
                quoteNumberGenerator,
                eventPublisher,
                quoteLineItemRepository,
                pricingServiceClient,
                productServiceClient,
                new ObjectMapper(),
                outboxEventPublisher,
                new CurrentActor("service:quote-service", false),
                RampScheduleTestSupport.guard(quoteLineItemRepository)
        );
    }

    @Test
    @DisplayName("clone copies N non-deleted lines with new ids and commercial snapshot")
    void cloneCopiesLinesWithNewIds() {
        Quote original = sourceQuote();
        QuoteLineItem line1 = sourceLine(original, 10L, "PROD-A", 0,
                new BigDecimal("100.00"), new BigDecimal("90.00"),
                new BigDecimal("0.1000"), new BigDecimal("10.00"),
                new BigDecimal("90.00"), new BigDecimal("180.00"));
        line1.setPricingExplanationCode("LIST_DISC");
        line1.setPricingWaterfallJson("{\"steps\":[]}");
        QuoteLineItem line2 = sourceLine(original, 11L, "PROD-B", 1,
                new BigDecimal("50.00"), new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("50.00"), new BigDecimal("50.00"));
        line2.setPricingExplanationCode("LIST");
        line2.setPricingWaterfallJson("{\"list\":50}");

        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(original));
        when(quoteNumberGenerator.generate()).thenReturn("Q-COPY-1");
        stubQuoteSaves(99L);
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line1, line2));
        stubLineItemSaveAll(1000L);
        when(quoteMapper.toDto(any(Quote.class))).thenAnswer(inv -> toDtoAnswer(inv.getArgument(0)));

        QuoteDto dto = quoteService.clone(1L);

        assertThat(dto.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(dto.getQuoteNumber()).isEqualTo("Q-COPY-1");
        assertThat(dto.getName()).isEqualTo("Acme Deal (Copy)");
        assertThat(dto.getLineItems()).hasSize(2);

        QuoteLineItemDto copy1 = dto.getLineItems().get(0);
        assertThat(copy1.getId()).isEqualTo(1000L);
        assertThat(copy1.getId()).isNotEqualTo(line1.getId());
        assertThat(copy1.getProductExternalId()).isEqualTo("PROD-A");
        assertThat(copy1.getProductName()).isEqualTo("Product A");
        assertThat(copy1.getProductType()).isEqualTo("SOFTWARE");
        assertThat(copy1.getLineItemType()).isEqualTo("SUBSCRIPTION");
        assertThat(copy1.getQuantity()).isEqualTo(2);
        assertThat(copy1.getUnitOfMeasure()).isEqualTo("EA");
        assertThat(copy1.getListPrice()).isEqualByComparingTo("100.00");
        assertThat(copy1.getUnitPrice()).isEqualByComparingTo("90.00");
        assertThat(copy1.getDiscountRate()).isEqualByComparingTo("0.1000");
        assertThat(copy1.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(copy1.getNetPrice()).isEqualByComparingTo("90.00");
        assertThat(copy1.getExtendedAmount()).isEqualByComparingTo("180.00");
        assertThat(copy1.getBillingFrequency()).isEqualTo("MONTHLY");
        assertThat(copy1.getSortOrder()).isZero();
        assertThat(copy1.getNotes()).isEqualTo("note-a");
        assertThat(copy1.getPricingExplanationCode()).isNull();

        QuoteLineItemDto copy2 = dto.getLineItems().get(1);
        assertThat(copy2.getId()).isEqualTo(1001L);
        assertThat(copy2.getId()).isNotEqualTo(line2.getId());
        assertThat(copy2.getProductExternalId()).isEqualTo("PROD-B");
        assertThat(copy2.getExtendedAmount()).isEqualByComparingTo("50.00");
        assertThat(copy2.getPricingExplanationCode()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuoteLineItem>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(quoteLineItemRepository).saveAll(linesCaptor.capture());
        List<QuoteLineItem> persisted = linesCaptor.getValue();
        assertThat(persisted).hasSize(2);
        assertThat(persisted.get(0).getPricingExplanationCode()).isNull();
        assertThat(persisted.get(0).getPricingWaterfallJson()).isNull();
        assertThat(persisted.get(1).getPricingExplanationCode()).isNull();
        assertThat(persisted.get(1).getPricingWaterfallJson()).isNull();
        assertThat(persisted.get(0).getMetadata()).isEqualTo("{\"tier\":\"gold\"}");
        assertThat(persisted.get(0).getQuote().getId()).isEqualTo(99L);

        verify(eventPublisher, never()).publishQuoteAccepted(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("clone with empty source lines returns empty lineItems and zero totals")
    void cloneEmptySourceYieldsEmptyClone() {
        Quote original = sourceQuote();
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(original));
        when(quoteNumberGenerator.generate()).thenReturn("Q-COPY-EMPTY");
        stubQuoteSaves(99L);
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of());
        when(quoteMapper.toDto(any(Quote.class))).thenAnswer(inv -> toDtoAnswer(inv.getArgument(0)));

        QuoteDto dto = quoteService.clone(1L);

        assertThat(dto.getLineItems()).isEmpty();
        assertThat(dto.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getTotalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(quoteLineItemRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("clone omits deleted lines (repository returns only non-deleted)")
    void cloneOmitsDeletedLines() {
        Quote original = sourceQuote();
        QuoteLineItem active = sourceLine(original, 10L, "PROD-ACTIVE", 0,
                new BigDecimal("20.00"), new BigDecimal("20.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("20.00"), new BigDecimal("20.00"));

        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(original));
        when(quoteNumberGenerator.generate()).thenReturn("Q-COPY-2");
        stubQuoteSaves(99L);
        // Soft-deleted lines are filtered by findByQuoteIdAndDeletedFalseOrderBySortOrderAsc
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(active));
        stubLineItemSaveAll(2000L);
        when(quoteMapper.toDto(any(Quote.class))).thenAnswer(inv -> toDtoAnswer(inv.getArgument(0)));

        QuoteDto dto = quoteService.clone(1L);

        assertThat(dto.getLineItems()).hasSize(1);
        assertThat(dto.getLineItems().get(0).getProductExternalId()).isEqualTo("PROD-ACTIVE");
        verify(quoteLineItemRepository).findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L);
    }

    @Test
    @DisplayName("clone rolls up totals like QuoteLineItemService.recalculateQuoteTotals")
    void cloneRollsUpTotalsFromExtendedAndDiscount() {
        Quote original = sourceQuote();
        original.setArr(new BigDecimal("999"));
        original.setTcv(new BigDecimal("888"));
        original.setAcv(new BigDecimal("777"));
        original.setPricingWaterfallJson("{\"header\":true}");
        original.setAcceptedAt(java.time.Instant.parse("2026-01-15T00:00:00Z"));
        original.setApprovalState(ApprovalState.APPROVED);
        original.setApprovalTier("TIER_2");

        QuoteLineItem line1 = sourceLine(original, 10L, "PROD-A", 0,
                new BigDecimal("100.00"), new BigDecimal("90.00"),
                new BigDecimal("0.1000"), new BigDecimal("10.00"),
                new BigDecimal("90.00"), new BigDecimal("180.00"));
        QuoteLineItem line2 = sourceLine(original, 11L, "PROD-B", 1,
                new BigDecimal("50.00"), new BigDecimal("40.00"),
                new BigDecimal("0.2000"), new BigDecimal("10.00"),
                new BigDecimal("40.00"), new BigDecimal("40.00"));

        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(original));
        when(quoteNumberGenerator.generate()).thenReturn("Q-COPY-3");
        ArgumentCaptor<Quote> quoteSaveCaptor = ArgumentCaptor.forClass(Quote.class);
        AtomicLong quoteId = new AtomicLong(99L);
        when(quoteRepository.save(quoteSaveCaptor.capture())).thenAnswer(inv -> {
            Quote q = inv.getArgument(0);
            if (q.getId() == null) {
                q.setId(quoteId.get());
            }
            return q;
        });
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line1, line2));
        stubLineItemSaveAll(3000L);
        when(quoteMapper.toDto(any(Quote.class))).thenAnswer(inv -> toDtoAnswer(inv.getArgument(0)));

        QuoteDto dto = quoteService.clone(1L);

        // subtotal = 180 + 40 = 220; totalDiscount = 10 + 10 = 20; totalAmount = 200
        assertThat(dto.getSubtotal()).isEqualByComparingTo("220.00");
        assertThat(dto.getTotalDiscount()).isEqualByComparingTo("20.00");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("200.00");

        Quote lastSaved = quoteSaveCaptor.getAllValues().get(quoteSaveCaptor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(lastSaved.getArr()).isNull();
        assertThat(lastSaved.getTcv()).isNull();
        assertThat(lastSaved.getAcv()).isNull();
        assertThat(lastSaved.getPricingWaterfallJson()).isNull();
        assertThat(lastSaved.getAcceptedAt()).isNull();
        assertThat(lastSaved.getApprovalState()).isEqualTo(ApprovalState.NONE);
        assertThat(lastSaved.getApprovalTier()).isNull();
    }

    @Test
    @DisplayName("cloned line waterfall fields are null/absent on DTO")
    void cloneWaterfallFieldsAbsent() {
        Quote original = sourceQuote();
        QuoteLineItem line = sourceLine(original, 10L, "PROD-A", 0,
                new BigDecimal("10.00"), new BigDecimal("10.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10.00"), new BigDecimal("10.00"));
        line.setPricingExplanationCode("SHOULD_NOT_COPY");
        line.setPricingWaterfallJson("{\"should\":\"not-copy\"}");

        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(original));
        when(quoteNumberGenerator.generate()).thenReturn("Q-COPY-4");
        stubQuoteSaves(99L);
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line));
        stubLineItemSaveAll(4000L);
        when(quoteMapper.toDto(any(Quote.class))).thenAnswer(inv -> toDtoAnswer(inv.getArgument(0)));

        QuoteDto dto = quoteService.clone(1L);

        assertThat(dto.getLineItems()).hasSize(1);
        assertThat(dto.getLineItems().get(0).getPricingExplanationCode()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuoteLineItem>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(quoteLineItemRepository).saveAll(linesCaptor.capture());
        QuoteLineItem persisted = linesCaptor.getValue().get(0);
        assertThat(persisted.getPricingExplanationCode()).isNull();
        assertThat(persisted.getPricingWaterfallJson()).isNull();
    }

    private void stubQuoteSaves(long newId) {
        when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> {
            Quote q = inv.getArgument(0);
            if (q.getId() == null) {
                q.setId(newId);
            }
            return q;
        });
    }

    private void stubLineItemSaveAll(long startId) {
        AtomicLong nextId = new AtomicLong(startId);
        when(quoteLineItemRepository.saveAll(anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<QuoteLineItem> items = inv.getArgument(0);
            for (QuoteLineItem item : items) {
                if (item.getId() == null) {
                    item.setId(nextId.getAndIncrement());
                }
            }
            return items;
        });
    }

    private static QuoteDto toDtoAnswer(Quote quote) {
        List<QuoteLineItemDto> lineDtos = new ArrayList<>();
        if (quote.getLineItems() != null) {
            for (QuoteLineItem item : quote.getLineItems()) {
                lineDtos.add(QuoteLineItemDto.builder()
                        .id(item.getId())
                        .uuid(item.getUuid())
                        .productExternalId(item.getProductExternalId())
                        .productName(item.getProductName())
                        .productType(item.getProductType())
                        .lineItemType(item.getLineItemType())
                        .quantity(item.getQuantity())
                        .unitOfMeasure(item.getUnitOfMeasure())
                        .listPrice(item.getListPrice())
                        .unitPrice(item.getUnitPrice())
                        .discountRate(item.getDiscountRate())
                        .discountAmount(item.getDiscountAmount())
                        .netPrice(item.getNetPrice())
                        .extendedAmount(item.getExtendedAmount())
                        .billingFrequency(item.getBillingFrequency())
                        .sortOrder(item.getSortOrder())
                        .notes(item.getNotes())
                        .pricingExplanationCode(item.getPricingExplanationCode())
                        .build());
            }
        }
        return QuoteDto.builder()
                .id(quote.getId())
                .quoteNumber(quote.getQuoteNumber())
                .name(quote.getName())
                .status(quote.getStatus())
                .subtotal(quote.getSubtotal())
                .totalDiscount(quote.getTotalDiscount())
                .totalAmount(quote.getTotalAmount())
                .arr(quote.getArr())
                .tcv(quote.getTcv())
                .acv(quote.getAcv())
                .approvalState(quote.getApprovalState())
                .approvalTier(quote.getApprovalTier())
                .lineItems(lineDtos)
                .build();
    }

    private static Quote sourceQuote() {
        Quote quote = Quote.builder()
                .quoteNumber("Q-SRC-1")
                .name("Acme Deal")
                .description("Source desc")
                .customerExternalId("CUST-1")
                .dealExternalId("DEAL-1")
                .status(QuoteStatus.SENT)
                .dealType(DealType.NEW)
                .subscriptionType("ANNUAL")
                .currency("USD")
                .region("NA")
                .durationInMonths(12)
                .notes("header notes")
                .build();
        quote.setId(1L);
        return quote;
    }

    private static QuoteLineItem sourceLine(
            Quote quote,
            Long id,
            String productExternalId,
            int sortOrder,
            BigDecimal listPrice,
            BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal discountAmount,
            BigDecimal netPrice,
            BigDecimal extendedAmount) {
        QuoteLineItem item = QuoteLineItem.builder()
                .quote(quote)
                .productExternalId(productExternalId)
                .productName(productExternalId.startsWith("PROD-A") ? "Product A" : "Product B")
                .productType("SOFTWARE")
                .lineItemType("SUBSCRIPTION")
                .quantity(productExternalId.equals("PROD-A") ? 2 : 1)
                .unitOfMeasure("EA")
                .listPrice(listPrice)
                .unitPrice(unitPrice)
                .discountRate(discountRate)
                .discountAmount(discountAmount)
                .netPrice(netPrice)
                .extendedAmount(extendedAmount)
                .billingFrequency("MONTHLY")
                .sortOrder(sortOrder)
                .notes(productExternalId.equals("PROD-A") ? "note-a" : "note-b")
                .metadata(productExternalId.equals("PROD-A") ? "{\"tier\":\"gold\"}" : null)
                .build();
        item.setId(id);
        return item;
    }
}
