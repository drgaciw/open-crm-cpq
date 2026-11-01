package com.opencrm.quote.lifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.ContinuationEvaluationView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.security.Caller;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Phase A lifecycle changes: create, price and submit for intent AMEND.
 * Intent AMEND is not a CRM DealType. This service does not write {@code quotes.deal_type}.
 * Quote DealType AMENDMENT stays on the excluded continuation facade.
 *
 * <p>No approval request, no outbox row and no event. Rejections go to
 * {@code lifecycle_change_audit}, not {@code commercial_decision_audit}.
 */
@Slf4j
@Service
public class LifecycleChangeService {

    static final String DECISION_KIND = "LIFECYCLE_CHANGE_REJECTED";
    private static final Set<String> STORABLE_ACTIONS = Set.of(
            "ADD", "UPDATE", "REMOVE", "SWAP", "UPGRADE", "DOWNGRADE");
    private static final Set<String> UNSUPPORTED_AT_SUBMIT = Set.of("SWAP", "UPGRADE", "DOWNGRADE");
    private static final String RETAIN_CONTRACTED = "RETAIN_CONTRACTED";
    private static final String DAILY = "DAILY";

    private final LifecycleChangeRepository changes;
    private final LifecycleChangeAuditWriter audit;
    private final SubscriptionTargetReader subscriptions;
    private final PricingServiceClient pricing;
    private final ObjectMapper json;
    private final LifecycleCreateLock createLock;

    public LifecycleChangeService(LifecycleChangeRepository changes, LifecycleChangeAuditWriter audit,
                                  SubscriptionTargetReader subscriptions, PricingServiceClient pricing,
                                  ObjectMapper json, LifecycleCreateLock createLock) {
        this.changes = changes;
        this.audit = audit;
        this.subscriptions = subscriptions;
        this.pricing = pricing;
        this.json = json;
        this.createLock = createLock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LifecycleChangeResult create(CreateLifecycleChangeRequest request, String idempotencyKey) {
        UserContext user = requireUser();
        String key = firstText(idempotencyKey, request.getRequestIdentifier());
        String hash = bodyHash(request);
        if (key != null) {
            createLock.lock(user.getTenantId(), key);
            Optional<LifecycleChange> existing = changes.findByTidAndIdempotencyKey(user.getTenantId(), key);
            if (existing.isPresent()) {
                if (hash.equals(existing.get().getIdempotencyBodyHash())) {
                    return LifecycleChangeResult.replay(toResponse(existing.get()));
                }
                throw new BusinessException("Idempotency key was already used with a different body",
                        HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
            }
        }
        if (!"AMEND".equals(request.getIntent())) {
            throw rejected("Only intent AMEND is accepted in this slice", "INTENT_NOT_SUPPORTED_IN_SLICE");
        }
        List<ChangeLine> lines = prepareLines(user.getTenantId(), request.getLines());
        for (ChangeLine line : lines) {
            if (line.getTargetSubscriptionId() != null) {
                assertCurrentTarget(user, null, line, request);
            }
        }
        LifecycleChange change = new LifecycleChange();
        change.setId(UUID.randomUUID());
        change.setTid(user.getTenantId());
        change.setIntent("AMEND");
        change.setInitiationTrigger(request.getInitiationTrigger());
        change.setRequestIdentifier(request.getRequestIdentifier());
        change.setIdempotencyKey(key);
        change.setIdempotencyBodyHash(hash);
        change.setStatus(LifecycleChangeStatus.DRAFT);
        change.setRevision(1);
        change.setVersion(0L);
        change.setActionDate(request.getActionDate());
        change.setContractEffectiveDate(request.getContractEffectiveDate());
        change.setContractId(request.getContractId());
        change.setCurrencyCode(request.getCurrencyCode());
        change.setOutputMode(request.getOutputMode() == null || request.getOutputMode().isBlank()
                ? "QUOTE" : request.getOutputMode());
        change.setCreatedAt(Instant.now());
        change.setCreatedBy(user.getUserId());
        lines.forEach(line -> line.setLifecycleChange(change));
        change.setLines(lines);
        LifecycleChange saved = changes.save(change);
        log.info("LifecycleChange created id={} tid={} actor={}", saved.getId(), saved.getTid(), user.getUserId());
        return LifecycleChangeResult.created(toResponse(saved));
    }

    @Transactional(readOnly = true)
    public LifecycleChangeResponse get(UUID id) {
        return toResponse(load(requireUser(), id));
    }

    @Transactional
    public LifecycleChangeResult price(UUID id, PriceLifecycleChangeRequest request) {
        UserContext user = requireUser();
        LifecycleChange change = load(user, id);
        requireVersion(change, request.getVersion());
        if (change.getStatus() != LifecycleChangeStatus.DRAFT && change.getStatus() != LifecycleChangeStatus.PRICED) {
            throw new BusinessException("Only a draft or priced change can be priced",
                    HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");
        }
        change = reserveVersion(user, change, request.getVersion());
        for (ChangeLine line : change.getLines()) {
            if (line.getTargetSubscriptionId() != null) {
                assertCurrentTarget(user, change.getId(), line, null);
            }
        }
        Map<String, Object> body = pricingRequest(change);
        ApiResponse<ContinuationEvaluationView> response = pricing.evaluateContinuation(body);
        if (response == null || !response.isSuccess() || response.getData() == null || response.getData().getLines() == null) {
            throw new BusinessException("Pricing continuation did not return a decision",
                    HttpStatus.BAD_GATEWAY, "PRICING_UNAVAILABLE");
        }
        applyPricing(change, response.getData());
        if (change.getLines().stream().allMatch(this::hasPricePins)) {
            change.setStatus(LifecycleChangeStatus.PRICED);
        }
        LifecycleChange saved = changes.save(change);
        log.info("LifecycleChange priced id={} tid={} actor={}", saved.getId(), saved.getTid(), user.getUserId());
        return LifecycleChangeResult.created(toResponse(saved));
    }

    @Transactional
    public LifecycleChangeResult submit(UUID id, VersionedCommand command) {
        UserContext user = requireUser();
        LifecycleChange change = load(user, id);
        if (change.getStatus() == LifecycleChangeStatus.IN_APPROVAL) {
            if (!isSubmitReplayVersion(change, command.getVersion())) {
                throw versionConflict();
            }
            log.info("LifecycleChange submitted id={} tid={} actor={} replay=true",
                    change.getId(), change.getTid(), user.getUserId());
            return LifecycleChangeResult.replay(toResponse(change));
        }
        requireVersion(change, command.getVersion());
        ChangeLine unsupported = change.getLines().stream()
                .filter(line -> UNSUPPORTED_AT_SUBMIT.contains(line.getActionCode()))
                .findFirst()
                .orElse(null);
        if (unsupported != null) {
            reject(user, change.getId(), unsupported.getTargetSubscriptionId(), "ACTION_NOT_SUPPORTED_IN_SLICE",
                    write(Map.of("lifecycleChangeId", change.getId().toString(), "actionCode", unsupported.getActionCode())),
                    write(Map.of("errorCode", "ACTION_NOT_SUPPORTED_IN_SLICE")));
        }
        if (change.getStatus() != LifecycleChangeStatus.PRICED) {
            throw new BusinessException("Submit is only allowed from PRICED",
                    HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");
        }
        if (changes.advanceVersion(change.getId(), user.getTenantId(), command.getVersion()) != 1) {
            LifecycleChange current = load(user, id);
            if (current.getStatus() == LifecycleChangeStatus.IN_APPROVAL && isSubmitReplayVersion(current, command.getVersion())) {
                return LifecycleChangeResult.replay(toResponse(current));
            }
            throw versionConflict();
        }
        change = load(user, id);
        for (ChangeLine line : change.getLines()) {
            if (line.getTargetSubscriptionId() != null) {
                assertCurrentTarget(user, change.getId(), line, null);
            }
            if (!hasDecisionIds(line)) {
                throw rejected("V8: a priced line is missing policyDecisionId, policyId, policyVersion or pricingTraceId",
                        "VALIDATION_REJECTED");
            }
            if (blank(line.getProductExternalId()) || blank(line.getPricebookVersion())) {
                throw rejected("V9: product and pricebook pins are required", "VALIDATION_REJECTED");
            }
        }
        change.setStatus(LifecycleChangeStatus.IN_APPROVAL);
        LifecycleChange saved = changes.save(change);
        log.info("LifecycleChange submitted id={} tid={} actor={}", saved.getId(), saved.getTid(), user.getUserId());
        return LifecycleChangeResult.created(toResponse(saved));
    }

    private void assertCurrentTarget(UserContext user, UUID changeId, ChangeLine line, CreateLifecycleChangeRequest request) {
        String subscriptionId = line.getTargetSubscriptionId();
        SubscriptionTarget target = subscriptions.read(subscriptionId);
        if (target != null && target.status() != null && "ACTIVE".equalsIgnoreCase(target.status())) {
            if (target.commercialRevision() != null && target.commercialRevision().equals(line.getObservedCommercialRevision())) {
                return;
            }
            String inputs = write(Map.of("subscriptionId", subscriptionId,
                    "observedRevision", String.valueOf(line.getObservedCommercialRevision()),
                    "currentRevision", String.valueOf(target.commercialRevision())));
            audit.record(new LifecycleChangeAuditEntry(user.getTenantId(), changeId, subscriptionId, DECISION_KIND,
                    "OBSERVED_REVISION_MISMATCH", user.getUserId(), inputs,
                    write(Map.of("errorCode", "VALIDATION_REJECTED", "ruleId", "V3"))));
            throw rejected("V3: observed commercial revision does not match the subscription", "VALIDATION_REJECTED");
        }
        String inputs = request == null
                ? write(Map.of("subscriptionId", subscriptionId))
                : write(request);
        reject(user, changeId, subscriptionId, "SUBSCRIPTION_NOT_ACTIVE", inputs,
                write(Map.of("errorCode", "VALIDATION_REJECTED", "ruleId", "V2")));
    }

    private void reject(UserContext user, UUID changeId, String subscriptionId, String reason,
                        String inputsJson, String outputsJson) {
        log.info("LifecycleChange rejected reason={} tid={} subscriptionId={} lifecycleChangeId={} actor={}",
                reason, user.getTenantId(), subscriptionId, changeId, user.getUserId());
        audit.record(new LifecycleChangeAuditEntry(
                user.getTenantId(), changeId, subscriptionId, DECISION_KIND, reason,
                user.getUserId(), inputsJson, outputsJson));
        if ("ACTION_NOT_SUPPORTED_IN_SLICE".equals(reason)) {
            throw rejected("Action is not supported in this slice", reason);
        }
        throw rejected("V2: target subscription is not ACTIVE", "VALIDATION_REJECTED");
    }

    private List<ChangeLine> prepareLines(String tid, List<ChangeLineWrite> requested) {
        List<ChangeLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (ChangeLineWrite write : requested) {
            if (!STORABLE_ACTIONS.contains(write.getActionCode())) {
                throw rejected("Action is not supported in this slice", "ACTION_NOT_SUPPORTED_IN_SLICE");
            }
            if (!"DELTA".equals(write.getQuantitySemantics()) && !"ABSOLUTE".equals(write.getQuantitySemantics())) {
                throw rejected("Quantity semantics must be DELTA or ABSOLUTE", "VALIDATION_REJECTED");
            }
            BigDecimal start = money(write.getStartQty());
            BigDecimal delta = money(write.getDeltaQty());
            BigDecimal end = money(write.getEndQty());
            if (end.signum() < 0) {
                throw rejected("end quantity must be zero or positive", "VALIDATION_REJECTED");
            }
            if ("DELTA".equals(write.getQuantitySemantics()) && start.add(delta).compareTo(end) != 0) {
                throw rejected("Delta quantity must satisfy start + delta = end", "VALIDATION_REJECTED");
            }
            if (write.getEffectiveTo() != null && write.getEffectiveTo().isBefore(write.getEffectiveFrom())) {
                throw rejected("effectiveTo is before effectiveFrom", "VALIDATION_REJECTED");
            }
            boolean targeted = !blank(write.getTargetSubscriptionId());
            if (("UPDATE".equals(write.getActionCode()) || "REMOVE".equals(write.getActionCode())) && !targeted) {
                throw rejected("UPDATE and REMOVE require a target subscription", "VALIDATION_REJECTED");
            }
            if (targeted && write.getObservedCommercialRevision() == null) {
                throw rejected("V3: observed revision is required when a target subscription is set", "VALIDATION_REJECTED");
            }
            ChangeLine line = new ChangeLine();
            line.setId(UUID.randomUUID());
            line.setTid(tid);
            line.setLineNo(lineNo++);
            line.setActionCode(write.getActionCode());
            line.setQuantitySemantics(write.getQuantitySemantics());
            line.setBusinessCategory(category(write, delta));
            line.setApplyTargetType(targeted ? "SUBSCRIPTION" : "NONE");
            line.setTargetSubscriptionId(targeted ? write.getTargetSubscriptionId() : null);
            line.setSourceAcceptedLineUuid(write.getSourceAcceptedLineUuid());
            line.setObservedCommercialRevision(write.getObservedCommercialRevision());
            line.setStartQty(start);
            line.setDeltaQty(delta);
            line.setEndQty(end);
            line.setEffectiveFrom(write.getEffectiveFrom());
            line.setEffectiveTo(write.getEffectiveTo());
            line.setChargeType("RECURRING");
            line.setPriceAsOf("EFFECTIVE_DATE");
            line.setPolicySlot("AMENDMENT");
            line.setProductExternalId(write.getProductExternalId());
            lines.add(line);
        }
        return lines;
    }

    private static String category(ChangeLineWrite write, BigDecimal delta) {
        if ("REMOVE".equals(write.getActionCode())) {
            return "CANCELLATION";
        }
        if ("UPDATE".equals(write.getActionCode()) && delta.signum() < 0) {
            return "DOWNSELL";
        }
        return "UPSELL";
    }

    private Map<String, Object> pricingRequest(LifecycleChange change) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ChangeLine line : change.getLines()) {
            line.setAmendMode(RETAIN_CONTRACTED);
            line.setProrationMethod(DAILY);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lineRef", line.getId().toString());
            item.put("action", line.getActionCode());
            item.put("quantitySemantics", line.getQuantitySemantics());
            item.put("startQuantity", plain(line.getStartQty()));
            item.put("deltaQuantity", plain(line.getDeltaQty()));
            item.put("endQuantity", plain(line.getEndQty()));
            item.put("amendMode", RETAIN_CONTRACTED);
            item.put("prorationMethod", DAILY);
            item.put("effectiveFrom", line.getEffectiveFrom().toString());
            item.put("effectiveTo", line.getEffectiveTo() == null ? null : line.getEffectiveTo().toString());
            item.put("productExternalId", line.getProductExternalId());
            lines.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currency", change.getCurrencyCode());
        body.put("lines", lines);
        return body;
    }

    private void applyPricing(LifecycleChange change, ContinuationEvaluationView view) {
        Map<String, ContinuationEvaluationView.Line> byRef = new LinkedHashMap<>();
        for (ContinuationEvaluationView.Line priced : view.getLines()) {
            if (priced.getLineRef() != null) {
                byRef.put(priced.getLineRef(), priced);
            }
        }
        for (ChangeLine line : change.getLines()) {
            ContinuationEvaluationView.Line priced = byRef.get(line.getId().toString());
            if (priced == null) {
                throw new BusinessException("Pricing continuation omitted a line",
                        HttpStatus.BAD_GATEWAY, "PRICING_UNAVAILABLE");
            }
            line.setAmendMode(RETAIN_CONTRACTED);
            line.setProrationMethod(DAILY);
            line.setPolicyDecisionId(priced.getPolicyDecisionId());
            line.setPolicyId(firstText(priced.getPolicyId(), view.getPolicyId()));
            line.setPolicyVersion(firstText(priced.getPolicyVersion(), view.getPolicyVersion()));
            line.setPricingTraceId(firstText(priced.getTraceId(), view.getTraceId()));
            if (!blank(priced.getProductExternalId())) {
                line.setProductExternalId(priced.getProductExternalId());
            }
            line.setPricebookVersion(priced.getPricebookVersion());
            line.setConfigurationHash(priced.getConfigurationHash());
            line.setUnitPrice(optionalMoney(priced.getUnitRate()));
            line.setNetAmount(optionalMoney(priced.getAmount()));
        }
    }

    private boolean hasPricePins(ChangeLine line) {
        return hasDecisionIds(line) && !blank(line.getProductExternalId()) && !blank(line.getPricebookVersion());
    }

    private static boolean hasDecisionIds(ChangeLine line) {
        return !blank(line.getPolicyDecisionId()) && !blank(line.getPolicyId())
                && !blank(line.getPolicyVersion()) && !blank(line.getPricingTraceId());
    }

    private LifecycleChange load(UserContext user, UUID id) {
        return changes.findByIdAndTid(id, user.getTenantId())
                .orElseThrow(() -> new BusinessException("LifecycleChange not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private LifecycleChange reserveVersion(UserContext user, LifecycleChange change, Long expected) {
        if (expected == null || changes.advanceVersion(change.getId(), user.getTenantId(), expected) != 1) {
            throw versionConflict();
        }
        return load(user, change.getId());
    }

    private static boolean isSubmitReplayVersion(LifecycleChange change, Long requested) {
        Long current = change.getVersion();
        return requested != null && current != null
                && (requested.equals(current) || (current > 0 && requested == current - 1));
    }

    private static BusinessException versionConflict() {
        return new BusinessException("Version conflict", HttpStatus.CONFLICT, "VERSION_CONFLICT");
    }

    private static void requireVersion(LifecycleChange change, Long version) {
        if (version == null || !version.equals(change.getVersion())) {
            throw new BusinessException("Version conflict", HttpStatus.CONFLICT, "VERSION_CONFLICT");
        }
    }

    private UserContext requireUser() {
        Optional<Caller> caller = SecurityUtils.getCurrentCaller();
        if (caller.isEmpty()) {
            throw new BusinessException("An authenticated user is required",
                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        }
        if (!(caller.get() instanceof Caller.User)) {
            throw new BusinessException("Lifecycle changes are decided by people, not by services",
                    HttpStatus.FORBIDDEN, "LIFECYCLE_CHANGE_MUST_BE_A_USER");
        }
        UserContext user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new BusinessException("An authenticated user is required",
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));
        if (!LifecycleAuthority.mayChange(user.getRoles())) {
            log.warn("Refused lifecycle change by {}: none of {}", user.getUserId(), LifecycleAuthority.ALLOWED);
            throw new BusinessException("Creating or submitting a lifecycle change needs a CPQ role",
                    HttpStatus.FORBIDDEN, "LIFECYCLE_ROLE_REQUIRED");
        }
        if (blank(user.getTenantId())) {
            throw new BusinessException("The caller token has no tenant", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
        return user;
    }

    private LifecycleChangeResponse toResponse(LifecycleChange change) {
        List<ChangeLineResponse> lines = change.getLines().stream()
                .sorted((left, right) -> Integer.compare(left.getLineNo(), right.getLineNo()))
                .map(this::toLine)
                .toList();
        return LifecycleChangeResponse.builder()
                .id(change.getId())
                .intent(change.getIntent())
                .initiationTrigger(change.getInitiationTrigger())
                .status(change.getStatus().name())
                .revision(change.getRevision())
                .version(change.getVersion())
                .actionDate(change.getActionDate())
                .contractEffectiveDate(change.getContractEffectiveDate())
                .contractId(change.getContractId())
                .currencyCode(change.getCurrencyCode())
                .outputMode(change.getOutputMode())
                .createdBy(change.getCreatedBy())
                .lines(lines)
                .build();
    }

    private ChangeLineResponse toLine(ChangeLine line) {
        return ChangeLineResponse.builder()
                .lineId(line.getId())
                .lineNo(line.getLineNo())
                .actionCode(line.getActionCode())
                .quantitySemantics(line.getQuantitySemantics())
                .observedCommercialRevision(line.getObservedCommercialRevision())
                .startQty(plain(line.getStartQty()))
                .deltaQty(plain(line.getDeltaQty()))
                .endQty(plain(line.getEndQty()))
                .effectiveFrom(line.getEffectiveFrom())
                .effectiveTo(line.getEffectiveTo())
                .targetSubscriptionId(line.getTargetSubscriptionId())
                .amendMode(line.getAmendMode())
                .prorationMethod(line.getProrationMethod())
                .policyDecisionId(line.getPolicyDecisionId())
                .policyId(line.getPolicyId())
                .policyVersion(line.getPolicyVersion())
                .pricingTraceId(line.getPricingTraceId())
                .productExternalId(line.getProductExternalId())
                .pricebookVersion(line.getPricebookVersion())
                .configurationHash(line.getConfigurationHash())
                .build();
    }

    private String bodyHash(CreateLifecycleChangeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", request.getIntent());
        body.put("initiationTrigger", request.getInitiationTrigger());
        body.put("requestIdentifier", request.getRequestIdentifier());
        body.put("actionDate", request.getActionDate());
        body.put("contractEffectiveDate", request.getContractEffectiveDate());
        body.put("contractId", request.getContractId());
        body.put("currencyCode", request.getCurrencyCode());
        body.put("outputMode", request.getOutputMode());
        body.put("lines", request.getLines());
        try {
            byte[] bytes = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Could not hash the lifecycle request", ex);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not write lifecycle audit JSON", ex);
        }
    }

    private static BigDecimal money(String raw) {
        if (blank(raw)) {
            throw rejected("A quantity is required", "VALIDATION_REJECTED");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw rejected("Quantity is not a decimal", "VALIDATION_REJECTED");
        }
        if (value.scale() > 4) {
            return value.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal optionalMoney(String raw) {
        if (blank(raw)) {
            return null;
        }
        return money(raw);
    }

    private static String plain(BigDecimal value) {
        return value.setScale(4, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String firstText(String preferred, String fallback) {
        if (!blank(preferred)) {
            return preferred.trim();
        }
        if (!blank(fallback)) {
            return fallback.trim();
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static BusinessException rejected(String message, String code) {
        return new BusinessException(message, HttpStatus.UNPROCESSABLE_CONTENT, code);
    }
}
