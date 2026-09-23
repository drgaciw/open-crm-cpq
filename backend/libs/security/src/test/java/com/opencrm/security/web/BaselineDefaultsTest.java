package com.opencrm.security.web;

import com.opencrm.security.test.AbstractSecurityBaselineTest;
import org.junit.jupiter.api.DisplayName;

/** The shared per-service slice test, run once here on the library defaults (no service application.yml). */
@DisplayName("ADR-016 step 0: baseline chain on library defaults")
class BaselineDefaultsTest extends AbstractSecurityBaselineTest {

    @Override
    protected String userApiPath() {
        return "/api/v1/anything";
    }
}
