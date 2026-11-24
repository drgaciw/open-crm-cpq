package com.opencrm.quote.document;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.security.CurrentActor;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuoteDocumentReadTest {

    private static final long QUOTE_ID = 7L;
    private static final long DOCUMENT_ID = 11L;
    private static final byte[] PDF = "%PDF-test".getBytes(StandardCharsets.UTF_8);

    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final QuoteDocumentRepository documents = mock(QuoteDocumentRepository.class);
    private final QuoteDocumentController controller = new QuoteDocumentController(new QuoteDocumentService(
            documents, quotes, mock(QuoteLineItemRepository.class), mock(SpringTemplateEngine.class),
            new CurrentActor("service:quote-service", false)));

    @Test
    void listsDocumentsForAnActiveQuote() {
        activeQuote();
        when(documents.findByQuoteIdAndDeletedFalse(QUOTE_ID)).thenReturn(List.of(document(QUOTE_ID)));

        assertThat(controller.list(QUOTE_ID).getData()).singleElement().satisfies(document -> {
            assertThat(document.getId()).isEqualTo(DOCUMENT_ID);
            assertThat(document.getQuoteId()).isEqualTo(QUOTE_ID);
        });
    }

    @Test
    void downloadsUnchangedBytesForAnActiveQuote() {
        activeQuote();
        when(documents.findByIdAndDeletedFalse(DOCUMENT_ID)).thenReturn(Optional.of(document(QUOTE_ID)));

        assertThat(controller.download(QUOTE_ID, DOCUMENT_ID).getBody()).isEqualTo(PDF);
    }

    @Test
    void listRejectsAnUnavailableQuoteEvenWhenDocumentsAreRetained() {
        when(quotes.findByIdAndDeletedFalse(QUOTE_ID)).thenReturn(Optional.empty());
        when(documents.findByQuoteIdAndDeletedFalse(QUOTE_ID)).thenReturn(List.of(document(QUOTE_ID)));

        assertNotFound(() -> controller.list(QUOTE_ID), "Quote", QUOTE_ID);

        verifyNoInteractions(documents);
    }

    @Test
    void downloadRejectsAnUnavailableQuoteEvenWhenTheDocumentIsRetained() {
        when(quotes.findByIdAndDeletedFalse(QUOTE_ID)).thenReturn(Optional.empty());
        when(documents.findByIdAndDeletedFalse(DOCUMENT_ID)).thenReturn(Optional.of(document(QUOTE_ID)));

        assertNotFound(() -> controller.download(QUOTE_ID, DOCUMENT_ID), "Quote", QUOTE_ID);

        verifyNoInteractions(documents);
    }

    @Test
    void downloadRejectsADocumentOwnedByAnotherQuote() {
        activeQuote();
        when(documents.findByIdAndDeletedFalse(DOCUMENT_ID)).thenReturn(Optional.of(document(23L)));

        assertNotFound(() -> controller.download(QUOTE_ID, DOCUMENT_ID), "QuoteDocument", DOCUMENT_ID);
    }

    @Test
    void downloadRejectsAMissingDocumentForAnActiveQuote() {
        activeQuote();
        when(documents.findByIdAndDeletedFalse(DOCUMENT_ID)).thenReturn(Optional.empty());

        assertNotFound(() -> controller.download(QUOTE_ID, DOCUMENT_ID), "QuoteDocument", DOCUMENT_ID);
    }

    private void activeQuote() {
        when(quotes.findByIdAndDeletedFalse(QUOTE_ID)).thenReturn(Optional.of(Quote.builder().id(QUOTE_ID).build()));
    }

    private static QuoteDocument document(long quoteId) {
        return QuoteDocument.builder().id(DOCUMENT_ID).quoteId(quoteId).fileName("proposal.pdf").content(PDF).build();
    }

    private static void assertNotFound(ThrowingCallable action, String resourceType, long id) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResourceNotFoundException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(error.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
            assertThat(error).hasMessage("%s not found with identifier: %d", resourceType, id);
        });
    }
}
