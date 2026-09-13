package com.opencrm.crm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseIntegrationTest JDBC URL helpers")
class BaseIntegrationTestUrlTest {

    @Test
    void appendQueryParam_whenUrlHasNoQuery_usesQuestionMark() {
        String result = BaseIntegrationTest.appendQueryParam(
                "jdbc:postgresql://localhost:5432/opencrm", "currentSchema", "crm");
        assertThat(result).isEqualTo("jdbc:postgresql://localhost:5432/opencrm?currentSchema=crm");
    }

    @Test
    void appendQueryParam_whenUrlAlreadyHasQuery_usesAmpersand() {
        String result = BaseIntegrationTest.appendQueryParam(
                "jdbc:postgresql://localhost:5432/opencrm?loggerLevel=OFF", "currentSchema", "crm");
        assertThat(result).isEqualTo(
                "jdbc:postgresql://localhost:5432/opencrm?loggerLevel=OFF&currentSchema=crm");
    }
}
