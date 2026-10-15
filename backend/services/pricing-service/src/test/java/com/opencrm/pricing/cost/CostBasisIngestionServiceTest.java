package com.opencrm.pricing.cost;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.product.ProductType;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.opencrm.pricing.cost.CostExportCsvTest.HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("CostBasisIngestionService: F-2 ingestion, idempotent by version, audited")
class CostBasisIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final LocalDate Q4 = LocalDate.of(2026, 10, 1);

    private final Map<String, PricingProduct> catalog = new HashMap<>();
    private final List<CostBasisRow> stored = new ArrayList<>();
    private final List<CostBasisImport> audits = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private CurrentActor actor;
    private CostBasisIngestionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void wire() {
        PricingProductRepository products = mock(PricingProductRepository.class);
        when(products.findByExternalIdInAndDeletedFalse(anyCollection())).thenAnswer(call ->
                ((Collection<String>) call.getArgument(0)).stream().map(catalog::get)
                        .filter(java.util.Objects::nonNull).toList());

        CostBasisRowRepository rows = mock(CostBasisRowRepository.class);
        when(rows.findByProductExternalIdInAndEffectiveFromIn(anyCollection(), anyCollection())).thenAnswer(call -> {
            Collection<String> ids = call.getArgument(0);
            Collection<LocalDate> dates = call.getArgument(1);
            return stored.stream().filter(row -> ids.contains(row.getProductExternalId())
                    && dates.contains(row.getEffectiveFrom())).toList();
        });
        when(rows.saveAll(anyCollection())).thenAnswer(call -> {
            List<CostBasisRow> saved = List.copyOf((Collection<CostBasisRow>) call.getArgument(0));
            stored.addAll(saved);
            return saved;
        });

        CostBasisImportRepository imports = mock(CostBasisImportRepository.class);
        when(imports.save(any(CostBasisImport.class))).thenAnswer(call -> {
            CostBasisImport audit = call.getArgument(0);
            audit.setId(ids.incrementAndGet());
            audits.add(audit);
            return audit;
        });

        actor = mock(CurrentActor.class);
        Caller admin = new Caller.User("oid-of-admin", java.util.Set.of("ROLE_ADMIN"));
        when(actor.caller()).thenReturn(Optional.of(admin));
        when(actor.id()).thenReturn("oid-of-admin");

        service = new CostBasisIngestionService(products, rows, imports, actor, new CostBasisProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("mock v1 loads all 31 rows and records the audit entry with the token's principal")
    void loadsMockVersionOne() {
        String csv = CostExportCsvTest.fixtureV1();
        knowAllProductsIn(csv);

        CostBasisImportReport report = service.ingest(csv);

        CostBasisImportSummary audit = report.audit();
        assertThat(audit.importId()).isEqualTo(1L);
        assertThat(audit.exportVersion()).isEqualTo(1L);
        assertThat(audit.approvedBy()).isEqualTo("Danny (project owner, Finance approver)");
        assertThat(audit.ingestedBy()).isEqualTo("oid-of-admin");
        assertThat(audit.ingestedAt()).isEqualTo(NOW);
        assertThat(audit.contentSha256()).hasSize(64);
        assertThat(audit.received()).isEqualTo(31);
        assertThat(audit.accepted()).isEqualTo(31);
        assertThat(audit.inserted()).isEqualTo(31);
        assertThat(audit.unchanged()).isZero();
        assertThat(audit.rejected()).isZero();
        assertThat(audit.unitUnchecked()).isEqualTo(31);
        assertThat(report.rejections()).isEmpty();

        assertThat(stored).hasSize(31).allSatisfy(row -> {
            assertThat(row.getImportId()).isEqualTo(1L);
            assertThat(row.getIngestedBy()).isEqualTo("oid-of-admin");
            assertThat(row.getIngestedAt()).isEqualTo(NOW);
            assertThat(row.getApprovedBy()).isEqualTo("Danny (project owner, Finance approver)");
        });
        assertThat(audits).singleElement().satisfies(entry -> {
            assertThat(entry.getRowsAccepted()).isEqualTo(31);
            assertThat(entry.getRowsRejected()).isZero();
        });
    }

    @Test
    @DisplayName("re-ingesting the same version is a no-op: nothing inserted, every row unchanged, still audited")
    void sameVersionIsNoOp() {
        String csv = CostExportCsvTest.fixtureV1();
        knowAllProductsIn(csv);
        service.ingest(csv);

        CostBasisImportSummary replay = service.ingest(csv).audit();

        assertThat(stored).hasSize(31);
        assertThat(replay.inserted()).isZero();
        assertThat(replay.unchanged()).isEqualTo(31);
        assertThat(replay.accepted()).isEqualTo(31);
        assertThat(replay.importId()).isEqualTo(2L);
        assertThat(audits).hasSize(2);
        assertThat(audits.get(0).getContentSha256()).isEqualTo(audits.get(1).getContentSha256());
    }

    @Test
    @DisplayName("a higher version adds a row for the same key and date; the earlier row stays on record")
    void higherVersionInserts() {
        know("P");
        service.ingest(HEADER + "\nP,DIRECT,seat,1.0000,USD,2026-10-01,1,Finance");

        CostBasisImportSummary v2 = service.ingest(HEADER + "\nP,DIRECT,seat,1.2000,USD,2026-10-01,2,Finance").audit();

        assertThat(v2.inserted()).isEqualTo(1);
        assertThat(stored).extracting(CostBasisRow::getVersion).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("an older version arriving after a newer one is superseded, not stored")
    void olderVersionIsSuperseded() {
        know("P");
        service.ingest(HEADER + "\nP,DIRECT,seat,1.2000,USD,2026-10-01,2,Finance");

        CostBasisImportSummary late = service.ingest(HEADER + "\nP,DIRECT,seat,1.0000,USD,2026-10-01,1,Finance").audit();

        assertThat(late.superseded()).isEqualTo(1);
        assertThat(late.inserted()).isZero();
        assertThat(stored).hasSize(1);
    }

    @Test
    @DisplayName("the same version with a different cost is a VERSION_CONFLICT rejection, never a silent overwrite")
    void sameVersionDifferentContentIsRejected() {
        know("P");
        service.ingest(HEADER + "\nP,DIRECT,seat,1.0000,USD,2026-10-01,1,Finance");

        CostBasisImportReport report = service.ingest(HEADER + "\nP,DIRECT,seat,9.0000,USD,2026-10-01,1,Finance");

        assertThat(report.rejections()).singleElement()
                .extracting(RowRejection::reason).isEqualTo(RejectionReason.VERSION_CONFLICT);
        assertThat(stored).singleElement().extracting(CostBasisRow::getUnitCost)
                .satisfies(cost -> assertThat((BigDecimal) cost).isEqualByComparingTo("1"));
    }

    @Test
    @DisplayName("an unknown product is rejected and reported; the rest of the export loads")
    void unknownProduct() {
        know("KNOWN");
        CostBasisImportReport report = service.ingest(HEADER
                + "\nKNOWN,DIRECT,seat,1,USD,2026-10-01,1,Finance"
                + "\nGHOST,DIRECT,seat,1,USD,2026-10-01,1,Finance");

        assertThat(report.audit().inserted()).isEqualTo(1);
        assertThat(report.audit().rejected()).isEqualTo(1);
        assertThat(report.rejections()).singleElement().satisfies(rejection -> {
            assertThat(rejection.line()).isEqualTo(3);
            assertThat(rejection.productExternalId()).isEqualTo("GHOST");
            assertThat(rejection.reason()).isEqualTo(RejectionReason.UNKNOWN_PRODUCT);
        });
    }

    @Test
    @DisplayName("billing unit is checked where the catalog states one (metadata billingUnit), case-insensitively")
    void billingUnitAgainstCatalog() {
        knowWithUnit("SEAT", "Seat");
        knowWithUnit("SUB", "subscription");
        CostBasisImportReport report = service.ingest(HEADER
                + "\nSEAT,DIRECT,seat,1,USD,2026-10-01,1,Finance"
                + "\nSUB,DIRECT,seat,1,USD,2026-10-01,1,Finance");

        assertThat(report.audit().inserted()).isEqualTo(1);
        assertThat(report.audit().unitUnchecked()).isZero();
        assertThat(report.rejections()).singleElement().satisfies(rejection -> {
            assertThat(rejection.productExternalId()).isEqualTo("SUB");
            assertThat(rejection.reason()).isEqualTo(RejectionReason.BILLING_UNIT_MISMATCH);
        });
    }

    @Test
    @DisplayName("a key repeated in one file rejects every occurrence")
    void duplicateKeyInFile() {
        know("P");
        CostBasisImportReport report = service.ingest(HEADER
                + "\nP,DIRECT,seat,1,USD,2026-10-01,1,Finance"
                + "\nP,DIRECT,seat,2,USD,2026-10-01,1,Finance"
                + "\nP,COST_TO_SERVE,seat,1,USD,2026-10-01,1,Finance");

        assertThat(report.rejections()).extracting(RowRejection::line, RowRejection::reason).containsExactly(
                org.assertj.core.groups.Tuple.tuple(2, RejectionReason.DUPLICATE_KEY_IN_FILE),
                org.assertj.core.groups.Tuple.tuple(3, RejectionReason.DUPLICATE_KEY_IN_FILE));
        assertThat(report.audit().inserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("components of one product and date must share billing unit and currency, else the group is rejected")
    void inconsistentGroup() {
        know("P");
        know("Q");
        CostBasisImportReport report = service.ingest(HEADER
                + "\nP,DIRECT,seat,1,USD,2026-10-01,1,Finance"
                + "\nP,COST_TO_SERVE,subscription,1,USD,2026-10-01,1,Finance"
                + "\nQ,DIRECT,seat,1,USD,2026-10-01,1,Finance");

        assertThat(report.rejections()).extracting(RowRejection::productExternalId, RowRejection::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("P", RejectionReason.INCONSISTENT_GROUP),
                        org.assertj.core.groups.Tuple.tuple("P", RejectionReason.INCONSISTENT_GROUP));
        assertThat(report.audit().inserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("a row whose unit or currency disagrees with the stored components of that date is rejected")
    void conflictWithStored() {
        know("P");
        service.ingest(HEADER + "\nP,COST_TO_SERVE,seat,1,EUR,2026-10-01,1,Finance");

        CostBasisImportReport report = service.ingest(HEADER + "\nP,DIRECT,seat,1,USD,2026-10-01,1,Finance");

        assertThat(report.rejections()).singleElement()
                .extracting(RowRejection::reason).isEqualTo(RejectionReason.CONFLICTS_WITH_STORED);
    }

    @Test
    @DisplayName("an export with more than one version or approver is refused as a whole (422), and not audited")
    void oneVersionAndApproverPerExport() {
        know("P");
        assertThatThrownBy(() -> service.ingest(HEADER
                + "\nP,DIRECT,seat,1,USD,2026-10-01,1,Finance"
                + "\nP,COST_TO_SERVE,seat,1,USD,2026-10-01,2,Finance"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getErrorCode()).isEqualTo("COST_EXPORT_MIXED_VERSIONS");
                });
        assertThatThrownBy(() -> service.ingest(HEADER
                + "\nP,DIRECT,seat,1,USD,2026-10-01,1,Finance"
                + "\nP,COST_TO_SERVE,seat,1,USD,2026-10-01,1,Someone else"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("COST_EXPORT_MIXED_APPROVERS"));
        assertThat(audits).isEmpty();
    }

    @Test
    @DisplayName("an export whose rows are all rejected is still audited, with no version when none parsed")
    void allRejectedIsAudited() {
        CostBasisImportSummary audit = service.ingest(HEADER + "\nP,DIRECT,seat,1,USD,2026-10-01,x,Finance").audit();
        assertThat(audit.rejected()).isEqualTo(1);
        assertThat(audit.exportVersion()).isNull();
        assertThat(audits).hasSize(1);
    }

    @Test
    @DisplayName("without a verified principal the import is refused (401) unless dev mode allows unverified callers")
    void requiresVerifiedPrincipal() {
        when(actor.caller()).thenReturn(Optional.empty());
        when(actor.unverifiedCallersAllowed()).thenReturn(false);
        know("P");
        assertThatThrownBy(() -> service.ingest(HEADER + "\nP,DIRECT,seat,1,USD,2026-10-01,1,Finance"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        when(actor.unverifiedCallersAllowed()).thenReturn(true);
        when(actor.id()).thenReturn("service:pricing-service");
        assertThat(service.ingest(HEADER + "\nP,DIRECT,seat,1,USD,2026-10-01,1,Finance").audit().ingestedBy())
                .isEqualTo("service:pricing-service");
    }

    private void knowAllProductsIn(String csv) {
        CostExportCsv.read(csv, 10_000).rows().forEach(row -> know(row.productExternalId()));
    }

    private void know(String externalId) {
        catalog.put(externalId, PricingProduct.builder().externalId(externalId).name(externalId)
                .productType(ProductType.SAAS_SEAT_NAMED).build());
    }

    private void knowWithUnit(String externalId, String unit) {
        catalog.put(externalId, PricingProduct.builder().externalId(externalId).name(externalId)
                .productType(ProductType.SAAS_SEAT_NAMED).metadata("{\"billingUnit\":\"" + unit + "\"}").build());
    }
}
