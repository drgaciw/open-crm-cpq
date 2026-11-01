package com.opencrm.crm.merge;

import com.opencrm.common.event.EventActorResolver;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.AccountsMergedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.lead.Lead;
import com.opencrm.crm.lead.LeadRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import com.opencrm.security.Caller;
import com.opencrm.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Merges one organization into a survivor the caller names (DRG-484).
 *
 * <p>CRM-owned contacts, leads, and deals are repointed in this transaction by loading
 * them through their repositories. Quote, contract, and other services are not written;
 * they repoint when they consume {@code AccountsMerged}. Exactly one event is published,
 * through {@link OutboxEventPublisher}, in the same transaction as the audit row.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountMergeService {

    private final OrganizationRepository organizationRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final AccountMergeRepository accountMergeRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final EventActorResolver eventActorResolver;
    private final AccountMergeReadiness readiness;
    private final EntityManager entityManager;

    @CacheEvict(value = {"contacts", "organizations"}, allEntries = true)
    public AccountMergeResult merge(AccountMergeRequest request) {
        requireAuthorizedHuman();
        if (!readiness.isReady()) {
            throw new BusinessException("Account merge is unavailable until identity and reconciliation are ready",
                    HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_MERGE_HOLD");
        }
        if (request == null || request.getSurvivorId() == null || request.getMergedAwayId() == null) {
            throw new BusinessException(
                    "Survivor and merged-away organization ids are required", "MERGE_IDS_REQUIRED");
        }

        // Refreshing stale request-bound entities must never discard a caller's pending edits.
        if (entityManager.unwrap(org.hibernate.Session.class).isDirty()) {
            throw new BusinessException("Account merge cannot run with unflushed changes", HttpStatus.CONFLICT,
                    "ACCOUNT_MERGE_DIRTY_CONTEXT");
        }
        long firstId = Math.min(request.getSurvivorId(), request.getMergedAwayId());
        long secondId = Math.max(request.getSurvivorId(), request.getMergedAwayId());
        Organization first = requireActive(firstId);
        Organization second = firstId == secondId ? first : requireActive(secondId);
        Organization survivor = request.getSurvivorId() == firstId ? first : second;
        Organization mergedAway = request.getMergedAwayId() == firstId ? first : second;
        if (sameOrganization(survivor, mergedAway)) {
            throw new BusinessException(
                    "Cannot merge an organization into itself", "MERGE_SAME_ORGANIZATION");
        }

        String decidedBy = decidedBy();
        Instant mergedAt = Instant.now();
        String survivorAccountId = AccountId.of(survivor.getUuid()).value();
        String mergedAwayAccountId = AccountId.of(mergedAway.getUuid()).value();

        int contactsRepointed = repointContacts(mergedAway, survivor);
        int leadsRepointed = repointLeads(mergedAway, survivor);
        int dealsRepointed = repointDeals(mergedAway, survivor);

        mergedAway.softDelete();
        organizationRepository.save(mergedAway);

        AccountMerge audit = accountMergeRepository.save(AccountMerge.builder()
                .survivor(survivor)
                .mergedAway(mergedAway)
                .decidedBy(decidedBy)
                .build());

        outboxEventPublisher.publish(
                "Organization",
                survivorAccountId,
                EventContractRegistry.ACCOUNTS_MERGED_V1,
                new AccountsMergedEvent(survivorAccountId, mergedAwayAccountId, decidedBy, mergedAt));

        log.info("Merged organization {} into {} as {} decided by {}",
                mergedAway.getId(), survivor.getId(), audit.getUuid(), decidedBy);

        return AccountMergeResult.builder()
                .survivorId(survivor.getId())
                .mergedAwayId(mergedAway.getId())
                .survivorAccountId(survivorAccountId)
                .mergedAwayAccountId(mergedAwayAccountId)
                .decidedBy(decidedBy)
                .mergeId(audit.getUuid())
                .contactsRepointed(contactsRepointed)
                .leadsRepointed(leadsRepointed)
                .dealsRepointed(dealsRepointed)
                .build();
    }

    private int repointContacts(Organization mergedAway, Organization survivor) {
        List<Contact> contacts = contactRepository.findAllByOrganizationId(mergedAway.getId());
        for (Contact contact : contacts) {
            contact.setOrganization(survivor);
        }
        contactRepository.saveAll(contacts);
        return contacts.size();
    }

    private int repointLeads(Organization mergedAway, Organization survivor) {
        List<Lead> leads = leadRepository.findAllByOrganization_Id(mergedAway.getId());
        for (Lead lead : leads) {
            lead.setOrganization(survivor);
        }
        leadRepository.saveAll(leads);
        return leads.size();
    }

    private int repointDeals(Organization mergedAway, Organization survivor) {
        List<Deal> deals = dealRepository.findAllByOrganization_Id(mergedAway.getId());
        for (Deal deal : deals) {
            deal.setOrganization(survivor);
        }
        dealRepository.saveAll(deals);
        return deals.size();
    }

    private Organization requireActive(Long id) {
        Organization organization = organizationRepository.findByIdForMerge(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        // A pessimistic query can return an already-managed, stale OSIV instance.
        // The row lock is already held; refresh before consulting its state.
        entityManager.refresh(organization);
        if (organization.isDeleted()) {
            throw new ResourceNotFoundException("Organization", id);
        }
        return organization;
    }

    private static boolean sameOrganization(Organization survivor, Organization mergedAway) {
        if (survivor.getId() != null && survivor.getId().equals(mergedAway.getId())) {
            return true;
        }
        return survivor.getUuid() != null && survivor.getUuid().equalsIgnoreCase(mergedAway.getUuid());
    }

    private static void requireAuthorizedHuman() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Caller caller = SecurityUtils.getCurrentCaller().orElse(null);
        if (!(auth instanceof JwtAuthenticationToken jwt)
                || !(jwt.getToken().getClaims().get("scp") instanceof String scope) || scope.isBlank()
                || !(caller instanceof Caller.User user)
                || user.actorId() == null || user.actorId().isBlank()
                || !user.hasAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE")) {
            throw new BusinessException("Account merge requires the sales operations role",
                    HttpStatus.FORBIDDEN, "ACCOUNT_MERGE_FORBIDDEN");
        }
    }

    private String decidedBy() {
        String actor = eventActorResolver.currentActor();
        if (actor == null || actor.isBlank()) {
            throw new BusinessException("Account merge requires an actor", "MERGE_ACTOR_REQUIRED");
        }
        return actor;
    }
}
