package com.opencrm.crm.activity;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.organization.OrganizationRepository;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @Transactional @RequiredArgsConstructor
public class ActivityService {
    private final ActivityRepository activities;
    private final OrganizationRepository organizations;
    private final ContactRepository contacts;
    private final DealRepository deals;
    private final ActivityMapper mapper;

    public ActivityDto create(ActivityCreateRequest request) {
        UserContext user = user();
        validate(request);
        requireSubjects(request.getOrganizationId(), request.getContactId(), request.getDealId());
        Activity row = new Activity();
        row.setWorkforceTenantId(user.getTenantId());
        row.setAuthorId(user.getUserId());
        row.setType(request.getType());
        row.setTitle(request.getTitle().trim());
        row.setNote(request.getNote());
        row.setOrganizationId(request.getOrganizationId());
        row.setContactId(request.getContactId());
        row.setDealId(request.getDealId());
        row.setReference(request.getReference());
        row.setOccurredAt(request.getOccurredAt());
        row.setDueAt(request.getDueAt());
        return mapper.toDto(activities.save(row));
    }

    @Transactional(readOnly = true)
    public Page<ActivityDto> timeline(Long organizationId, Long contactId, Long dealId, int page, int size) {
        UserContext user = user();
        if (page < 0 || size < 1 || size > 100) throw invalid("Page must be nonnegative and size between 1 and 100");
        requireSubjects(organizationId, contactId, dealId);
        return activities.timeline(user.getTenantId(), organizationId, contactId, dealId,
                PageRequest.of(page, size)).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public ActivityDto getById(Long id) {
        Activity row = activities.findByIdAndWorkforceTenantIdAndDeletedFalse(id, user().getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
        requireSubjects(row.getOrganizationId(), row.getContactId(), row.getDealId());
        return mapper.toDto(row);
    }

    /** The row lock makes repeated/concurrent completion preserve the first timestamp. */
    public ActivityDto complete(Long id) {
        Activity row = activities.lockForCompletion(id, user().getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
        requireSubjects(row.getOrganizationId(), row.getContactId(), row.getDealId());
        if (row.getType() != ActivityType.TASK) throw invalid("Only a task can be completed");
        if (row.getCompletedAt() == null) {
            row.setCompletedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            activities.save(row);
        }
        return mapper.toDto(row);
    }

    private UserContext user() {
        UserContext user = SecurityUtils.getCurrentUser().orElseThrow(() -> new BusinessException(
                "A verified human caller is required", SecurityUtils.isAuthenticated() ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED,
                "ACTIVITY_USER_REQUIRED"));
        if (user.getTenantId() == null || user.getTenantId().isBlank()
                || user.getTenantId().length() > 128 || user.getUserId() == null || user.getUserId().isBlank()) {
            throw new BusinessException("Verified workforce context is required", HttpStatus.UNAUTHORIZED, "ACTIVITY_CONTEXT_REQUIRED");
        }
        return user;
    }

    private void requireSubjects(Long organizationId, Long contactId, Long dealId) {
        if (organizationId == null && contactId == null && dealId == null) throw invalid("At least one subject is required");
        if (organizationId != null && (organizationId <= 0 || organizations.findById(organizationId).filter(o -> !o.isDeleted()).isEmpty()))
            throw new ResourceNotFoundException("Organization", organizationId);
        if (contactId != null && (contactId <= 0 || contacts.findById(contactId).filter(c -> !c.isDeleted()).isEmpty()))
            throw new ResourceNotFoundException("Contact", contactId);
        if (dealId != null && (dealId <= 0 || deals.findById(dealId).filter(d -> !d.isDeleted()).isEmpty()))
            throw new ResourceNotFoundException("Deal", dealId);
    }

    private void validate(ActivityCreateRequest r) {
        if (r.getType() == null || r.getOccurredAt() == null || r.getTitle() == null
                || r.getTitle().isBlank() || r.getTitle().length() > 255
                || (r.getNote() != null && r.getNote().length() > 10000)
                || (r.getReference() != null && r.getReference().length() > 512)) throw invalid("Invalid activity fields");
        boolean external = r.getType() == ActivityType.EMAIL || r.getType() == ActivityType.MEETING;
        if (external && (r.getReference() == null || r.getReference().isBlank())) throw invalid("A thread or event reference is required");
        if (external && r.getNote() != null) throw invalid("Email and meeting bodies are not accepted");
        if (!external && r.getReference() != null) throw invalid("Only email and meeting activities accept references");
        if (r.getType() != ActivityType.TASK && r.getDueAt() != null) throw invalid("Only tasks accept a due time");
        if (r.getType() == ActivityType.NOTE && (r.getNote() == null || r.getNote().isBlank())) throw invalid("Note text is required");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(message, "ACTIVITY_INVALID");
    }
}
