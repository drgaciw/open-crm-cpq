package com.opencrm.approval.isolation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.ApprovalSubmitRequest;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.service.ApprovalService;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Canonical H4 contract for {@code approval_requests.tenant_id} ({@link ApprovalRowTenant}).
 * A caller in another tenant, or another account of the same tenant, must get
 * {@link ResourceNotFoundException} so the row's existence does not leak. Lists return
 * only that caller's rows. The column value comes from {@link ApprovalCallerScope}, never
 * from the body or {@code X-Tenant-Id} / {@code X-Account-Id}.
 *
 * <p>DRG-444 {@code com.opencrm.approval.testdoubles.PendingApproval} (#1076) re-points at
 * {@link ApprovalRowTenant}. This suite does not edit that pull request. Merge order is
 * DRG-442, DRG-443, H4, DRG-444.
 *
 * <p>Lenient stubs: the unscoped service walks the success path. A scoped service returns
 * before those collaborators, and the assertion is the spec either way.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Approval routing tenant and account isolation")
class ApprovalIsolationTest {

    static final String TENANT_A = "tenant-a";
    static final String TENANT_B = "tenant-b";
    static final String ACCOUNT_1 = "acct-1";
    static final String ACCOUNT_2 = "acct-2";
    /** Client header values. They must not equal a caller tid or account_id in these specs. */
    static final String HEADER_TENANT = "tenant-from-x-tenant-id";
    static final String HEADER_ACCOUNT = "acct-from-x-account-id";

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private ApprovalRequestMapper approvalRequestMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
                approvalRequestRepository,
                outboxEventPublisher,
                approvalRequestMapper,
                new CurrentActor("service:approval-routing", false));
        when(approvalRequestMapper.toDto(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> toDto(invocation.getArgument(0)));
        when(approvalRequestMapper.toDtoList(any()))
                .thenAnswer(invocation -> {
                    List<ApprovalRequest> rows = invocation.getArgument(0);
                    return rows.stream().map(this::toDto).toList();
                });
        // The real ApprovalService (H4) lists pending rows through the tenant-scoped JPQL queries. Each spec
        // states the table through the unscoped stubs below; these answer from that same data with the
        // queries' own WHERE clause: tenantId = :tenantId AND (:accountId IS NULL OR accountId = :accountId).
        when(approvalRequestRepository.findScopedByStatus(any(), any(), any())).thenAnswer(invocation ->
                scoped(approvalRequestRepository.findByStatus(invocation.getArgument(0)),
                        invocation.getArgument(1), invocation.getArgument(2)));
        when(approvalRequestRepository.findScopedByTierAndStatus(any(), any(), any(), any())).thenAnswer(invocation ->
                scoped(approvalRequestRepository.findByApprovalTierAndStatus(
                                invocation.getArgument(0), invocation.getArgument(1)),
                        invocation.getArgument(2), invocation.getArgument(3)));
    }

    private static List<ApprovalRequest> scoped(List<ApprovalRequest> rows, String tenantId, String accountId) {
        return rows == null ? List.of() : rows.stream()
                .filter(row -> tenantId.equals(row.getTenantId()))
                .filter(row -> accountId == null || accountId.equals(row.getAccountId()))
                .toList();
    }

    @AfterEach
    void signOut() {
        ApprovalCallerScope.clear();
    }

    @Test
    @DisplayName("get-by-id: tenant B does not read tenant A's request")
    void getByIdHidesAnotherTenant() {
        ApprovalRequest ownedByA = stored(1L, TENANT_A, ACCOUNT_1);
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(ownedByA));
        spoofClientHeaders();
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);
        assertCallerIs(TENANT_B, ACCOUNT_1);
        assertThat(new ApprovalRowTenant(TENANT_A).visibleTo(ApprovalRowTenant.fromCaller())).isFalse();

        assertThatThrownBy(() -> approvalService.getById(1L))
                .as("%s %s must not read approval 1 owned by %s", ApprovalRowTenant.COLUMN, TENANT_B, TENANT_A)
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("get-by-id: another account in the same tenant does not read the request")
    void getByIdHidesAnotherAccount() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(stored(1L, TENANT_A, ACCOUNT_1)));
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_2);

        assertThatThrownBy(() -> approvalService.getById(1L))
                .as("account %s must not read account %s approval 1", ACCOUNT_2, ACCOUNT_1)
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("get-by-id: the same tenant and account receives the request")
    void getByIdReturnsTheCallersRequest() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(stored(1L, TENANT_A, ACCOUNT_1)));
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        assertThat(approvalService.getById(1L).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("approve: tenant B does not decide tenant A's request")
    void approveHidesAnotherTenant() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(stored(1L, TENANT_A, ACCOUNT_1)));
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        spoofClientHeaders();
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);
        assertCallerIs(TENANT_B, ACCOUNT_1);
        assertThat(new ApprovalRowTenant(TENANT_A).visibleTo(ApprovalRowTenant.fromCaller())).isFalse();

        assertThatThrownBy(() -> approvalService.approve(1L))
                .as("%s %s must not approve approval 1 owned by %s", ApprovalRowTenant.COLUMN, TENANT_B, TENANT_A)
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("approve: another account in the same tenant does not decide the request")
    void approveHidesAnotherAccount() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(stored(1L, TENANT_A, ACCOUNT_1)));
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_2);

        assertThatThrownBy(() -> approvalService.approve(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("approve: the same tenant and account decides the request")
    void approveSucceedsForTheCaller() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(stored(1L, TENANT_A, ACCOUNT_1)));
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String approver = ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        ApprovalRequestDto decided = approvalService.approve(1L);

        assertThat(decided.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.getApproverId()).isEqualTo(approver);
    }

    @Test
    @DisplayName("reject: tenant B does not decide tenant A's request")
    void rejectHidesAnotherTenant() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(stored(1L, TENANT_A, ACCOUNT_1)));
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);

        assertThatThrownBy(() -> approvalService.reject(1L, "no"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("reject: another account in the same tenant does not decide the request")
    void rejectHidesAnotherAccount() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(stored(1L, TENANT_A, ACCOUNT_1)));
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_2);

        assertThatThrownBy(() -> approvalService.reject(1L, "no"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("submit: persisted scope is the caller, not the body or a client header")
    void submitStampsCallerScopeNotBodyOrHeader() throws Exception {
        spoofClientHeaders();
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_2);
        assertCallerIs(TENANT_B, ACCOUNT_2);
        ApprovalSubmitRequest request = objectMapper.readValue(
                "{\"quoteId\":\"Q-42\",\"tier\":\"DEAL_DESK\",\"tenantId\":\"" + TENANT_A
                        + "\",\"accountId\":\"" + ACCOUNT_1 + "\"}",
                ApprovalSubmitRequest.class);
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        approvalService.submit(request);

        ApprovalRequest saved = saved();
        assertThat(ApprovalRowTenant.read(saved))
                .as("%s is the caller tid, not the body or X-Tenant-Id", ApprovalRowTenant.COLUMN)
                .isEqualTo(ApprovalRowTenant.fromCaller());
        assertThat(StoredScope.read(saved, "accountId"))
                .as("accountId is the caller account_id claim, not the body or X-Account-Id")
                .isEqualTo(ACCOUNT_2);
    }

    @Test
    @DisplayName("submit: the same tenant and account creates a pending request")
    void submitReturnsPendingForTheCaller() {
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);
        when(approvalRequestRepository.save(any())).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(50L);
            return saved;
        });

        ApprovalRequestDto created = approvalService.submit(ApprovalSubmitRequest.builder()
                .quoteId("Q-42")
                .tier(QuoteApprovalTier.DEAL_DESK)
                .build());

        assertThat(created.getId()).isEqualTo(50L);
        assertThat(created.getQuoteId()).isEqualTo("Q-42");
        assertThat(created.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("list pending: tenant B does not see tenant A's request")
    void pendingHidesAnotherTenant() {
        when(approvalRequestRepository.findByStatus(ApprovalStatus.PENDING)).thenReturn(List.of(
                stored(1L, TENANT_A, ACCOUNT_1),
                stored(2L, TENANT_B, ACCOUNT_1)));
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);

        assertThat(approvalService.getPendingApprovals())
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("list pending: another account in the same tenant is hidden")
    void pendingHidesAnotherAccount() {
        when(approvalRequestRepository.findByStatus(ApprovalStatus.PENDING)).thenReturn(List.of(
                stored(1L, TENANT_A, ACCOUNT_1),
                stored(2L, TENANT_A, ACCOUNT_2)));
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        assertThat(approvalService.getPendingApprovals())
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("list pending: the same tenant and account sees its own request")
    void pendingReturnsTheCallersRequest() {
        when(approvalRequestRepository.findByStatus(ApprovalStatus.PENDING))
                .thenReturn(List.of(stored(1L, TENANT_A, ACCOUNT_1)));
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        assertThat(approvalService.getPendingApprovals())
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("list pending by tier: tenant B does not see tenant A's request")
    void pendingByTierHidesAnotherTenant() {
        when(approvalRequestRepository.findByApprovalTierAndStatus("DEAL_DESK", ApprovalStatus.PENDING))
                .thenReturn(List.of(stored(1L, TENANT_A, ACCOUNT_1), stored(2L, TENANT_B, ACCOUNT_1)));
        ApprovalCallerScope.signIn("manager-1", TENANT_B, ACCOUNT_1);

        assertThat(approvalService.getPendingApprovalsByTier("DEAL_DESK"))
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("list pending by tier: another account in the same tenant is hidden")
    void pendingByTierHidesAnotherAccount() {
        when(approvalRequestRepository.findByApprovalTierAndStatus("DEAL_DESK", ApprovalStatus.PENDING))
                .thenReturn(List.of(stored(1L, TENANT_A, ACCOUNT_1), stored(2L, TENANT_A, ACCOUNT_2)));
        ApprovalCallerScope.signIn("manager-1", TENANT_A, ACCOUNT_1);

        assertThat(approvalService.getPendingApprovalsByTier("DEAL_DESK"))
                .extracting(ApprovalRequestDto::getId)
                .containsExactly(1L);
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

    private ApprovalRequest saved() {
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(captor.capture());
        return captor.getValue();
    }

    private ApprovalRequest stored(long id, String tenantId, String accountId) {
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId("Q-" + id)
                .lineId("L-" + id)
                .approvalType(ApprovalType.QUOTE)
                .status(ApprovalStatus.PENDING)
                .requesterId(TestTokens.oidOf("seller-1"))
                .approvalTier("DEAL_DESK")
                .submittedAt(Instant.now())
                .build();
        request.setId(id);
        new ApprovalRowTenant(tenantId).stampOn(request);
        StoredScope.stamp(request, "accountId", accountId);
        return request;
    }

    private ApprovalRequestDto toDto(ApprovalRequest request) {
        return ApprovalRequestDto.builder()
                .id(request.getId())
                .quoteId(request.getQuoteId())
                .status(request.getStatus())
                .requesterId(request.getRequesterId())
                .approverId(request.getApproverId())
                .approvalTier(request.getApprovalTier())
                .build();
    }
}
