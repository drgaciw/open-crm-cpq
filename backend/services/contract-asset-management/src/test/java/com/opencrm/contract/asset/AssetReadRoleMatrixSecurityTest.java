package com.opencrm.contract.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Defensive C16 role-matrix tests for existing CAM installed-base read enforcement
 * across all seven {@code /api/v1/assets/**} GET routes (#713, #719, #740).
 *
 * <p>Role catalog and policy references: {@code infra/entra/opencrm-identity.json:50},
 * {@code docs/security/drg-514-write-route-role-table.md},
 * {@code docs/runbooks/identity-go-live.md}, {@code docs/runbooks/asset-read-access.md},
 * and {@code docs/security/drg-926-asset-read-role-table.md}.
 * Tenant/account isolation (DRG-906 / C17) is tested separately.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AssetReadRoleMatrixSecurityTest.App.class)
@TestPropertySource(properties = {
        "opencrm.security.mode=jwt",
        "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER
})
@Tag("unit")
class AssetReadRoleMatrixSecurityTest {

    private static final String ALLOWED_ROLE = "InstalledBase.Read";
    private static final Set<String> REFUSED_MANIFEST_USER_ROLES = Set.of(
            "CPQ.User", "DealDesk", "SalesVP", "CFO", "Admin");
    private static final String REFUSED_MANIFEST_APP_ROLE = "Internal.Invoke";

    private static final AssetRepository ASSETS = mock(AssetRepository.class);
    private static final AssetMapper MAPPER = mock(AssetMapper.class);
    private static final AssetHistoryStore HISTORY_STORE = mock(AssetHistoryStore.class);

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({
            TestJwtDecoderConfiguration.class,
            AssetController.class,
            AssetQueryController.class,
            AssetReportController.class,
            GlobalExceptionHandler.class
    })
    static class App {
        @Bean
        AssetQueryService assetQueryService() {
            return new AssetQueryService(ASSETS, MAPPER);
        }

        @Bean
        AssetHistoryProjection assetHistoryProjection() {
            return new AssetHistoryProjection(HISTORY_STORE);
        }

        @Bean
        AssetReportService assetReportService(AssetHistoryProjection projection) {
            return new AssetReportService(projection, HISTORY_STORE);
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        reset(ASSETS, MAPPER, HISTORY_STORE);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void identityManifestRefusedRolesMatchTestedMatrix() throws Exception {
        Path manifest = locateManifest();
        JsonNode root = new ObjectMapper().readTree(Files.readString(manifest, StandardCharsets.UTF_8));
        Set<String> declared = new TreeSet<>();
        root.path("api").path("appRoles").forEach(role -> declared.add(role.path("value").asText()));

        Set<String> expected = new TreeSet<>(REFUSED_MANIFEST_USER_ROLES);
        expected.add(REFUSED_MANIFEST_APP_ROLE);
        expected.add(ALLOWED_ROLE);

        assertThat(declared)
                .as("every app role in infra/entra/opencrm-identity.json must appear in the C16 role matrix")
                .isEqualTo(expected);
    }

    @Test
    void anonymousIsUnauthorizedAcrossAllSevenRoutes() throws Exception {
        for (MockHttpServletRequestBuilder request : allSevenRoutes()) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(ASSETS, MAPPER, HISTORY_STORE);
    }

    @ParameterizedTest(name = "delegated user with refused role {0} receives 403 across all 7 routes")
    @ValueSource(strings = {"CPQ.User", "DealDesk", "SalesVP", "CFO", "Admin", "NotARealRole"})
    void refusedUserRoleIsForbiddenAcrossAllSevenRoutes(String role) throws Exception {
        String bearer = TestTokens.user("caller", role).bearer();
        for (MockHttpServletRequestBuilder request : allSevenRoutes()) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(ASSETS, MAPPER, HISTORY_STORE);
    }

    @Test
    void rolelessUserTokenIsForbiddenAcrossAllSevenRoutes() throws Exception {
        String bearer = TestTokens.user("caller").bearer();
        for (MockHttpServletRequestBuilder request : allSevenRoutes()) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(ASSETS, MAPPER, HISTORY_STORE);
    }

    @Test
    void internalInvokeServiceTokenIsForbiddenAcrossAllSevenRoutes() throws Exception {
        String bearer = TestTokens.service("quote-service", REFUSED_MANIFEST_APP_ROLE).bearer();
        for (MockHttpServletRequestBuilder request : allSevenRoutes()) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(ASSETS, MAPPER, HISTORY_STORE);
    }

    @Test
    void appOnlyServiceTokenWithInstalledBaseReadIsForbiddenAcrossAllSevenRoutes() throws Exception {
        String bearer = TestTokens.service("quote-service", ALLOWED_ROLE).bearer();
        for (MockHttpServletRequestBuilder request : allSevenRoutes()) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(ASSETS, MAPPER, HISTORY_STORE);
    }

    @Test
    void delegatedUserWithInstalledBaseReadOnlyIsAllowedAcrossAllSevenRoutes() throws Exception {
        stubAllowedAssetLookup();
        String bearer = TestTokens.user("reader", ALLOWED_ROLE).bearer();
        for (MockHttpServletRequestBuilder request : allSevenRoutes()) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void delegatedUserWithCpqUserAndInstalledBaseReadIsAllowedAcrossAllSevenRoutes() throws Exception {
        stubAllowedAssetLookup();
        String bearer = TestTokens.user("reader", "CPQ.User", ALLOWED_ROLE).bearer();
        for (MockHttpServletRequestBuilder request : allSevenRoutes()) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
        }
    }

    private static void stubAllowedAssetLookup() {
        Asset asset = Asset.builder().id(1L).build();
        when(ASSETS.findOne(any(Specification.class))).thenReturn(Optional.of(asset));
        when(MAPPER.toDto(asset)).thenReturn(AssetDto.builder().id(1L).build());
    }

    private static List<MockHttpServletRequestBuilder> allSevenRoutes() {
        return List.of(
                get("/api/v1/assets"),
                get("/api/v1/assets/1"),
                get("/api/v1/assets/a/history"),
                get("/api/v1/assets/a/supersede-chain"),
                get("/api/v1/assets").param("accountId", "a").param("asOf", "2026-06-01"),
                get("/api/v1/assets/reports/upcoming-renewals")
                        .param("accountId", "a")
                        .param("withinDays", "30")
                        .param("asOf", "2026-06-01"),
                get("/api/v1/assets/reports/churn")
                        .param("accountId", "a")
                        .param("periodStart", "2026-01-01")
                        .param("periodEnd", "2026-06-30")
        );
    }

    private static Path locateManifest() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("infra/entra/opencrm-identity.json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("infra/entra/opencrm-identity.json not found above " + Path.of("").toAbsolutePath());
    }
}
