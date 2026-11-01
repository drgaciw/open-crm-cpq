package com.opencrm.crm.merge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Audit store for account merges. The unique constraint on the merged-away organization
 * rejects a second merge of the same loser.
 */
@Repository
public interface AccountMergeRepository extends JpaRepository<AccountMerge, Long> {
}
