package com.opencrm.quote.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.order.AcceptedOrderSnapshot;
import com.opencrm.common.order.AcceptedSnapshotEvent;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.lineitem.QuoteLineItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class AcceptedSnapshotService {
    private final AcceptedSnapshotRepository repository;
    private final OutboxEventPublisher outbox;
    private final ObjectMapper mapper;
    private final OmsQuoteProperties properties;

    public boolean owns(Quote quote) {
        return "OMS".equals(quote.getFulfillmentOwner()) || (properties.isEnabled()
            && properties.getScopeId() != null && !properties.getScopeId().isBlank()
            && properties.getCustomerIds().contains(quote.getCustomerExternalId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AcceptedSnapshot capture(Quote quote, List<QuoteLineItem> source) {
        if (!owns(quote)) throw new IllegalStateException("Quote is outside OMS cohort");
        if (quote.getAcceptedAt() == null || quote.getUuid() == null) throw new IllegalStateException("Acceptance identity required");
        UUID.fromString(quote.getUuid());
        Set<String> holds = new LinkedHashSet<>();
        JsonNode metadata = metadata(quote.getMetadata(), holds);
        String address = fact(metadata, "address", holds, "MISSING_ADDRESS");
        String approval = quote.getApprovalState() == com.opencrm.quote.quote.ApprovalState.APPROVED ? quote.getOmsApprovalReference() : null;
        if (approval == null) holds.add("MISSING_APPROVAL_REFERENCE");
        if (quote.getApprovalState() != com.opencrm.quote.quote.ApprovalState.APPROVED) holds.add("MISSING_APPROVED_SOURCE");
        String taxEvidence = fact(metadata, "taxEvidence", holds, "MISSING_TAX_EVIDENCE");
        String configuration = fact(metadata, "configurationReference", holds, "MISSING_CONFIGURATION_REFERENCE");
        if (configuration != null && properties.getClassificationMappingVersion() != null) {
            configuration = configuration + ";classification-mapping=" + properties.getClassificationMappingVersion();
        }
        if (!"USD".equals(quote.getCurrency())) holds.add("UNSUPPORTED_CURRENCY");
        List<AcceptedOrderSnapshot.Line> lines = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (QuoteLineItem line : source) {
            if (line.isDeleted()) continue;
            if (line.getUuid() == null || !ids.add(line.getUuid())) throw new IllegalArgumentException("Missing or duplicate sourceLineId");
            JsonNode lm = metadata(line.getMetadata(), holds);
            String fulfillment = fact(lm, "fulfillmentType", holds, "MISSING_FULFILLMENT_TYPE");
            String approvedType = line.getProductExternalId() == null ? null : properties.getProductFulfillmentTypes().get(line.getProductExternalId());
            if (line.getProductExternalId() == null) holds.add("MISSING_PRODUCT_ID");
            if (properties.getClassificationMappingVersion() == null || properties.getClassificationMappingVersion().isBlank()
                || approvedType == null || !approvedType.equals(fulfillment)) {
                fulfillment = null;
                holds.add("CLASSIFICATION_REQUIRES_VALIDATION");
            }
            String charge = fact(lm, "chargeType", holds, "MISSING_CHARGE_TYPE");
            String tax = fact(lm, "tax", holds, "MISSING_LINE_TAX");
            String frequency = line.getBillingFrequency();
            if (!Set.of("PHYSICAL", "DIGITAL", "SERVICE").contains(Objects.toString(fulfillment, ""))) holds.add("INVALID_FULFILLMENT_TYPE");
            if (!Set.of("ONE_TIME", "RECURRING").contains(Objects.toString(charge, ""))) holds.add("INVALID_CHARGE_TYPE");
            if (!Set.of("ONE_TIME", "MONTHLY", "ANNUAL").contains(Objects.toString(frequency, ""))) holds.add("INVALID_BILLING_FREQUENCY");
            if (line.getQuantity() <= 0) holds.add("INVALID_QUANTITY");
            if (line.getUnitOfMeasure() == null) holds.add("MISSING_UOM");
            LocalDate start = date(lm, "startDate", holds);
            LocalDate end = date(lm, "endDate", holds);
            if (start != null && end != null && end.isBefore(start)) holds.add("INVALID_SOURCE_DATES");
            lines.add(new AcceptedOrderSnapshot.Line(line.getUuid(), line.getProductExternalId(), line.getProductName(),
                fulfillment, charge, line.getQuantity(), line.getUnitOfMeasure(), amount(line.getUnitPrice()),
                amount(line.getDiscountAmount()), tax, amount(line.getExtendedAmount()), frequency, start, end));
        }
        if (lines.isEmpty()) holds.add("MISSING_LINES");
        reconcile(quote, lines, holds);
        String snapshotId = UUID.randomUUID().toString();
        int revision = quote.getAcceptedRevision() + 1;
        var snapshot = new AcceptedOrderSnapshot(1, snapshotId, quote.getUuid(), quote.getQuoteNumber(), revision,
            properties.getScopeId(), quote.getCustomerExternalId(), quote.getAcceptedAt(), quote.getCurrency(),
            amount(quote.getSubtotal()), amount(quote.getTotalDiscount()), amount(quote.getTotalTax()), amount(quote.getTotalAmount()),
            address, approval, taxEvidence, configuration, lines);
        try {
            String payload = mapper.writeValueAsString(snapshot);
            String hash = sha256(payload);
            var stored = AcceptedSnapshot.builder().snapshotId(snapshotId).quoteId(quote.getUuid())
                .quoteNumber(quote.getQuoteNumber()).acceptedRevision(revision).scopeId(properties.getScopeId())
                .customerId(quote.getCustomerExternalId()).payload(payload).snapshotHash(hash)
                .holds(mapper.writeValueAsString(holds)).build();
            repository.save(stored);
            quote.setAcceptedRevision(revision);
            quote.setFulfillmentOwner("OMS");
            outbox.publish("Quote", quote.getUuid(), "oms.quote-accepted.v1",
                new AcceptedSnapshotEvent(UUID.randomUUID().toString(), snapshot, hash));
            return stored;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize accepted snapshot", e);
        }
    }
    private JsonNode metadata(String value, Set<String> holds) {
        if (value == null) return mapper.createObjectNode();
        try { return mapper.readTree(value).path("oms"); }
        catch (Exception e) { holds.add("INVALID_SOURCE_METADATA"); return mapper.createObjectNode(); }
    }
    private String fact(JsonNode node, String key, Set<String> holds, String missing) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank()) { holds.add(missing); return null; }
        return value.asText();
    }
    private LocalDate date(JsonNode node, String key, Set<String> holds) {
        String value = fact(node, key, holds, "MISSING_SOURCE_DATES");
        try { return value == null ? null : LocalDate.parse(value); }
        catch (RuntimeException e) { holds.add("INVALID_SOURCE_DATES"); return null; }
    }
    private String amount(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private void reconcile(Quote q, List<AcceptedOrderSnapshot.Line> lines, Set<String> holds) {
        try {
            BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, tax = BigDecimal.ZERO, total = BigDecimal.ZERO;
            for (var line : lines) {
                var gross = new BigDecimal(line.unitPrice()).multiply(BigDecimal.valueOf(line.quantity()));
                var ld = new BigDecimal(line.discount()); var lt = new BigDecimal(line.tax()); var net = new BigDecimal(line.total());
                if (gross.signum() < 0 || ld.signum() < 0 || lt.signum() < 0 || net.signum() < 0
                    || gross.subtract(ld).add(lt).compareTo(net) != 0) holds.add("ECONOMICS_MISMATCH");
                subtotal = subtotal.add(gross); discount = discount.add(ld); tax = tax.add(lt); total = total.add(net);
            }
            if (subtotal.compareTo(q.getSubtotal()) != 0 || discount.compareTo(q.getTotalDiscount()) != 0
                || tax.compareTo(q.getTotalTax()) != 0 || total.compareTo(q.getTotalAmount()) != 0) holds.add("ECONOMICS_MISMATCH");
        } catch (RuntimeException e) { holds.add("MISSING_OR_INVALID_ECONOMICS"); }
    }
    public static String sha256(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
