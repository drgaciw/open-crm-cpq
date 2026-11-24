package com.opencrm.approval.service;

import com.opencrm.approval.domain.CommercialPolicy;
import com.opencrm.approval.domain.PolicyException;
import com.opencrm.approval.domain.PolicyStatus;
import com.opencrm.approval.dto.PolicyExceptionDto;
import com.opencrm.approval.dto.RecordPolicyExceptionRequest;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.approval.repository.PolicyExceptionRepository;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ExceptionRecordedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordException")
class PolicyExceptionServiceTest {

    @Mock
    private PolicyExceptionRepository exceptions;
    @Mock
    private CommercialPolicyRepository policies;
    @Mock
    private OutboxEventPublisher outbox;
    @Mock
    private CurrentActor currentActor;

    private PolicyExceptionService service;

    @BeforeEach
    void setUp() {
        service = new PolicyExceptionService(exceptions, policies, outbox, currentActor);
        lenient().when(currentActor.unverifiedCallersAllowed()).thenReturn(false);
        lenient().when(policies.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(policy()));
        lenient().when(exceptions.save(any(PolicyException.class))).thenAnswer(invocation -> {
            PolicyException saved = invocation.getArgument(0);
            saved.setId(15L);
            return saved;
        });
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a deal desk user records the token actor and emits ExceptionRecorded")
    void recordsAndEmits() {
        asUser("tenant-1", "desk-oid", "ROLE_DEAL_DESK");

        PolicyExceptionDto dto = service.record(request("  one-off for this version  ", Instant.now().plusSeconds(3600), "tenant-1"));

        assertThat(dto.getId()).isEqualTo(15L);
        assertThat(dto.getRecordedBy()).isEqualTo("user:desk-oid");
        assertThat(dto.getJustification()).isEqualTo("one-off for this version");
        ArgumentCaptor<PolicyException> saved = ArgumentCaptor.forClass(PolicyException.class);
        verify(exceptions).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getValue().getQuoteId()).isEqualTo("42");
        assertThat(saved.getValue().getQuoteVersion()).isEqualTo(3L);
        assertThat(saved.getValue().getPolicyId()).isEqualTo(7L);
        ArgumentCaptor<ExceptionRecordedEvent> event = ArgumentCaptor.forClass(ExceptionRecordedEvent.class);
        verify(outbox).publish(eq("PolicyException"), eq("15"), eq(EventContractRegistry.EXCEPTION_RECORDED_V1), event.capture());
        assertThat(event.getValue().recordedBy()).isEqualTo("user:desk-oid");
        assertThat(event.getValue().quoteVersion()).isEqualTo(3L);
        assertThat(event.getValue().policyId()).isEqualTo(7L);
        assertThat(event.getValue().tenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("a service token, a tenant mismatch, a missing tenant and a seller role record nothing")
    void actorAndTenantNegativesRecordNothing() {
        asService("quote-service");
        assertCode("ACTOR_REQUIRED", request("because", Instant.now().plusSeconds(60), "tenant-1"));

        asUser("tenant-1", "desk-oid", "ROLE_DEAL_DESK");
        assertCode("TENANT_MISMATCH", request("because", Instant.now().plusSeconds(60), "other-tenant"));

        asUser(null, "desk-oid", "ROLE_DEAL_DESK");
        assertCode("TENANT_REQUIRED", request("because", Instant.now().plusSeconds(60), "tenant-1"));

        asUser("tenant-1", "seller-oid", "ROLE_CPQ_USER");
        assertCode("EXCEPTION_ROLE_REQUIRED", request("because", Instant.now().plusSeconds(60), "tenant-1"));

        verify(exceptions, never()).save(any());
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an expiry that is not in the future and a blank justification are refused")
    void expiredAndBlankJustificationAreRefused() {
        asUser("tenant-1", "desk-oid", "ROLE_DEAL_DESK");
        assertCode("EXCEPTION_EXPIRED", request("because", Instant.now().minusSeconds(1), "tenant-1"));
        assertCode("JUSTIFICATION_REQUIRED", request("   ", Instant.now().plusSeconds(60), "tenant-1"));
        verify(exceptions, never()).save(any());
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    private void assertCode(String code, RecordPolicyExceptionRequest request) {
        assertThatThrownBy(() -> service.record(request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(code);
                    assertThat(ex.getStatus()).isIn(HttpStatus.FORBIDDEN, HttpStatus.BAD_REQUEST);
                });
    }

    private static RecordPolicyExceptionRequest request(String justification, Instant expiresAt, String tenant) {
        return RecordPolicyExceptionRequest.builder()
                .quoteId("42")
                .quoteVersion(3L)
                .policyId(7L)
                .justification(justification)
                .expiresAt(expiresAt)
                .tenantId(tenant)
                .build();
    }

    private static CommercialPolicy policy() {
        CommercialPolicy policy = CommercialPolicy.builder().name("Standard").status(PolicyStatus.ACTIVE).policyVersion(1).build();
        policy.setId(7L);
        return policy;
    }

    private static void asUser(String tenantId, String oid, String authority) {
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("oid", oid);
        claims.put("scp", "access_as_user");
        if (tenantId != null) {
            claims.put("tid", tenantId);
        }
        authenticate(claims, oid, authority);
    }

    private static void asService(String clientId) {
        authenticate(Map.of("azp", clientId, "idtyp", "app", "oid", "sp-" + clientId), "sp-" + clientId, "ROLE_INTERNAL_INVOKE");
    }

    private static void authenticate(Map<String, Object> claims, String subject, String authority) {
        Jwt jwt = Jwt.withTokenValue("token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .header("alg", "none")
                .subject(subject)
                .claims(builder -> builder.putAll(claims))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority(authority))));
    }
}
