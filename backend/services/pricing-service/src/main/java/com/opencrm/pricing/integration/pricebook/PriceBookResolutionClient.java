package com.opencrm.pricing.integration.pricebook;

import java.util.List;

public interface PriceBookResolutionClient {

    ResolveBatchResponse resolveBatch(String region, List<String> productExternalIds);
}
