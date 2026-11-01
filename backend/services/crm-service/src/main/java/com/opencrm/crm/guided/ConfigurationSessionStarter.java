package com.opencrm.crm.guided;

import java.util.Map;

/**
 * Port to the existing configuration API. Implementations must not call {@code complete()}
 * and must not invent a second constraint solver.
 */
public interface ConfigurationSessionStarter {

    StartedConfiguration start(String productId, Map<String, String> selections);
}
