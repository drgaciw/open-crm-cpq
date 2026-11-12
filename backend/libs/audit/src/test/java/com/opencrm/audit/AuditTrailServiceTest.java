package com.opencrm.audit;

import com.opencrm.audit.dto.AppendAuditEntryCommand;
import com.opencrm.audit.dto.AuditEntryDto;
import com.opencrm.audit.dto.SearchAuditTrailQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailServiceTest {

    @Mock
    private AuditEntryRepository repository;

    private final AuditEntryMapper mapper = org.mapstruct.factory.Mappers.getMapper(AuditEntryMapper.class);

    private AuditTrailService service;

    @BeforeEach
    void setUp() {
        service = new AuditTrailService(repository, mapper);
    }

    @Test
    void appendUsesTheGenesisHashWhenTheTrailIsEmpty() {
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(repository.save(any(AuditEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditEntryDto result = service.append(new AppendAuditEntryCommand("tenant-1", "Lead", "42", "user:abc",
                "CREATE", "{}", Instant.parse("2026-11-11T10:00:00Z")));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(captor.capture());
        AuditEntry saved = captor.getValue();

        assertThat(saved.getPreviousHash()).isEqualTo(AuditHashing.GENESIS_HASH);
        assertThat(saved.getEntryHash()).isNotEqualTo(AuditHashing.GENESIS_HASH).hasSize(64);
        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getAggregateType()).isEqualTo("Lead");
        assertThat(saved.getAggregateId()).isEqualTo("42");
        assertThat(saved.getPrincipal()).isEqualTo("user:abc");
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(result.getEntryHash()).isEqualTo(saved.getEntryHash());
    }

    @Test
    void appendChainsOffTheCurrentHeadsEntryHash() {
        AuditEntry head = AuditEntry.builder().id(7L).entryHash("f".repeat(64)).build();
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.of(head));
        when(repository.save(any(AuditEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.append(new AppendAuditEntryCommand("tenant-1", "Lead", "42", "user:abc", "UPDATE", null, null));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPreviousHash()).isEqualTo("f".repeat(64));
    }

    @Test
    void appendDefaultsOccurredAtToNowWhenNotGiven() {
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(repository.save(any(AuditEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant before = Instant.now();

        service.append(new AppendAuditEntryCommand(null, "Lead", "42", null, "CREATE", null, null));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isBetween(before, Instant.now().plusSeconds(1));
    }

    @Test
    void searchDelegatesEveryFilterToTheRepository() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-31T00:00:00Z");
        Pageable pageable = PageRequest.of(0, 20);
        AuditEntry entry = AuditEntry.builder().id(1L).aggregateType("Lead").aggregateId("42")
                .previousHash(AuditHashing.GENESIS_HASH).entryHash("a".repeat(64)).build();
        when(repository.search("tenant-1", "Lead", "42", "user:abc", from, to, pageable))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Page<AuditEntryDto> result = service.search(
                new SearchAuditTrailQuery("tenant-1", "Lead", "42", "user:abc", from, to), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAggregateType()).isEqualTo("Lead");
        verify(repository).search(eq("tenant-1"), eq("Lead"), eq("42"), eq("user:abc"), eq(from), eq(to), eq(pageable));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void verifyChainIntegrityReportsIntactForAnEmptyTrail() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of());

        ChainIntegrityResult result = service.verifyChainIntegrity();

        assertThat(result.intact()).isTrue();
        assertThat(result.entriesChecked()).isZero();
        assertThat(result.brokenAtEntryId()).isNull();
    }

    @Test
    void verifyChainIntegrityReportsIntactForAProperlyLinkedChain() {
        AuditEntry first = entryWithValidHash(1L, AuditHashing.GENESIS_HASH, "tenant", "Lead", "1", "p", "CREATE",
                "d1", Instant.parse("2026-11-11T10:00:00Z"));
        AuditEntry second = entryWithValidHash(2L, first.getEntryHash(), "tenant", "Lead", "1", "p", "UPDATE",
                "d2", Instant.parse("2026-11-11T10:05:00Z"));
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(first, second));

        ChainIntegrityResult result = service.verifyChainIntegrity();

        assertThat(result.intact()).isTrue();
        assertThat(result.entriesChecked()).isEqualTo(2);
    }

    @Test
    void verifyChainIntegrityDetectsATamperedEntryContent() {
        AuditEntry first = entryWithValidHash(1L, AuditHashing.GENESIS_HASH, "tenant", "Lead", "1", "p", "CREATE",
                "d1", Instant.parse("2026-11-11T10:00:00Z"));
        // Content changed after the hash was computed (simulating a direct, out-of-band row edit).
        AuditEntry tampered = AuditEntry.builder().id(first.getId()).tenantId(first.getTenantId())
                .aggregateType(first.getAggregateType()).aggregateId(first.getAggregateId())
                .principal(first.getPrincipal()).action(first.getAction()).details("TAMPERED")
                .occurredAt(first.getOccurredAt()).recordedAt(first.getRecordedAt())
                .previousHash(first.getPreviousHash()).entryHash(first.getEntryHash()).build();
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(tampered));

        ChainIntegrityResult result = service.verifyChainIntegrity();

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtEntryId()).isEqualTo(1L);
        assertThat(result.reason()).contains("entryHash");
    }

    @Test
    void verifyChainIntegrityDetectsABrokenPreviousHashLink() {
        AuditEntry first = entryWithValidHash(1L, AuditHashing.GENESIS_HASH, "tenant", "Lead", "1", "p", "CREATE",
                "d1", Instant.parse("2026-11-11T10:00:00Z"));
        // second's previousHash does not match first's entryHash - e.g. a row was deleted out-of-band and this
        // one re-inserted, or first's entryHash was changed without recomputing what follows it.
        AuditEntry second = entryWithValidHash(2L, "f".repeat(64), "tenant", "Lead", "1", "p", "UPDATE",
                "d2", Instant.parse("2026-11-11T10:05:00Z"));
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(first, second));

        ChainIntegrityResult result = service.verifyChainIntegrity();

        assertThat(result.intact()).isFalse();
        assertThat(result.entriesChecked()).isEqualTo(1);
        assertThat(result.brokenAtEntryId()).isEqualTo(2L);
        assertThat(result.reason()).contains("previousHash");
    }

    private static AuditEntry entryWithValidHash(Long id, String previousHash, String tenantId, String aggregateType,
                                                   String aggregateId, String principal, String action,
                                                   String details, Instant occurredAt) {
        String entryHash = AuditHashing.hash(previousHash, tenantId, aggregateType, aggregateId, principal, action,
                details, occurredAt);
        return AuditEntry.builder().id(id).tenantId(tenantId).aggregateType(aggregateType).aggregateId(aggregateId)
                .principal(principal).action(action).details(details).occurredAt(occurredAt)
                .recordedAt(occurredAt).previousHash(previousHash).entryHash(entryHash).build();
    }
}
