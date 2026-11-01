package com.opencrm.contract.asset;

import com.opencrm.contract.config.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetReadHttpAuthorizationTest {
    private final AssetRepository assets = mock(AssetRepository.class);
    private final AssetMapper mapper = mock(AssetMapper.class);
    private final AssetQueryService query = new AssetQueryService(assets, mapper);
    private final AssetHistoryStore store = mock(AssetHistoryStore.class);
    private final AssetHistoryProjection history = new AssetHistoryProjection(store);
    private final AssetReportService reports = new AssetReportService(history, store);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AssetController(query), new AssetQueryController(history), new AssetReportController(reports))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void ordinaryCallerReceivesForbiddenForEveryAssetRead() throws Exception {
        signIn("ROLE_CPQ_USER");
        mvc.perform(get("/api/v1/assets")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/a/history")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/a/supersede-chain")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets").param("accountId", "a").param("asOf", "2026-01-01"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", "a").param("windowStart", "2026-06-01").param("windowEnd", "2026-06-30"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/reports/churn")
                        .param("accountId", "a").param("periodStart", "2026-01-01").param("periodEnd", "2026-06-30"))
                .andExpect(status().isForbidden());
    }

    @Test void authorizedCallerCanRead() throws Exception {
        signIn("ROLE_INSTALLED_BASE_READ");
        Asset asset = Asset.builder().id(1L).build();
        when(assets.findById(1L)).thenReturn(Optional.of(asset));
        when(mapper.toDto(asset)).thenReturn(AssetDto.builder().id(1L).build());
        mvc.perform(get("/api/v1/assets")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/assets/1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/assets/a/history")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", "a").param("withinDays", "30").param("asOf", "2026-06-01"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/assets/reports/churn")
                        .param("accountId", "a").param("periodStart", "2026-01-01").param("periodEnd", "2026-06-30"))
                .andExpect(status().isOk());
    }

    @Test void appOnlyTokenWithRoleReceivesForbidden() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("service")
                .claim("idtyp", "app").claim("azp", "client").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_INSTALLED_BASE_READ"))));
        mvc.perform(get("/api/v1/assets")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/a/history")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/reports/churn")
                        .param("accountId", "a").param("periodStart", "2026-01-01").param("periodEnd", "2026-06-30"))
                .andExpect(status().isForbidden());
    }

    @Test void noAuthenticationIsForbiddenEvenWhenNoSecurityFilterIsPresent() throws Exception {
        mvc.perform(get("/api/v1/assets")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/a/history")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", "a").param("windowStart", "2026-06-01").param("windowEnd", "2026-06-30"))
                .andExpect(status().isForbidden());
    }

    private static void signIn(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "caller", "n/a", List.of(new SimpleGrantedAuthority(role))));
    }
}
