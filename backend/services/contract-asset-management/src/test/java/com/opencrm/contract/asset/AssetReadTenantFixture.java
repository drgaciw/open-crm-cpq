package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Two tenants, two accounts in each, a null-tenant subscription, and a supersede
 * hop that leaves the caller's tenant. Account is not an isolation boundary.
 */
final class AssetReadTenantFixture {
    static final UUID TID_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static final UUID TID_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    static final String ACCOUNT_A1 = "acct-a1";
    static final String ACCOUNT_A2 = "acct-a2";
    static final String ACCOUNT_B1 = "acct-b1";
    static final String ACCOUNT_B2 = "acct-b2";
    static final String ACCOUNT_NULL = "acct-null";
    static final String PREDECESSOR = "11111111-1111-4111-8111-111111111111";
    static final String SUCCESSOR = "22222222-2222-4222-8222-222222222222";
    static final String OTHER_TENANT = "33333333-3333-4333-8333-333333333333";
    static final String OTHER_ACCOUNT = "44444444-4444-4444-8444-444444444444";
    static final String RENEWAL = "55555555-5555-4555-8555-555555555555";
    static final String CHURN = "66666666-6666-4666-8666-666666666666";
    static final String NULL_TENANT = "77777777-7777-4777-8777-777777777777";
    static final String MISSING_UUID = "00000000-0000-4000-8000-000000000099";
    static final long MISSING_ID = 9_000_000L;
    static final LocalDate AS_OF = LocalDate.parse("2026-06-01");
    static final LocalDate WINDOW_START = LocalDate.parse("2026-06-01");
    static final LocalDate WINDOW_END = LocalDate.parse("2026-06-30");
    static final LocalDate PERIOD_START = LocalDate.parse("2026-04-01");
    static final LocalDate PERIOD_END = LocalDate.parse("2026-06-30");

    final Map<String, Long> assetIds = new LinkedHashMap<>();
    final Map<String, Long> subscriptionIds = new LinkedHashMap<>();

    void seed(SubscriptionRepository subscriptions, AssetRepository assets, AssetHistoryProjection history) {
        Holding predecessor = save(subscriptions, assets, TID_A, ACCOUNT_A1, PREDECESSOR, "line-p", "2026-12-31", "1");
        Holding successor = save(subscriptions, assets, TID_A, ACCOUNT_A2, SUCCESSOR, "line-s", "2026-12-31", "1");
        Holding otherTenant = save(subscriptions, assets, TID_B, ACCOUNT_B1, OTHER_TENANT, "line-x", "2026-12-31", "1");
        Holding otherAccount = save(subscriptions, assets, TID_B, ACCOUNT_B2, OTHER_ACCOUNT, "line-y", "2026-12-31", "2");
        Holding renewal = save(subscriptions, assets, TID_A, ACCOUNT_A1, RENEWAL, "line-r", "2026-06-15", "10");
        Holding churn = save(subscriptions, assets, TID_A, ACCOUNT_A1, CHURN, "line-c", "2026-12-31", "4");
        Holding nullTenant = save(subscriptions, assets, null, ACCOUNT_NULL, NULL_TENANT, "line-n", "2026-12-31", "1");
        created(history, predecessor);
        created(history, successor);
        created(history, otherTenant);
        created(history, otherAccount);
        created(history, renewal);
        created(history, churn);
        created(history, nullTenant);
        history.apply(new AssetSupersededEvent(
                PREDECESSOR, ACCOUNT_A1, SUCCESSOR, PREDECESSOR, "SUPERSEDED",
                LocalDate.parse("2026-07-01"), "amd-ps", "1"), "evt-ps");
        history.apply(new AssetSupersededEvent(
                SUCCESSOR, ACCOUNT_A2, OTHER_TENANT, SUCCESSOR, "SUPERSEDED",
                LocalDate.parse("2026-08-01"), "amd-sx", "1"), "evt-sx");
        AssetReportTestEvents.terminated(history, "evt-churn", CHURN, ACCOUNT_A1,
                LocalDate.parse("2026-05-01"), "amd-c", "1");
        AssetReportTestEvents.terminated(history, "evt-y", OTHER_ACCOUNT, ACCOUNT_B2,
                LocalDate.parse("2026-05-15"), "amd-y", "1");
    }

    static void signIn(UUID tenantId) {
        signIn(tenantId == null ? null : tenantId.toString(), "ROLE_INSTALLED_BASE_READ");
    }

    static void signInRawTid(String tid) {
        signIn(tid, "ROLE_INSTALLED_BASE_READ");
    }

    static void signInWithoutRole(UUID tenantId) {
        signIn(tenantId.toString(), "ROLE_CPQ_USER");
    }

    static String normalizeNotFound(byte[] body, String identifier) {
        String json = new String(body, StandardCharsets.UTF_8);
        return json.replace("identifier: " + identifier, "identifier: ID")
                .replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"TIMESTAMP\"");
    }

    private static void signIn(String tid, String role) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none")
                .subject("installed-base-reader")
                .claim("scp", "access_as_user");
        if (tid != null) {
            builder.claim("tid", tid);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                builder.build(), List.of(new SimpleGrantedAuthority(role))));
    }

    private Holding save(SubscriptionRepository subscriptions, AssetRepository assets, UUID tenantId, String accountId,
                         String assetUuid, String contractLineId, String end, String quantity) {
        Subscription subscription = subscriptions.saveAndFlush(Subscription.builder()
                .subscriptionNumber("SUB-" + assetUuid)
                .customerId(accountId)
                .quoteId("Q-" + assetUuid)
                .status(SubscriptionStatus.ACTIVE)
                .planName("Installed base")
                .startDate(LocalDate.parse("2026-01-01"))
                .endDate(LocalDate.parse(end))
                .tenantId(tenantId)
                .build());
        Asset asset = assets.saveAndFlush(Asset.builder()
                .uuid(assetUuid)
                .accountId(accountId)
                .contractLineId(contractLineId)
                .subscriptionId(subscription.getId())
                .productRef("prod-" + contractLineId)
                .quantity(new BigDecimal(quantity))
                .status(AssetStatus.ACTIVE)
                .effectiveStart(LocalDate.parse("2026-01-01"))
                .effectiveEnd(LocalDate.parse(end))
                .build());
        assetIds.put(assetUuid, asset.getId());
        subscriptionIds.put(assetUuid, subscription.getId());
        return new Holding(assetUuid, accountId, contractLineId, subscription.getId(), end, quantity);
    }

    private static void created(AssetHistoryProjection history, Holding holding) {
        history.apply(new AssetCreatedEvent(
                holding.assetUuid, holding.accountId, holding.contractLineId, holding.subscriptionId, null,
                "prod-" + holding.contractLineId, new BigDecimal(holding.quantity), "ACTIVE",
                LocalDate.parse("2026-01-01"), LocalDate.parse(holding.end)), "evt-" + holding.assetUuid);
    }

    private record Holding(String assetUuid, String accountId, String contractLineId, Long subscriptionId,
                           String end, String quantity) {
    }
}
