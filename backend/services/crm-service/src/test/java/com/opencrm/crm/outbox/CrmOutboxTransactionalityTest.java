package com.opencrm.crm.outbox;

import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.organization.OrganizationCreateRequest;
import com.opencrm.crm.organization.OrganizationDto;
import com.opencrm.crm.organization.OrganizationService;
import com.opencrm.crm.organization.OrganizationType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The organization insert and its OrganizationCreated outbox row commit and roll back together.
 */
@DisplayName("DRG-510: CRM outbox transactionality")
class CrmOutboxTransactionalityTest extends BaseIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CrmOutboxRows rows;

    @BeforeEach
    void setUp() {
        rows = new CrmOutboxRows(jdbcTemplate);
    }

    @Test
    @DisplayName("state change and outbox row commit together and roll back together")
    void stateChangeAndOutboxCommitAndRollBackTogether() {
        OrganizationDto committed = organizationService.create(request("Commit Org"));
        assertThat(rows.organizations(committed.getUuid())).isEqualTo(1);
        assertThat(rows.forAggregate(committed.getUuid(), CrmOutboxTopics.ORGANIZATION_CREATED)).hasSize(1);

        String rolledBackUuid = transactionTemplate.execute(status -> {
            OrganizationDto created = organizationService.create(request("Rollback Org"));
            entityManager.flush();
            assertThat(rows.organizations(created.getUuid())).isEqualTo(1);
            assertThat(rows.forAggregate(created.getUuid(), CrmOutboxTopics.ORGANIZATION_CREATED)).hasSize(1);
            status.setRollbackOnly();
            return created.getUuid();
        });

        assertThat(rows.organizations(rolledBackUuid)).isZero();
        assertThat(rows.forAggregate(rolledBackUuid, CrmOutboxTopics.ORGANIZATION_CREATED)).isEmpty();
        assertThat(rows.forAggregate(committed.getUuid(), CrmOutboxTopics.ORGANIZATION_CREATED)).hasSize(1);
    }

    private static OrganizationCreateRequest request(String name) {
        return OrganizationCreateRequest.builder()
                .name(name)
                .type(OrganizationType.PROSPECT)
                .build();
    }
}
