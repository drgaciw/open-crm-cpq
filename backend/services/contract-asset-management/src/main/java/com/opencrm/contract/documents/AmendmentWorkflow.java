package com.opencrm.contract.documents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.commercial.CanonicalBytes;
import com.opencrm.common.commercial.CommercialDigest;
import com.opencrm.common.commercial.CommercialEnvelope;
import com.opencrm.common.commercial.CommercialLine;
import com.opencrm.common.commercial.CommercialUnit;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.documents.events.AmendmentEventAppend;
import com.opencrm.contract.documents.events.AmendmentEventHasher;
import com.opencrm.contract.documents.events.AmendmentEventWriter;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentApplicationUnit;
import com.opencrm.contract.domain.documents.AmendmentDocument;
import com.opencrm.contract.domain.documents.AmendmentDocumentLine;
import com.opencrm.contract.domain.documents.AmendmentDocumentRevision;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.AmendmentIntent;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import com.opencrm.contract.domain.documents.ContractHeader;
import com.opencrm.contract.domain.documents.DocumentKind;
import com.opencrm.contract.domain.documents.EvidenceClass;
import com.opencrm.contract.domain.documents.InitiationTrigger;
import com.opencrm.contract.domain.documents.RevisionCloseStatus;
import com.opencrm.contract.domain.documents.SignaturePolicy;
import com.opencrm.contract.repository.documents.AmendmentCommercialEnvelopeRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
import com.opencrm.contract.repository.documents.AmendmentOperationReceiptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class AmendmentWorkflow {

    private static final String CREATE = "CREATE";
    private static final String OPEN = "OPEN_REVISION";
    private static final String PRICE = "INSTALL_PRICING";
    private static final String SUBMIT = "SUBMIT";
    private static final String APPROVE = "APPROVE";
    private static final String ACCEPT = "ACCEPT";
    private static final String EXECUTE = "EXECUTE";
    private static final String WITHDRAW = "WITHDRAW";
    private static final String REJECT = "REJECT";

    private final EntityManager entityManager;
    private final AmendmentDocumentRepository documents;
    private final AmendmentCommercialEnvelopeRepository envelopes;
    private final AmendmentOperationReceiptRepository receipts;
    private final AmendmentEventWriter events;
    private final ObjectMapper json;
    private final TransactionTemplate business;
    private final TransactionTemplate auditTx;

    AmendmentWorkflow(EntityManager entityManager, AmendmentDocumentRepository documents,
                      AmendmentCommercialEnvelopeRepository envelopes, AmendmentOperationReceiptRepository receipts,
                      AmendmentEventWriter events, ObjectMapper json, PlatformTransactionManager transactions) {
        this.entityManager = entityManager;
        this.documents = documents;
        this.envelopes = envelopes;
        this.receipts = receipts;
        this.events = events;
        this.json = json;
        this.business = new TransactionTemplate(transactions);
        this.auditTx = new TransactionTemplate(transactions);
        this.auditTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    AmendmentOutcome create(UUID contractId, CreateAmendment command, CommandContext context) {
        return run(() -> business.execute(status -> createTx(contractId, command, context)));
    }

    AmendmentView get(UUID id) {
        return view(load(id));
    }

    AmendmentView getForTenant(UUID id, CommandContext context) {
        AmendmentDocument document = load(id);
        requireTenant(document, context);
        return view(document);
    }

    AmendmentView submit(UUID id, int version, CommandContext context) {
        return run(() -> business.execute(status -> submitTx(id, version, context))).view();
    }

    AmendmentView recordApproval(UUID id, int version, String approvalRef, CommandContext context) {
        return run(() -> business.execute(status -> approveTx(id, version, approvalRef, context))).view();
    }

    AmendmentView accept(UUID id, int version, CommandContext context) {
        return run(() -> business.execute(status -> acceptTx(id, version, context))).view();
    }

    AmendmentView execute(UUID id, int version, CommandContext context) {
        return run(() -> business.execute(status -> executeTx(id, version, context))).view();
    }

    AmendmentView withdraw(UUID id, int version, CommandContext context) {
        return run(() -> business.execute(status -> withdrawTx(id, version, context))).view();
    }

    AmendmentView rejectReview(UUID id, int version, CommandContext context) {
        return run(() -> business.execute(status -> rejectTx(id, version, context))).view();
    }

    AmendmentView sendForSignature(UUID id, int version, CommandContext context) {
        return run(() -> business.execute(status -> {
            AmendmentDocument document = refreshLocked(id);
            requireTenant(document, context);
            requireVersion(document, version, context);
            throw AmendmentCommandException.signature(document.getId(), context);
        })).view();
    }

    AmendmentOutcome open(UUID id, OpenRevision command, CommandContext context) {
        return run(() -> business.execute(status -> openTx(id, command, context)));
    }

    AmendmentView installPricing(UUID id, int revisionNo, InstallPricing command, CommandContext context) {
        return run(() -> business.execute(status -> priceTx(id, revisionNo, command, context))).view();
    }

    private AmendmentOutcome createTx(UUID contractId, CreateAmendment command, CommandContext context) {
        String digest = digest("create", command.lifecycleChangeId(), command.lifecycleChangeRevision(),
                command.targetQuoteId(), command.inputsSnapshotRef(), command.signaturePolicy().name(),
                command.actionDate(), command.contractEffectiveDate(), command.backdatingReasonCode(),
                command.currency(), command.lines());
        AmendmentOutcome replay = replay(context, CREATE, digest, null, command.lifecycleChangeId(), command.lifecycleChangeRevision());
        if (replay != null) {
            return replay;
        }
        Optional<AmendmentDocument> existing = documents.findByLifecycleChangeId(command.lifecycleChangeId());
        if (existing.isPresent()) {
            throw AmendmentCommandException.conflict(existing.get().getId(), "IDEMPOTENCY_CONFLICT",
                    "lifecycle change already has a different document body", context);
        }
        v6(null, command.actionDate(), command.contractEffectiveDate(), command.backdatingReasonCode(), context);
        validateLines(null, command.lines(), context);
        ContractHeader contract = lockContract(contractId);
        requireTenant(contract.getTenantId(), context);
        UUID documentId = UUID.randomUUID();
        AmendmentDocument document = AmendmentDocument.builder()
                .id(documentId)
                .tenantId(contract.getTenantId())
                .contractId(contract.getId())
                .lifecycleChangeId(command.lifecycleChangeId())
                .lifecycleChangeRevision(command.lifecycleChangeRevision())
                .intent(AmendmentIntent.AMEND)
                .initiationTrigger(InitiationTrigger.API)
                .provisionalNumber(contract.getContractNumber() + "-P" + command.lifecycleChangeId().toString().substring(0, 8))
                .documentKind(DocumentKind.AMENDMENT)
                .status(AmendmentDocumentStatus.DRAFT)
                .currentRevisionNo(1)
                .signaturePolicy(command.signaturePolicy())
                .actionDate(command.actionDate())
                .contractEffectiveDate(command.contractEffectiveDate())
                .backdatingReasonCode(blankToNull(command.backdatingReasonCode()))
                .currencyCode(command.currency())
                .evidenceClass(EvidenceClass.STANDARD)
                .legalHold(false)
                .deleted(false)
                .createdBy(context.actorId())
                .build();
        entityManager.persist(document);
        AmendmentDocumentRevision revision = revision(document, 1, command.lifecycleChangeRevision(), command.targetQuoteId(), context);
        entityManager.persist(revision);
        persistStructure(document, 1, command.lines(), context);
        bindEnvelope(document, revision, command.inputsSnapshotRef(), null, false, context);
        entityManager.flush();
        succeed(document, "AmendmentDrafted", null, AmendmentDocumentStatus.DRAFT, 1, envelopeRef(revision), context);
        return store(context, CREATE, digest, document, command.lifecycleChangeId(), command.lifecycleChangeRevision(), false);
    }

    private AmendmentOutcome openTx(UUID id, OpenRevision command, CommandContext context) {
        String digest = digest("open", command.lifecycleChangeId(), command.lifecycleChangeRevision(),
                command.targetQuoteId(), command.inputsSnapshotRef(), "",
                command.actionDate(), command.contractEffectiveDate(), command.backdatingReasonCode(),
                "", command.lines());
        AmendmentDocument document = refreshLocked(id);
        requireTenant(document, context);
        AmendmentOutcome replay = replay(context, OPEN, digest, document.getId(), command.lifecycleChangeId(), command.lifecycleChangeRevision());
        if (replay != null) {
            return replay;
        }
        requireVersion(document, command.version(), context);
        Transition gate = AmendmentStateMachine.onExpectedVersion(command.version(), version(document), document.getStatus());
        if (!gate.allowed() && gate.errorCode() != null && !"VERSION_CONFLICT".equals(gate.errorCode())) {
            throw refusal(document, gate, context);
        }
        Transition revise = AmendmentStateMachine.revise(document.getStatus());
        if (!revise.allowed()) {
            throw refusal(document, revise, context);
        }
        if (!document.getLifecycleChangeId().equals(command.lifecycleChangeId())) {
            throw AmendmentCommandException.rule(document.getId(), null, "lifecycle change does not belong to this document", context);
        }
        int max = revisions(document.getId()).stream().mapToInt(AmendmentDocumentRevision::getLifecycleChangeRevision).max().orElse(0);
        if (command.lifecycleChangeRevision() != max + 1) {
            throw AmendmentCommandException.rule(document.getId(), null, "lifecycle revision is not the next revision", context);
        }
        v6(document.getId(), command.actionDate(), command.contractEffectiveDate(), command.backdatingReasonCode(), context);
        validateLines(document.getId(), command.lines(), context);
        AmendmentDocumentRevision current = revision(document.getId(), document.getCurrentRevisionNo());
        if (current.getContentDigest() == null) {
            current.setContentDigest(currentDigest(document, current));
        }
        current.setStatusAtClose(RevisionCloseStatus.SUPERSEDED);
        current.setFrozenAt(Instant.now());
        current.setUpdatedBy(context.actorId());
        AmendmentDocumentStatus from = document.getStatus();
        int next = document.getCurrentRevisionNo() + 1;
        AmendmentDocumentRevision opened = revision(document, next, command.lifecycleChangeRevision(), command.targetQuoteId(), context);
        entityManager.persist(opened);
        entityManager.flush();
        persistStructure(document, next, command.lines(), context);
        document.setCurrentRevisionNo(next);
        document.setStatus(AmendmentDocumentStatus.DRAFT);
        document.setLifecycleChangeRevision(command.lifecycleChangeRevision());
        document.setActionDate(command.actionDate());
        document.setContractEffectiveDate(command.contractEffectiveDate());
        document.setBackdatingReasonCode(blankToNull(command.backdatingReasonCode()));
        document.setTcvDelta(null);
        document.setArrDelta(null);
        document.setOneTimeTotal(null);
        document.setCreditTotal(null);
        document.setContentDigest(null);
        document.setApprovalRef(null);
        document.setSignatureEnvelopeRef(null);
        document.setUpdatedBy(context.actorId());
        bindEnvelope(document, opened, command.inputsSnapshotRef(), null, false, context);
        entityManager.flush();
        succeed(document, "AMENDMENT_SUPERSEDED", from, AmendmentDocumentStatus.DRAFT, next, envelopeRef(opened), context);
        return store(context, OPEN, digest, document, command.lifecycleChangeId(), command.lifecycleChangeRevision(), false);
    }

    private AmendmentOutcome priceTx(UUID id, int revisionNo, InstallPricing command, CommandContext context) {
        String digest = CommercialDigest.sha256(String.valueOf(command).getBytes(StandardCharsets.UTF_8));
        AmendmentDocument document = refreshLocked(id);
        requireTenant(document, context);
        AmendmentOutcome replay = replay(context, PRICE, digest, document.getId(), command.lifecycleChangeId(), command.lifecycleChangeRevision());
        if (replay != null) {
            return replay;
        }
        requireVersion(document, command.version(), context);
        Transition price = AmendmentStateMachine.price(document.getStatus());
        if (!price.allowed()) {
            throw refusal(document, price, context);
        }
        if (revisionNo != document.getCurrentRevisionNo()) {
            throw AmendmentCommandException.conflict(document.getId(), "ILLEGAL_TRANSITION", "only the open revision accepts pricing", context);
        }
        if (!command.currency().equals(document.getCurrencyCode())) {
            throw AmendmentCommandException.rule(document.getId(), "V8", "currency does not match the document", context);
        }
        List<AmendmentDocumentLine> lines = lines(document.getId(), revisionNo);
        if (lines.size() != command.lines().size()) {
            throw AmendmentCommandException.rule(document.getId(), "V8", "priced lines do not match the revision", context);
        }
        for (PricedLine priced : command.lines()) {
            AmendmentDocumentLine line = lines.stream().filter(item -> item.getChangeLineRef().equals(priced.changeLineRef())).findFirst()
                    .orElseThrow(() -> AmendmentCommandException.rule(document.getId(), "V8", "priced line is not on the revision", context));
            line.setProductExternalId(priced.productExternalId());
            line.setPricebookVersion(priced.pricebookVersion());
            line.setConfigurationHash(priced.configurationHash());
            line.setPolicyId(priced.policyId());
            line.setPolicyVersion(priced.policyVersion());
            line.setPolicyDecisionId(priced.policyDecisionId());
            line.setPricingTraceId(priced.pricingTraceId());
            line.setUnitPrice(priced.unitPrice());
            line.setNetAmount(priced.netAmount());
            line.setUpdatedBy(context.actorId());
        }
        document.setTcvDelta(command.tcvDelta());
        document.setArrDelta(command.arrDelta());
        document.setOneTimeTotal(command.oneTimeTotal());
        document.setCreditTotal(command.creditTotal());
        document.setApprovalRef(null);
        document.setUpdatedBy(context.actorId());
        AmendmentDocumentRevision revision = revision(document.getId(), revisionNo);
        bindEnvelope(document, revision, command.inputsSnapshotRef(), command.inputDigest(), false, context);
        revision.setContentDigest(null);
        document.setContentDigest(null);
        entityManager.flush();
        return store(context, PRICE, digest, document, command.lifecycleChangeId(), command.lifecycleChangeRevision(), true);
    }

    private AmendmentOutcome submitTx(UUID id, int version, CommandContext context) {
        AmendmentDocument document = refreshLocked(id);
        requireTenant(document, context);
        String digest = CommercialDigest.sha256(("submit:" + id + ":" + version).getBytes(StandardCharsets.UTF_8));
        AmendmentOutcome replay = replay(context, SUBMIT, digest, document.getId(), document.getLifecycleChangeId(), document.getLifecycleChangeRevision());
        if (replay != null) {
            return replay;
        }
        requireVersion(document, version, context);
        Transition submit = AmendmentStateMachine.submit(document.getStatus());
        if (!submit.allowed()) {
            throw refusal(document, submit, context);
        }
        if (lines(document.getId(), document.getCurrentRevisionNo()).stream().anyMatch(line -> line.getConfigurationHash() == null
                || line.getProductExternalId() == null || line.getPricebookVersion() == null)) {
            throw AmendmentCommandException.rule(document.getId(), "V9", "product, pricebook or configuration pin is missing", context);
        }
        for (RuleFailure failure : AmendmentValidationRules.onSubmit(facts(document))) {
            throw AmendmentCommandException.rule(document.getId(), failure.ruleId(), failure.message(), context);
        }
        AmendmentDocumentRevision revision = revision(document.getId(), document.getCurrentRevisionNo());
        String content = currentDigest(document, revision);
        document.setContentDigest(content);
        revision.setContentDigest(content);
        document.setStatus(AmendmentDocumentStatus.IN_REVIEW);
        document.setUpdatedBy(context.actorId());
        entityManager.flush();
        succeed(document, "AmendmentSubmitted", AmendmentDocumentStatus.DRAFT, AmendmentDocumentStatus.IN_REVIEW,
                revision.getRevisionNo(), envelopeRef(revision), context);
        return store(context, SUBMIT, digest, document, document.getLifecycleChangeId(), document.getLifecycleChangeRevision(), true);
    }

    private AmendmentOutcome approveTx(UUID id, int version, String approvalRef, CommandContext context) {
        UUID contractId = contractId(id);
        if (autoExecute(id)) {
            lockContract(contractId);
        }
        AmendmentDocument document = refreshLocked(id);
        requireTenant(document, context);
        requireVersion(document, version, context);
        Transition approve = AmendmentStateMachine.approve(document.getStatus(), document.getSignaturePolicy());
        if (!approve.allowed()) {
            throw refusal(document, approve, context);
        }
        document.setApprovalRef(approvalRef);
        document.setUpdatedBy(context.actorId());
        if (approve.status() == AmendmentDocumentStatus.EXECUTED) {
            return executeLocked(document, context, APPROVE);
        }
        document.setStatus(AmendmentDocumentStatus.APPROVED);
        entityManager.flush();
        succeed(document, "AmendmentApproved", AmendmentDocumentStatus.IN_REVIEW, AmendmentDocumentStatus.APPROVED,
                document.getCurrentRevisionNo(), "approval:" + approvalRef, context);
        String digest = CommercialDigest.sha256(("approve:" + id + ":" + approvalRef).getBytes(StandardCharsets.UTF_8));
        return store(context, APPROVE, digest, document, document.getLifecycleChangeId(), document.getLifecycleChangeRevision(), true);
    }

    private AmendmentOutcome acceptTx(UUID id, int version, CommandContext context) {
        lockContract(contractId(id));
        AmendmentDocument document = refreshLocked(id);
        requireTenant(document, context);
        requireVersion(document, version, context);
        Transition execute = AmendmentStateMachine.execute(document.getStatus(), document.getSignaturePolicy());
        if (!execute.allowed()) {
            if ("SIGNATURE_NOT_SUPPORTED_IN_SLICE".equals(execute.errorCode())) {
                throw AmendmentCommandException.signature(document.getId(), context);
            }
            throw refusal(document, execute, context);
        }
        return executeLocked(document, context, ACCEPT);
    }

    private AmendmentOutcome executeTx(UUID id, int version, CommandContext context) {
        lockContract(contractId(id));
        AmendmentDocument document = refreshLocked(id);
        requireTenant(document, context);
        requireVersion(document, version, context);
        Transition execute = AmendmentStateMachine.execute(document.getStatus(), document.getSignaturePolicy());
        if (!execute.allowed()) {
            throw refusal(document, execute, context);
        }
        return executeLocked(document, context, EXECUTE);
    }

    private AmendmentOutcome executeLocked(AmendmentDocument document, CommandContext context, String operation) {
        AmendmentDocumentRevision revision = revision(document.getId(), document.getCurrentRevisionNo());
        boolean already = revisions(document.getId()).stream().anyMatch(item -> item.getStatusAtClose() == RevisionCloseStatus.EXECUTED);
        String digest = currentDigest(document, revision);
        boolean matches = digest.equals(document.getContentDigest()) && digest.equals(revision.getContentDigest());
        ExecuteFacts facts = ExecuteFacts.passing();
        facts.digestMatches = matches;
        facts.executedRevisionAlreadyExists = already;
        facts.pinsPresent = true;
        facts.policyPresent = true;
        facts.totalsPresent = document.getTcvDelta() != null;
        for (RuleFailure failure : AmendmentValidationRules.onExecute(facts)) {
            throw AmendmentCommandException.rule(document.getId(), failure.ruleId(), failure.message(), context);
        }
        ContractHeader contract = lockContract(document.getContractId());
        int seq = contract.getLastExecutedSeq() + 1;
        contract.setLastExecutedSeq(seq);
        contract.setUpdatedBy(context.actorId());
        document.setAmendmentSeq(seq);
        document.setAmendmentNumber(contract.getContractNumber() + "-A" + String.format("%03d", seq));
        document.setStatus(AmendmentDocumentStatus.EXECUTED);
        document.setExecutedRevisionNo(revision.getRevisionNo());
        document.setExecutedAt(Instant.now());
        document.setUpdatedBy(context.actorId());
        revision.setStatusAtClose(RevisionCloseStatus.EXECUTED);
        revision.setFrozenAt(Instant.now());
        revision.setContentDigest(digest);
        revision.setUpdatedBy(context.actorId());
        entityManager.flush();
        succeed(document, "AmendmentExecuted", AmendmentDocumentStatus.APPROVED, AmendmentDocumentStatus.EXECUTED,
                revision.getRevisionNo(), envelopeRef(revision), context);
        String request = CommercialDigest.sha256((operation + ":" + document.getId() + ":" + seq).getBytes(StandardCharsets.UTF_8));
        return store(context, operation, request, document, document.getLifecycleChangeId(), document.getLifecycleChangeRevision(), true);
    }

    private AmendmentOutcome withdrawTx(UUID id, int version, CommandContext context) {
        AmendmentDocument document = refreshLocked(id);
        requireTenant(document, context);
        requireVersion(document, version, context);
        Transition withdraw = AmendmentStateMachine.withdraw(document.getStatus());
        if (!withdraw.allowed()) {
            throw refusal(document, withdraw, context);
        }
        AmendmentDocumentStatus from = document.getStatus();
        AmendmentDocumentRevision revision = revision(document.getId(), document.getCurrentRevisionNo());
        if (revision.getContentDigest() == null) {
            revision.setContentDigest(currentDigest(document, revision));
        }
        revision.setStatusAtClose(RevisionCloseStatus.WITHDRAWN);
        revision.setFrozenAt(Instant.now());
        document.setStatus(AmendmentDocumentStatus.WITHDRAWN);
        document.setUpdatedBy(context.actorId());
        entityManager.flush();
        succeed(document, "AmendmentWithdrawn", from, AmendmentDocumentStatus.WITHDRAWN, revision.getRevisionNo(), envelopeRef(revision), context);
        String digest = CommercialDigest.sha256(("withdraw:" + id).getBytes(StandardCharsets.UTF_8));
        return store(context, WITHDRAW, digest, document, document.getLifecycleChangeId(), document.getLifecycleChangeRevision(), true);
    }

    private AmendmentOutcome rejectTx(UUID id, int version, CommandContext context) {
        AmendmentDocument document = refreshLocked(id);
        requireTenant(document, context);
        requireVersion(document, version, context);
        Transition reject = AmendmentStateMachine.rejectReview(document.getStatus());
        if (!reject.allowed()) {
            throw refusal(document, reject, context);
        }
        AmendmentDocumentRevision revision = revision(document.getId(), document.getCurrentRevisionNo());
        if (revision.getContentDigest() == null) {
            revision.setContentDigest(currentDigest(document, revision));
        }
        revision.setStatusAtClose(RevisionCloseStatus.REJECTED);
        revision.setFrozenAt(Instant.now());
        document.setStatus(AmendmentDocumentStatus.REJECTED);
        document.setUpdatedBy(context.actorId());
        entityManager.flush();
        succeed(document, "AmendmentRejected", AmendmentDocumentStatus.IN_REVIEW, AmendmentDocumentStatus.REJECTED,
                revision.getRevisionNo(), envelopeRef(revision), context);
        String digest = CommercialDigest.sha256(("reject:" + id).getBytes(StandardCharsets.UTF_8));
        return store(context, REJECT, digest, document, document.getLifecycleChangeId(), document.getLifecycleChangeRevision(), true);
    }

    private void bindEnvelope(AmendmentDocument document, AmendmentDocumentRevision revision, String sourceRef,
                              String sourceDigest, boolean storeContentDigest, CommandContext context) {
        CanonicalBytes bytes = CommercialDigest.digest(projection(document, revision.getRevisionNo(), sourceRef, sourceDigest));
        UUID envelopeId = UUID.randomUUID();
        envelopes.save(AmendmentCommercialEnvelope.create(envelopeId, document.getTenantId(), document.getId(),
                revision.getRevisionNo(), bytes.sha256Hex(), bytes.utf8(), context.actorId()));
        revision.setInputsSnapshotRef("cam-envelope:v1:" + envelopeId);
        if (storeContentDigest) {
            revision.setContentDigest(bytes.sha256Hex());
            document.setContentDigest(bytes.sha256Hex());
        }
    }

    private String currentDigest(AmendmentDocument document, AmendmentDocumentRevision revision) {
        String sourceRef = "unbound";
        String sourceDigest = null;
        String ref = revision.getInputsSnapshotRef();
        if (ref != null && ref.startsWith("cam-envelope:v1:")) {
            UUID envelopeId = UUID.fromString(ref.substring("cam-envelope:v1:".length()));
            AmendmentCommercialEnvelope stored = envelopes.findById(envelopeId).orElseThrow();
            try {
                JsonNode node = json.readTree(stored.getCanonicalBytes());
                sourceRef = node.path("sourceInputRef").asText("unbound");
                sourceDigest = node.get("sourceInputDigest") == null || node.get("sourceInputDigest").isNull()
                        ? null : node.get("sourceInputDigest").asText();
            } catch (Exception failure) {
                throw new IllegalStateException("stored envelope cannot be read", failure);
            }
        }
        return CommercialDigest.digest(projection(document, revision.getRevisionNo(), sourceRef, sourceDigest)).sha256Hex();
    }

    private CommercialEnvelope projection(AmendmentDocument document, int revisionNo, String sourceRef, String sourceDigest) {
        List<CommercialLine> commercialLines = lines(document.getId(), revisionNo).stream().map(this::commercialLine).toList();
        List<CommercialUnit> commercialUnits = units(document.getId()).stream().map(this::commercialUnit).toList();
        return new CommercialEnvelope(document.getTenantId(), document.getId(), revisionNo, document.getCurrencyCode(),
                document.getActionDate(), document.getContractEffectiveDate(), document.getServiceActivationDate(),
                document.getCustomerAcceptanceDate(), document.getBackdatingReasonCode(), document.getCoTermDate(),
                document.getTcvDelta(), document.getArrDelta(), document.getOneTimeTotal(), document.getCreditTotal(),
                sourceRef, sourceDigest, null, commercialLines, commercialUnits);
    }

    private CommercialLine commercialLine(AmendmentDocumentLine line) {
        return new CommercialLine(line.getChangeLineRef(), line.getLineNo(), line.getActionCode().name(),
                line.getBusinessCategory().name(), line.getApplyTargetType().name(), line.getQuantitySemantics().name(),
                line.getStartQty(), line.getDeltaQty(), line.getEndQty(), line.getMetric(), line.getEffectiveFrom(),
                line.getEffectiveTo(), line.getChargeType().name(), line.getPriceAsOf().name(), line.getPolicySlot().name(),
                line.getApplicationUnitNo(), 0L, line.getTargetSubscriptionId(), line.getSourceAcceptedLineUuid(),
                line.getProductExternalId(), line.getPricebookVersion(), line.getConfigurationHash(), line.getUnitPrice(),
                line.getNetAmount(), line.getPolicyId(), line.getPolicyVersion(), line.getPolicyDecisionId(),
                line.getPricingTraceId(), line.getOverrideValue(), line.getOverrideReasonCode(), line.getOverrideActor());
    }

    private CommercialUnit commercialUnit(AmendmentApplicationUnit unit) {
        return new CommercialUnit(unit.getUnitNo(), unit.getDependencyOrder(), unit.getTargetType().name(),
                unit.getTargetRef(), unit.getObservedCommercialRevision());
    }

    private SubmitFacts facts(AmendmentDocument document) {
        AmendmentDocumentLine line = lines(document.getId(), document.getCurrentRevisionNo()).get(0);
        ContractHeader contract = entityManager.find(ContractHeader.class, document.getContractId());
        SubscriptionFact subscription = subscription(line.getTargetSubscriptionId());
        SubmitFacts facts = SubmitFacts.passing();
        facts.intentName = document.getIntent().name();
        facts.action = line.getActionCode();
        facts.deltaQty = line.getDeltaQty();
        facts.category = line.getBusinessCategory();
        facts.actionDate = document.getActionDate();
        facts.effectiveDate = document.getContractEffectiveDate();
        facts.backdatingReason = document.getBackdatingReasonCode();
        facts.contractStart = contract.getStartDate();
        facts.contractEnd = contract.getEndDate();
        facts.lineStart = line.getEffectiveFrom();
        facts.lineEnd = line.getEffectiveTo();
        facts.targetOnContract = subscription != null && document.getContractId().equals(subscription.contractId());
        facts.subscriptionStatus = subscription == null ? "MISSING" : subscription.status();
        facts.observedRevision = observed(document.getId(), line.getApplicationUnitNo());
        facts.commercialRevision = subscription == null ? null : subscription.commercialRevision();
        facts.sourcePresent = line.getSourceAcceptedLineUuid() != null;
        facts.semantics = line.getQuantitySemantics();
        facts.startQty = line.getStartQty();
        facts.endQty = line.getEndQty();
        facts.pinsPresent = line.getProductExternalId() != null && line.getPricebookVersion() != null && line.getConfigurationHash() != null;
        facts.policyPresent = line.getPolicyId() != null && line.getPolicyVersion() != null
                && line.getPolicyDecisionId() != null && line.getPricingTraceId() != null;
        facts.totalsPresent = document.getTcvDelta() != null && document.getArrDelta() != null
                && document.getOneTimeTotal() != null && document.getCreditTotal() != null;
        facts.overrideRequested = line.getOverrideValue() != null || line.getOverrideActor() != null || line.getOverrideReasonCode() != null;
        facts.overrideComplete = line.getOverrideValue() != null && line.getOverrideActor() != null && line.getOverrideReasonCode() != null;
        facts.hasRemove = line.getActionCode() == ActionCode.REMOVE;
        facts.removeAfterAdd = true;
        facts.unitKeysUnique = true;
        return facts;
    }

    private void persistStructure(AmendmentDocument document, int revisionNo, List<LineCommand> lines, CommandContext context) {
        int lineNo = 1;
        for (LineCommand line : lines) {
            AmendmentApplicationUnit unit = entityManager.find(AmendmentApplicationUnit.class,
                    new com.opencrm.contract.domain.documents.AmendmentApplicationUnitKey(document.getId(), line.applicationUnitNo()));
            if (unit == null) {
                unit = AmendmentApplicationUnit.builder()
                        .documentId(document.getId())
                        .unitNo(line.applicationUnitNo())
                        .tenantId(document.getTenantId())
                        .dependencyOrder(line.applicationUnitNo())
                        .targetType(line.targetType())
                        .targetRef(line.targetSubscriptionId() == null ? "none" : line.targetSubscriptionId().toString())
                        .observedCommercialRevision(line.observedCommercialRevision())
                        .status(ApplicationUnitStatus.PENDING)
                        .createdBy(context.actorId())
                        .build();
                entityManager.persist(unit);
                entityManager.flush();
            }
            AmendmentDocumentLine row = AmendmentDocumentLine.builder()
                    .id(UUID.randomUUID())
                    .tenantId(document.getTenantId())
                    .documentId(document.getId())
                    .revisionNo(revisionNo)
                    .lineNo(lineNo++)
                    .changeLineRef(line.changeLineRef())
                    .actionCode(line.action())
                    .businessCategory(line.category())
                    .applyTargetType(line.targetType())
                    .targetSubscriptionId(line.targetSubscriptionId())
                    .sourceAcceptedLineUuid(line.sourceAcceptedLineUuid())
                    .quantitySemantics(line.semantics())
                    .startQty(line.startQty())
                    .deltaQty(line.deltaQty())
                    .endQty(line.endQty())
                    .effectiveFrom(line.effectiveFrom())
                    .effectiveTo(line.effectiveTo())
                    .chargeType(line.chargeType())
                    .priceAsOf(line.priceAsOf())
                    .policySlot(line.policySlot())
                    .applicationUnitNo(line.applicationUnitNo())
                    .createdBy(context.actorId())
                    .build();
            entityManager.persist(row);
        }
    }

    private AmendmentDocumentRevision revision(AmendmentDocument document, int revisionNo, int lifecycleRevision,
                                               UUID targetQuoteId, CommandContext context) {
        return AmendmentDocumentRevision.builder()
                .documentId(document.getId())
                .revisionNo(revisionNo)
                .tenantId(document.getTenantId())
                .lifecycleChangeRevision(lifecycleRevision)
                .targetQuoteId(targetQuoteId)
                .inputsSnapshotRef("pending")
                .statusAtClose(RevisionCloseStatus.OPEN)
                .createdBy(context.actorId())
                .build();
    }

    private AmendmentOutcome store(CommandContext context, String operation, String digest, AmendmentDocument document,
                                   UUID lifecycleId, int sourceRevision, boolean ok) {
        entityManager.flush();
        AmendmentView body = view(document);
        try {
            receipts.save(AmendmentOperationReceipt.create(context.tenantId(), context.callerId(), operation, context.idempotencyKey(),
                    document.getId(), lifecycleId, sourceRevision, digest, json.writeValueAsString(body), context.actorId()));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(failure);
        }
        return new AmendmentOutcome(body, false);
    }

    private AmendmentOutcome replay(CommandContext context, String operation, String digest, UUID documentId,
                                    UUID lifecycleId, int sourceRevision) {
        Optional<AmendmentOperationReceipt> byKey = receipts.findByTenantIdAndCallerIdAndOperationAndIdempotencyKey(
                context.tenantId(), context.callerId(), operation, context.idempotencyKey());
        if (byKey.isPresent()) {
            return decide(byKey.get(), digest, context);
        }
        if (documentId != null) {
            Optional<AmendmentOperationReceipt> natural = receipts.findByTenantIdAndDocumentIdAndSourceLifecycleChangeIdAndSourceRevisionAndOperation(
                    context.tenantId(), documentId, lifecycleId, sourceRevision, operation);
            if (natural.isPresent()) {
                return decide(natural.get(), digest, context);
            }
        } else if (CREATE.equals(operation)) {
            Optional<AmendmentDocument> existing = documents.findByLifecycleChangeId(lifecycleId);
            if (existing.isPresent()) {
                Optional<AmendmentOperationReceipt> natural = receipts.findByTenantIdAndDocumentIdAndSourceLifecycleChangeIdAndSourceRevisionAndOperation(
                        context.tenantId(), existing.get().getId(), lifecycleId, sourceRevision, operation);
                if (natural.isPresent()) {
                    return decide(natural.get(), digest, context);
                }
                throw AmendmentCommandException.conflict(existing.get().getId(), "IDEMPOTENCY_CONFLICT",
                        "lifecycle change already has a document", context);
            }
        }
        return null;
    }

    private AmendmentOutcome decide(AmendmentOperationReceipt receipt, String digest, CommandContext context) {
        AmendmentIdempotency.Decision decision = AmendmentIdempotency.decide(receipt.getRequestDigest(), digest);
        if (decision == AmendmentIdempotency.Decision.REPLAY) {
            try {
                return new AmendmentOutcome(json.readValue(receipt.getResponseJson(), AmendmentView.class), true);
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException(failure);
            }
        }
        throw AmendmentCommandException.conflict(receipt.getDocumentId(), "IDEMPOTENCY_CONFLICT",
                "idempotency key was already used with a different body", context);
    }

    private void succeed(AmendmentDocument document, String type, AmendmentDocumentStatus from, AmendmentDocumentStatus to,
                         Integer revisionNo, String payloadRef, CommandContext context) {
        events.append(new AmendmentEventAppend(document.getId(), type, from, to, revisionNo, null,
                context.actorId(), context.actorType(), context.actorRole(), Instant.now(), null,
                AmendmentEventHasher.sha256(type), payloadRef, null, context.correlationId(), context.actorId()));
    }

    private AmendmentOutcome run(java.util.function.Supplier<AmendmentOutcome> work) {
        try {
            AmendmentOutcome outcome = work.get();
            if (outcome == null) {
                throw new IllegalStateException("empty transaction");
            }
            return outcome;
        } catch (AmendmentCommandException failure) {
            if (failure.isAudit()) {
                try {
                    auditTx.executeWithoutResult(status -> events.append(new AmendmentEventAppend(
                            failure.getDocumentId(), failure.getEventType(), null, null, null, null,
                            failure.getContext().actorId(), failure.getContext().actorType(), failure.getContext().actorRole(),
                            Instant.now(), failure.getRuleId() == null ? failure.getErrorCode() : failure.getRuleId(),
                            AmendmentEventHasher.sha256(failure.getErrorCode()), "refusal:" + failure.getErrorCode(),
                            null, failure.getContext().correlationId(), failure.getContext().actorId())));
                } catch (RuntimeException auditFailure) {
                    failure.addSuppressed(auditFailure);
                }
            }
            throw failure;
        } catch (DataIntegrityViolationException failure) {
            throw new AmendmentCommandException("conflicting commercial write", HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    null, null, "VALIDATION_REJECTED", false, null);
        }
    }

    private void requireVersion(AmendmentDocument document, int expected, CommandContext context) {
        Transition version = AmendmentStateMachine.onExpectedVersion(expected, version(document), document.getStatus());
        if (!version.allowed()) {
            throw refusal(document, version, context);
        }
    }

    private AmendmentCommandException refusal(AmendmentDocument document, Transition transition, CommandContext context) {
        if ("SIGNATURE_NOT_SUPPORTED_IN_SLICE".equals(transition.errorCode())) {
            return AmendmentCommandException.signature(document.getId(), context);
        }
        if (transition.httpStatus() == 422) {
            return AmendmentCommandException.rule(document.getId(), null, transition.errorCode(), context);
        }
        return AmendmentCommandException.conflict(document.getId(), transition.errorCode(), transition.errorCode(), context);
    }

    private void v6(UUID documentId, java.time.LocalDate action, java.time.LocalDate effective, String reason, CommandContext context) {
        AmendmentValidationRules.v6(action, effective, reason).ifPresent(failure -> {
            throw AmendmentCommandException.rule(documentId, failure.ruleId(), failure.message(), context);
        });
    }

    private void validateLines(UUID documentId, List<LineCommand> lines, CommandContext context) {
        for (LineCommand line : lines) {
            if (line.action() != ActionCode.ADD && line.action() != ActionCode.UPDATE && line.action() != ActionCode.REMOVE) {
                throw AmendmentCommandException.rule(documentId, "V1", "action is outside the first slice", context);
            }
            if (line.category() != AmendmentValidationRules.categoryFor(line.action(), line.deltaQty())) {
                throw AmendmentCommandException.rule(documentId, "V1", "business category does not match the line", context);
            }
        }
    }

    private AmendmentView view(AmendmentDocument document) {
        List<RevisionView> revisionViews = revisions(document.getId()).stream()
                .sorted(Comparator.comparingInt(AmendmentDocumentRevision::getRevisionNo))
                .map(revision -> new RevisionView(revision.getRevisionNo(), revision.getLifecycleChangeRevision(),
                        revision.getStatusAtClose(), revision.getContentDigest(), revision.getInputsSnapshotRef()))
                .toList();
        List<LineView> lineViews = lines(document.getId(), document.getCurrentRevisionNo()).stream()
                .map(line -> new LineView(line.getChangeLineRef(), line.getRevisionNo(), line.getProductExternalId(),
                        line.getPricebookVersion(), line.getConfigurationHash(), line.getPolicyDecisionId(), line.getBusinessCategory()))
                .toList();
        return new AmendmentView(document.getId(), document.getContractId(), document.getProvisionalNumber(),
                document.getAmendmentSeq(), document.getAmendmentNumber(), document.getStatus(), version(document),
                document.getContentDigest(), document.getApprovalRef(), document.getSignatureEnvelopeRef(),
                document.getTcvDelta(), document.getArrDelta(), document.getOneTimeTotal(), document.getCreditTotal(),
                revisionViews, lineViews);
    }

    private int version(AmendmentDocument document) {
        return document.getVersion() == null ? 0 : document.getVersion();
    }

    private String envelopeRef(AmendmentDocumentRevision revision) {
        return revision.getInputsSnapshotRef() == null ? "document:" + revision.getDocumentId() : revision.getInputsSnapshotRef();
    }

    private AmendmentDocument load(UUID id) {
        AmendmentDocument document = entityManager.find(AmendmentDocument.class, id);
        if (document == null) {
            throw new ResourceNotFoundException("Amendment", id.toString());
        }
        return document;
    }

    private UUID contractId(UUID documentId) {
        Object value = entityManager.createNativeQuery("select contract_id from amendment_documents where id = :id")
                .setParameter("id", documentId).getSingleResult();
        return UUID.fromString(value.toString());
    }

    private boolean autoExecute(UUID documentId) {
        Object value = entityManager.createNativeQuery("select signature_policy from amendment_documents where id = :id")
                .setParameter("id", documentId).getSingleResult();
        return "NOT_REQUIRED".equals(value) || "NOTICE_ONLY".equals(value);
    }

    private AmendmentDocument refreshLocked(UUID id) {
        AmendmentDocument document = lockDocument(id);
        entityManager.refresh(document, LockModeType.PESSIMISTIC_WRITE);
        return document;
    }

    private AmendmentDocument lockDocument(UUID id) {
        AmendmentDocument document = entityManager.find(AmendmentDocument.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (document == null) {
            throw new ResourceNotFoundException("Amendment", id.toString());
        }
        return document;
    }

    private ContractHeader lockContract(UUID id) {
        ContractHeader contract = entityManager.find(ContractHeader.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (contract == null) {
            throw new ResourceNotFoundException("Contract", id.toString());
        }
        return contract;
    }

    private void requireTenant(AmendmentDocument document, CommandContext context) {
        requireTenant(document.getTenantId(), context);
    }

    private void requireTenant(UUID tenantId, CommandContext context) {
        if (context.tenantId() == null || !context.tenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Amendment", tenantId == null ? "tenant" : tenantId.toString());
        }
    }

    private AmendmentDocumentRevision revision(UUID documentId, int revisionNo) {
        return entityManager.find(AmendmentDocumentRevision.class,
                new com.opencrm.contract.domain.documents.AmendmentRevisionKey(documentId, revisionNo));
    }

    @SuppressWarnings("unchecked")
    private List<AmendmentDocumentRevision> revisions(UUID documentId) {
        return entityManager.createQuery(
                        "select r from AmendmentDocumentRevision r where r.documentId = :id", AmendmentDocumentRevision.class)
                .setParameter("id", documentId).getResultList();
    }

    private List<AmendmentDocumentLine> lines(UUID documentId, int revisionNo) {
        return entityManager.createQuery(
                        "select l from AmendmentDocumentLine l where l.documentId = :id and l.revisionNo = :revision order by l.lineNo",
                        AmendmentDocumentLine.class)
                .setParameter("id", documentId).setParameter("revision", revisionNo).getResultList();
    }

    private List<AmendmentApplicationUnit> units(UUID documentId) {
        return entityManager.createQuery(
                        "select u from AmendmentApplicationUnit u where u.documentId = :id order by u.unitNo",
                        AmendmentApplicationUnit.class)
                .setParameter("id", documentId).getResultList();
    }

    private long observed(UUID documentId, int unitNo) {
        AmendmentApplicationUnit unit = entityManager.find(AmendmentApplicationUnit.class,
                new com.opencrm.contract.domain.documents.AmendmentApplicationUnitKey(documentId, unitNo));
        return unit == null ? -1L : unit.getObservedCommercialRevision();
    }

    @SuppressWarnings("unchecked")
    private SubscriptionFact subscription(UUID id) {
        if (id == null) {
            return null;
        }
        List<Object[]> rows = entityManager.createNativeQuery(
                        "select contract_id, status, commercial_revision from subscriptions where uuid = :uuid")
                .setParameter("uuid", id.toString()).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        UUID contractId = row[0] == null ? null : UUID.fromString(row[0].toString());
        long revision = row[2] instanceof Number number ? number.longValue() : 0L;
        return new SubscriptionFact(contractId, String.valueOf(row[1]), revision);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String digest(String operation, UUID lifecycleId, int revision, UUID quote, String ref, String policy,
                                 java.time.LocalDate action, java.time.LocalDate effective, String reason, String currency,
                                 List<LineCommand> lines) {
        StringBuilder raw = new StringBuilder(operation).append('|').append(lifecycleId).append('|').append(revision)
                .append('|').append(quote).append('|').append(ref).append('|').append(policy).append('|')
                .append(action).append('|').append(effective).append('|').append(reason).append('|').append(currency);
        List<LineCommand> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(line -> line.changeLineRef().toString()));
        for (LineCommand line : sorted) {
            raw.append('|').append(line.changeLineRef()).append('|').append(line.action()).append('|').append(line.category())
                    .append('|').append(line.startQty()).append('|').append(line.deltaQty()).append('|').append(line.endQty())
                    .append('|').append(line.applicationUnitNo()).append('|').append(line.targetSubscriptionId());
        }
        return CommercialDigest.sha256(raw.toString().getBytes(StandardCharsets.UTF_8));
    }

    private record SubscriptionFact(UUID contractId, String status, long commercialRevision) {
    }
}
