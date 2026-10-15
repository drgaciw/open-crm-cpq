package com.opencrm.pricing.cost;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("F-2 export contract: CSV reading and per-row validation")
class CostExportCsvTest {

    static final String HEADER = "productExternalId,component,billingUnit,unitCost,currency,effectiveFrom,version,approvedBy";

    static String fixtureV1() {
        try (InputStream in = CostExportCsvTest.class.getResourceAsStream("/fixtures/cost-basis/cpq-cost-export-v1.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("the approved mock v1 export reads as 31 valid rows with quoted fields intact")
    void readsMockVersionOne() {
        CostExportCsv.Result result = CostExportCsv.read(fixtureV1(), 10_000);

        assertThat(result.received()).isEqualTo(31);
        assertThat(result.rejections()).isEmpty();
        assertThat(result.rows()).hasSize(31);
        CostExportRow metered = result.rows().stream()
                .filter(row -> row.productExternalId().equals("MOCK-SAAS-USAGE-METERED")).findFirst().orElseThrow();
        assertThat(metered.billingUnit()).isEqualTo("rated unit (1,000 calls)");
        assertThat(metered.component()).isEqualTo(CostComponent.USAGE_UNIT);
        assertThat(metered.unitCost()).isEqualByComparingTo("0.1000");
        assertThat(metered.unitCost().scale()).isEqualTo(4);
        assertThat(metered.effectiveFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(metered.version()).isEqualTo(1L);
        assertThat(metered.approvedBy()).isEqualTo("Danny (project owner, Finance approver)");
        assertThat(metered.line()).isEqualTo(8);
    }

    @Test
    @DisplayName("CRLF and LF line endings, a BOM and trailing blank lines are accepted")
    void lineEndings() {
        String csv = "\uFEFF" + HEADER + "\r\nP-1,DIRECT,seat,1.5,USD,2026-10-01,2,Finance\r\n\r\n";
        CostExportCsv.Result result = CostExportCsv.read(csv, 10);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.unitCost()).isEqualByComparingTo(new BigDecimal("1.5"));
            assertThat(row.unitCost().scale()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("m8: only a leading U+FEFF is stripped; the reader's source holds no literal BOM")
    void bomIsAnEscapeNotALiteral() throws IOException {
        String csv = "\uFEFF" + HEADER + "\nP-1,DIRECT,seat,1,USD,2026-10-01,1,F";
        assertThat(CostExportCsv.read(csv, 10).rows()).hasSize(1);
        assertThatThrownBy(() -> CostExportCsv.read("\uFEFF\uFEFF" + HEADER, 10))
                .isInstanceOf(BusinessException.class);

        java.nio.file.Path source = java.nio.file.Path.of(
                "src/main/java/com/opencrm/pricing/cost/CostExportCsv.java");
        assertThat(source).exists();
        assertThat(java.nio.file.Files.readString(source, StandardCharsets.UTF_8)).doesNotContain("\uFEFF");
    }

    @Test
    @DisplayName("each malformed row is rejected with its line number and reason; the rest still reads")
    void perRowRejections() {
        String csv = String.join("\n",
                HEADER,
                "P-OK,DIRECT,seat,1.0000,USD,2026-10-01,1,Finance",
                "P-NEG,DIRECT,seat,-0.0001,USD,2026-10-01,1,Finance",
                "P-COMP,OVERHEAD,seat,1,USD,2026-10-01,1,Finance",
                "P-SCALE,DIRECT,seat,1.00001,USD,2026-10-01,1,Finance",
                "P-NAN,DIRECT,seat,abc,USD,2026-10-01,1,Finance",
                "P-CUR,DIRECT,seat,1,XYZ,2026-10-01,1,Finance",
                "P-DATE,DIRECT,seat,1,USD,2026-13-01,1,Finance",
                "P-VER,DIRECT,seat,1,USD,2026-10-01,0,Finance",
                "P-APPR,DIRECT,seat,1,USD,2026-10-01,1,  ",
                "P-UNIT,DIRECT,,1,USD,2026-10-01,1,Finance",
                "bad id!,DIRECT,seat,1,USD,2026-10-01,1,Finance",
                "P-SHORT,DIRECT,seat",
                "\"P-QUOTE,DIRECT,seat,1,USD,2026-10-01,1,Finance");

        CostExportCsv.Result result = CostExportCsv.read(csv, 100);

        assertThat(result.received()).isEqualTo(13);
        assertThat(result.rows()).extracting(CostExportRow::productExternalId).containsExactly("P-OK");
        assertThat(result.rejections()).extracting(RowRejection::line, RowRejection::reason).containsExactly(
                org.assertj.core.groups.Tuple.tuple(3, RejectionReason.NEGATIVE_COST),
                org.assertj.core.groups.Tuple.tuple(4, RejectionReason.UNKNOWN_COMPONENT),
                org.assertj.core.groups.Tuple.tuple(5, RejectionReason.MALFORMED_COST),
                org.assertj.core.groups.Tuple.tuple(6, RejectionReason.MALFORMED_COST),
                org.assertj.core.groups.Tuple.tuple(7, RejectionReason.INVALID_CURRENCY),
                org.assertj.core.groups.Tuple.tuple(8, RejectionReason.MALFORMED_DATE),
                org.assertj.core.groups.Tuple.tuple(9, RejectionReason.MALFORMED_VERSION),
                org.assertj.core.groups.Tuple.tuple(10, RejectionReason.MISSING_APPROVER),
                org.assertj.core.groups.Tuple.tuple(11, RejectionReason.MISSING_BILLING_UNIT),
                org.assertj.core.groups.Tuple.tuple(12, RejectionReason.MALFORMED_PRODUCT_ID),
                org.assertj.core.groups.Tuple.tuple(13, RejectionReason.MALFORMED_ROW),
                org.assertj.core.groups.Tuple.tuple(14, RejectionReason.MALFORMED_ROW));
    }

    @Test
    @DisplayName("zero cost is valid (a component can be free); only negative cost is rejected")
    void zeroCostIsValid() {
        CostExportCsv.Result result = CostExportCsv.read(HEADER + "\nP-0,DIRECT,seat,0,USD,2026-10-01,1,Finance", 10);
        assertThat(result.rows()).hasSize(1);
    }

    @Test
    @DisplayName("a missing or different header refuses the whole file")
    void headerIsRequired() {
        assertThatThrownBy(() -> CostExportCsv.read("P-1,DIRECT,seat,1,USD,2026-10-01,1,Finance", 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("header");
        assertThatThrownBy(() -> CostExportCsv.read("", 10))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("more data rows than the configured maximum refuses the whole file")
    void maximumRows() {
        String csv = HEADER + "\nP-1,DIRECT,seat,1,USD,2026-10-01,1,F\nP-2,DIRECT,seat,1,USD,2026-10-01,1,F";
        assertThatThrownBy(() -> CostExportCsv.read(csv, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at most 1");
    }
}
