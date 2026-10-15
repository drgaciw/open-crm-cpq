package com.opencrm.pricing.cost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.security.CurrentActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ingests the F-2 cost export (DRG-526 / BC-03-13), in one transaction with its audit entry.
 *
 * <p>Order of checks, each removing the rows it rejects before the next:
 * <ol>
 *   <li>the file: body size ({@code max-bytes}, 413), header (400), row count ({@code max-rows}, 422), one
 *       {@code version} and one {@code approvedBy} per export (422). Any of these refuses the file whole: it is logged
 *       at WARN with actor, SHA-256 and error code, and no audit row is written;</li>
 *   <li>each row's format ({@link CostExportCsv});</li>
 *   <li>the catalog replica {@code pricing_products}: the product must exist and not be deleted; where its
 *       {@code metadata} JSON states a {@code billingUnit}, the row's must match it, ignoring case. The replica has no
 *       unit column, so rows of products without one are accepted and counted as {@code unitUnchecked};</li>
 *   <li>a key (product, component, effective date) appearing twice in the file rejects every occurrence;</li>
 *   <li>the components of one product and date in the file must share billing unit and currency;</li>
 *   <li>idempotency by version against stored rows: the same version with the same content is unchanged (the replay
 *       no-op); with different content it is a {@code VERSION_CONFLICT}; a lower version than one stored is
 *       superseded; otherwise the row is inserted;</li>
 *   <li>after the merge, the effective components of each product and date (highest version per component) must still
 *       share billing unit and currency, else the new rows of that group are rejected.</li>
 * </ol>
 *
 * <p><b>Billing unit coverage (M5, partial F-2).</b> The billing unit is checked only where the catalog metadata carries
 * {@code billingUnit}; every other accepted row is counted in {@code unitUnchecked} and the report says so.
 *
 * <p><b>Omitted components (m4).</b> A newer version that omits a component does not remove it: the stored row of that
 * component keeps applying on its effective date. To retire a component, export it with {@code unitCost} 0.
 */
@Slf4j
@Service
public class CostBasisIngestionService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CATALOG_UNIT_KEY = "billingUnit";

    private final PricingProductRepository products;
    private final CostBasisRowRepository rows;
    private final CostBasisImportRepository imports;
    private final CurrentActor currentActor;
    private final CostBasisProperties properties;
    private final Clock clock;

    @Autowired
    public CostBasisIngestionService(PricingProductRepository products, CostBasisRowRepository rows,
                                     CostBasisImportRepository imports, CurrentActor currentActor,
                                     CostBasisProperties properties) {
        this(products, rows, imports, currentActor, properties, Clock.systemUTC());
    }

    CostBasisIngestionService(PricingProductRepository products, CostBasisRowRepository rows,
                              CostBasisImportRepository imports, CurrentActor currentActor,
                              CostBasisProperties properties, Clock clock) {
        this.products = products;
        this.rows = rows;
        this.imports = imports;
        this.currentActor = currentActor;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CostBasisImportReport ingest(String csv) {
        String ingestedBy = verifiedActor();
        String contentSha256 = sha256(csv);
        CostExportCsv.Result parsed;
        Long exportVersion;
        String approvedBy;
        try {
            long bytes = (csv == null ? "" : csv).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > properties.getMaxBytes().toBytes()) {
                throw tooLarge(bytes + " bytes");
            }
            parsed = CostExportCsv.read(csv, properties.getMaxRows());
            exportVersion = single(parsed.rows(), CostExportRow::version, "version", "COST_EXPORT_MIXED_VERSIONS");
            approvedBy = single(parsed.rows(), CostExportRow::approvedBy, "approvedBy", "COST_EXPORT_MIXED_APPROVERS");
        } catch (BusinessException refused) {
            warnRefused(ingestedBy, contentSha256, refused);
            throw refused;
        }

        List<RowRejection> rejections = new ArrayList<>(parsed.rejections());
        Set<Integer> unitUnchecked = new HashSet<>();
        List<CostExportRow> candidates = againstCatalog(parsed.rows(), rejections, unitUnchecked);
        candidates = withoutDuplicateKeys(candidates, rejections);
        candidates = withConsistentGroups(candidates, rejections);

        List<CostBasisRow> stored = candidates.isEmpty() ? List.of() : rows.findByProductExternalIdInAndEffectiveFromIn(
                candidates.stream().map(CostExportRow::productExternalId).collect(Collectors.toSet()),
                candidates.stream().map(CostExportRow::effectiveFrom).collect(Collectors.toSet()));
        Map<CostExportRow.Key, List<CostBasisRow>> storedByKey = stored.stream()
                .collect(Collectors.groupingBy(CostBasisIngestionService::keyOf));

        List<CostExportRow> toInsert = new ArrayList<>();
        int unchanged = 0;
        int superseded = 0;
        for (CostExportRow row : candidates) {
            List<CostBasisRow> sameKey = storedByKey.getOrDefault(row.key(), List.of());
            Optional<CostBasisRow> sameVersion = sameKey.stream().filter(s -> s.getVersion() == row.version()).findFirst();
            if (sameVersion.isPresent()) {
                if (sameVersion.get().sameContentAs(row)) {
                    unchanged++;
                } else {
                    rejections.add(RowRejection.of(row, RejectionReason.VERSION_CONFLICT, "version " + row.version()
                            + " of this key is already stored with a different cost, unit or currency; issue a higher version"));
                }
            } else if (sameKey.stream().anyMatch(s -> s.getVersion() > row.version())) {
                superseded++;
            } else {
                toInsert.add(row);
            }
        }
        toInsert = consistentWithStored(toInsert, stored, rejections);

        Instant ingestedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        int accepted = toInsert.size() + unchanged + superseded;
        rejections.sort(Comparator.comparingInt(RowRejection::line));
        Set<Integer> rejectedLines = rejections.stream().map(RowRejection::line).collect(Collectors.toSet());
        int unitUncheckedAccepted = (int) unitUnchecked.stream().filter(line -> !rejectedLines.contains(line)).count();

        CostBasisImport audit = imports.save(CostBasisImport.builder()
                .exportVersion(exportVersion)
                .approvedBy(approvedBy)
                .ingestedBy(ingestedBy)
                .ingestedAt(ingestedAt)
                .contentSha256(contentSha256)
                .rowsReceived(parsed.received())
                .rowsAccepted(accepted)
                .rowsInserted(toInsert.size())
                .rowsUnchanged(unchanged)
                .rowsSuperseded(superseded)
                .rowsRejected(rejections.size())
                .rowsUnitUnchecked(unitUncheckedAccepted)
                .build());
        if (!toInsert.isEmpty()) {
            rows.saveAll(toInsert.stream().map(row -> CostBasisRow.builder()
                    .importId(audit.getId())
                    .productExternalId(row.productExternalId())
                    .component(row.component())
                    .billingUnit(row.billingUnit())
                    .unitCost(row.unitCost())
                    .currency(row.currency())
                    .effectiveFrom(row.effectiveFrom())
                    .version(row.version())
                    .approvedBy(row.approvedBy())
                    .ingestedAt(ingestedAt)
                    .ingestedBy(ingestedBy)
                    .build()).toList());
        }
        log.info("Cost basis import {}: version {}, approved by {}, ingested by {}: {} received, {} inserted, "
                        + "{} unchanged, {} superseded, {} rejected", audit.getId(), exportVersion, approvedBy,
                ingestedBy, parsed.received(), toInsert.size(), unchanged, superseded, rejections.size());
        return new CostBasisImportReport(audit.toSummary(), rejections);
    }

    @Transactional(readOnly = true)
    public Page<CostBasisImportSummary> list(Pageable pageable) {
        return imports.findAllByOrderByIngestedAtDescIdDesc(pageable).map(CostBasisImport::toSummary);
    }

    /**
     * The refusal of a body the controller did not read because it is over {@code max-bytes} (m3). Logged like every
     * whole-file refusal, with {@code sha256=unread}: the body was never read in full, so it has no hash. Nothing is
     * persisted.
     *
     * @param bytes the declared {@code Content-Length}, or the bytes read before the limit was passed
     */
    public BusinessException bodyTooLarge(long bytes) {
        BusinessException refused = tooLarge(bytes > 0 ? "at least " + bytes + " bytes" : "an undeclared length");
        warnRefused(currentActor.id(), "unread", refused);
        return refused;
    }

    private BusinessException tooLarge(String size) {
        return new BusinessException("A cost export body may hold at most " + properties.getMaxBytes().toBytes()
                + " bytes (opencrm.pricing.cost-basis.max-bytes); this one has " + size,
                HttpStatus.CONTENT_TOO_LARGE, "COST_EXPORT_BODY_TOO_LARGE");
    }

    /**
     * (d) A whole file refused (bad header, too many rows, mixed version or approver, oversize) is logged at WARN with
     * the actor, the file's SHA-256 and the error code. No audit row is written for it: the audit trail records
     * ingestions, and the refusal rolls back with the transaction.
     */
    private static void warnRefused(String actor, String contentSha256, BusinessException refused) {
        log.warn("Cost export refused whole: actor={} sha256={} errorCode={} status={}", actor, contentSha256,
                refused.getErrorCode(), refused.getStatus().value());
    }

    /** ADR-016 step 4: the actor comes from the verified token only. Only dev mode may proceed without one. */
    private String verifiedActor() {
        if (currentActor.caller().isEmpty() && !currentActor.unverifiedCallersAllowed()) {
            throw new BusinessException("Cost basis ingestion needs a verified principal",
                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        }
        return currentActor.id();
    }

    /** F-2: one export carries one version and one approver; otherwise the whole file is refused. */
    private static <T> T single(List<CostExportRow> rows, Function<CostExportRow, T> field, String name, String code) {
        Set<T> values = rows.stream().map(field).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (values.size() > 1) {
            throw new BusinessException("A cost export carries one " + name + "; this one has " + values,
                    HttpStatus.UNPROCESSABLE_ENTITY, code);
        }
        return values.isEmpty() ? null : values.iterator().next();
    }

    private List<CostExportRow> againstCatalog(List<CostExportRow> parsed, List<RowRejection> rejections,
                                               Set<Integer> unitUnchecked) {
        if (parsed.isEmpty()) {
            return List.of();
        }
        Map<String, PricingProduct> catalog = products.findByExternalIdInAndDeletedFalse(
                        parsed.stream().map(CostExportRow::productExternalId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(PricingProduct::getExternalId, Function.identity(), (a, b) -> a));
        List<CostExportRow> known = new ArrayList<>();
        for (CostExportRow row : parsed) {
            PricingProduct product = catalog.get(row.productExternalId());
            if (product == null) {
                rejections.add(RowRejection.of(row, RejectionReason.UNKNOWN_PRODUCT,
                        "product is not in pricing-service's catalog replica"));
                continue;
            }
            Optional<String> catalogUnit = catalogUnit(product);
            if (catalogUnit.isEmpty()) {
                unitUnchecked.add(row.line());
            } else if (!normalise(catalogUnit.get()).equals(normalise(row.billingUnit()))) {
                rejections.add(RowRejection.of(row, RejectionReason.BILLING_UNIT_MISMATCH,
                        "the catalog bills this product per '" + catalogUnit.get() + "'"));
                continue;
            }
            known.add(row);
        }
        return known;
    }

    /** The billing unit the catalog states in the product's metadata JSON, if any. Unreadable metadata states none. */
    static Optional<String> catalogUnit(PricingProduct product) {
        String metadata = product.getMetadata();
        if (metadata == null || metadata.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode unit = JSON.readTree(metadata).path(CATALOG_UNIT_KEY);
            return unit.isTextual() && !unit.asText().isBlank() ? Optional.of(unit.asText().trim()) : Optional.empty();
        } catch (Exception e) {
            log.warn("Product {} has unreadable metadata; its billing unit is not checked", product.getExternalId());
            return Optional.empty();
        }
    }

    private static List<CostExportRow> withoutDuplicateKeys(List<CostExportRow> candidates,
                                                            List<RowRejection> rejections) {
        Map<CostExportRow.Key, Long> counts = candidates.stream()
                .collect(Collectors.groupingBy(CostExportRow::key, Collectors.counting()));
        List<CostExportRow> unique = new ArrayList<>();
        for (CostExportRow row : candidates) {
            if (counts.get(row.key()) > 1) {
                rejections.add(RowRejection.of(row, RejectionReason.DUPLICATE_KEY_IN_FILE,
                        "this product, component and effective date appear more than once in the file"));
            } else {
                unique.add(row);
            }
        }
        return unique;
    }

    private static List<CostExportRow> withConsistentGroups(List<CostExportRow> candidates,
                                                            List<RowRejection> rejections) {
        Map<Group, List<CostExportRow>> groups = candidates.stream()
                .collect(Collectors.groupingBy(Group::ofRow, LinkedHashMap::new, Collectors.toList()));
        List<CostExportRow> consistent = new ArrayList<>();
        for (CostExportRow row : candidates) {
            List<CostExportRow> group = groups.get(Group.ofRow(row));
            boolean oneUnit = group.stream().map(r -> normalise(r.billingUnit())).distinct().count() == 1;
            boolean oneCurrency = group.stream().map(CostExportRow::currency).distinct().count() == 1;
            if (oneUnit && oneCurrency) {
                consistent.add(row);
            } else {
                rejections.add(RowRejection.of(row, RejectionReason.INCONSISTENT_GROUP,
                        "the components of this product and date in the file disagree on billing unit or currency"));
            }
        }
        return consistent;
    }

    private static List<CostExportRow> consistentWithStored(List<CostExportRow> toInsert, List<CostBasisRow> stored,
                                                            List<RowRejection> rejections) {
        Map<Group, List<CostExportRow>> incoming = toInsert.stream()
                .collect(Collectors.groupingBy(Group::ofRow, LinkedHashMap::new, Collectors.toList()));
        Set<Group> conflicting = new HashSet<>();
        incoming.forEach((group, rowsOfGroup) -> {
            Map<CostComponent, Effective> effective = new HashMap<>();
            stored.stream().filter(s -> group.equals(Group.ofStored(s)))
                    .forEach(s -> effective.merge(s.getComponent(),
                            new Effective(s.getVersion(), normalise(s.getBillingUnit()), s.getCurrency()), Effective::max));
            rowsOfGroup.forEach(r -> effective.merge(r.component(),
                    new Effective(r.version(), normalise(r.billingUnit()), r.currency()), Effective::max));
            boolean oneUnit = effective.values().stream().map(Effective::unit).distinct().count() == 1;
            boolean oneCurrency = effective.values().stream().map(Effective::currency).distinct().count() == 1;
            if (!oneUnit || !oneCurrency) {
                conflicting.add(group);
            }
        });
        List<CostExportRow> kept = new ArrayList<>();
        for (CostExportRow row : toInsert) {
            if (conflicting.contains(Group.ofRow(row))) {
                rejections.add(RowRejection.of(row, RejectionReason.CONFLICTS_WITH_STORED,
                        "the stored components of this product and date use another billing unit or currency"));
            } else {
                kept.add(row);
            }
        }
        return kept;
    }

    private static CostExportRow.Key keyOf(CostBasisRow row) {
        return new CostExportRow.Key(row.getProductExternalId(), row.getComponent(), row.getEffectiveFrom());
    }

    static String normalise(String billingUnit) {
        return billingUnit.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String csv) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((csv == null ? "" : csv).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    /** A product on one effective date. */
    private record Group(String productExternalId, LocalDate effectiveFrom) {
        static Group ofRow(CostExportRow row) {
            return new Group(row.productExternalId(), row.effectiveFrom());
        }

        static Group ofStored(CostBasisRow row) {
            return new Group(row.getProductExternalId(), row.getEffectiveFrom());
        }
    }

    /** The highest version of one component in a group, with its unit and currency. */
    private record Effective(long version, String unit, String currency) {
        static Effective max(Effective a, Effective b) {
            return b.version() > a.version() ? b : a;
        }
    }
}
