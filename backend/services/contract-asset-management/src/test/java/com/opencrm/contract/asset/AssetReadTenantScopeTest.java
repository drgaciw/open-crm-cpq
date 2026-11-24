package com.opencrm.contract.asset;

import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Caller scope is the JWT tid. A missing role is still 403. A closed scope does not query. */
class AssetReadTenantScopeTest {
    private final AssetRepository assets = mock(AssetRepository.class);
    private final AssetQueryService query = new AssetQueryService(assets, mock(AssetMapper.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scopeIsTheCallerTidOnly() throws Exception {
        Method current = InstalledBaseReadScope.class.getDeclaredMethod("current");
        assertThat(current.getParameterCount()).isZero();
        AssetReadTenantFixture.signIn(AssetReadTenantFixture.TID_A);
        InstalledBaseReadScope scope = InstalledBaseReadScope.current();
        assertThat(scope.open()).isTrue();
        assertThat(scope.tenantId()).isEqualTo(AssetReadTenantFixture.TID_A);
    }

    @Test
    void nullAndNonGuidTidFailClosed() {
        AssetReadTenantFixture.signInRawTid(null);
        assertThat(InstalledBaseReadScope.current().open()).isFalse();
        AssetReadTenantFixture.signInRawTid("not-a-guid");
        assertThat(InstalledBaseReadScope.current().open()).isFalse();
        assertThat(query.list("acct-b1", 9L, "line-x")).isEmpty();
        assertThatThrownBy(() -> query.get(4L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Asset not found with identifier: 4");
        verifyNoInteractions(assets);
    }

    @Test
    void missingRoleIsForbiddenBeforeAQuery() {
        AssetReadTenantFixture.signInWithoutRole(AssetReadTenantFixture.TID_A);
        assertThatThrownBy(InstalledBaseReadScope::current).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> query.list(null, null, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(assets);
    }

    @Test
    void unboundedListFinderIsGone() {
        assertThat(Arrays.stream(AssetRepository.class.getMethods()).map(Method::getName))
                .doesNotContain("findByDeletedFalse");
    }
}
