package com.opencrm.crm.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Audit store for hierarchy link / unlink decisions (DRG-714).
 */
@Repository
public interface AccountHierarchyChangeRepository extends JpaRepository<AccountHierarchyChange, Long> {
}
