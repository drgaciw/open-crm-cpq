package com.opencrm.crm.fact;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.fields.FixtureTenants;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaRecordFieldAccessTest {

    @Mock
    private ContactRepository contactRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private CrmFactRepository factRepository;
    @Mock
    private ObjectProvider<org.springframework.cache.CacheManager> cacheManager;

    private JpaRecordFieldAccess access;

    @BeforeEach
    void setUp() {
        access = new JpaRecordFieldAccess(
                contactRepository, organizationRepository, factRepository, cacheManager);
    }

    @Test
    void sameTenantSubjectIsReadableAndWritable() {
        Contact contact = new Contact();
        contact.setId(4L);
        contact.setJobTitle("Director");
        when(factRepository.existsForSubjectUnderOtherTenant(FactSubjectType.CONTACT, "4", FixtureTenants.A))
                .thenReturn(false);
        when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        access.requireSubject(FactSubjectType.CONTACT, 4L, FixtureTenants.A);
        assertThat(access.read(FactSubjectType.CONTACT, 4L, "jobTitle", FixtureTenants.A))
                .contains(new ColumnState("Director", null));
        assertThat(access.write(FactSubjectType.CONTACT, 4L, "jobTitle", "VP Sales", FixtureTenants.A)).isTrue();
        assertThat(contact.getJobTitle()).isEqualTo("VP Sales");
    }

    @Test
    void crossTenantSubjectIsNotFoundOnReadOrWrite() {
        when(factRepository.existsForSubjectUnderOtherTenant(FactSubjectType.CONTACT, "4", FixtureTenants.B))
                .thenReturn(true);

        assertThatThrownBy(() -> access.requireSubject(FactSubjectType.CONTACT, 4L, FixtureTenants.B))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> access.read(FactSubjectType.CONTACT, 4L, "jobTitle", FixtureTenants.B))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> access.write(FactSubjectType.CONTACT, 4L, "jobTitle", "VP", FixtureTenants.B))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(contactRepository, never()).findById(anyLong());
        verify(contactRepository, never()).save(any());
    }

    @Test
    void virginSubjectIsClaimableByAuthenticatedTenant() {
        Contact contact = new Contact();
        contact.setId(7L);
        when(factRepository.existsForSubjectUnderOtherTenant(
                eq(FactSubjectType.CONTACT), eq("7"), eq(FixtureTenants.A)))
                .thenReturn(false);
        when(contactRepository.findById(7L)).thenReturn(Optional.of(contact));

        access.requireSubject(FactSubjectType.CONTACT, 7L, FixtureTenants.A);

        verify(factRepository).existsForSubjectUnderOtherTenant(FactSubjectType.CONTACT, "7", FixtureTenants.A);
        verify(contactRepository).findById(7L);
        verify(factRepository, never()).existsForSubjectUnderOtherTenant(any(), anyString(), eq(FixtureTenants.B));
    }
}
