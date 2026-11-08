package com.opencrm.crm.duplicate;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

import java.lang.reflect.Method;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC5: suggestions do not depend on account merge and the new repository methods are not writes.
 */
@AnalyzeClasses(packages = "com.opencrm.crm.duplicate", importOptions = ImportOption.DoNotIncludeTests.class)
@DisplayName("DRG-517 duplicate candidate boundaries")
class DuplicateCandidateBoundariesTest {

    @ArchTest
    static final ArchRule serviceDoesNotDependOnAccountMerge =
            noClasses()
                    .should().dependOnClassesThat().resideInAnyPackage("com.opencrm.crm.merge..")
                    .because("duplicate suggestions are read-only and never call BC-12-04 merge");

    @Test
    @DisplayName("AC5: repositoryCandidateQueriesAreNotModifying")
    void repositoryCandidateQueriesAreNotModifying() throws Exception {
        assertReadOnly(com.opencrm.crm.organization.OrganizationRepository.class, "findActiveDomainCandidates");
        assertReadOnly(com.opencrm.crm.contact.ContactRepository.class, "findActiveByNormalizedEmail");
        assertReadOnly(com.opencrm.crm.contact.ContactRepository.class, "findActiveInOrganization");
    }

    private static void assertReadOnly(Class<?> repository, String name) throws Exception {
        Method method = null;
        for (Method candidate : repository.getMethods()) {
            if (candidate.getName().equals(name)) {
                method = candidate;
                break;
            }
        }
        assertThat(method).isNotNull();
        assertThat(method.getAnnotation(Modifying.class)).isNull();
    }
}
