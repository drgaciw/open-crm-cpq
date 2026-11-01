package com.opencrm.contract.asset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AssetReadAuthorizationTest {
    private final AssetRepository assets = mock(AssetRepository.class);
    private final AssetMapper mapper = mock(AssetMapper.class);
    private final AssetHistoryStore history = mock(AssetHistoryStore.class);
    private final AssetQueryService query = new AssetQueryService(assets, mapper);
    private final AssetHistoryProjection projection = new AssetHistoryProjection(history);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void anonymousDirectCallsAreDeniedBeforeDataAccess() { assertAllReadsDenied(); }
    @Test void ordinaryUserDirectCallsAreDeniedBeforeDataAccess() {
        signIn("ROLE_CPQ_USER");
        assertAllReadsDenied();
    }
    @Test void explicitInstalledBaseCapabilityAllowsReads() {
        signIn("ROLE_INSTALLED_BASE_READ");
        query.list(null, null, null);
        projection.history("asset");
        projection.chain("asset", "both");
        projection.installedBase("account", LocalDate.of(2026, 1, 1));
        verify(assets).findByDeletedFalse();
        verify(history).findByAssetId("asset");
        verify(history).findByEventType(AssetHistoryKind.SUPERSEDED);
        verify(history).findByAccountId("account");
    }
    private void assertAllReadsDenied() {
        assertThatThrownBy(() -> query.list(null, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> query.get(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projection.history("asset")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projection.chain("asset", "both")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projection.installedBase("account", LocalDate.of(2026, 1, 1))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(assets, mapper, history);
    }
    private static void signIn(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "caller", "n/a", List.of(new SimpleGrantedAuthority(role))));
    }
}
