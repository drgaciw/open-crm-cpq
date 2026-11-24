package com.opencrm.quote.approval.isolation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.approval.ApprovalRequest;
import com.opencrm.quote.approval.ApprovalRequestDto;
import com.opencrm.quote.approval.ApprovalRequestRepository;
import com.opencrm.quote.approval.ApprovalResponseRequest;
import com.opencrm.quote.approval.ApprovalService;
import com.opencrm.quote.approval.ApprovalStatus;
import com.opencrm.quote.approval.ApprovalSubmitRequest;
import com.opencrm.quote.approval.ApprovalTier;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.ramp.RampScheduleGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Quote BFF half of the H4 {@code approval_requests.tenant_id} contract ({@link ApprovalRowTenant},
 * same type name as {@code com.opencrm.approval.isolation.ApprovalRowTenant}). Reads, decisions,
 * submit, and the pending inbox load quotes with {@code findByIdAndDeletedFalse} and return
 * routing rows with no tenant or account check. Account on a quote is {@code customerExternalId}.
 * A caller from another tenant, or another account of the same tenant, gets
 * {@link ResourceNotFoundException} on by-id and commands. The inbox returns only that caller's rows.
 *
 * <p>DRG-444 quote specs re-point at this {@link ApprovalRowTenant} if they take a tenant.
 * {@code PendingApproval} in #1076 re-points at the approval-routing type. This suite does not
 * edit #1076. Merge order is DRG-442, DRG-443, H4, DRG-444. There is no pending-by-tier method
 * here; the inbox is {@code getPendingApprovals}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Quote approval tenant and account isolation")
class QuoteApprovalIsolationTest {

    static final String TENANT_A = "tenant-a";
    static final String TENANT_B = "tenant-b";
    static final String ACCOUNT_1 = "acct-1";
    static final String ACCOUNT_2 = "acct-2";
    /** Client header values. They must not equal a caller tid or account_id in these specs. */
    static final String HEADER_TENANT = "tenant-from-x-tenant-id";
    static final String HEADER_ACCOUNT = "acct-from-x-account-id";

    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private ApprovalRoutingClient approvalRoutingClient;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ApprovalService approvalService;

    private final Map<Long, Quote> storedQuotes = new HashMap<>();

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
                approvalRepository,
                quoteRepository,
                approvalRoutingClient,
                true,
                mock(RampScheduleGuard.class));
        // Stands in for quote.quotes: the tenant-scoped lookups the real ApprovalService uses (H4) answer
        // like the database would, from the quotes each test registers with quote(...).
        when(quoteRepository.findByIdAndTenantIdAndDeletedFalse(any(), any())).thenAnswer(invocation -> {
            Quote quote = storedQuotes.get(invocation.<Long>getArgument(0));
            return quote != null && Objects.equals(quote.getTenantId(), invocation.getArgument(1))
                    ? Optional.of(quote) : Optional.empty();
        });
        when(quoteRepository.findByIdInAndTenantIdAndDeletedFalse(any(), any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            String tenantId = invocation.getArgument(1);
            return ids.stream().map(storedQuotes::get)
                    .filter(quote -> quote != null && Objects.equals(quote.getTenantId(), tenantId))
                    .toList();
        });
    }

    @AfterEach
    void signOut() {
        ApprovalCallerScope.clear();
    }

    @Test
    @DisplayName("get-by-id: tenant B does not read tenant A's quote approvals")
    void getByQuoteIdHidesAnotherTenant() {
        Quote ownedByA = quote(42L, TENANT_A, ACCOUNT_1);
        when(approvalRoutingClient.getByQuoteId(42L)).thenReturn(List.of(remote(10L, 42L, TENANT_A, ACCOUNT_1)));
        when(approvalRepository.findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(42L))
                .thenReturn(List.of(local(12L, 42L, TENANT_A, ACCOUNT_1)));
        spoofClientHeaders();
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);
        assertCallerIs(TENANT_B, ACCOUNT_1);
        assertThat(ApprovalRowTenant.read(ownedByA).visibleTo(ApprovalRowTenant.fromCaller())).isFalse();

        assertThatThrownBy(() -> approvalService.getByQuoteId(42L))
                .as("%s %s must not read approvals for quote 42", ApprovalRowTenant.COLUMN, TENANT_B)
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("get-by-id: another account in the same tenant does not read the quote approvals")
    void getByQuoteIdHidesAnotherAccount() {
        quote(42L, TENANT_A, ACCOUNT_1);
        when(approvalRoutingClient.getByQuoteId(42L)).thenReturn(List.of(remote(10L, 42L, TENANT_A, ACCOUNT_1)));
        when(approvalRepository.findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(42L)).thenReturn(List.of());
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_2);

        assertThatThrownBy(() -> approvalService.getByQuoteId(42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("get-by-id: the same tenant and account receives the quote approvals")
    void getByQuoteIdReturnsTheCallersHistory() {
        quote(42L, TENANT_A, ACCOUNT_1);
        when(approvalRoutingClient.getByQuoteId(42L)).thenReturn(List.of(remote(10L, 42L, TENANT_A, ACCOUNT_1)));
        when(approvalRepository.findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(42L)).thenReturn(List.of());
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        assertThat(approvalService.getByQuoteId(42L))
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(10L);
    }

    @Test
    @DisplayName("approve: tenant B does not decide tenant A's approval")
    void approveHidesAnotherTenant() {
        Quote ownedByA = quote(42L, TENANT_A, ACCOUNT_1);
        stubDecision(9L, 42L, "APPROVED");
        spoofClientHeaders();
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);
        assertCallerIs(TENANT_B, ACCOUNT_1);
        assertThat(ApprovalRowTenant.read(ownedByA).visibleTo(ApprovalRowTenant.fromCaller())).isFalse();

        assertThatThrownBy(() -> approvalService.approveRequest(9L, response("ok")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("approve: another account in the same tenant does not decide the approval")
    void approveHidesAnotherAccount() {
        quote(42L, TENANT_A, ACCOUNT_1);
        stubDecision(9L, 42L, "APPROVED");
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_2);

        assertThatThrownBy(() -> approvalService.approveRequest(9L, response("ok")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("approve: the same tenant and account decides the approval")
    void approveSucceedsForTheCaller() {
        quote(42L, TENANT_A, ACCOUNT_1);
        stubDecision(9L, 42L, "APPROVED");
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        assertThat(approvalService.approveRequest(9L, response("ok")).getStatus())
                .isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("reject: tenant B does not decide tenant A's approval")
    void rejectHidesAnotherTenant() {
        quote(42L, TENANT_A, ACCOUNT_1);
        stubDecision(9L, 42L, "REJECTED");
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);

        assertThatThrownBy(() -> approvalService.rejectRequest(9L, response("no")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("reject: another account in the same tenant does not decide the approval")
    void rejectHidesAnotherAccount() {
        quote(42L, TENANT_A, ACCOUNT_1);
        stubDecision(9L, 42L, "REJECTED");
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_2);

        assertThatThrownBy(() -> approvalService.rejectRequest(9L, response("no")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("submit: a body or header tenant does not authorize another tenant's quote")
    void submitIgnoresBodyAndHeaderAndHidesAnotherTenant() throws Exception {
        Quote ownedByA = quote(42L, TENANT_A, ACCOUNT_1);
        stubSubmit(ownedByA);
        spoofClientHeaders();
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);
        assertCallerIs(TENANT_B, ACCOUNT_1);
        assertThat(ApprovalRowTenant.read(ownedByA).visibleTo(ApprovalRowTenant.fromCaller())).isFalse();
        ApprovalSubmitRequest request = objectMapper.readValue(
                "{\"tier\":\"DEAL_DESK\",\"expectedVersion\":3,\"tenantId\":\"" + TENANT_A
                        + "\",\"accountId\":\"" + ACCOUNT_1 + "\"}",
                ApprovalSubmitRequest.class);

        assertThatThrownBy(() -> approvalService.submitForApproval(42L, request))
                .as("body tenant %s and X-Tenant-Id must not submit quote 42 for caller %s", TENANT_A, TENANT_B)
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(ownedByA.getTenantId()).isEqualTo(TENANT_A);
        assertThat(ownedByA.getCustomerExternalId()).isEqualTo(ACCOUNT_1);
    }

    @Test
    @DisplayName("submit: another account in the same tenant does not submit the quote")
    void submitHidesAnotherAccount() throws Exception {
        Quote owned = quote(42L, TENANT_A, ACCOUNT_1);
        stubSubmit(owned);
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_2);
        ApprovalSubmitRequest request = objectMapper.readValue(
                "{\"tier\":\"DEAL_DESK\",\"expectedVersion\":3,\"accountId\":\"" + ACCOUNT_1 + "\"}",
                ApprovalSubmitRequest.class);

        assertThatThrownBy(() -> approvalService.submitForApproval(42L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("submit: the same tenant and account submits the quote")
    void submitSucceedsForTheCaller() {
        Quote owned = quote(42L, TENANT_A, ACCOUNT_1);
        stubSubmit(owned);
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        ApprovalRequestDto created = approvalService.submitForApproval(42L, ApprovalSubmitRequest.builder()
                .tier(ApprovalTier.DEAL_DESK)
                .expectedVersion(3L)
                .build());

        assertThat(created.getId()).isEqualTo(9001L);
        assertThat(created.getQuoteId()).isEqualTo(42L);
        assertThat(created.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("inbox: tenant B does not see tenant A's pending approvals")
    void inboxHidesAnotherTenant() {
        quote(42L, TENANT_B, ACCOUNT_1);
        quote(99L, TENANT_A, ACCOUNT_1);
        quote(55L, TENANT_A, ACCOUNT_1);
        when(approvalRoutingClient.getPending()).thenReturn(List.of(
                remote(10L, 42L, TENANT_B, ACCOUNT_1),
                remote(11L, 99L, TENANT_A, ACCOUNT_1)));
        when(approvalRepository.findByStatusAndDeletedFalse(ApprovalStatus.PENDING))
                .thenReturn(List.of(local(12L, 55L, TENANT_A, ACCOUNT_1)));
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);

        assertThat(approvalService.getPendingApprovals())
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(10L);
    }

    @Test
    @DisplayName("inbox: another account in the same tenant is hidden")
    void inboxHidesAnotherAccount() {
        quote(42L, TENANT_A, ACCOUNT_1);
        quote(77L, TENANT_A, ACCOUNT_2);
        when(approvalRoutingClient.getPending()).thenReturn(List.of(
                remote(10L, 42L, TENANT_A, ACCOUNT_1),
                remote(11L, 77L, TENANT_A, ACCOUNT_2)));
        when(approvalRepository.findByStatusAndDeletedFalse(ApprovalStatus.PENDING)).thenReturn(List.of());
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        assertThat(approvalService.getPendingApprovals())
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(10L);
    }

    @Test
    @DisplayName("inbox: the same tenant and account sees its own pending approval")
    void inboxReturnsTheCallersRequest() {
        quote(42L, TENANT_A, ACCOUNT_1);
        when(approvalRoutingClient.getPending()).thenReturn(List.of(remote(10L, 42L, TENANT_A, ACCOUNT_1)));
        when(approvalRepository.findByStatusAndDeletedFalse(ApprovalStatus.PENDING)).thenReturn(List.of());
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        assertThat(approvalService.getPendingApprovals())
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(10L);
    }

    private void stubDecision(long approvalId, long quoteId, String status) {
        when(approvalRoutingClient.getById(approvalId)).thenReturn(remote(approvalId, quoteId, null, null, "PENDING"));
        when(approvalRoutingClient.approve(approvalId, "ok")).thenReturn(remote(approvalId, quoteId, null, null, status));
        when(approvalRoutingClient.reject(approvalId, "no")).thenReturn(remote(approvalId, quoteId, null, null, status));
        when(approvalRoutingClient.reject(approvalId, "ok")).thenReturn(remote(approvalId, quoteId, null, null, status));
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubSubmit(Quote quote) {
        RoutingApprovalDto pending = remote(9001L, quote.getId(), quote.getTenantId(), quote.getCustomerExternalId(), "PENDING");
        when(approvalRoutingClient.submit(any())).thenReturn(pending);
        when(approvalRoutingClient.getById(9001L)).thenReturn(pending);
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Quote quote(long id, String tenantId, String accountId) {
        Quote quote = Quote.builder()
                .quoteNumber("Q-" + id)
                .name("Scoped")
                .customerExternalId(accountId)
                .status(QuoteStatus.DRAFT)
                .build();
        quote.setId(id);
        quote.setUuid("quote-" + id);
        quote.setVersion(3L);
        new ApprovalRowTenant(tenantId).stampOn(quote);
        when(quoteRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(quote));
        storedQuotes.put(id, quote);
        return quote;
    }

    private RoutingApprovalDto remote(long id, long quoteId, String tenantId, String accountId) {
        return remote(id, quoteId, tenantId, accountId, "PENDING");
    }

    private RoutingApprovalDto remote(long id, long quoteId, String tenantId, String accountId, String status) {
        RoutingApprovalDto dto = RoutingApprovalDto.builder()
                .id(id)
                .quoteId(String.valueOf(quoteId))
                .status(status)
                .approvalTier("DEAL_DESK")
                .requesterId("seller-1")
                .submittedAt(Instant.parse("2026-08-30T12:00:00Z"))
                .createdAt(Instant.parse("2026-08-30T12:00:00Z"))
                .build();
        if (tenantId != null) {
            new ApprovalRowTenant(tenantId).stampOn(dto);
        }
        StoredScope.stamp(dto, "accountId", accountId);
        return dto;
    }

    private ApprovalRequest local(long id, long quoteId, String tenantId, String accountId) {
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId(quoteId)
                .tier(ApprovalTier.DEAL_DESK)
                .status(ApprovalStatus.PENDING)
                .requestedBy("seller-1")
                .requestedAt(Instant.parse("2026-08-30T12:00:00Z"))
                .build();
        request.setId(id);
        new ApprovalRowTenant(tenantId).stampOn(request);
        StoredScope.stamp(request, "accountId", accountId);
        return request;
    }

    private static ApprovalResponseRequest response(String comments) {
        return ApprovalResponseRequest.builder().comments(comments).build();
    }

    private void spoofClientHeaders() {
        ApprovalCallerScope.clientHeader(ApprovalCallerScope.TENANT_HEADER, HEADER_TENANT);
        ApprovalCallerScope.clientHeader(ApprovalCallerScope.ACCOUNT_HEADER, HEADER_ACCOUNT);
    }

    private static void assertCallerIs(String tenantId, String accountId) {
        assertThat(ApprovalCallerScope.tenantId())
                .isEqualTo(tenantId)
                .isNotEqualTo(ApprovalCallerScope.suppliedHeader(ApprovalCallerScope.TENANT_HEADER));
        assertThat(ApprovalCallerScope.accountId())
                .isEqualTo(accountId)
                .isNotEqualTo(ApprovalCallerScope.suppliedHeader(ApprovalCallerScope.ACCOUNT_HEADER));
    }
}
