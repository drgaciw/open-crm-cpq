package com.opencrm.contract.service;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;
import com.opencrm.contract.dto.RenewalPolicyRecordDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-scoped renewal policy store. Writes are service methods only. The HTTP controller does not call
 * {@link #append}.
 */
@Service
public class RenewalPolicyRecordService {

    private static final Set<String> MODES = Set.of("SAME", "UPLIFT_PCT");

    private final RenewalPolicyRecords records;
    private final Clock clock;

    public RenewalPolicyRecordService(RenewalPolicyRecords records) {
        this(records, Clock.systemUTC());
    }

    RenewalPolicyRecordService(RenewalPolicyRecords records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RenewalPolicyRecordDto> read(UUID tenantId, UUID accountId, UUID agreementId, UUID lineId, LocalDate asOf) {
        requireTenant(tenantId);
        LocalDate on = asOf == null ? LocalDate.now(clock) : asOf;
        List<RenewalPolicyRecordDto> found = new ArrayList<>();
        if (accountId != null) {
            select(tenantId, RenewalPolicyScopeKind.ACCOUNT, accountId, on).ifPresent(found::add);
        }
        if (agreementId != null) {
            select(tenantId, RenewalPolicyScopeKind.AGREEMENT, agreementId, on).ifPresent(found::add);
        }
        if (lineId != null) {
            select(tenantId, RenewalPolicyScopeKind.LINE, lineId, on).ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    @Transactional
    public RenewalPolicyRecord append(UUID tenantId, String actor, RenewalPolicyDraft draft) {
        requireTenant(tenantId);
        if (actor == null || actor.isBlank()) {
            throw new BusinessException("Actor is required", HttpStatus.BAD_REQUEST, "ACTOR_REQUIRED");
        }
        validate(draft);
        List<RenewalPolicyRecord> existing = visible(tenantId, draft.scopeKind(), draft.scopeRef());
        Optional<RenewalPolicyRecord> replay = existing.stream().filter(row -> sameBody(row, draft)).findFirst();
        if (replay.isPresent()) {
            return replay.get();
        }
        int next = existing.stream().map(RenewalPolicyRecord::getPolicyVersion).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0) + 1;
        if (draft.expectedVersion() != null && draft.expectedVersion() != next) {
            throw new RenewalPolicyConflictException("expected policy version does not match the next version");
        }
        RenewalPolicyRecord row = newRow(tenantId, actor, draft, next);
        try {
            return records.insert(row);
        } catch (RenewalPolicyVersionConflictException ex) {
            Optional<RenewalPolicyRecord> winner = visible(tenantId, draft.scopeKind(), draft.scopeRef()).stream()
                    .filter(existingRow -> next == existingRow.getPolicyVersion())
                    .findFirst();
            if (winner.isPresent() && sameBody(winner.get(), draft)) {
                return winner.get();
            }
            throw new RenewalPolicyConflictException("conflicting renewal policy version");
        }
    }

    private Optional<RenewalPolicyRecordDto> select(UUID tenantId, RenewalPolicyScopeKind kind, UUID scopeRef, LocalDate on) {
        return visible(tenantId, kind, scopeRef).stream()
                .filter(row -> effective(row, on))
                .max(Comparator.comparingInt(RenewalPolicyRecord::getPolicyVersion))
                .map(RenewalPolicyRecordService::toDto);
    }

    private List<RenewalPolicyRecord> visible(UUID tenantId, RenewalPolicyScopeKind kind, UUID scopeRef) {
        return records.findScoped(tenantId, kind, scopeRef).stream()
                .filter(row -> tenantId.equals(row.getTenantId()))
                .filter(row -> kind == row.getScopeKind() && scopeRef.equals(row.getScopeRef()))
                .toList();
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException("Tenant is required", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
    }

    private static void validate(RenewalPolicyDraft draft) {
        if (draft == null || draft.scopeKind() == null || draft.scopeRef() == null) {
            throw new BusinessException("Policy scope is required", HttpStatus.BAD_REQUEST, "SCOPE_REQUIRED");
        }
        if (draft.mode() == null || !MODES.contains(draft.mode())) {
            throw new BusinessException("Policy mode is outside this slice", HttpStatus.UNPROCESSABLE_CONTENT, "MODE_OUT_OF_SLICE");
        }
        if (draft.upliftPercentPoints() != null
                && (draft.upliftPercentPoints().signum() < 0 || draft.upliftPercentPoints().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BusinessException("Percent points must be between 0 and 100", HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR");
        }
        if (draft.retentionMarker() != null && draft.retentionMarker().length() > 128) {
            throw new BusinessException("Retention marker is too long", HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR");
        }
        if (draft.effectiveFrom() != null && draft.effectiveTo() != null && draft.effectiveTo().isBefore(draft.effectiveFrom())) {
            throw new BusinessException("Effective end is before the start", HttpStatus.BAD_REQUEST, "INVALID_EFFECTIVE_WINDOW");
        }
    }

    private static RenewalPolicyRecord newRow(UUID tenantId, String actor, RenewalPolicyDraft draft, int version) {
        return RenewalPolicyRecord.builder()
                .tenantId(tenantId)
                .createdBy(actor)
                .scopeKind(draft.scopeKind())
                .scopeRef(draft.scopeRef())
                .policyVersion(version)
                .mode(draft.mode())
                .basis(draft.basis())
                .upliftPercentPoints(draft.upliftPercentPoints())
                .maxIncreasePct(draft.maxIncreasePct())
                .minIncreasePct(draft.minIncreasePct())
                .minUnitAmount(draft.minUnitAmount())
                .notAboveList(draft.notAboveList())
                .notBelowExitNet(draft.notBelowExitNet())
                .priceAsOf(draft.priceAsOf())
                .retentionMarker(draft.retentionMarker())
                .effectiveFrom(draft.effectiveFrom())
                .effectiveTo(draft.effectiveTo())
                .status(RenewalPolicyRecord.STATUS_ACTIVE)
                .build();
    }

    static boolean sameBody(RenewalPolicyRecord row, RenewalPolicyDraft draft) {
        return row.getScopeKind() == draft.scopeKind()
                && Objects.equals(row.getScopeRef(), draft.scopeRef())
                && Objects.equals(row.getMode(), draft.mode())
                && Objects.equals(row.getBasis(), draft.basis())
                && sameAmount(row.getUpliftPercentPoints(), draft.upliftPercentPoints())
                && sameAmount(row.getMaxIncreasePct(), draft.maxIncreasePct())
                && sameAmount(row.getMinIncreasePct(), draft.minIncreasePct())
                && sameAmount(row.getMinUnitAmount(), draft.minUnitAmount())
                && Objects.equals(row.getNotAboveList(), draft.notAboveList())
                && Objects.equals(row.getNotBelowExitNet(), draft.notBelowExitNet())
                && Objects.equals(row.getPriceAsOf(), draft.priceAsOf())
                && Objects.equals(row.getRetentionMarker(), draft.retentionMarker())
                && Objects.equals(row.getEffectiveFrom(), draft.effectiveFrom())
                && Objects.equals(row.getEffectiveTo(), draft.effectiveTo());
    }

    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    private static boolean effective(RenewalPolicyRecord row, LocalDate on) {
        if (!RenewalPolicyRecord.STATUS_ACTIVE.equals(row.getStatus())) {
            return false;
        }
        if (row.getEffectiveFrom() != null && on.isBefore(row.getEffectiveFrom())) {
            return false;
        }
        return row.getEffectiveTo() == null || !on.isAfter(row.getEffectiveTo());
    }

    private static RenewalPolicyRecordDto toDto(RenewalPolicyRecord row) {
        return new RenewalPolicyRecordDto(
                row.getId(),
                row.getScopeKind().name(),
                row.getScopeRef(),
                row.getPolicyVersion(),
                row.getMode(),
                row.getBasis(),
                row.getUpliftPercentPoints(),
                row.getMaxIncreasePct(),
                row.getMinIncreasePct(),
                row.getMinUnitAmount(),
                row.getNotAboveList(),
                row.getNotBelowExitNet(),
                row.getPriceAsOf(),
                row.getRetentionMarker(),
                row.getEffectiveFrom(),
                row.getEffectiveTo(),
                row.getStatus());
    }
}
