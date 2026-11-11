package com.opencrm.crm.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    Optional<Activity> findByIdAndDeletedFalse(Long id);

    Page<Activity> findByDeletedFalseAndOrganizationId(Long organizationId, Pageable pageable);

    Page<Activity> findByDeletedFalseAndOrganizationIdAndType(
            Long organizationId, ActivityType type, Pageable pageable);

    Page<Activity> findByDeletedFalseAndContactId(Long contactId, Pageable pageable);

    Page<Activity> findByDeletedFalseAndContactIdAndType(
            Long contactId, ActivityType type, Pageable pageable);

    Page<Activity> findByDeletedFalseAndDealId(Long dealId, Pageable pageable);

    Page<Activity> findByDeletedFalseAndDealIdAndType(Long dealId, ActivityType type, Pageable pageable);
}
