package com.opencrm.crm.guided;

import java.util.Map;

/**
 * What {@link ConfigurationSessionStarter} did with a derived product and its pre-selected options.
 *
 * @param configurationSessionId remote session id, or null when the intent was only recorded locally
 * @param recordedLocally true when the configuration base URL was blank and no HTTP call was made
 */
public record StartedConfiguration(
        String productId,
        Map<String, String> selections,
        Long configurationSessionId,
        boolean recordedLocally) {
}
