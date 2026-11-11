package com.opencrm.crm.activity;

import com.opencrm.crm.config.GlobalExceptionHandler;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP binding/controller/service/mapper with verified principal fixtures; no DB or filter-chain claim. */
@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {
    @Mock ActivityRepository activities;
    @Mock OrganizationRepository organizations;
    @Mock ContactRepository contacts;
    @Mock DealRepository deals;
    MockMvc mvc;
    @BeforeEach void setup() {
        var service = new ActivityService(activities, organizations, contacts, deals, Mappers.getMapper(ActivityMapper.class));
        var validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ActivityController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        ActivityServiceTest.authenticate("workforce-a", false);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void createRejectsSpoofingByUsingPrincipalAndNeverReturnsBodyOrEntityContext() throws Exception {
        when(organizations.findById(1L)).thenReturn(Optional.of(new Organization()));
        when(activities.save(any())).thenAnswer(call -> { Activity a = call.getArgument(0); a.setId(7L); return a; });
        mvc.perform(post("/api/v1/activities").contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"EMAIL","title":"Manual email reference","reference":"opaque-thread",
                 "organizationId":1,"occurredAt":"2026-11-05T12:00:00Z",
                 "authorId":"spoof","workforceTenantId":"other","completedAt":"2026-01-01T00:00:00Z"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorId").value("verified-author"))
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.reference").value("opaque-thread"))
                .andExpect(jsonPath("$.data.workforceTenantId").doesNotExist())
                .andExpect(jsonPath("$.data.note").doesNotExist())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data.version").doesNotExist());
    }

    @Test void httpValidationAndDomainValidationDoNotWrite() throws Exception {
        mvc.perform(post("/api/v1/activities").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"NOTE\",\"title\":\" \",\"organizationId\":1}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/activities").contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"EMAIL","title":"Email","note":"not allowed","reference":"thread",
                 "organizationId":1,"occurredAt":"2026-11-05T12:00:00Z"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ACTIVITY_INVALID"));
        verifyNoInteractions(activities, organizations);
    }

    @Test void actualListEnvelopeHasArrayAndPaginationAndCompleteRouteIsIdempotent() throws Exception {
        when(organizations.findById(1L)).thenReturn(Optional.of(new Organization()));
        Activity task = new Activity(); task.setId(7L); task.setType(ActivityType.TASK);
        task.setOrganizationId(1L); task.setAuthorId("verified-author"); task.setOccurredAt(Instant.now());
        when(activities.timeline(eq("workforce-a"), eq(1L), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(task)));
        mvc.perform(get("/api/v1/activities").param("organizationId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
        when(activities.lockForCompletion(7L, "workforce-a")).thenReturn(Optional.of(task));
        mvc.perform(post("/api/v1/activities/7/complete")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").exists());
        mvc.perform(post("/api/v1/activities/7/complete")).andExpect(status().isOk());
        verify(activities, times(1)).save(task);
    }

    @Test void anonymousServiceAndMissingContextAreRejectedThroughHttp() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/activities/1")).andExpect(status().isUnauthorized());
        ActivityServiceTest.authenticate("workforce-a", true);
        mvc.perform(get("/api/v1/activities/1")).andExpect(status().isForbidden());
        ActivityServiceTest.authenticate(null, false);
        mvc.perform(get("/api/v1/activities/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(activities);
    }
}
