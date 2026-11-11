package com.opencrm.crm.activity;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    Optional<Activity> findByIdAndWorkforceTenantIdAndDeletedFalse(Long id, String tenant);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activity a where a.id = :id and a.workforceTenantId = :tenant and a.deleted = false")
    Optional<Activity> lockForCompletion(@Param("id") Long id, @Param("tenant") String tenant);

    @Query("""
        select a from Activity a where a.workforceTenantId = :tenant and a.deleted = false
        and (:organizationId is null or a.organizationId = :organizationId)
        and (:contactId is null or a.contactId = :contactId)
        and (:dealId is null or a.dealId = :dealId)
        and (a.organizationId is null or exists (select o.id from Organization o where o.id = a.organizationId and o.deleted = false))
        and (a.contactId is null or exists (select c.id from Contact c where c.id = a.contactId and c.deleted = false))
        and (a.dealId is null or exists (select d.id from Deal d where d.id = a.dealId and d.deleted = false))
        order by a.occurredAt desc, a.id desc
        """)
    Page<Activity> timeline(@Param("tenant") String tenant,
            @Param("organizationId") Long organizationId, @Param("contactId") Long contactId,
            @Param("dealId") Long dealId, Pageable pageable);
}
