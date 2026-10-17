package com.opencrm.contract.service;

import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;
import com.opencrm.contract.dto.RenewalPolicyRecordDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RenewalPolicyTenantIsolationTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ACCOUNT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 1);

    @Test
    void tenantBCannotReadOrVersionTenantA() {
        LeakyStore store = new LeakyStore();
        RenewalPolicyRecordService service = new RenewalPolicyRecordService(store, Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
        RenewalPolicyDraft draft = new RenewalPolicyDraft(
                RenewalPolicyScopeKind.ACCOUNT, ACCOUNT, "SAME", null, null, null, null, null,
                null, null, null, null, AS_OF.minusDays(1), null, null);
        RenewalPolicyRecord stored = service.append(TENANT_A, "service:quote-service", draft);
        stored.setId(UUID.randomUUID());

        List<RenewalPolicyRecordDto> seen = service.read(TENANT_B, ACCOUNT, ACCOUNT, ACCOUNT, AS_OF);
        assertThat(seen).isEmpty();
        assertThat(service.read(TENANT_A, ACCOUNT, null, null, AS_OF)).hasSize(1);

        RenewalPolicyRecord other = service.append(TENANT_B, "service:quote-service", draft);
        assertThat(other.getTenantId()).isEqualTo(TENANT_B);
        assertThat(store.rows.stream().filter(row -> TENANT_A.equals(row.getTenantId()))).hasSize(1);
        assertThat(store.rows.stream().filter(row -> TENANT_A.equals(row.getTenantId())).findFirst().orElseThrow().getPolicyVersion())
                .isEqualTo(1);
        assertThat(service.read(TENANT_B, ACCOUNT, null, null, AS_OF))
                .allSatisfy(dto -> assertThat(dto.mode()).isEqualTo("SAME"))
                .hasSize(1);
        assertThat(service.read(TENANT_B, ACCOUNT, null, null, AS_OF).getFirst().id()).isEqualTo(other.getId());
    }

    /** Returns every tenant's rows so the service filter, not the port, is what hides tenant A. */
    private static final class LeakyStore implements RenewalPolicyRecords {
        private final List<RenewalPolicyRecord> rows = new ArrayList<>();

        @Override
        public List<RenewalPolicyRecord> findScoped(UUID tenantId, RenewalPolicyScopeKind scopeKind, UUID scopeRef) {
            return rows.stream()
                    .filter(row -> row.getScopeKind() == scopeKind && scopeRef.equals(row.getScopeRef()))
                    .toList();
        }

        @Override
        public RenewalPolicyRecord insert(RenewalPolicyRecord record) {
            if (record.getId() == null) {
                record.setId(UUID.randomUUID());
            }
            rows.add(record);
            return record;
        }
    }
}
