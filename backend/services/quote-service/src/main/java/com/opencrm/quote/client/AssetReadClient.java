package com.opencrm.quote.client;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads an account's installed base from contract-asset-management over HTTP.
 * There is no tenant argument: the caller's token carries it.
 */
public interface AssetReadClient {

    /**
     * {@code GET /api/v1/assets?accountId=&asOf=yyyy-MM-dd}. The as-of date is the subscription term end.
     */
    List<InstalledAssetView> installedBase(String accountId, LocalDate asOf);
}
