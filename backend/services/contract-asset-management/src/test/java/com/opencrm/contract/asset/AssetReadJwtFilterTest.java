package com.opencrm.contract.asset;

import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AssetReadJwtFilterTest.App.class)
@TestPropertySource(properties = "opencrm.security.mode=jwt")
class AssetReadJwtFilterTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({AssetController.class, AssetQueryController.class, AssetReportController.class, GlobalExceptionHandler.class})
    static class App {
        @Bean AssetQueryService query() { return new AssetQueryService(mock(AssetRepository.class), mock(AssetMapper.class)); }
        @Bean AssetHistoryStore historyStore() { return mock(AssetHistoryStore.class); }
        @Bean AssetHistoryProjection history(AssetHistoryStore historyStore) { return new AssetHistoryProjection(historyStore); }
        @Bean AssetReportService reports(AssetHistoryProjection history, AssetHistoryStore historyStore) {
            return new AssetReportService(history, historyStore);
        }
        @Bean @Primary JwtDecoder decoder() {
            return token -> {
                var builder = Jwt.withTokenValue(token).header("alg", "none").subject("caller")
                        .claim("roles", List.of("InstalledBase.Read"))
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
                if ("app".equals(token)) builder.claim("idtyp", "app").claim("azp", "client");
                else if ("blank".equals(token)) builder.claim("scp", " ");
                else if (!"ambiguous".equals(token)) builder.claim("scp", "access_as_user");
                return builder.build();
            };
        }
    }

    @Autowired WebApplicationContext context;

    @Test void bearerChainRejectsApplicationAndAcceptsDelegatedUser() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        mvc.perform(get("/api/v1/assets").header("Authorization", "Bearer app")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/a/history").header("Authorization", "Bearer app")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets").header("Authorization", "Bearer ambiguous")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/a/history").header("Authorization", "Bearer blank")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets").header("Authorization", "Bearer user")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/assets/a/history").header("Authorization", "Bearer user")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/assets/reports/churn").header("Authorization", "Bearer app")
                        .param("accountId", "a").param("periodStart", "2026-01-01").param("periodEnd", "2026-06-30"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/reports/upcoming-renewals").header("Authorization", "Bearer user")
                        .param("accountId", "a").param("withinDays", "30").param("asOf", "2026-06-01"))
                .andExpect(status().isOk());
    }
}
