package com.opencrm.crm.territory;

import com.opencrm.crm.organization.Organization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Locks and reads facts outside a potentially stale request-bound JPA first-level cache. */
@Repository
class TerritoryOrganizationLockRepository {
    private final JdbcTemplate jdbc;

    TerritoryOrganizationLockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The detached value is used only for assignment; it is never saved. */
    Optional<Organization> findForAssignment(Long id) {
        return jdbc.query("""
                select id, uuid, is_deleted, country, state, industry, employee_count
                from organizations where id = ? for update
                """, (row, number) -> {
                    Organization organization = new Organization();
                    organization.setId(row.getLong("id"));
                    organization.setUuid(row.getString("uuid"));
                    organization.setDeleted(row.getBoolean("is_deleted"));
                    organization.setCountry(row.getString("country"));
                    organization.setState(row.getString("state"));
                    organization.setIndustry(row.getString("industry"));
                    organization.setEmployeeCount(row.getObject("employee_count", Integer.class));
                    return organization;
                }, id).stream().findFirst();
    }
}
