package com.opencrm.contract.asset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AssetReadAuthorizationTest {
    private final AssetRepository assets = mock(AssetRepository.class);
    private final AssetMapper mapper = mock(AssetMapper.class);
    private final AssetHistoryStore history = mock(AssetHistoryStore.class);
    private final AssetQueryService query = new AssetQueryService(assets, mapper);
    private final AssetHistoryProjection projection = new AssetHistoryProjection(history);
    private final AssetReportService reports = new AssetReportService(projection, history);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void anonymousDirectCallsAreDeniedBeforeDataAccess() { assertAllReadsDenied(); }
    @Test void ordinaryUserDirectCallsAreDeniedBeforeDataAccess() {
        signIn("ROLE_CPQ_USER");
        assertAllReadsDenied();
    }
    @Test void appOnlyTokenWithRoleIsDeniedBeforeDataAccess() {
        signInJwt(true, true);
        assertAllReadsDenied();
    }
    @Test void inferredAppOnlyTokenWithRoleIsDeniedBeforeDataAccess() {
        signInJwt(true, false);
        assertAllReadsDenied();
    }
    @Test void explicitInstalledBaseCapabilityAllowsReads() {
        signInJwt(false, false);
        when(assets.findById(1L)).thenReturn(Optional.of(Asset.builder().id(1L).build()));
        query.list(null, null, null);
        query.get(1L);
        projection.history("asset");
        projection.chain("asset", "both");
        projection.installedBase("account", LocalDate.of(2026, 1, 1));
        reports.upcomingRenewals("account", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null);
        reports.churn("account", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        verify(assets).findByDeletedFalse();
        verify(history).findByAssetId("asset");
        verify(history).findByEventType(AssetHistoryKind.SUPERSEDED);
        verify(history, times(3)).findByAccountId("account");
    }
    private void assertAllReadsDenied() {
        assertThatThrownBy(() -> query.list(null, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> query.get(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projection.history("asset")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projection.chain("asset", "both")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projection.installedBase("account", LocalDate.of(2026, 1, 1))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reports.upcomingRenewals("account", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reports.churn("account", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(assets, mapper, history);
    }
    private static void signIn(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "caller", "n/a", List.of(new SimpleGrantedAuthority(role))));
    }
    private static void signInJwt(boolean appOnly, boolean explicitType) {
        var builder = Jwt.withTokenValue("token").header("alg", "none").subject("caller").claim("azp", "client");
        if (explicitType) builder.claim("idtyp", "app");
        if (!appOnly) builder.claim("scp", "access_as_user");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(),
                List.of(new SimpleGrantedAuthority("ROLE_INSTALLED_BASE_READ"))));
    }
}
