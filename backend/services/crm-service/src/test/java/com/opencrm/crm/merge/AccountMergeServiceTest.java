package com.opencrm.crm.merge;

import com.opencrm.common.event.EventActorResolver;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.AccountsMergedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.lead.Lead;
import com.opencrm.crm.lead.LeadRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountMergeServiceTest {

    private static final String SURVIVOR_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String MERGED_AWAY_UUID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String DECIDED_BY = "user:sales-ops";

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private DealRepository dealRepository;
    @Mock
    private AccountMergeRepository accountMergeRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private EventActorResolver eventActorResolver;

    @InjectMocks
    private AccountMergeService accountMergeService;

    private Organization survivor;
    private Organization mergedAway;

    @BeforeEach
    void setUp() {
        survivor = organization(1L, SURVIVOR_UUID);
        mergedAway = organization(2L, MERGED_AWAY_UUID);
    }

    @Test
    @DisplayName("repoints CRM children, closes the loser, audits the decision, and emits one AccountsMerged event")
    void mergesIntoTheChosenSurvivor() {
        Contact live = Contact.builder().id(10L).organization(mergedAway).deleted(false).build();
        Contact deleted = Contact.builder().id(11L).organization(mergedAway).deleted(true).build();
        Lead lead = Lead.builder().id(20L).organization(mergedAway).title("Inbound").build();
        Deal deal = Deal.builder().id(30L).organization(mergedAway).name("Renewal").build();
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(survivor));
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(mergedAway));
        when(eventActorResolver.currentActor()).thenReturn(DECIDED_BY);
        when(contactRepository.findAllByOrganizationId(2L)).thenReturn(List.of(live, deleted));
        when(leadRepository.findAllByOrganization_Id(2L)).thenReturn(List.of(lead));
        when(dealRepository.findAllByOrganization_Id(2L)).thenReturn(List.of(deal));
        when(contactRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(leadRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dealRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationRepository.save(mergedAway)).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountMergeRepository.save(any(AccountMerge.class))).thenAnswer(invocation -> {
            AccountMerge audit = invocation.getArgument(0);
            audit.setUuid("merge-audit-1");
            return audit;
        });

        AccountMergeResult result = accountMergeService.merge(request(1L, 2L));

        assertThat(result.getSurvivorId()).isEqualTo(1L);
        assertThat(result.getMergedAwayId()).isEqualTo(2L);
        assertThat(result.getSurvivorAccountId()).isEqualTo(SURVIVOR_UUID);
        assertThat(result.getMergedAwayAccountId()).isEqualTo(MERGED_AWAY_UUID);
        assertThat(result.getDecidedBy()).isEqualTo(DECIDED_BY);
        assertThat(result.getMergeId()).isEqualTo("merge-audit-1");
        assertThat(result.getContactsRepointed()).isEqualTo(2);
        assertThat(result.getLeadsRepointed()).isEqualTo(1);
        assertThat(result.getDealsRepointed()).isEqualTo(1);
        assertThat(live.getOrganization()).isSameAs(survivor);
        assertThat(deleted.getOrganization()).isSameAs(survivor);
        assertThat(lead.getOrganization()).isSameAs(survivor);
        assertThat(deal.getOrganization()).isSameAs(survivor);
        assertThat(mergedAway.isDeleted()).isTrue();
        assertThat(survivor.isDeleted()).isFalse();

        ArgumentCaptor<AccountMerge> audit = ArgumentCaptor.forClass(AccountMerge.class);
        verify(accountMergeRepository).save(audit.capture());
        assertThat(audit.getValue().getSurvivor()).isSameAs(survivor);
        assertThat(audit.getValue().getMergedAway()).isSameAs(mergedAway);
        assertThat(audit.getValue().getDecidedBy()).isEqualTo(DECIDED_BY);

        ArgumentCaptor<AccountsMergedEvent> payload = ArgumentCaptor.forClass(AccountsMergedEvent.class);
        verify(outboxEventPublisher, times(1)).publish(
                eq("Organization"), eq(SURVIVOR_UUID), eq(EventContractRegistry.ACCOUNTS_MERGED_V1), payload.capture());
        verify(outboxEventPublisher, never()).publish(anyString(), anyString(), any(), any(), any());
        AccountsMergedEvent event = payload.getValue();
        assertThat(event.getSurvivorId()).isEqualTo(SURVIVOR_UUID);
        assertThat(event.getMergedAwayId()).isEqualTo(MERGED_AWAY_UUID);
        assertThat(event.getDecidedBy()).isEqualTo(DECIDED_BY);
        assertThat(event.getMergedAt()).isNotNull();

        InOrder order = inOrder(contactRepository, leadRepository, dealRepository,
                organizationRepository, accountMergeRepository, outboxEventPublisher);
        order.verify(contactRepository).saveAll(any());
        order.verify(leadRepository).saveAll(any());
        order.verify(dealRepository).saveAll(any());
        order.verify(organizationRepository).save(mergedAway);
        order.verify(accountMergeRepository).save(any());
        order.verify(outboxEventPublisher).publish(
                eq("Organization"), eq(SURVIVOR_UUID), eq(EventContractRegistry.ACCOUNTS_MERGED_V1), any());
    }

    @Test
    @DisplayName("rejects merging an organization into itself")
    void rejectsSelfMerge() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(survivor));

        assertThatThrownBy(() -> accountMergeService.merge(request(1L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MERGE_SAME_ORGANIZATION");

        verify(organizationRepository, never()).save(any());
        verifyNoInteractions(contactRepository, leadRepository, dealRepository,
                accountMergeRepository, outboxEventPublisher);
    }

    @Test
    @DisplayName("rejects a missing survivor")
    void rejectsMissingSurvivor() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountMergeService.merge(request(1L, 2L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("1");

        verifyNoInteractions(outboxEventPublisher, accountMergeRepository);
    }

    @Test
    @DisplayName("rejects a missing merged-away organization")
    void rejectsMissingMergedAway() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(survivor));
        when(organizationRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountMergeService.merge(request(1L, 2L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("2");

        verifyNoInteractions(outboxEventPublisher, accountMergeRepository);
    }

    @Test
    @DisplayName("treats a soft-deleted organization as not found")
    void rejectsDeletedOrganization() {
        mergedAway.setDeleted(true);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(survivor));
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(mergedAway));

        assertThatThrownBy(() -> accountMergeService.merge(request(1L, 2L)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(survivor.isDeleted()).isFalse();
        verify(organizationRepository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("does not publish when the actor is unknown")
    void rejectsBlankActor() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(survivor));
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(mergedAway));
        when(eventActorResolver.currentActor()).thenReturn("  ");

        assertThatThrownBy(() -> accountMergeService.merge(request(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "MERGE_ACTOR_REQUIRED");

        verifyNoInteractions(contactRepository, outboxEventPublisher, accountMergeRepository);
        assertThat(mergedAway.isDeleted()).isFalse();
    }

    private static AccountMergeRequest request(Long survivorId, Long mergedAwayId) {
        return AccountMergeRequest.builder().survivorId(survivorId).mergedAwayId(mergedAwayId).build();
    }

    private static Organization organization(Long id, String uuid) {
        return Organization.builder().id(id).uuid(uuid).name("Org " + id).deleted(false).build();
    }
}
