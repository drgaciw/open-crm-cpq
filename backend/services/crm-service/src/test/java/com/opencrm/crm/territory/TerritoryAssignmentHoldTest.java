package com.opencrm.crm.territory;

import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.crm.organization.OrganizationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TerritoryAssignmentHoldTest {
    @Test
    void directCallsFailClosedBeforeReadsOrWrites() {
        var organizations = mock(OrganizationRepository.class);
        var locks = mock(TerritoryOrganizationLockRepository.class);
        var rules = mock(TerritoryAssignmentRuleRepository.class);
        var assignments = mock(OrganizationTerritoryAssignmentRepository.class);
        var publisher = mock(OutboxEventPublisher.class);
        var manager = mock(EntityManager.class);
        var service = new TerritoryAssignmentService(organizations, locks, rules, assignments, publisher, manager,
                new TerritoryAssignmentReadiness());
        assertHeld(() -> service.createRule(TerritoryRuleRequest.builder().name("Rule").country("USA")
                .industry("Software").territoryCode("WEST").segmentCode("SMB").build()));
        assertHeld(service::listRules);
        assertHeld(() -> service.assign(1L));
        assertHeld(() -> service.getSegment(1L));
        verifyNoInteractions(organizations, locks, rules, assignments, publisher, manager);
    }

    private static void assertHeld(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(ex.getErrorCode()).isEqualTo("TERRITORY_ASSIGNMENT_NOT_READY");
        });
    }
}
