package com.opencrm.crm.activity;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    @Mock ActivityRepository activities;
    @Mock OrganizationRepository organizations;
    @Mock ContactRepository contacts;
    @Mock DealRepository deals;
    ActivityService service;

    @BeforeEach void setup() {
        service = new ActivityService(activities, organizations, contacts, deals, Mappers.getMapper(ActivityMapper.class));
        authenticate("workforce-a", false);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    static void authenticate(String tenant, boolean app) {
        var builder = Jwt.withTokenValue("test").header("alg", "none").subject("pairwise-sub")
                .claim("oid", "verified-author");
        if (tenant != null) builder.claim("tid", tenant);
        if (app) builder.claim("idtyp", "app").claim("azp", "service-client");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
    }

    ActivityCreateRequest request(ActivityType type) {
        ActivityCreateRequest r = new ActivityCreateRequest();
        r.setType(type); r.setTitle("Relationship update"); r.setOrganizationId(1L); r.setOccurredAt(NOW);
        if (type == ActivityType.NOTE) r.setNote("Seller-entered note");
        if (type == ActivityType.TASK) r.setDueAt(NOW.plusSeconds(3600));
        if (type == ActivityType.EMAIL || type == ActivityType.MEETING) r.setReference("opaque-reference");
        return r;
    }
    void activeOrg() { when(organizations.findById(1L)).thenReturn(Optional.of(new Organization())); }
    Activity stored(ActivityType type) {
        Activity row = new Activity(); row.setId(9L); row.setUuid("activity-uuid"); row.setType(type);
        row.setOrganizationId(1L); row.setWorkforceTenantId("workforce-a"); row.setAuthorId("verified-author");
        row.setOccurredAt(NOW); row.setTitle("Timeline entry"); return row;
    }

    @ParameterizedTest @EnumSource(ActivityType.class)
    void createsEveryTypeWithServerIdentityAndManualProvenance(ActivityType type) {
        activeOrg();
        when(activities.save(any())).thenAnswer(call -> {
            Activity row = call.getArgument(0); row.setId(9L); return row;
        });
        ActivityDto result = service.create(request(type));
        assertThat(result.getType()).isEqualTo(type);
        assertThat(result.getAuthorId()).isEqualTo("verified-author");
        assertThat(result.getSource()).isEqualTo("MANUAL");
        assertThat(result.getCompletedAt()).isNull();
        var captor = org.mockito.ArgumentCaptor.forClass(Activity.class);
        verify(activities).save(captor.capture());
        assertThat(captor.getValue().getWorkforceTenantId()).isEqualTo("workforce-a");
        if (type == ActivityType.EMAIL || type == ActivityType.MEETING) assertThat(result.getNote()).isNull();
    }

    @Test void anonymousAndMissingWorkforceContextFailBeforeRepositoryAccess() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.create(request(ActivityType.NOTE)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        authenticate(null, false);
        assertThatThrownBy(() -> service.timeline(1L, null, null, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo("ACTIVITY_CONTEXT_REQUIRED"));
        verifyNoInteractions(activities, organizations, contacts, deals);
    }

    @Test void serviceTokenCannotMasqueradeAsHumanAuthor() {
        authenticate("workforce-a", true);
        assertThatThrownBy(() -> service.create(request(ActivityType.NOTE)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(activities, organizations);
    }

    @Test void everyProvidedSubjectMustBeActiveAndFailureDoesNotWrite() {
        activeOrg();
        ActivityCreateRequest r = request(ActivityType.NOTE); r.setContactId(2L);
        Contact deleted = new Contact(); deleted.setDeleted(true);
        when(contacts.findById(2L)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(activities);
    }

    @Test void allThreeActiveSubjectTypesAreSupported() {
        activeOrg(); when(contacts.findById(2L)).thenReturn(Optional.of(new Contact()));
        when(deals.findById(3L)).thenReturn(Optional.of(new Deal()));
        when(activities.save(any())).thenAnswer(call -> call.getArgument(0));
        ActivityCreateRequest r = request(ActivityType.TASK); r.setContactId(2L); r.setDealId(3L);
        ActivityDto dto = service.create(r);
        assertThat(dto.getContactId()).isEqualTo(2L); assertThat(dto.getDealId()).isEqualTo(3L);
    }

    @Test void invalidTypeSpecificCombinationsAndMissingSubjectsNeverWrite() {
        for (ActivityType type : List.of(ActivityType.EMAIL, ActivityType.MEETING)) {
            ActivityCreateRequest r = request(type); r.setNote("mail body must not be stored");
            assertThatThrownBy(() -> service.create(r)).isInstanceOf(BusinessException.class);
            r.setNote(null); r.setReference(" ");
            assertThatThrownBy(() -> service.create(r)).isInstanceOf(BusinessException.class);
        }
        ActivityCreateRequest r = request(ActivityType.NOTE); r.setReference("not-valid-for-note");
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(BusinessException.class);
        r.setReference(null); r.setNote(" ");
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(BusinessException.class);
        r.setNote("note"); r.setDueAt(NOW);
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(BusinessException.class);
        r.setDueAt(null); r.setOrganizationId(null);
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(activities, organizations);
    }

    @Test void getAndCompleteUseVerifiedWorkforcePartitionAndHideMissingRows() {
        when(activities.findByIdAndWorkforceTenantIdAndDeletedFalse(9L, "workforce-a")).thenReturn(Optional.empty());
        when(activities.lockForCompletion(9L, "workforce-a")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.complete(9L)).isInstanceOf(ResourceNotFoundException.class);
        verify(activities).findByIdAndWorkforceTenantIdAndDeletedFalse(9L, "workforce-a");
        verify(activities).lockForCompletion(9L, "workforce-a");
        verify(activities, never()).save(any());
    }

    @Test void completionIsIdempotentAndOnlyTasksCanComplete() {
        activeOrg(); Activity row = stored(ActivityType.TASK);
        when(activities.lockForCompletion(9L, "workforce-a")).thenReturn(Optional.of(row));
        Instant first = service.complete(9L).getCompletedAt();
        assertThat(first).isNotNull(); assertThat(service.complete(9L).getCompletedAt()).isEqualTo(first);
        assertThat(first.getNano() % 1000).isZero();
        verify(activities, times(1)).save(row);
        row.setType(ActivityType.EMAIL);
        assertThatThrownBy(() -> service.complete(9L)).isInstanceOf(BusinessException.class);
    }

    @Test void timelinePassesBoundedPageAndIntersectingSubjectFilters() {
        activeOrg(); when(contacts.findById(2L)).thenReturn(Optional.of(new Contact()));
        when(activities.timeline("workforce-a", 1L, 2L, null, PageRequest.of(2, 10)))
                .thenReturn(new PageImpl<>(List.of(stored(ActivityType.EMAIL)), PageRequest.of(2, 10), 21));
        var page = service.timeline(1L, 2L, null, 2, 10);
        assertThat(page.getNumber()).isEqualTo(2); assertThat(page.getTotalElements()).isEqualTo(21);
        assertThat(page.getContent().getFirst().getSource()).isEqualTo("MANUAL");
        assertThatThrownBy(() -> service.timeline(1L, null, null, -1, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.timeline(1L, null, null, 0, 101)).isInstanceOf(BusinessException.class);
    }

    @Test void controllerKeepsArrayAndPageInfoContract() {
        activeOrg();
        when(activities.timeline("workforce-a", 1L, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(stored(ActivityType.NOTE)), PageRequest.of(0, 20), 1));
        var response = new ActivityController(service).timeline(1L, null, null, 0, 20);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getPagination().getTotalElements()).isEqualTo(1);
    }

    @Test void requestHasNoIdentityBodyOrCompletionFields() {
        assertThat(ActivityCreateRequest.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .doesNotContain("authorId", "tenantId", "workforceTenantId", "source", "completedAt", "body");
    }
}
