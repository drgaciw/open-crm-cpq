package com.opencrm.crm.buyingrole;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealBuyingRoleServiceTest {

    @Mock
    private DealBuyingRoleRepository dealBuyingRoleRepository;

    @Mock
    private DealRepository dealRepository;

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private DealBuyingRoleService service;

    private Deal deal;
    private Contact contact;

    @BeforeEach
    void setUp() {
        deal = Deal.builder().id(10L).uuid("deal-uuid").name("Expansion").deleted(false).build();
        contact = Contact.builder()
                .id(4L)
                .uuid("contact-uuid")
                .firstName("Ada")
                .lastName("Lovelace")
                .deleted(false)
                .build();
    }

    @Nested
    @DisplayName("assign")
    class Assign {

        @Test
        @DisplayName("attaches the contact with the requested role")
        void assignsRole() {
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            when(dealBuyingRoleRepository.findByDealIdAndContactId(10L, 4L)).thenReturn(Optional.empty());
            when(dealBuyingRoleRepository.saveAndFlush(any())).thenAnswer(invocation -> {
                DealBuyingRole saved = invocation.getArgument(0);
                saved.setId(70L);
                saved.setUuid("role-uuid");
                return saved;
            });

            DealBuyingRoleDto result = service.assign(10L, request(BuyingRole.CHAMPION));

            assertThat(result.getId()).isEqualTo(70L);
            assertThat(result.getUuid()).isEqualTo("role-uuid");
            assertThat(result.getDealId()).isEqualTo(10L);
            assertThat(result.getContactId()).isEqualTo(4L);
            assertThat(result.getContactName()).isEqualTo("Ada Lovelace");
            assertThat(result.getRole()).isEqualTo(BuyingRole.CHAMPION);

            ArgumentCaptor<DealBuyingRole> captor = ArgumentCaptor.forClass(DealBuyingRole.class);
            verify(dealBuyingRoleRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getDeal()).isSameAs(deal);
            assertThat(captor.getValue().getContact()).isSameAs(contact);
            assertThat(captor.getValue().getRole()).isEqualTo(BuyingRole.CHAMPION);
            assertThat(captor.getValue().isDeleted()).isFalse();
        }

        @Test
        @DisplayName("looks up the pair on this deal so the same contact can sit on another deal")
        void sameContactOnAnotherDeal() {
            Deal other = Deal.builder().id(11L).uuid("other").name("Other").deleted(false).build();
            when(dealRepository.findById(11L)).thenReturn(Optional.of(other));
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            when(dealBuyingRoleRepository.findByDealIdAndContactId(11L, 4L)).thenReturn(Optional.empty());
            when(dealBuyingRoleRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.assign(11L, request(BuyingRole.BLOCKER));

            verify(dealBuyingRoleRepository).findByDealIdAndContactId(11L, 4L);
            verify(dealBuyingRoleRepository).saveAndFlush(any());
        }

        @Test
        @DisplayName("rejects a second role for an active pair, including the same role")
        void rejectsSecondRole() {
            DealBuyingRole existing = active(BuyingRole.ECONOMIC_BUYER);
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            when(dealBuyingRoleRepository.findByDealIdAndContactId(10L, 4L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.assign(10L, request(BuyingRole.CHAMPION)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
                    .hasFieldOrPropertyWithValue("errorCode", DealBuyingRoleService.ALREADY_ASSIGNED);
            assertThatThrownBy(() -> service.assign(10L, request(BuyingRole.ECONOMIC_BUYER)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DealBuyingRoleService.ALREADY_ASSIGNED);

            verify(dealBuyingRoleRepository, never()).saveAndFlush(any());
            assertThat(existing.getRole()).isEqualTo(BuyingRole.ECONOMIC_BUYER);
            assertThat(existing.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("reactivates a soft-deleted pair instead of inserting a second row")
        void reactivatesRemovedPair() {
            DealBuyingRole removed = active(BuyingRole.BILLING_CONTACT);
            removed.softDelete();
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            when(dealBuyingRoleRepository.findByDealIdAndContactId(10L, 4L)).thenReturn(Optional.of(removed));
            when(dealBuyingRoleRepository.saveAndFlush(removed)).thenReturn(removed);

            DealBuyingRoleDto result = service.assign(10L, request(BuyingRole.TECHNICAL_EVALUATOR));

            assertThat(result.getRole()).isEqualTo(BuyingRole.TECHNICAL_EVALUATOR);
            assertThat(removed.isDeleted()).isFalse();
            assertThat(removed.getRole()).isEqualTo(BuyingRole.TECHNICAL_EVALUATOR);
            verify(dealBuyingRoleRepository).saveAndFlush(removed);
        }

        @Test
        @DisplayName("maps a unique-constraint race to the same conflict")
        void mapsUniqueRace() {
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            when(dealBuyingRoleRepository.findByDealIdAndContactId(10L, 4L)).thenReturn(Optional.empty());
            when(dealBuyingRoleRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"uq_deal_buying_roles_deal_contact\""));

            assertThatThrownBy(() -> service.assign(10L, request(BuyingRole.DECISION_MAKER)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
                    .hasFieldOrPropertyWithValue("errorCode", DealBuyingRoleService.ALREADY_ASSIGNED);
        }

        @Test
        @DisplayName("leaves other integrity failures untouched")
        void rethrowsOtherIntegrityFailures() {
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            when(dealBuyingRoleRepository.findByDealIdAndContactId(10L, 4L)).thenReturn(Optional.empty());
            DataIntegrityViolationException failure = new DataIntegrityViolationException("fk");
            when(dealBuyingRoleRepository.saveAndFlush(any())).thenThrow(failure);

            assertThatThrownBy(() -> service.assign(10L, request(BuyingRole.BLOCKER))).isSameAs(failure);
        }

        @Test
        @DisplayName("returns 404 when the deal is missing or soft-deleted")
        void missingOrDeletedDeal() {
            when(dealRepository.findById(10L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.assign(10L, request(BuyingRole.CHAMPION)))
                    .isInstanceOf(ResourceNotFoundException.class);

            Deal deleted = Deal.builder().id(10L).uuid("gone").name("Gone").deleted(true).build();
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deleted));
            assertThatThrownBy(() -> service.assign(10L, request(BuyingRole.CHAMPION)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(contactRepository, dealBuyingRoleRepository);
        }

        @Test
        @DisplayName("returns 404 when the contact is missing or soft-deleted")
        void missingOrDeletedContact() {
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(4L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.assign(10L, request(BuyingRole.CHAMPION)))
                    .isInstanceOf(ResourceNotFoundException.class);

            contact.setDeleted(true);
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            assertThatThrownBy(() -> service.assign(10L, request(BuyingRole.CHAMPION)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(dealBuyingRoleRepository, never()).saveAndFlush(any());
            verify(dealBuyingRoleRepository, never()).findByDealIdAndContactId(10L, 4L);
        }

        @Test
        @DisplayName("rejects a request that omits the contact or the role")
        void rejectsIncompleteRequest() {
            assertThatThrownBy(() -> service.assign(10L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DealBuyingRoleService.INVALID);
            assertThatThrownBy(() -> service.assign(10L, AssignBuyingRoleRequest.builder().contactId(4L).build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_CONTENT);
            assertThatThrownBy(() -> service.assign(10L, AssignBuyingRoleRequest.builder().role(BuyingRole.CHAMPION).build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DealBuyingRoleService.INVALID);
            verifyNoInteractions(dealRepository, contactRepository, dealBuyingRoleRepository);
        }
    }

    @Nested
    @DisplayName("list")
    class ListRoles {

        @Test
        @DisplayName("returns the committee and drops a seat whose contact was soft-deleted")
        void listsLiveContacts() {
            Contact removed = Contact.builder()
                    .id(5L)
                    .uuid("removed")
                    .firstName("Grace")
                    .lastName("Hopper")
                    .deleted(true)
                    .build();
            DealBuyingRole live = active(BuyingRole.DECISION_MAKER);
            DealBuyingRole hidden = DealBuyingRole.builder()
                    .id(71L)
                    .uuid("hidden")
                    .deal(deal)
                    .contact(removed)
                    .role(BuyingRole.BLOCKER)
                    .deleted(false)
                    .build();
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(dealBuyingRoleRepository.findActiveByDealId(10L)).thenReturn(List.of(live, hidden));

            List<DealBuyingRoleDto> result = service.list(10L);

            assertThat(result).extracting(DealBuyingRoleDto::getContactId).containsExactly(4L);
            assertThat(result.get(0).getRole()).isEqualTo(BuyingRole.DECISION_MAKER);
            assertThat(result.get(0).getContactName()).isEqualTo("Ada Lovelace");
        }

        @Test
        @DisplayName("returns 404 when the deal is missing or soft-deleted")
        void missingDeal() {
            when(dealRepository.findById(10L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.list(10L)).isInstanceOf(ResourceNotFoundException.class);

            deal.setDeleted(true);
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            assertThatThrownBy(() -> service.list(10L)).isInstanceOf(ResourceNotFoundException.class);
            verify(dealBuyingRoleRepository, never()).findActiveByDealId(10L);
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("soft-deletes the active pair")
        void softDeletes() {
            DealBuyingRole existing = active(BuyingRole.BILLING_CONTACT);
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            when(dealBuyingRoleRepository.findByDealIdAndContactId(10L, 4L)).thenReturn(Optional.of(existing));

            service.remove(10L, 4L);

            assertThat(existing.isDeleted()).isTrue();
            verify(dealBuyingRoleRepository).save(existing);
        }

        @Test
        @DisplayName("returns 404 when the pair has no active role")
        void missingAssignment() {
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            when(dealBuyingRoleRepository.findByDealIdAndContactId(10L, 4L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.remove(10L, 4L)).isInstanceOf(ResourceNotFoundException.class);

            DealBuyingRole removed = active(BuyingRole.CHAMPION);
            removed.softDelete();
            when(dealBuyingRoleRepository.findByDealIdAndContactId(10L, 4L)).thenReturn(Optional.of(removed));
            assertThatThrownBy(() -> service.remove(10L, 4L)).isInstanceOf(ResourceNotFoundException.class);
            verify(dealBuyingRoleRepository, never()).save(any());
        }

        @Test
        @DisplayName("returns 404 when the deal or contact is missing or soft-deleted")
        void missingParents() {
            when(dealRepository.findById(10L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.remove(10L, 4L)).isInstanceOf(ResourceNotFoundException.class);

            deal.setDeleted(true);
            when(dealRepository.findById(10L)).thenReturn(Optional.of(deal));
            assertThatThrownBy(() -> service.remove(10L, 4L)).isInstanceOf(ResourceNotFoundException.class);

            deal.setDeleted(false);
            when(contactRepository.findById(4L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.remove(10L, 4L)).isInstanceOf(ResourceNotFoundException.class);

            contact.setDeleted(true);
            when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));
            assertThatThrownBy(() -> service.remove(10L, 4L)).isInstanceOf(ResourceNotFoundException.class);
            verify(dealBuyingRoleRepository, never()).save(any());
        }
    }

    private AssignBuyingRoleRequest request(BuyingRole role) {
        return AssignBuyingRoleRequest.builder().contactId(4L).role(role).build();
    }

    private DealBuyingRole active(BuyingRole role) {
        return DealBuyingRole.builder()
                .id(70L)
                .uuid("role-uuid")
                .deal(deal)
                .contact(contact)
                .role(role)
                .deleted(false)
                .build();
    }
}
