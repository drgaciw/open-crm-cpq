package com.opencrm.quote.quote;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/** Create and update must ignore a client-supplied continuation operation. The column is server-authored. */
@Tag("unit")
class QuoteContinuationOperationMappingTest {
    private final QuoteMapper mapper = Mappers.getMapper(QuoteMapper.class);

    @Test
    void createAndUpdateIgnoreClientSuppliedContinuationOperation() {
        Quote created = mapper.toEntity(QuoteCreateRequest.builder()
                .name("Client extension")
                .customerExternalId("synthetic-customer")
                .continuationOperation(QuoteContinuationRequest.Operation.EXTENSION)
                .build());
        assertThat(created.getContinuationOperation()).isNull();

        Quote quote = Quote.builder()
                .quoteNumber("Q-1")
                .name("Renewal")
                .customerExternalId("synthetic-customer")
                .continuationOperation(QuoteContinuationRequest.Operation.RENEWAL)
                .build();
        mapper.updateEntity(QuoteUpdateRequest.builder()
                .name("Renamed")
                .continuationOperation(QuoteContinuationRequest.Operation.EXTENSION)
                .build(), quote);
        assertThat(quote.getName()).isEqualTo("Renamed");
        assertThat(quote.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.RENEWAL);
    }
}
