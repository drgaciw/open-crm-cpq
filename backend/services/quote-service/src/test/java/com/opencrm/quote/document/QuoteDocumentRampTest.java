package com.opencrm.quote.document;

import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.ramp.QuoteRampStep;
import com.opencrm.quote.ramp.RampScheduleException;
import com.opencrm.security.CurrentActor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuoteDocumentRampTest {
    private final QuoteDocumentRepository documents = mock(QuoteDocumentRepository.class);
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
    private final QuoteDocumentService service = new QuoteDocumentService(
            documents, quotes, lines, templateEngine(), new CurrentActor("service:quote-service", false));

    @Test
    void generatedPdfIncludesPersistedAc01PeriodsAndEscapesProductName() throws Exception {
        Quote quote = quote();
        QuoteLineItem line = QuoteLineItem.builder()
                .quote(quote)
                .productName("Seats <script>unsafe</script>")
                .quantity(50).listPrice(new BigDecimal("50")).build();
        line.setRampSteps(List.of(QuoteRampStep.builder().sequence(1).startMonth(1).months(12).build(),
                QuoteRampStep.builder().sequence(2).startMonth(13).months(12).build(),
                QuoteRampStep.builder().sequence(3).startMonth(25).months(12).build()));
        line.setPricingWaterfallJson("""
                {"pricedPeriods":[
                {"sequence":1,"startMonth":1,"months":12,"quantity":50,"netUnitPrice":40,"netMonthly":2000,"periodAmount":24000},
                {"sequence":2,"startMonth":13,"months":12,"quantity":50,"netUnitPrice":45,"netMonthly":2250,"periodAmount":27000},
                {"sequence":3,"startMonth":25,"months":12,"quantity":50,"netUnitPrice":50,"netMonthly":2500,"periodAmount":30000}]}
                """);
        given(quote, line);
        AtomicReference<QuoteDocument> saved = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            QuoteDocument document = invocation.getArgument(0);
            saved.set(document);
            return document;
        });

        service.generateDocument(7L, DocumentType.values()[0]);

        try (PDDocument pdf = PDDocument.load(saved.get().getContent())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Ramp Schedule", "Seats <script>unsafe</script>",
                    "USD 40.0000", "USD 45.0000", "USD 50.0000", "USD 24000.00", "USD 27000.00", "USD 30000.00",
                    "Initial amount:", "USD 2000.00", "Total contract value:", "USD 81000.00", "Annual contract value:",
                    "Starting annual run rate:", "Exit annual run rate:");
        }
    }

    @Test
    void flatQuoteStillGeneratesPdfWithoutScheduleTable() throws Exception {
        QuoteLineItem line = QuoteLineItem.builder().productName("Flat seats")
                .quantity(1).listPrice(new BigDecimal("50")).build();
        line.setPricingWaterfallJson("{\"steps\":[]}");
        given(quote(), line);
        AtomicReference<QuoteDocument> saved = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            QuoteDocument document = invocation.getArgument(0);
            saved.set(document);
            return document;
        });
        service.generateDocument(7L, DocumentType.values()[0]);
        try (PDDocument pdf = PDDocument.load(saved.get().getContent())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Flat seats").doesNotContain("Ramp Schedule");
        }
    }

    @Test
    void staleQuoteRefusesDocumentWithoutPersistingPdf() {
        Quote quote = quote();
        quote.setRampScheduleStale(true);
        given(quote, QuoteLineItem.builder().productName("Seats").build());
        assertThatThrownBy(() -> service.generateDocument(7L, DocumentType.values()[0]))
                .isInstanceOf(RampScheduleException.class);
        verifyNoInteractions(documents);
    }

    @Test
    void unpricedAuthoredScheduleRefusesDocument() {
        Quote quote = quote();
        QuoteLineItem line = QuoteLineItem.builder().quote(quote).productName("Seats").build();
        line.setRampSteps(List.of(QuoteRampStep.builder().sequence(1).startMonth(1).months(36).build()));
        given(quote, line);
        assertThatThrownBy(() -> service.generateDocument(7L, DocumentType.values()[0]))
                .isInstanceOf(RampScheduleException.class);
        verifyNoInteractions(documents);
    }

    private void given(Quote quote, QuoteLineItem line) {
        when(quotes.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote));
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(7L)).thenReturn(List.of(line));
    }

    private static Quote quote() {
        return Quote.builder().quoteNumber("Q-RAMP-TEST").name("Acceptance sample").currency("USD")
                .durationInMonths(36).subtotal(new BigDecimal("2500")).totalAmount(new BigDecimal("2000"))
                .totalDiscount(new BigDecimal("500")).totalTax(BigDecimal.ZERO)
                .tcv(new BigDecimal("81000")).acv(new BigDecimal("27000"))
                .arr(new BigDecimal("24000")).exitArr(new BigDecimal("30000")).build();
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
