package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.AmendmentIntent;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import com.opencrm.contract.domain.documents.ApplyTargetType;
import com.opencrm.contract.domain.documents.BusinessCategory;
import com.opencrm.contract.domain.documents.ChargeType;
import com.opencrm.contract.domain.documents.ContractStatus;
import com.opencrm.contract.domain.documents.DocumentKind;
import com.opencrm.contract.domain.documents.EvidenceClass;
import com.opencrm.contract.domain.documents.InitiationTrigger;
import com.opencrm.contract.domain.documents.PolicySlot;
import com.opencrm.contract.domain.documents.PriceAsOf;
import com.opencrm.contract.domain.documents.QuantitySemantics;
import com.opencrm.contract.domain.documents.RevisionCloseStatus;
import com.opencrm.contract.domain.documents.SignaturePolicy;
import com.opencrm.contract.domain.documents.TermModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Docker-free guard: the V1_7 script stays inside the S1 allowlist and names every stored token. */
class ContractDocumentsMigrationScriptTest {

    @Test
    void migrationDeclaresS1SchemaAndOmitsLaterSliceColumns() throws IOException {
        String sql = script().replaceAll("(?m)--.*$", "");
        assertThat(sql).contains("CREATE TABLE contracts");
        assertThat(sql).contains("CREATE TABLE amendment_documents");
        assertThat(sql).contains("CREATE TABLE amendment_document_revisions");
        assertThat(sql).contains("CREATE TABLE amendment_document_lines");
        assertThat(sql).contains("CREATE TABLE amendment_application_units");
        assertThat(sql).contains("CREATE TABLE amendment_document_events");
        assertThat(sql).contains("ADD COLUMN contract_id");
        assertThat(sql).contains("ADD COLUMN amendment_document_line_id");
        assertThat(sql).contains("ADD COLUMN revision_no");
        assertThat(sql).contains("uq_amendment_revision_one_executed");
        assertThat(sql).contains("uq_amendment_document_seq");
        assertThat(sql).contains("CREATE ROLE cam_app");
        assertThat(sql).contains("GRANT SELECT, INSERT ON TABLE amendment_document_events TO cam_app");
        assertThat(sql).contains("reject_amendment_document_event_mutation");
        assertThat(sql).doesNotContain("precedence_flag");
        assertThat(sql).doesNotContain("apply_status");
        assertThat(sql).doesNotContain("applied_quote_acceptances");
        assertThat(sql).doesNotContain("document_apply_ledger");
        for (Class<?> type : storedTokens()) {
            Object[] values = type.getEnumConstants();
            assertThat(values).isNotEmpty();
            for (Object value : values) {
                assertThat(sql).contains("'" + ((Enum<?>) value).name() + "'");
            }
        }
    }

    private static String script() throws IOException {
        try (var in = ContractDocumentsMigrationScriptTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V1_7__contract_documents.sql")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Class<?>[] storedTokens() {
        return new Class<?>[] {
                TermModel.class, ContractStatus.class, AmendmentIntent.class, InitiationTrigger.class,
                DocumentKind.class, AmendmentDocumentStatus.class, SignaturePolicy.class, EvidenceClass.class,
                RevisionCloseStatus.class, ActionCode.class, BusinessCategory.class, ApplyTargetType.class,
                QuantitySemantics.class, ChargeType.class, PriceAsOf.class, PolicySlot.class,
                ApplicationUnitStatus.class, ActorType.class
        };
    }
}
