package com.opencrm.contract.documents;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.contract.domain.documents.ActionCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-594 option B. drg owner ruling, 7:15 PM CT Nov 23 2026.
 * These tests are red until the server-side classifier exists. 594-F1-C stays held:
 * a new SaaS SKU still requires legal review.
 */
class LegalReviewClassifierTest {

    private static final UUID QUOTE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String SAAS = "SAAS_SEAT_NAMED";
    private static final String HARDWARE = "HARDWARE";

    @Test
    void existingSaasSkuAddSkipsLegalReview() {
        assertThat(classify(catalog(Map.of("SKU-A", SAAS)), Set.of("SKU-A"),
                line(ActionCode.ADD, "SKU-A")).legalReviewRequired()).isFalse();
    }

    @Test
    void existingSaasSkuRemoveSkipsLegalReview() {
        assertThat(classify(catalog(Map.of("SKU-A", SAAS)), Set.of("SKU-A"),
                line(ActionCode.REMOVE, "SKU-A")).legalReviewRequired()).isFalse();
    }

    @Test
    void newSaasSkuPredicateStaysOptionB() {
        assertThat(LegalReviewClassifier.newSaasSkuRequiresLegalReview(true, false)).isTrue();
        assertThat(LegalReviewClassifier.newSaasSkuRequiresLegalReview(true, true)).isFalse();
        assertThat(LegalReviewClassifier.newSaasSkuRequiresLegalReview(false, false)).isFalse();
    }

    @Test
    void newSaasSkuRequiresLegalReview() {
        LegalReviewClassifier.Decision decision = classify(catalog(Map.of("SKU-B", SAAS)), Set.of("SKU-A"),
                line(ActionCode.ADD, "SKU-B"));
        assertThat(decision.legalReviewRequired()).isTrue();
        assertThat(decision.errorCode()).isEqualTo("LEGAL_REVIEW_REQUIRED");
    }

    @Test
    void nonSaasAddRequiresLegalReview() {
        assertThat(classify(catalog(Map.of("SKU-HW", HARDWARE)), Set.of("SKU-HW"),
                line(ActionCode.ADD, "SKU-HW")).legalReviewRequired()).isTrue();
    }

    @Test
    void nonSaasRemoveRequiresLegalReview() {
        assertThat(classify(catalog(Map.of("SKU-HW", HARDWARE)), Set.of("SKU-HW"),
                line(ActionCode.REMOVE, "SKU-HW")).legalReviewRequired()).isTrue();
    }

    @Test
    void mixedAmendmentRequiresLegalReview() {
        assertThat(classify(
                catalog(Map.of("SKU-A", SAAS, "SKU-HW", HARDWARE)),
                Set.of("SKU-A", "SKU-HW"),
                line(ActionCode.ADD, "SKU-A"),
                line(ActionCode.ADD, "SKU-HW")).legalReviewRequired()).isTrue();
    }

    @Test
    void unknownSkuTypeRequiresLegalReview() {
        assertThat(classify(catalog(Map.of("SKU-A", "NOT_A_CATALOG_TYPE")), Set.of("SKU-A"),
                line(ActionCode.ADD, "SKU-A")).legalReviewRequired()).isTrue();
    }

    @Test
    void missingCatalogRequiresLegalReview() {
        assertThat(classify(catalog(Map.of()), Set.of("SKU-A"),
                line(ActionCode.REMOVE, "SKU-A")).legalReviewRequired()).isTrue();
    }

    @Test
    void evidenceLookupErrorRequiresLegalReview() {
        LegalReviewEvidence evidence = new LegalReviewEvidence() {
            @Override
            public LegalReviewClassifier.QuoteMembership quoteMembership(UUID quoteId) {
                throw new IllegalStateException("quote schema unavailable");
            }

            @Override
            public LegalReviewClassifier.CatalogProduct catalogProduct(String sku) {
                throw new IllegalStateException("catalog schema unavailable");
            }
        };
        assertThat(LegalReviewClassifier.classify(evidence, QUOTE_ID,
                List.of(line(ActionCode.ADD, "SKU-A")), Set.of()).legalReviewRequired()).isTrue();
    }

    @Test
    void clientSuppliedSkipCannotOverrideClassification() throws Exception {
        ObjectMapper lenient = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        VersionBody body = lenient.readValue(
                "{\"version\":3,\"skipLegalReview\":true,\"legalReviewRequired\":false,\"legalHold\":false}",
                VersionBody.class);
        assertThat(body.version()).isEqualTo(3);
        assertThat(recordNames(CreateAmendment.class)).noneMatch(name -> name.toLowerCase().contains("legal"));
        assertThat(recordNames(VersionBody.class)).containsExactly("version");
        assertThat(Arrays.stream(LegalReviewClassifier.class.getDeclaredMethod(
                        "classify", LegalReviewEvidence.class, UUID.class, List.class, Set.class).getParameterTypes())
                .noneMatch(type -> type == boolean.class || type == Boolean.class)).isTrue();
        assertThat(classify(catalog(Map.of("SKU-B", SAAS)), Set.of("SKU-A"),
                line(ActionCode.ADD, "SKU-B")).legalReviewRequired()).isTrue();
    }

    private static LegalReviewClassifier.Decision classify(Map<String, String> catalog, Set<String> quoteSkus,
                                                           LegalReviewClassifier.Line... lines) {
        return LegalReviewClassifier.classify(evidence(catalog, quoteSkus), QUOTE_ID, List.of(lines), Set.of());
    }

    private static LegalReviewClassifier.Line line(ActionCode action, String sku) {
        return new LegalReviewClassifier.Line(action, sku);
    }

    private static Map<String, String> catalog(Map<String, String> types) {
        return types;
    }

    private static LegalReviewEvidence evidence(Map<String, String> catalog, Set<String> quoteSkus) {
        return new LegalReviewEvidence() {
            @Override
            public LegalReviewClassifier.QuoteMembership quoteMembership(UUID quoteId) {
                return new LegalReviewClassifier.QuoteMembership(true, quoteSkus);
            }

            @Override
            public LegalReviewClassifier.CatalogProduct catalogProduct(String sku) {
                if (!catalog.containsKey(sku)) {
                    return new LegalReviewClassifier.CatalogProduct(false, null);
                }
                return new LegalReviewClassifier.CatalogProduct(true, catalog.get(sku));
            }
        };
    }

    private static List<String> recordNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
