package com.opencrm.common.event;

import com.opencrm.common.event.AmendmentLifecycleEventCatalog.Entry;
import com.opencrm.common.event.AmendmentLifecycleEventCatalog.Publication;
import com.opencrm.common.event.dto.AmendmentLifecycleEventContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-S6-4. 'Registered' means a catalog entry, a DTO, and a contract test. Nothing is published and
 * nothing is emitted. EventContractRegistry and KafkaTopics are not the registry for these events.
 */
@DisplayName("Amendment lifecycle catalog (AC-S6-4)")
class AmendmentLifecycleEventCatalogTest {

    private static final List<String> EVENT_TYPES = List.of(
            "AmendmentDrafted",
            "AmendmentSubmitted",
            "AmendmentApproved",
            "AmendmentExecuted",
            "AmendmentUnitApplied",
            "AmendmentApplied",
            "AmendmentApplyBlocked",
            "AmendmentWithdrawn");

    private static final List<String> DOCUMENT_STATUSES = List.of(
            "DRAFT",
            "IN_REVIEW",
            "APPROVED",
            "EXECUTED",
            "PARTIALLY_APPLIED",
            "APPLIED",
            "APPLY_BLOCKED",
            "WITHDRAWN");

    /** S3 AmendmentDocumentStatus names. Three of them have no lifecycle event in this catalog. */
    private static final Set<String> S3_STATUS_NAMES = Set.of(
            "DRAFT",
            "IN_REVIEW",
            "APPROVED",
            "OUT_FOR_SIGNATURE",
            "REJECTED",
            "EXECUTED",
            "WITHDRAWN",
            "APPLIED",
            "PARTIALLY_APPLIED",
            "APPLY_BLOCKED",
            "VOID");

    @Test
    @DisplayName("eight RESERVED entries, each with a DTO and a contract test, none PUBLISHED")
    void eightReservedEntriesEachHaveADtoAndAContractTest() {
        List<Entry> entries = AmendmentLifecycleEventCatalog.all();

        assertThat(entries).hasSize(8);
        assertThat(entries).extracting(Entry::eventType).containsExactlyElementsOf(EVENT_TYPES);
        assertThat(entries).extracting(Entry::documentStatus).containsExactlyElementsOf(DOCUMENT_STATUSES);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.publication()).isEqualTo(Publication.RESERVED);
            assertThat(entry.eventVersion()).isEqualTo(1);
            assertThat(entry.payloadClass()).isNotNull();
            assertThat(entry.payloadClass().isRecord()).isTrue();
            assertThat(entry.payloadClass().getSimpleName()).isEqualTo(entry.eventType());
        });
        assertThat(entries).noneMatch(entry -> entry.publication() == Publication.PUBLISHED);
        assertThat(entries).extracting(Entry::payloadClass).doesNotHaveDuplicates();
        assertThat(S3_STATUS_NAMES).containsAll(entries.stream().map(Entry::documentStatus).toList());
        assertThat(AmendmentLifecycleEventContractTest.testedPayloads())
                .containsExactlyInAnyOrderElementsOf(entries.stream().map(Entry::payloadClass).toList());
        assertThat(entries).allSatisfy(entry -> {
            for (RecordComponent component : entry.payloadClass().getRecordComponents()) {
                assertThat(component.getName()).isNotEqualTo("tenantId");
            }
        });
    }

    @Test
    @DisplayName("the published event registry does not list these types")
    void notRegisteredInThePublishedEventContractRegistry() {
        Set<String> publishedTypes = EventContractRegistry.all().stream()
                .map(EventContractRegistry.EventContract::eventType)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(publishedTypes).doesNotContainAnyElementsOf(EVENT_TYPES);
    }

    @Test
    @DisplayName("catalog and DTO sources do not emit")
    void lifecycleSourcesDoNotEmit() throws IOException {
        Path backend = locateBackend();
        List<Path> files = Stream.concat(
                Stream.of(backend.resolve("libs/common/src/main/java/com/opencrm/common/event/AmendmentLifecycleEventCatalog.java")),
                EVENT_TYPES.stream().map(name -> backend.resolve(
                        "libs/common/src/main/java/com/opencrm/common/event/dto/" + name + ".java"))
        ).toList();
        Path consumer = backend.resolve(
                "services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java");
        assertThat(files).allSatisfy(file -> assertThat(file).exists());
        for (Path file : Stream.concat(files.stream(), Stream.of(consumer)).toList()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(text).as(file.toString()).doesNotContain("KafkaTemplate");
            assertThat(text).as(file.toString()).doesNotContain(".publish(");
            assertThat(text).as(file.toString()).doesNotContain("OutboxEventPublisher");
        }
    }

    private static Path locateBackend() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("libs").resolve("common"))
                    && Files.isDirectory(dir.resolve("services"))) {
                return dir;
            }
            Path nested = dir.resolve("backend");
            if (Files.isDirectory(nested.resolve("libs").resolve("common"))) {
                return nested;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("backend not found above " + Path.of("").toAbsolutePath());
    }
}
