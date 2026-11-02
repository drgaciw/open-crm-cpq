package com.opencrm.crm.guided;

import java.util.Map;

/**
 * Product and option selections the starter intended to apply. Kept when the configuration
 * base URL is blank, and also after a successful remote start.
 */
public record RecordedSessionIntent(String productId, Map<String, String> selections) {
}
