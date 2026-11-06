package com.opencrm.contract.documents;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-665 AC5 — QuoteAccepted path stays interim; this story does not wire contract_lines writers there.
 */
@Tag("unit")
class ContractLineQuoteAcceptedPathUnchangedTest {

    @Test
    void acceptedQuoteSubscriptionServiceDoesNotOpenContractLines() throws IOException {
        Path path = Path.of("src/main/java/com/opencrm/contract/service/AcceptedQuoteSubscriptionService.java");
        if (!Files.exists(path)) {
            path = Path.of("backend/services/contract-asset-management")
                    .resolve("src/main/java/com/opencrm/contract/service/AcceptedQuoteSubscriptionService.java");
        }
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(source).doesNotContain("ContractLineOpenService");
        assertThat(source).doesNotContain("contract_lines");
        assertThat(source).doesNotContain("setContractLineId");
    }
}
