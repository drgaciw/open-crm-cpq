package com.opencrm.contract.asset;

import com.opencrm.contract.config.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetReadHttpAuthorizationTest {
    private final AssetQueryService query = new AssetQueryService(mock(AssetRepository.class), mock(AssetMapper.class));
    private final AssetHistoryProjection history = new AssetHistoryProjection(mock(AssetHistoryStore.class));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AssetController(query), new AssetQueryController(history))
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
    }

    @Test void authorizedCallerCanRead() throws Exception {
        signIn("ROLE_INSTALLED_BASE_READ");
        mvc.perform(get("/api/v1/assets")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/assets/a/history")).andExpect(status().isOk());
    }

    private static void signIn(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "caller", "n/a", List.of(new SimpleGrantedAuthority(role))));
    }
}
