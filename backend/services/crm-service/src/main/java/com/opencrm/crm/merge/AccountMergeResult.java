package com.opencrm.crm.merge;

import lombok.Builder;
import lombok.Value;

/**
 * Result of one account merge. Account ids are the public UUIDs carried on {@code AccountsMerged}.
 */
@Value
@Builder
public class AccountMergeResult {

    Long survivorId;
    Long mergedAwayId;
    String survivorAccountId;
    String mergedAwayAccountId;
    String decidedBy;
    String mergeId;
    int contactsRepointed;
    int leadsRepointed;
    int dealsRepointed;
}
