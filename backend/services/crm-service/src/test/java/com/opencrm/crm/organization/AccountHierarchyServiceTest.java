package com.opencrm.crm.organization;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.HierarchyChangedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountHierarchyServiceTest {

    private static final String ROOT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PARENT_ID = "22222222-2222-2222-2222-222222222222";
    private static final String CHILD_ID = "33333333-3333-3333-3333-333333333333";

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private AccountHierarchyService service;

    private Organization root;
    private Organization parent;
    private Organization child;

    @BeforeEach
    void setUp() {
        service = new AccountHierarchyService(organizationRepository, outboxEventPublisher, org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class), org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class), true);
        root = organization(1L, ROOT_ID, "Root", null);
        parent = organization(2L, PARENT_ID, "Parent", 1L);
        child = organization(3L, CHILD_ID, "Child", null);
    }

    @Test
    void productionServiceRejectsDirectReadAndWriteBeforeAnySideEffects() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var em = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        var held = new AccountHierarchyService(organizationRepository, outboxEventPublisher, jdbc, em);
        assertThatThrownBy(() -> held.linkToParent(AccountId.of(CHILD_ID), 1L))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", "HIERARCHY_UNAVAILABLE");
        assertThatThrownBy(() -> held.resolveHierarchy(AccountId.of(CHILD_ID)))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", "HIERARCHY_UNAVAILABLE");
        org.mockito.Mockito.verifyNoInteractions(organizationRepository, outboxEventPublisher, jdbc, em);
    }

    @Test
    @DisplayName("links a child to a parent and emits exactly one HierarchyChanged event")
    void linkPublishesOneHierarchyChangedEvent() {
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.of(child));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(root));
        when(organizationRepository.save(child)).thenReturn(child);

        AccountHierarchyDto result = service.linkToParent(AccountId.of(CHILD_ID), 1L);

        assertThat(child.getParentOrganizationId()).isEqualTo(1L);
        assertThat(result.getParentOrganizationId()).isEqualTo(1L);
        assertThat(result.getAncestorIds()).containsExactly(ROOT_ID);
        assertThat(result.getChain()).extracting(AccountHierarchyNodeDto::getAccountId)
                .containsExactly(ROOT_ID, CHILD_ID);

        ArgumentCaptor<HierarchyChangedEvent> payload = ArgumentCaptor.forClass(HierarchyChangedEvent.class);
        verify(outboxEventPublisher, times(1)).publish(
                eq("Organization"),
                eq(CHILD_ID),
                eq(EventContractRegistry.HIERARCHY_CHANGED_V1),
                payload.capture());
        HierarchyChangedEvent event = payload.getValue();
        assertThat(event.getOrganizationId()).isEqualTo(CHILD_ID);
        assertThat(event.getParentOrganizationId()).isEqualTo(1L);
        assertThat(event.getParentAccountId()).isEqualTo(ROOT_ID);
        assertThat(event.getPreviousParentOrganizationId()).isNull();
        assertThat(event.getAncestorIds()).containsExactly(ROOT_ID);
        assertThat(event.getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("changing the parent emits one event with the new ancestor chain")
    void changingParentPublishesOneEvent() {
        child.setParentOrganizationId(2L);
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.of(child));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(root));
        when(organizationRepository.save(child)).thenReturn(child);

        AccountHierarchyDto result = service.linkToParent(AccountId.of(CHILD_ID), 1L);

        assertThat(result.getAncestorIds()).containsExactly(ROOT_ID);
        ArgumentCaptor<HierarchyChangedEvent> payload = ArgumentCaptor.forClass(HierarchyChangedEvent.class);
        verify(outboxEventPublisher, times(1)).publish(
                eq("Organization"),
                eq(CHILD_ID),
                eq(EventContractRegistry.HIERARCHY_CHANGED_V1),
                payload.capture());
        assertThat(payload.getValue().getPreviousParentOrganizationId()).isEqualTo(2L);
        assertThat(payload.getValue().getParentAccountId()).isEqualTo(ROOT_ID);
    }

    @Test
    @DisplayName("clearing the parent emits one event and an empty ancestor list")
    void unlinkPublishesOneEvent() {
        child.setParentOrganizationId(2L);
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.of(child));
        when(organizationRepository.save(child)).thenReturn(child);

        AccountHierarchyDto result = service.linkToParent(AccountId.of(CHILD_ID), null);

        assertThat(child.getParentOrganizationId()).isNull();
        assertThat(result.getAncestorIds()).isEmpty();
        assertThat(result.getChain()).extracting(AccountHierarchyNodeDto::getAccountId).containsExactly(CHILD_ID);
        ArgumentCaptor<HierarchyChangedEvent> payload = ArgumentCaptor.forClass(HierarchyChangedEvent.class);
        verify(outboxEventPublisher, times(1)).publish(
                eq("Organization"),
                eq(CHILD_ID),
                eq(EventContractRegistry.HIERARCHY_CHANGED_V1),
                payload.capture());
        assertThat(payload.getValue().getParentOrganizationId()).isNull();
        assertThat(payload.getValue().getPreviousParentOrganizationId()).isEqualTo(2L);
        assertThat(payload.getValue().getAncestorIds()).isEmpty();
    }

    @Test
    @DisplayName("linking the same parent again does not emit")
    void unchangedParentDoesNotEmit() {
        child.setParentOrganizationId(1L);
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.of(child));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(root));

        AccountHierarchyDto result = service.linkToParent(AccountId.of(CHILD_ID), 1L);

        assertThat(result.getAncestorIds()).containsExactly(ROOT_ID);
        verify(organizationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("rejects a self-parent")
    void rejectsSelfParent() {
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.linkToParent(AccountId.of(CHILD_ID), 3L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "HIERARCHY_CYCLE");

        assertThat(child.getParentOrganizationId()).isNull();
        verify(organizationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("rejects a link that would loop through an ancestor")
    void rejectsAncestorLoop() {
        root.setParentOrganizationId(3L);
        parent.setParentOrganizationId(1L);
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.of(child));
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(parent));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(root));
        when(organizationRepository.findById(3L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.linkToParent(AccountId.of(CHILD_ID), 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "HIERARCHY_CYCLE")
                .hasMessageContaining("cycle");

        assertThat(child.getParentOrganizationId()).isNull();
        verify(organizationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("rejects a deleted parent")
    void rejectsDeletedParent() {
        root.setDeleted(true);
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.of(child));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(root));

        assertThatThrownBy(() -> service.linkToParent(AccountId.of(CHILD_ID), 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORGANIZATION_DELETED");
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("resolve returns the chain and ancestor ids from the parent up to the root")
    void resolveReturnsChainAndAncestorIds() {
        child.setParentOrganizationId(2L);
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.of(child));
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(parent));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(root));

        AccountHierarchyDto result = service.resolveHierarchy(AccountId.of(CHILD_ID));

        assertThat(result.getOrganizationId()).isEqualTo(3L);
        assertThat(result.getAccountId()).isEqualTo(CHILD_ID);
        assertThat(result.getParentOrganizationId()).isEqualTo(2L);
        assertThat(result.getAncestorIds()).containsExactly(PARENT_ID, ROOT_ID);
        assertThat(result.getChain()).extracting(AccountHierarchyNodeDto::getName)
                .containsExactly("Root", "Parent", "Child");
        assertThat(result.getChain()).extracting(AccountHierarchyNodeDto::getId)
                .containsExactly(1L, 2L, 3L);
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("resolve of a root has an empty ancestor list and a one-node chain")
    void resolveRoot() {
        when(organizationRepository.findByUuidIgnoreCase(ROOT_ID)).thenReturn(Optional.of(root));

        AccountHierarchyDto result = service.resolveHierarchy(AccountId.of(ROOT_ID));

        assertThat(result.getAncestorIds()).isEmpty();
        assertThat(result.getChain()).extracting(AccountHierarchyNodeDto::getAccountId).containsExactly(ROOT_ID);
    }

    @Test
    @DisplayName("missing organization is not found")
    void missingOrganization() {
        when(organizationRepository.findByUuidIgnoreCase(CHILD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveHierarchy(AccountId.of(CHILD_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Organization organization(Long id, String uuid, String name, Long parentOrganizationId) {
        return Organization.builder()
                .id(id)
                .uuid(uuid)
                .name(name)
                .parentOrganizationId(parentOrganizationId)
                .deleted(false)
                .build();
    }
}
