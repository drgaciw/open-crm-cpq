package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.service.JpaRenewalPolicyRecords;
import com.opencrm.contract.service.RenewalPolicyVersionConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class RenewalPolicyRecordRepositoryTest {

    @Test
    void declaredFindersAreTenantScoped() {
        assertThat(RenewalPolicyRecordRepository.class.getDeclaredMethods()).isNotEmpty();
        for (Method method : RenewalPolicyRecordRepository.class.getDeclaredMethods()) {
            assertThat(method.getName()).contains("TenantId");
            assertThat(method.getParameterTypes()).contains(UUID.class);
        }
    }

    @Test
    void adapterMapsADuplicateInsertToAConflictAndDoesNotCallFindById() {
        RenewalPolicyRecordRepository repository = mock(RenewalPolicyRecordRepository.class);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThatThrownBy(() -> new JpaRenewalPolicyRecords(repository).insert(new RenewalPolicyRecord()))
                .isInstanceOf(RenewalPolicyVersionConflictException.class);
        verify(repository, never()).findById(any());
    }
}
