package com.opencrm.crm.activity;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.organization.OrganizationRepository;
import com.opencrm.security.Caller;
import com.opencrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Notes and tasks on a CRM timeline (DRG-520). Email and meeting rows are reserved
 * for mailbox ingestion and are not created here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final OrganizationRepository organizationRepository;
    private final ContactRepository contactRepository;
    private final DealRepository dealRepository;

    @Transactional
    public ActivityDto create(ActivityCreateRequest request) {
        ActivityType type = request.getType();
        if (type == null) {
            throw new BusinessException("Activity type is required", "ACTIVITY_TYPE_REQUIRED");
        }
        // TODO(DRG-521): mailbox ingestion writes EMAIL (emailThreadRef) and MEETING (calendarEventRef).
        rejectIngestOnlyTypes(type);
        if (request.getOrganizationId() == null
                && request.getContactId() == null
                && request.getDealId() == null) {
            throw new BusinessException(
                    "An activity must link to an organization, contact, or deal",
                    "ACTIVITY_PARENT_REQUIRED");
        }
        requireActiveParents(request.getOrganizationId(), request.getContactId(), request.getDealId());
        applyTypeFields(type, request);

        Activity activity = new Activity();
        activity.setType(type);
        activity.setSubject(trimToNull(request.getSubject()));
        activity.setBody(trimToNull(request.getBody()));
        activity.setOrganizationId(request.getOrganizationId());
        activity.setContactId(request.getContactId());
        activity.setDealId(request.getDealId());
        activity.setDueAt(type == ActivityType.TASK ? request.getDueAt() : null);
        activity.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : Instant.now());
        recordAuthor(activity);

        Activity saved = activityRepository.save(activity);
        log.info("Created activity id={} type={}", saved.getId(), saved.getType());
        return ActivityMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ActivityDto getById(Long id) {
        return ActivityMapper.toDto(loadActive(id));
    }

    /**
     * One parent at a time: the organization timeline, the contact timeline, or the deal timeline.
     */
    @Transactional(readOnly = true)
    public Page<ActivityDto> timeline(
            Long organizationId,
            Long contactId,
            Long dealId,
            ActivityType type,
            Pageable pageable) {
        int parents = countParents(organizationId, contactId, dealId);
        if (parents != 1) {
            throw new BusinessException(
                    "A timeline needs exactly one of organizationId, contactId, or dealId",
                    "ACTIVITY_TIMELINE_FILTER");
        }
        requireActiveParents(organizationId, contactId, dealId);
        Page<Activity> page = loadTimeline(organizationId, contactId, dealId, type, pageable);
        return page.map(ActivityMapper::toDto);
    }

    @Transactional
    public ActivityDto complete(Long id) {
        Activity activity = loadActive(id);
        if (activity.getType() != ActivityType.TASK) {
            throw new BusinessException("Only a task can be completed", "ACTIVITY_NOT_A_TASK");
        }
        if (activity.getCompletedAt() == null) {
            activity.setCompletedAt(Instant.now());
            activityRepository.save(activity);
            log.info("Completed activity id={}", activity.getId());
        }
        return ActivityMapper.toDto(activity);
    }

    private void rejectIngestOnlyTypes(ActivityType type) {
        switch (type) {
            case NOTE, TASK -> {
                // written by this service
            }
            case EMAIL, MEETING -> throw new BusinessException(
                    type + " activities are recorded by mailbox ingestion (DRG-521), not by this endpoint",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "ACTIVITY_INGEST_NOT_IMPLEMENTED");
            default -> {
                ActivityType unexpected = type;
                throw new IllegalStateException("Unhandled activity type: " + unexpected);
            }
        }
    }

    private void applyTypeFields(ActivityType type, ActivityCreateRequest request) {
        switch (type) {
            case NOTE -> {
                if (request.getDueAt() != null) {
                    throw new BusinessException("A note does not have a due date", "ACTIVITY_FIELD_NOT_ALLOWED");
                }
                if (trimToNull(request.getBody()) == null) {
                    throw new BusinessException("A note needs text", "ACTIVITY_NOTE_BODY_REQUIRED");
                }
            }
            case TASK -> {
                if (trimToNull(request.getSubject()) == null) {
                    throw new BusinessException("A task needs a subject", "ACTIVITY_TASK_SUBJECT_REQUIRED");
                }
            }
            case EMAIL, MEETING -> throw new IllegalStateException("Ingest types are rejected before field checks");
            default -> {
                ActivityType unexpected = type;
                throw new IllegalStateException("Unhandled activity type: " + unexpected);
            }
        }
    }

    private Page<Activity> loadTimeline(
            Long organizationId,
            Long contactId,
            Long dealId,
            ActivityType type,
            Pageable pageable) {
        if (organizationId != null) {
            if (type == null) {
                return activityRepository.findByDeletedFalseAndOrganizationId(organizationId, pageable);
            }
            return activityRepository.findByDeletedFalseAndOrganizationIdAndType(organizationId, type, pageable);
        }
        if (contactId != null) {
            if (type == null) {
                return activityRepository.findByDeletedFalseAndContactId(contactId, pageable);
            }
            return activityRepository.findByDeletedFalseAndContactIdAndType(contactId, type, pageable);
        }
        if (type == null) {
            return activityRepository.findByDeletedFalseAndDealId(dealId, pageable);
        }
        return activityRepository.findByDeletedFalseAndDealIdAndType(dealId, type, pageable);
    }

    private void requireActiveParents(Long organizationId, Long contactId, Long dealId) {
        if (organizationId != null) {
            requireActive("Organization", organizationId, organizationRepository.findById(organizationId));
        }
        if (contactId != null) {
            requireActive("Contact", contactId, contactRepository.findById(contactId));
        }
        if (dealId != null) {
            requireActive("Deal", dealId, dealRepository.findById(dealId));
        }
    }

    private static void requireActive(String resource, Long id, Optional<? extends BaseEntity> found) {
        BaseEntity parent = found.orElseThrow(() -> new ResourceNotFoundException(resource, id));
        if (parent.isDeleted()) {
            throw new BusinessException(
                    "Cannot link an activity to a deleted " + resource.toLowerCase(),
                    "ACTIVITY_PARENT_DELETED");
        }
    }

    private static int countParents(Long organizationId, Long contactId, Long dealId) {
        int count = 0;
        if (organizationId != null) {
            count++;
        }
        if (contactId != null) {
            count++;
        }
        if (dealId != null) {
            count++;
        }
        return count;
    }

    private void recordAuthor(Activity activity) {
        SecurityUtils.getCurrentCaller().ifPresent(caller -> {
            activity.setAuthorId(caller.actorId());
            if (caller instanceof Caller.Service) {
                activity.setAuthorKind(ActivityAuthorKind.SERVICE);
            } else if (caller instanceof Caller.User) {
                activity.setAuthorKind(ActivityAuthorKind.USER);
            } else {
                Caller unexpected = caller;
                throw new IllegalStateException("Unhandled caller: " + unexpected);
            }
        });
    }

    private Activity loadActive(Long id) {
        return activityRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
