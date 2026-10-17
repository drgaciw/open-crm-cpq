package com.opencrm.contract.documents;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AMENDMENT_SUPERSEDED and AMENDMENT_REVISION_REJECTED are local document-log names.
 * The S2 writer stores that string on the event row. The outbox publication is the
 * existing published contract AMENDMENT_AUDIT_RECORDED_V1. Neither name is a registry entry.
 */
class AmendmentLocalAuditNamesTest {

    @Test
    void localNamesAreAcceptedWithoutARegistryEdit() throws IOException {
        String registry = Files.readString(repositoryFile(
                "backend/libs/common/src/main/java/com/opencrm/common/event/EventContractRegistry.java"));
        assertThat(registry).doesNotContain("AMENDMENT_SUPERSEDED");
        assertThat(registry).doesNotContain("AMENDMENT_REVISION_REJECTED");
        assertThat(registry).contains("AMENDMENT_AUDIT_RECORDED_V1");

        String outbox = Files.readString(repositoryFile(
                "backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/audit/AmendmentAuditOutbox.java"));
        assertThat(outbox).contains("EventContractRegistry.AMENDMENT_AUDIT_RECORDED_V1");
        assertThat(outbox).doesNotContain("AMENDMENT_SUPERSEDED");
        assertThat(outbox).doesNotContain("AMENDMENT_REVISION_REJECTED");

        String writer = Files.readString(repositoryFile(
                "backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/events/AmendmentEventWriter.java"));
        assertThat(writer).doesNotContain("EventContractRegistry");
        assertThat(writer).contains("command.eventType()");
    }

    private static Path repositoryFile(String relative) {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve(relative))) {
            cursor = cursor.getParent();
        }
        assertThat(cursor).as(relative).isNotNull();
        return cursor.resolve(relative);
    }
}
