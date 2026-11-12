package com.opencrm.crm.fact;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardFactFieldsTest {

    @Test
    void emailAndOrganizationLinksAreSensitive() {
        assertThat(StandardFactFields.sensitive("email")).isTrue();
        assertThat(StandardFactFields.sensitive("organizationId")).isTrue();
        assertThat(StandardFactFields.sensitive("organization_id")).isTrue();
        assertThat(StandardFactFields.sensitive("parentOrganizationId")).isTrue();
        assertThat(StandardFactFields.sensitive("jobTitle")).isFalse();
    }

    @Test
    void reparentIsNotAColumnThisLedgerWrites() {
        assertThat(StandardFactFields.reparent(FactSubjectType.ORGANIZATION, "parent_organization_id")).isTrue();
        assertThat(StandardFactFields.columnWritable(FactSubjectType.ORGANIZATION, "parentOrganizationId")).isFalse();
        assertThat(StandardFactFields.columnWritable(FactSubjectType.CONTACT, "jobTitle")).isTrue();
        assertThat(StandardFactFields.columnWritable(FactSubjectType.CONTACT, "organizationId")).isTrue();
    }
}
