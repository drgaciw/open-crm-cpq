package com.opencrm.crm.organization;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the production Spring bean, not the test-ready implementation seam. */
@Tag("integration")
class AccountHierarchyServiceHoldIntegrationTest extends BaseIntegrationTest {
    @Autowired AccountHierarchyService hierarchy;
    @Autowired JdbcTemplate jdbc;

    @Test void productionBeanRejectsDirectReadAndWriteWithoutChangingDataOrPublishing() {
        String uuid=UUID.randomUUID().toString();
        long child=jdbc.queryForObject("insert into crm.organizations(uuid,name,created_by) values(?, 'Child', 'test') returning id",Long.class,uuid);
        long parent=jdbc.queryForObject("insert into crm.organizations(uuid,name,created_by) values(?, 'Parent', 'test') returning id",Long.class,UUID.randomUUID().toString());
        assertThatThrownBy(() -> hierarchy.linkToParent(AccountId.of(uuid), parent))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", "HIERARCHY_UNAVAILABLE");
        assertThatThrownBy(() -> hierarchy.resolveHierarchy(AccountId.of(uuid)))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", "HIERARCHY_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select parent_organization_id from crm.organizations where id=?",Long.class,child)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from crm.outbox_events",Long.class)).isZero();
    }
}
