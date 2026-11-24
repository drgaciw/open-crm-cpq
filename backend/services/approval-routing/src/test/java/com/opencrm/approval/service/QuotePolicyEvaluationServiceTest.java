package com.opencrm.approval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.CommercialPolicy;
import com.opencrm.approval.domain.PolicyStatus;
import com.opencrm.approval.domain.PolicyThreshold;
import com.opencrm.approval.domain.PolicyVerdict;
import com.opencrm.approval.domain.ThresholdType;
import com.opencrm.approval.dto.QuoteEvaluationDto;
import com.opencrm.approval.dto.QuoteEvaluationRequest;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.audit.AuditTrailService;
import com.opencrm.audit.dto.AppendAuditEntryCommand;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.QuoteEvaluatedEvent;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuoteEvaluated evidence")
class QuotePolicyEvaluationServiceTest {

    @Mock
    private CommercialPolicyRepository repository;
    @Mock
    private OutboxEventPublisher outbox;
    @Mock
    private AuditTrailService audit;
    @Mock
    private CurrentActor currentActor;

    private QuotePolicyEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new QuotePolicyEvaluationService(repository, outbox, audit, new ObjectMapper(), currentActor);
        lenient().when(currentActor.unverifiedCallersAllowed()).thenReturn(false);
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Pass, RequiresApproval and Blocked each emit QuoteEvaluated with actual against limit")
    void eachVerdictIsEmittedAndAudited() {
        when(repository.findByStatusAndDeletedFalse(PolicyStatus.ACTIVE)).thenReturn(List.of(policy()));
        asUser("tenant-1", "seller-oid");

        QuoteEvaluationDto pass = service.evaluate(request("10", "40", "tenant-1"));
        assertThat(pass.getVerdict()).isEqualTo(PolicyVerdict.PASS);
        assertThat(pass.getBreaches()).isEmpty();

        QuoteEvaluationDto approval = service.evaluate(request("32", "40", "tenant-1"));
        assertThat(approval.getVerdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
        assertThat(approval.getBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.getActual()).isEqualTo("32");
            assertThat(breach.getLimit()).isEqualTo("25");
        });

        QuoteEvaluationDto blocked = service.evaluate(request("10", "15", "tenant-1"));
        assertThat(blocked.getVerdict()).isEqualTo(PolicyVerdict.BLOCKED);
        assertThat(blocked.getBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.getThresholdType()).isEqualTo(ThresholdType.MARGIN_FLOOR);
            assertThat(breach.getActual()).isEqualTo("15");
            assertThat(breach.getLimit()).isEqualTo("20");
        });

        ArgumentCaptor<QuoteEvaluatedEvent> events = ArgumentCaptor.forClass(QuoteEvaluatedEvent.class);
        verify(outbox, org.mockito.Mockito.times(3)).publish(
                eq("Quote"),
                eq("42"),
                eq(EventContractRegistry.QUOTE_EVALUATED_V1),
                events.capture(),
                any());
        assertThat(events.getAllValues()).extracting(QuoteEvaluatedEvent::verdict)
                .containsExactly("PASS", "REQUIRES_APPROVAL", "BLOCKED");
        assertThat(events.getAllValues().get(1).breaches()).singleElement().satisfies(breach -> {
            assertThat(breach.actual()).isEqualTo("32");
            assertThat(breach.limit()).isEqualTo("25");
        });
        assertThat(events.getAllValues().get(1).actorId()).isEqualTo("user:seller-oid");

        ArgumentCaptor<AppendAuditEntryCommand> audits = ArgumentCaptor.forClass(AppendAuditEntryCommand.class);
        verify(audit, org.mockito.Mockito.times(3)).append(audits.capture());
        assertThat(audits.getAllValues()).allSatisfy(command -> {
            assertThat(command.action()).isEqualTo("QuoteEvaluated");
            assertThat(command.aggregateType()).isEqualTo("Quote");
            assertThat(command.aggregateId()).isEqualTo("42");
            assertThat(command.principal()).isEqualTo("user:seller-oid");
            assertThat(command.tenantId()).isEqualTo("tenant-1");
        });
        assertThat(audits.getAllValues().get(1).details()).contains("32").contains("25");
        assertThat(audits.getAllValues().get(2).details()).contains("15").contains("20");
    }

    @Test
    @DisplayName("a service token and a tenant mismatch emit nothing")
    void actorAndTenantNegativesEmitNothing() {
        asService("quote-service");
        assertThatThrownBy(() -> service.evaluate(request("10", "40", "tenant-1")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("ACTOR_REQUIRED"));

        asUser("tenant-1", "seller-oid");
        assertThatThrownBy(() -> service.evaluate(request("10", "40", "other-tenant")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("TENANT_MISMATCH"));

        asUser(null, "seller-oid");
        assertThatThrownBy(() -> service.evaluate(request("10", "40", "tenant-1")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("TENANT_REQUIRED"));

        verify(outbox, never()).publish(any(), any(), any(), any(), any());
        verify(audit, never()).append(any());
        verify(repository, never()).findByStatusAndDeletedFalse(any());
    }

    private static QuoteEvaluationRequest request(String discount, String margin, String tenant) {
        return QuoteEvaluationRequest.builder()
                .quoteId("42")
                .quoteVersion(3L)
                .tenantId(tenant)
                .discountPercent(new BigDecimal(discount))
                .marginPercent(new BigDecimal(margin))
                .termMonths(12)
                .build();
    }

    private static CommercialPolicy policy() {
        CommercialPolicy policy = CommercialPolicy.builder()
                .name("Standard")
                .status(PolicyStatus.ACTIVE)
                .policyVersion(1)
                .build();
        policy.setId(7L);
        policy.addThreshold(PolicyThreshold.builder()
                .thresholdType(ThresholdType.DISCOUNT_PERCENT)
                .limitValue(new BigDecimal("25"))
                .build());
        policy.addThreshold(PolicyThreshold.builder()
                .thresholdType(ThresholdType.MARGIN_FLOOR)
                .limitValue(new BigDecimal("20"))
                .build());
        return policy;
    }

    private static void asUser(String tenantId, String oid) {
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("oid", oid);
        claims.put("scp", "access_as_user");
        if (tenantId != null) {
            claims.put("tid", tenantId);
        }
        authenticate(claims, oid);
    }

    private static void asService(String clientId) {
        authenticate(Map.of("azp", clientId, "idtyp", "app", "oid", "sp-" + clientId), "sp-" + clientId);
    }

    private static void authenticate(Map<String, Object> claims, String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .header("alg", "none")
                .subject(subject)
                .claims(builder -> builder.putAll(claims))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_CPQ_USER"))));
    }
}
