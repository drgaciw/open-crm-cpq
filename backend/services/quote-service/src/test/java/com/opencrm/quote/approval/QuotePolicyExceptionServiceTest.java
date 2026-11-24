package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingPolicyExceptionDto;
import com.opencrm.quote.client.RoutingRecordPolicyExceptionRequest;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
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

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Quote BFF RecordException")
class QuotePolicyExceptionServiceTest {

    @Mock
    private QuoteRepository quotes;
    @Mock
    private ApprovalRoutingClient routing;

    private QuotePolicyExceptionService service;

    @BeforeEach
    void setUp() {
        service = new QuotePolicyExceptionService(quotes, routing);
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("DealDesk relays the quote version and the quote tenant, not a body tenant")
    void relaysQuoteVersionAndStoredTenant() {
        asUser("tenant-a", "desk-oid", "ROLE_DEAL_DESK");
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote("tenant-a", 3L)));
        when(routing.recordPolicyException(any())).thenReturn(RoutingPolicyExceptionDto.builder()
                .id(15L).quoteId("42").quoteVersion(3L).policyId(7L)
                .justification("one-off").expiresAt(Instant.parse("2026-12-01T00:00:00Z"))
                .recordedBy("user:desk-oid").build());

        PolicyExceptionResponse response = service.record(42L, request(3L));

        assertThat(response.recordedBy()).isEqualTo("user:desk-oid");
        assertThat(response.quoteVersion()).isEqualTo(3L);
        ArgumentCaptor<RoutingRecordPolicyExceptionRequest> sent =
                ArgumentCaptor.forClass(RoutingRecordPolicyExceptionRequest.class);
        verify(routing).recordPolicyException(sent.capture());
        assertThat(sent.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(sent.getValue().getQuoteVersion()).isEqualTo(3L);
        assertThat(sent.getValue().getQuoteId()).isEqualTo("42");
        assertThat(fieldNames(RecordQuotePolicyExceptionRequest.class)).doesNotContain("tenantId", "recordedBy");
    }

    @Test
    @DisplayName("a service, a seller, a tenant mismatch and the wrong version do not call approval-routing")
    void actorTenantAndVersionNegativesDoNotWrite() {
        Quote stored = quote("tenant-a", 3L);
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(stored));

        asService("quote-service");
        assertCode("ACTOR_REQUIRED", request(3L));

        asUser("tenant-a", "seller-oid", "ROLE_CPQ_USER");
        assertCode("EXCEPTION_ROLE_REQUIRED", request(3L));

        asUser("tenant-b", "desk-oid", "ROLE_DEAL_DESK");
        assertCode("TENANT_MISMATCH", request(3L));

        asUser("tenant-a", "desk-oid", "ROLE_DEAL_DESK");
        assertCode("QUOTE_VERSION_MISMATCH", request(2L));

        asUser(null, "desk-oid", "ROLE_DEAL_DESK");
        assertCode("TENANT_REQUIRED", request(3L));

        verify(routing, never()).recordPolicyException(any());
    }

    private void assertCode(String code, RecordQuotePolicyExceptionRequest request) {
        assertThatThrownBy(() -> service.record(42L, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }

    private static RecordQuotePolicyExceptionRequest request(long version) {
        return RecordQuotePolicyExceptionRequest.builder()
                .policyId(7L)
                .quoteVersion(version)
                .justification("one-off")
                .expiresAt(Instant.parse("2026-12-01T00:00:00Z"))
                .build();
    }

    private static Quote quote(String tenant, long version) {
        Quote quote = Quote.builder().quoteNumber("Q-42").name("Acme").customerExternalId("cust").build();
        quote.setId(42L);
        quote.setTenantId(tenant);
        quote.setVersion(version);
        return quote;
    }

    private static void asUser(String tenantId, String oid, String authority) {
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("oid", oid);
        claims.put("scp", "access_as_user");
        if (tenantId != null) {
            claims.put("tid", tenantId);
        }
        Jwt jwt = jwt(claims, oid);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority))));
    }

    private static void asService(String clientId) {
        Jwt jwt = jwt(Map.of("azp", clientId, "idtyp", "app", "oid", "sp-" + clientId, "tid", "tenant-a"), "sp-" + clientId);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_INVOKE"))));
    }

    private static Jwt jwt(Map<String, Object> claims, String subject) {
        return Jwt.withTokenValue("token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .header("alg", "none")
                .subject(subject)
                .claims(builder -> builder.putAll(claims))
                .build();
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
