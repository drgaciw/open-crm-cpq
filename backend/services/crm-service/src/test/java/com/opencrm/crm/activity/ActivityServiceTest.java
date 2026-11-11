package com.opencrm.crm.activity;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private DealRepository dealRepository;

    private ActivityService activityService;
    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(
                activityRepository, organizationRepository, contactRepository, dealRepository);
        lenient().when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> {
            Activity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(11L);
            }
            return saved;
        });
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNoteRequiresAParent() {
        BusinessException ex = catchThrowableOfType(
                BusinessException.class,
                () -> activityService.create(ActivityCreateRequest.builder()
                        .type(ActivityType.NOTE)
                        .body("Spoke with the buyer")
                        .build()));

        assertThat(ex.getErrorCode()).isEqualTo("ACTIVITY_PARENT_REQUIRED");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void createNoteTrimsTheBodyAndRecordsTheCaller() {
        Organization organization = new Organization();
        organization.setId(7L);
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(organization));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("oid-1", null, List.of()));

        ActivityDto created = activityService.create(ActivityCreateRequest.builder()
                .type(ActivityType.NOTE)
                .organizationId(7L)
                .body("  Spoke with the buyer  ")
                .build());

        assertThat(created.getType()).isEqualTo(ActivityType.NOTE);
        assertThat(created.getBody()).isEqualTo("Spoke with the buyer");
        assertThat(created.getOrganizationId()).isEqualTo(7L);
        assertThat(created.getAuthorId()).isEqualTo("oid-1");
        assertThat(created.getAuthorKind()).isEqualTo(ActivityAuthorKind.USER);
        assertThat(created.getOccurredAt()).isNotNull();
        assertThat(created.getEmailThreadRef()).isNull();
        assertThat(created.getCalendarEventRef()).isNull();
    }

    @Test
    void createNoteRejectsABlankBody() {
        Organization organization = new Organization();
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(organization));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class,
                () -> activityService.create(ActivityCreateRequest.builder()
                        .type(ActivityType.NOTE)
                        .organizationId(7L)
                        .body("   ")
                        .build()));

        assertThat(ex.getErrorCode()).isEqualTo("ACTIVITY_NOTE_BODY_REQUIRED");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void createTaskStoresTheDueDateAndLeavesItOpen() {
        Deal deal = new Deal();
        deal.setId(9L);
        when(dealRepository.findById(9L)).thenReturn(Optional.of(deal));
        Instant due = Instant.parse("2026-12-01T15:00:00Z");

        ActivityDto created = activityService.create(ActivityCreateRequest.builder()
                .type(ActivityType.TASK)
                .dealId(9L)
                .subject("  Send the recap  ")
                .dueAt(due)
                .build());

        assertThat(created.getType()).isEqualTo(ActivityType.TASK);
        assertThat(created.getSubject()).isEqualTo("Send the recap");
        assertThat(created.getDealId()).isEqualTo(9L);
        assertThat(created.getDueAt()).isEqualTo(due);
        assertThat(created.getCompletedAt()).isNull();
    }

    @Test
    void createTaskRequiresASubject() {
        Deal deal = new Deal();
        when(dealRepository.findById(9L)).thenReturn(Optional.of(deal));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class,
                () -> activityService.create(ActivityCreateRequest.builder()
                        .type(ActivityType.TASK)
                        .dealId(9L)
                        .subject("  ")
                        .build()));

        assertThat(ex.getErrorCode()).isEqualTo("ACTIVITY_TASK_SUBJECT_REQUIRED");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void createRejectsEmailAndMeetingUntilMailboxIngestion() {
        for (ActivityType type : List.of(ActivityType.EMAIL, ActivityType.MEETING)) {
            BusinessException ex = catchThrowableOfType(
                    BusinessException.class,
                    () -> activityService.create(ActivityCreateRequest.builder()
                            .type(type)
                            .organizationId(7L)
                            .body("synced")
                            .build()));

            assertThat(ex.getErrorCode()).isEqualTo("ACTIVITY_INGEST_NOT_IMPLEMENTED");
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        }
        verifyNoInteractions(organizationRepository);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void createRejectsADueDateOnANote() {
        Organization organization = new Organization();
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(organization));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class,
                () -> activityService.create(ActivityCreateRequest.builder()
                        .type(ActivityType.NOTE)
                        .organizationId(7L)
                        .body("A note")
                        .dueAt(Instant.parse("2026-12-01T15:00:00Z"))
                        .build()));

        assertThat(ex.getErrorCode()).isEqualTo("ACTIVITY_FIELD_NOT_ALLOWED");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void missingOrganizationIsNotFound() {
        when(organizationRepository.findById(7L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = catchThrowableOfType(
                ResourceNotFoundException.class,
                () -> activityService.create(ActivityCreateRequest.builder()
                        .type(ActivityType.NOTE)
                        .organizationId(7L)
                        .body("A note")
                        .build()));

        assertThat(ex.getMessage()).contains("Organization");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void deletedContactIsRejected() {
        Contact contact = new Contact();
        contact.setDeleted(true);
        when(contactRepository.findById(4L)).thenReturn(Optional.of(contact));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class,
                () -> activityService.create(ActivityCreateRequest.builder()
                        .type(ActivityType.NOTE)
                        .contactId(4L)
                        .body("A note")
                        .build()));

        assertThat(ex.getErrorCode()).isEqualTo("ACTIVITY_PARENT_DELETED");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void timelineRequiresExactlyOneParent() {
        BusinessException none = catchThrowableOfType(
                BusinessException.class,
                () -> activityService.timeline(null, null, null, null, pageable));
        BusinessException both = catchThrowableOfType(
                BusinessException.class,
                () -> activityService.timeline(7L, 4L, null, null, pageable));

        assertThat(none.getErrorCode()).isEqualTo("ACTIVITY_TIMELINE_FILTER");
        assertThat(both.getErrorCode()).isEqualTo("ACTIVITY_TIMELINE_FILTER");
        verify(activityRepository, never()).save(any());
        verify(activityRepository, never()).findByIdAndDeletedFalse(any());
        verify(activityRepository, never()).findByDeletedFalseAndDealIdAndType(any(), any(), any());
        verify(activityRepository, never()).findByDeletedFalseAndOrganizationId(any(), any());
    }

    @Test
    void timelineByDealPassesTheTypeFilter() {
        Deal deal = new Deal();
        when(dealRepository.findById(9L)).thenReturn(Optional.of(deal));
        Activity note = new Activity();
        note.setId(3L);
        note.setType(ActivityType.NOTE);
        note.setBody("Recap");
        note.setDealId(9L);
        note.setOccurredAt(Instant.parse("2026-11-01T12:00:00Z"));
        when(activityRepository.findByDeletedFalseAndDealIdAndType(9L, ActivityType.NOTE, pageable))
                .thenReturn(new PageImpl<>(List.of(note)));

        Page<ActivityDto> page = activityService.timeline(null, null, 9L, ActivityType.NOTE, pageable);

        assertThat(page.getContent()).extracting(ActivityDto::getBody).containsExactly("Recap");
        verify(activityRepository).findByDeletedFalseAndDealIdAndType(9L, ActivityType.NOTE, pageable);
    }

    @Test
    void completeTaskSetsCompletedAtOnce() {
        Activity task = openTask();
        when(activityRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(task));

        ActivityDto completed = activityService.complete(11L);

        assertThat(completed.getCompletedAt()).isNotNull();
        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(saved.capture());
        assertThat(saved.getValue().getCompletedAt()).isEqualTo(completed.getCompletedAt());
    }

    @Test
    void completeIsIdempotent() {
        Activity task = openTask();
        Instant already = Instant.parse("2026-01-01T00:00:00Z");
        task.setCompletedAt(already);
        when(activityRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(task));

        ActivityDto completed = activityService.complete(11L);

        assertThat(completed.getCompletedAt()).isEqualTo(already);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void completeRejectsANote() {
        Activity note = new Activity();
        note.setId(11L);
        note.setType(ActivityType.NOTE);
        when(activityRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(note));

        BusinessException ex = catchThrowableOfType(BusinessException.class, () -> activityService.complete(11L));

        assertThat(ex.getErrorCode()).isEqualTo("ACTIVITY_NOT_A_TASK");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void getMissingIsNotFound() {
        when(activityRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = catchThrowableOfType(
                ResourceNotFoundException.class, () -> activityService.getById(11L));

        assertThat(ex.getMessage()).contains("Activity");
    }

    private static Activity openTask() {
        Activity task = new Activity();
        task.setId(11L);
        task.setType(ActivityType.TASK);
        task.setSubject("Send the recap");
        task.setDealId(9L);
        task.setOccurredAt(Instant.parse("2026-11-01T12:00:00Z"));
        return task;
    }
}
