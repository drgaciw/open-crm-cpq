package com.opencrm.audit;

import com.opencrm.audit.dto.AppendAuditEntryCommand;
import com.opencrm.audit.dto.AuditEntryDto;
import com.opencrm.audit.dto.SearchAuditTrailQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The append-only audit trail service (DRG-309 / BC-20-01): {@link #append} writes a new, hash-chained
 * {@link AuditEntry}; {@link #search} is SearchAuditTrail — filter by tenant, aggregate, principal and time range.
 *
 * <p>Registered as a bean by {@link AuditAutoConfiguration}, not discovered by component scanning; see that
 * class's Javadoc for why. A service adopts this by adding the {@code com.opencrm:audit} dependency and creating
 * the {@code audit_entries} table with its own Flyway migration (libs own no schema) — see crm-service's
 * {@code V20__audit_entries.sql} for the reference migration, including the database trigger that enforces
 * append-only at the storage layer. No other service is wired to call this yet; that is future integration work.
 *
 * <p><b>Concurrency:</b> {@link #append} runs at {@code SERIALIZABLE} isolation so two concurrent appends can
 * never both read the same chain head and write siblings that fork the chain: Postgres aborts one of the two
 * transactions with a serialization failure (Spring translates this to
 * {@link org.springframework.dao.CannotSerializeTransactionException}), which the caller sees as a thrown
 * exception. Nothing in this slice retries that automatically — today nothing else calls {@code append}, so there
 * is no concurrent writer to observe it. Wiring a retry (e.g. Spring Retry) around call sites is a follow-up once
 * a second writer exists.
 */
public class AuditTrailService {

    private final AuditEntryRepository repository;
    private final AuditEntryMapper mapper;

    AuditTrailService(AuditEntryRepository repository, AuditEntryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Appends one entry, computing its hash from the current chain head (or the genesis hash for the first
     * entry). Returns the persisted entry, including the hashes the caller cannot set themselves. */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AuditEntryDto append(AppendAuditEntryCommand command) {
        Objects.requireNonNull(command, "command is required");
        Instant occurredAt = command.occurredAt() != null ? command.occurredAt() : Instant.now();
        String previousHash = repository.findTopByOrderByIdDesc()
                .map(AuditEntry::getEntryHash)
                .orElse(AuditHashing.GENESIS_HASH);
        String entryHash = AuditHashing.hash(previousHash, command.tenantId(), command.aggregateType(),
                command.aggregateId(), command.principal(), command.action(), command.details(), occurredAt);

        AuditEntry entry = AuditEntry.builder()
                .tenantId(command.tenantId())
                .aggregateType(command.aggregateType())
                .aggregateId(command.aggregateId())
                .principal(command.principal())
                .action(command.action())
                .details(command.details())
                .occurredAt(occurredAt)
                .recordedAt(Instant.now())
                .previousHash(previousHash)
                .entryHash(entryHash)
                .build();

        return mapper.toDto(repository.save(entry));
    }

    /** SearchAuditTrail: filter by tenant, aggregate, principal and/or time range, newest first. */
    @Transactional(readOnly = true)
    public Page<AuditEntryDto> search(SearchAuditTrailQuery query, Pageable pageable) {
        Objects.requireNonNull(query, "query is required");
        Page<AuditEntry> page = repository.search(query.tenantId(), query.aggregateType(), query.aggregateId(),
                query.principal(), query.from(), query.to(), pageable);
        return page.map(mapper::toDto);
    }

    /**
     * Walks the whole chain in insertion order and recomputes every hash, to detect a row whose content or
     * recorded hashes were changed after it was written (the point of hash-chaining it in the first place). Scans
     * the full table: a maintenance/verification operation, not a request-serving one.
     */
    @Transactional(readOnly = true)
    public ChainIntegrityResult verifyChainIntegrity() {
        List<AuditEntry> entries = repository.findAllByOrderByIdAsc();
        String expectedPreviousHash = AuditHashing.GENESIS_HASH;
        long checked = 0;
        for (AuditEntry entry : entries) {
            if (!expectedPreviousHash.equals(entry.getPreviousHash())) {
                return ChainIntegrityResult.broken(checked, entry.getId(),
                        "previousHash does not match the prior entry's entryHash");
            }
            String recomputed = AuditHashing.hash(entry.getPreviousHash(), entry.getTenantId(),
                    entry.getAggregateType(), entry.getAggregateId(), entry.getPrincipal(), entry.getAction(),
                    entry.getDetails(), entry.getOccurredAt());
            if (!recomputed.equals(entry.getEntryHash())) {
                return ChainIntegrityResult.broken(checked, entry.getId(),
                        "entryHash does not match this entry's recomputed content hash");
            }
            expectedPreviousHash = entry.getEntryHash();
            checked++;
        }
        return ChainIntegrityResult.intact(checked);
    }
}
