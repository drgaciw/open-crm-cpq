package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.ChargeType;
import com.opencrm.contract.domain.documents.ContractHeader;
import com.opencrm.contract.domain.documents.ContractLine;
import com.opencrm.contract.domain.documents.ContractLineStatus;
import com.opencrm.contract.domain.documents.ContractStatus;
import com.opencrm.contract.domain.documents.TermModel;
import com.opencrm.contract.repository.documents.ContractHeaderRepository;
import com.opencrm.contract.repository.documents.ContractLineRepository;
import com.opencrm.contract.service.ContractLineOpenCommand;
import com.opencrm.contract.service.ContractLineOpenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-665 AC1 — tenant_id on a contract line is taken from the parent contract, never a body field.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ContractLineTenantProvenanceTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OTHER_TENANT = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID CONTRACT_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @Mock ContractHeaderRepository contracts;
    @Mock ContractLineRepository lines;

    ContractLineOpenService service;

    @BeforeEach
    void setUp() {
        service = new ContractLineOpenService(contracts, lines);
    }

    @Test
    void openStampsTenantFromContractAndIgnoresBodyTenant() {
        when(contracts.findById(CONTRACT_ID)).thenReturn(Optional.of(header(TENANT)));
        when(lines.save(any(ContractLine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractLineOpenCommand command = new ContractLineOpenCommand(
                CONTRACT_ID,
                OTHER_TENANT,
                1,
                "11111111-1111-4111-8111-111111111111",
                "quote-1",
                "SKU-1",
                "Plan A",
                BigDecimal.ONE,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                ChargeType.RECURRING,
                "tester");

        ContractLine opened = service.open(command);

        assertThat(opened.getTenantId()).isEqualTo(TENANT);
        assertThat(opened.getTenantId()).isNotEqualTo(OTHER_TENANT);
        assertThat(opened.getContractId()).isEqualTo(CONTRACT_ID);
        assertThat(opened.getStatus()).isEqualTo(ContractLineStatus.PENDING);
        assertThat(opened.getAcceptedLineUuid()).isEqualTo("11111111-1111-4111-8111-111111111111");

        ArgumentCaptor<ContractLine> captor = ArgumentCaptor.forClass(ContractLine.class);
        verify(lines).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
    }

    @Test
    void openRejectsUnknownContract() {
        when(contracts.findById(CONTRACT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(new ContractLineOpenCommand(
                CONTRACT_ID,
                TENANT,
                1,
                "line-1",
                null,
                "SKU-1",
                null,
                BigDecimal.ONE,
                LocalDate.of(2026, 1, 1),
                null,
                ChargeType.RECURRING,
                "tester")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contract");
    }

    private static ContractHeader header(UUID tenantId) {
        return ContractHeader.builder()
                .id(CONTRACT_ID)
                .tenantId(tenantId)
                .contractNumber("C-1")
                .customerAccountId(UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"))
                .termModel(TermModel.TERMED)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.ACTIVE)
                .lastExecutedSeq(0)
                .createdBy("seed")
                .build();
    }
}
