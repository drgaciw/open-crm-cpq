package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One unexplained difference. Blank identity fields are left blank; they are not filled in. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetReconciliationDelta {
    private AssetReconciliationDeltaKind kind;
    private Long subscriptionId;
    private String contractLineId;
    private String assetUuid;
    private String detail;
}
