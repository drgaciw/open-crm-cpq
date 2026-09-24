package com.opencrm.configuration.config;

import com.opencrm.security.test.AbstractSecurityBaselineTest;
import org.junit.jupiter.api.DisplayName;

/**
 * ADR-016 rollout step 0 evidence for {@code configuration-engine}: the {@code libs/security} baseline chain with this
 * service's own {@code application.yml}, outside the {@code dev} profile, with real signed tokens. It replaces
 * the {@code SecurityConfig} profile tests that step 1 deleted together with the chains they guarded.
 */
@DisplayName("configuration-engine: ADR-016 baseline chain outside dev")
class SecurityBaselineTest extends AbstractSecurityBaselineTest {

    @Override
    protected String userApiPath() {
        return "/api/v1/configurations";
    }

    @Override
    protected String internalPath() {
        return "/internal/v1/configurations/evaluate";
    }
}
