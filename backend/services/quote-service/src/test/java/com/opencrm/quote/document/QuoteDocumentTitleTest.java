package com.opencrm.quote.document;

import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.security.CurrentActor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteDocumentTitleTest {

    @ParameterizedTest
    @CsvSource({
            "PROPOSAL, Quote Proposal",
            "ORDER_FORM, Order Form",
            "SOW, Statement of Work"
    })
    void generatedPdfUsesRequestedTitleAndRetainsCommercialContent(DocumentType type, String title) throws Exception {
        QuoteDocumentRepository documents = mock(QuoteDocumentRepository.class);
        QuoteRepository quotes = mock(QuoteRepository.class);
        QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
        QuoteDocumentService service = new QuoteDocumentService(
                documents, quotes, lines, templateEngine(), new CurrentActor("service:quote-service", false));

        Quote quote = Quote.builder().quoteNumber("Q-TITLE-TEST").name("Synthetic quote")
                .customerExternalId("SYNTHETIC-CUSTOMER").currency("USD").durationInMonths(12)
                .subtotal(new BigDecimal("125")).totalAmount(new BigDecimal("125"))
                .totalDiscount(BigDecimal.ZERO).totalTax(BigDecimal.ZERO).build();
        QuoteLineItem line = QuoteLineItem.builder().quote(quote).productName("Synthetic line")
                .quantity(1).listPrice(new BigDecimal("125")).extendedAmount(new BigDecimal("125")).build();
        when(quotes.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote));
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(7L)).thenReturn(List.of(line));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.generateDocument(7L, type).getDocumentType()).isEqualTo(type);

        ArgumentCaptor<QuoteDocument> saved = ArgumentCaptor.forClass(QuoteDocument.class);
        verify(documents).save(saved.capture());
        try (PDDocument pdf = PDDocument.load(saved.getValue().getContent())) {
            String content = new PDFTextStripper().getText(pdf);
            assertThat(content.stripLeading().lines().findFirst()).contains(title);
            assertThat(content).contains(
                    "Q-TITLE-TEST", "Synthetic quote", "SYNTHETIC-CUSTOMER", "Synthetic line", "$125.00");
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo(title);
        }
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
