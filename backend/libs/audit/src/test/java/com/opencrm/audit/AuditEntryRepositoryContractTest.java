package com.opencrm.audit;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the application-layer half of DRG-309's "updates and deletes are rejected" requirement: even if someone
 * later changes {@link AuditEntryRepository} to extend {@code JpaRepository}/{@code CrudRepository} (which both
 * expose {@code delete}/{@code deleteAll}), this test fails and says why. The database trigger in
 * {@code V18__audit_entries.sql} (crm-service) is the independent, storage-layer half of that guarantee.
 */
class AuditEntryRepositoryContractTest {

    @Test
    void repositoryExtendsOnlyTheBareSpringDataMarkerInterface() {
        assertThat(AuditEntryRepository.class.getInterfaces()).containsExactly(Repository.class);
    }

    @Test
    void repositoryDeclaresNoDeleteOrUpdateMethod() {
        String[] forbidden = {"delete", "deleteAll", "deleteById", "update"};
        Method[] declared = AuditEntryRepository.class.getDeclaredMethods();

        assertThat(Arrays.stream(declared).map(Method::getName))
                .noneMatch(name -> Arrays.stream(forbidden).anyMatch(name::startsWith));
    }

    @Test
    void repositoryOnlyExposesSaveAndFindByMethods() {
        for (Method method : AuditEntryRepository.class.getDeclaredMethods()) {
            assertThat(method.getName())
                    .as("unexpected repository method: %s", method.getName())
                    .matches("save|findTopByOrderByIdDesc|findAllByOrderByIdAsc|search");
        }
    }
}
