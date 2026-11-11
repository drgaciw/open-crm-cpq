package com.opencrm.crm.fact;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.fields.AgentFieldValueGuard;
import com.opencrm.crm.fields.FixtureTenants;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ProposeFact, the subject proposals list, and accept / dismiss (ADR-028).
 * The score is computed here. Request bodies have no score field.
 * <p>
 * Tenant ids are the field-registry fixtures until JWT {@code tid} is wired.
 * A tenant-wide inbox is not this slice: list is per subject.
 * Accept and dismiss call {@link FactDecisionAudit}. BC-20-01 is not landed, so the
 * bean only logs. TODO(BC-20-01): keep the call when the trail replaces the log.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FactLedgerService {

    private static final Pattern FIELD_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");
    private static final Pattern METHOD = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private static final int MAX_EVIDENCE = 20;

    private final CrmFactRepository factRepository;
    private final RecordFieldAccess recordFields;
    private final EvidenceWeights weights;
    private final FactDecisionAudit decisionAudit;
    private final CurrentActor currentActor;
    private final Clock clock;

    public FactResponse propose(FactSubjectType subjectType, long subjectId, String tenant, ProposeFactRequest request) {
        FixtureTenants.require(tenant);
        String actorId = AgentFieldValueGuard.actorId(currentActor);
        Prepared proposal = prepare(request);
        recordFields.requireSubject(subjectType, subjectId);
        String subject = Long.toString(subjectId);
        boolean humanOwns = humanOwns(tenant, subjectType, subject, proposal.fieldKey());
        FactPrincipalKind principal = principal();
        FactDisposition disposition = FactDispositionDecider.decide(
                proposal.score(),
                proposal.primary(),
                proposal.sensitive(),
                proposal.contradicting(),
                principal,
                humanOwns);
        if (!disposition.stored()) {
            return preview(tenant, subjectType, subject, proposal, disposition);
        }
        Instant now = clock.instant();
        supersedeOpen(factRepository.findCurrent(tenant, subjectType, subject, proposal.fieldKey()), disposition.status(), now);
        CrmFact fact = newFact(tenant, subjectType, subject, proposal, disposition, principal, now);
        if (disposition.status() == FactStatus.APPLIED) {
            fact.setDecidedBy(actorId);
            fact.setDecidedAt(now);
        }
        CrmFact saved = factRepository.save(fact);
        boolean appliedToRecord = false;
        if (disposition.writeRecord()) {
            appliedToRecord = recordFields.write(subjectType, subjectId, proposal.fieldKey(), proposal.value());
        }
        if (saved.getStatus() == FactStatus.APPLIED) {
            audit(saved, actorId, now);
        }
        return toResponse(saved, appliedToRecord, disposition.humanProtected());
    }

    @Transactional(readOnly = true)
    public List<FactResponse> listProposals(FactSubjectType subjectType, long subjectId, String tenant) {
        FixtureTenants.require(tenant);
        recordFields.requireSubject(subjectType, subjectId);
        return factRepository.findInbox(tenant, subjectType, Long.toString(subjectId), FactStatus.inbox())
                .stream()
                .sorted(Comparator.comparing(CrmFact::getObservedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CrmFact::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(fact -> toResponse(fact, false, false))
                .toList();
    }

    public FactResponse accept(FactSubjectType subjectType, long subjectId, long factId, String tenant) {
        return decide(subjectType, subjectId, factId, tenant, true);
    }

    public FactResponse dismiss(FactSubjectType subjectType, long subjectId, long factId, String tenant) {
        return decide(subjectType, subjectId, factId, tenant, false);
    }

    private FactResponse decide(
            FactSubjectType subjectType,
            long subjectId,
            long factId,
            String tenant,
            boolean accept) {
        FixtureTenants.require(tenant);
        AgentFieldValueGuard.requireHumanWriter(currentActor);
        String actorId = AgentFieldValueGuard.actorId(currentActor);
        CrmFact fact = requireFact(subjectType, subjectId, factId, tenant);
        requireDecidable(fact.getStatus());
        if (accept && StandardFactFields.reparent(subjectType, fact.getFieldKey())) {
            throw new BusinessException(
                    "Re-parenting is not applied from the fact ledger. Account hierarchy owns that column.",
                    HttpStatus.CONFLICT,
                    "FACT_REPARENT_UNAVAILABLE");
        }
        Instant now = clock.instant();
        boolean appliedToRecord = false;
        if (accept) {
            appliedToRecord = recordFields.write(subjectType, subjectId, fact.getFieldKey(), fact.getFactValue());
            supersedeOthers(tenant, subjectType, fact, now);
            fact.setStatus(FactStatus.APPLIED);
            fact.setWriterKind(WriterKind.HUMAN);
        } else {
            fact.setStatus(FactStatus.DISMISSED);
        }
        fact.setDecidedBy(actorId);
        fact.setDecidedAt(now);
        fact.setSupersededAt(null);
        CrmFact saved = factRepository.save(fact);
        audit(saved, actorId, now);
        return toResponse(saved, appliedToRecord, false);
    }

    private void supersedeOpen(List<CrmFact> current, FactStatus incoming, Instant now) {
        for (CrmFact existing : current) {
            boolean replaceApplied = incoming == FactStatus.APPLIED && existing.getStatus() == FactStatus.APPLIED;
            boolean replaceOpen = existing.getStatus().shownInInbox() || existing.getStatus() == FactStatus.PROVENANCE;
            if (replaceApplied || replaceOpen) {
                existing.setStatus(FactStatus.SUPERSEDED);
                existing.setSupersededAt(now);
            }
        }
    }

    private void supersedeOthers(String tenant, FactSubjectType subjectType, CrmFact kept, Instant now) {
        for (CrmFact other : factRepository.findCurrent(tenant, subjectType, kept.getSubjectId(), kept.getFieldKey())) {
            if (other.getId().equals(kept.getId())) {
                continue;
            }
            if (other.getStatus() == FactStatus.APPLIED
                    || other.getStatus().shownInInbox()
                    || other.getStatus() == FactStatus.PROVENANCE) {
                other.setStatus(FactStatus.SUPERSEDED);
                other.setSupersededAt(now);
            }
        }
    }

    private boolean humanOwns(String tenant, FactSubjectType subjectType, String subjectId, String fieldKey) {
        for (CrmFact current : factRepository.findCurrent(tenant, subjectType, subjectId, fieldKey)) {
            if (current.getStatus() == FactStatus.APPLIED && current.getWriterKind() == WriterKind.HUMAN) {
                return true;
            }
        }
        return recordFields.read(subjectType, Long.parseLong(subjectId), fieldKey)
                .filter(column -> column.value() != null && !column.value().isBlank())
                .map(column -> !serviceActor(column.lastWriter()))
                .orElse(false);
    }

    private static boolean serviceActor(String actor) {
        return actor != null && actor.startsWith(Caller.SERVICE_PREFIX);
    }

    private FactPrincipalKind principal() {
        return currentActor.caller()
                .map(caller -> switch (caller) {
                    case Caller.Service ignored -> FactPrincipalKind.AGENT;
                    case Caller.User ignored -> FactPrincipalKind.HUMAN;
                })
                .orElse(FactPrincipalKind.HUMAN);
    }

    private CrmFact requireFact(FactSubjectType subjectType, long subjectId, long factId, String tenant) {
        CrmFact fact = factRepository.findActiveById(factId)
                .orElseThrow(() -> new ResourceNotFoundException("Fact", factId));
        if (!tenant.equals(fact.getTenantId())
                || fact.getSubjectType() != subjectType
                || !Long.toString(subjectId).equals(fact.getSubjectId())) {
            throw new ResourceNotFoundException("Fact", factId);
        }
        return fact;
    }

    private static void requireDecidable(FactStatus status) {
        switch (status) {
            case PROPOSED, UNRESOLVED -> {
                // A human can accept or dismiss these.
            }
            case APPLIED, DISMISSED, SUPERSEDED, PROVENANCE -> throw new BusinessException(
                    "This fact cannot be accepted or dismissed",
                    HttpStatus.CONFLICT,
                    "FACT_NOT_DECIDABLE");
        }
    }

    private Prepared prepare(ProposeFactRequest request) {
        if (request == null) {
            throw new BusinessException("Fact proposal is required", HttpStatus.BAD_REQUEST, "FACT_REQUIRED");
        }
        String fieldKey = required(request.getFieldKey(), "fieldKey");
        if (!FIELD_KEY.matcher(fieldKey).matches()) {
            throw new BusinessException(
                    "fieldKey must start with a letter and use letters, digits, or underscores",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "FACT_FIELD_KEY");
        }
        String value = required(request.getValue(), "value");
        if (value.length() > 4000) {
            throw new BusinessException("value is too long", HttpStatus.UNPROCESSABLE_CONTENT, "FACT_VALUE");
        }
        String method = required(request.getMethod(), "method");
        if (!METHOD.matcher(method).matches()) {
            throw new BusinessException("method must be a short token", HttpStatus.UNPROCESSABLE_CONTENT, "FACT_METHOD");
        }
        if (request.getEvidence() == null || request.getEvidence().isEmpty()) {
            throw new BusinessException(
                    "At least one evidence item is required",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "FACT_EVIDENCE_REQUIRED");
        }
        if (request.getEvidence().size() > MAX_EVIDENCE) {
            throw new BusinessException("Too many evidence items", HttpStatus.UNPROCESSABLE_CONTENT, "FACT_EVIDENCE_LIMIT");
        }
        List<ScoredEvidence> evidence = new ArrayList<>();
        boolean contradicting = false;
        boolean primary = false;
        for (EvidenceRequest item : request.getEvidence()) {
            if (item == null || item.getKind() == null) {
                throw new BusinessException("Evidence kind is required", HttpStatus.UNPROCESSABLE_CONTENT, "FACT_EVIDENCE_KIND");
            }
            String url = blankToNull(item.getUrl());
            requireUrl(url);
            String asserted = blankToNull(item.getAssertedValue());
            if (asserted != null && !asserted.equals(value)) {
                contradicting = true;
            }
            if (weights.primary(item.getKind())) {
                primary = true;
            }
            evidence.add(new ScoredEvidence(
                    item.getKind(),
                    weights.weight(item.getKind()),
                    url,
                    item.getObservedAt(),
                    asserted));
        }
        BigDecimal score = FactScoreCalculator.score(evidence.stream().map(ScoredEvidence::weight).toList());
        Instant observedAt = evidence.stream()
                .map(ScoredEvidence::observedAt)
                .filter(instant -> instant != null)
                .max(Comparator.naturalOrder())
                .orElseGet(clock::instant);
        return new Prepared(
                fieldKey,
                value,
                method,
                evidence,
                score,
                primary,
                StandardFactFields.sensitive(fieldKey),
                contradicting,
                observedAt);
    }

    private CrmFact newFact(
            String tenant,
            FactSubjectType subjectType,
            String subjectId,
            Prepared proposal,
            FactDisposition disposition,
            FactPrincipalKind principal,
            Instant now) {
        CrmFact fact = new CrmFact();
        fact.setTenantId(tenant);
        fact.setSubjectType(subjectType);
        fact.setSubjectId(subjectId);
        fact.setFieldKey(proposal.fieldKey());
        fact.setFactValue(proposal.value());
        fact.setScore(proposal.score());
        fact.setBand(disposition.band());
        fact.setMethod(proposal.method());
        fact.setStatus(disposition.status());
        fact.setWeightCatalogVersion(weights.version());
        fact.setWriterKind(switch (principal) {
            case AGENT -> WriterKind.AGENT;
            case HUMAN -> WriterKind.HUMAN;
        });
        fact.setSensitive(proposal.sensitive());
        fact.setContradicting(proposal.contradicting());
        fact.setObservedAt(proposal.observedAt() == null ? now : proposal.observedAt());
        int order = 0;
        for (ScoredEvidence item : proposal.evidence()) {
            CrmFactEvidence row = new CrmFactEvidence();
            row.setKind(item.kind());
            row.setWeight(item.weight());
            row.setUrl(item.url());
            row.setObservedAt(item.observedAt());
            row.setAssertedValue(item.assertedValue());
            row.setSortOrder(order++);
            fact.addEvidence(row);
        }
        return fact;
    }

    private FactResponse preview(
            String tenant,
            FactSubjectType subjectType,
            String subjectId,
            Prepared proposal,
            FactDisposition disposition) {
        return FactResponse.builder()
                .stored(false)
                .appliedToRecord(false)
                .humanProtected(disposition.humanProtected())
                .tenantId(tenant)
                .subjectType(subjectType)
                .subjectId(subjectId)
                .fieldKey(proposal.fieldKey())
                .value(proposal.value())
                .score(proposal.score())
                .band(disposition.band())
                .status(disposition.status())
                .method(proposal.method())
                .weightCatalogVersion(weights.version())
                .sensitive(proposal.sensitive())
                .contradicting(proposal.contradicting())
                .observedAt(proposal.observedAt())
                .evidence(proposal.evidence().stream().map(FactLedgerService::toEvidence).toList())
                .build();
    }

    private void audit(CrmFact fact, String actorId, Instant at) {
        decisionAudit.record(new FactAuditEntry(
                fact.getTenantId(),
                fact.getSubjectType(),
                fact.getSubjectId(),
                fact.getId(),
                fact.getFieldKey(),
                fact.getStatus(),
                actorId,
                at));
    }

    private static FactResponse toResponse(CrmFact fact, boolean appliedToRecord, boolean humanProtected) {
        List<FactEvidenceResponse> evidence = fact.getEvidence() == null
                ? List.of()
                : fact.getEvidence().stream()
                .map(item -> FactEvidenceResponse.builder()
                        .kind(item.getKind())
                        .weight(item.getWeight())
                        .url(item.getUrl())
                        .observedAt(item.getObservedAt())
                        .assertedValue(item.getAssertedValue())
                        .build())
                .toList();
        return FactResponse.builder()
                .id(fact.getId())
                .stored(true)
                .appliedToRecord(appliedToRecord)
                .humanProtected(humanProtected)
                .tenantId(fact.getTenantId())
                .subjectType(fact.getSubjectType())
                .subjectId(fact.getSubjectId())
                .fieldKey(fact.getFieldKey())
                .value(fact.getFactValue())
                .score(fact.getScore())
                .band(fact.getBand())
                .status(fact.getStatus())
                .method(fact.getMethod())
                .weightCatalogVersion(fact.getWeightCatalogVersion())
                .writerKind(fact.getWriterKind())
                .sensitive(fact.isSensitive())
                .contradicting(fact.isContradicting())
                .decidedBy(fact.getDecidedBy())
                .decidedAt(fact.getDecidedAt())
                .observedAt(fact.getObservedAt())
                .supersededAt(fact.getSupersededAt())
                .evidence(evidence)
                .build();
    }

    private static FactEvidenceResponse toEvidence(ScoredEvidence item) {
        return FactEvidenceResponse.builder()
                .kind(item.kind())
                .weight(item.weight())
                .url(item.url())
                .observedAt(item.observedAt())
                .assertedValue(item.assertedValue())
                .build();
    }

    private static void requireUrl(String url) {
        if (url == null) {
            return;
        }
        if (url.length() > 2000 || url.chars().anyMatch(Character::isWhitespace)
                || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new BusinessException(
                    "Evidence url must be an http or https url",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "FACT_EVIDENCE_URL");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(name + " is required", HttpStatus.UNPROCESSABLE_CONTENT, "FACT_" + name.toUpperCase());
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record Prepared(
            String fieldKey,
            String value,
            String method,
            List<ScoredEvidence> evidence,
            BigDecimal score,
            boolean primary,
            boolean sensitive,
            boolean contradicting,
            Instant observedAt
    ) {
    }

    private record ScoredEvidence(
            EvidenceKind kind,
            BigDecimal weight,
            String url,
            Instant observedAt,
            String assertedValue
    ) {
    }
}
