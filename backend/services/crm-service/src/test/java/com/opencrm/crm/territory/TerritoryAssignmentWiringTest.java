package com.opencrm.crm.territory;

import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Exercises the constructor Spring actually selects, rather than direct construction in unit tests. */
class TerritoryAssignmentWiringTest {
    @Test
    void componentCanBeConstructedWithoutAClockBean() {
        new ApplicationContextRunner()
                .withBean(OrganizationRepository.class, () -> mock(OrganizationRepository.class))
                .withBean(TerritoryOrganizationLockRepository.class, () -> mock(TerritoryOrganizationLockRepository.class))
                .withBean(TerritoryAssignmentRuleRepository.class, () -> mock(TerritoryAssignmentRuleRepository.class))
                .withBean(OrganizationTerritoryAssignmentRepository.class, () -> mock(OrganizationTerritoryAssignmentRepository.class))
                .withBean(OutboxEventPublisher.class, () -> mock(OutboxEventPublisher.class))
                .withBean(jakarta.persistence.EntityManager.class, () -> mock(jakarta.persistence.EntityManager.class))
                .withBean(TerritoryAssignmentReadiness.class)
                .withBean(TerritoryAssignmentService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TerritoryAssignmentService.class);
                    assertThat(context.getBean(TerritoryAssignmentReadiness.class).isReady()).isFalse();
                });
    }
}
