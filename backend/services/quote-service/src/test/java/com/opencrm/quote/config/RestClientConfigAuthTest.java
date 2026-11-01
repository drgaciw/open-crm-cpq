package com.opencrm.quote.config;

import com.opencrm.security.client.OutboundAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ADR-016 step 2 for quote-service: inside a user's request, pricing and approval-routing receive the user's token
 * (so they see the real actor), and configuration-engine's {@code /internal} evaluate receives quote-service's token.
 */
@DisplayName("quote-service outbound calls: user token relayed to /api/v1, service token to CE /internal")
class RestClientConfigAuthTest {

    private final RestClientConfig config = new RestClientConfig();
    private final OutboundAuth outboundAuth = new OutboundAuth(() -> Optional.of("quote-service-token"), false);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAlice() {
        Jwt jwt = Jwt.withTokenValue("alice-token").header("alg", "RS256").subject("alice")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_CPQ_USER")));
    }

    private static void expectBearer(RestClient client, String url, String path, String token) {
        RestClient.Builder builder = client.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(url + path)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(withSuccess());
        builder.build().post().uri(path).retrieve().toBodilessEntity();
        server.verify();
    }

    @Test
    @DisplayName("pricing and approval-routing get the user's token")
    void relaysToApiV1() {
        signInAlice();

        expectBearer(config.pricingRestClient("http://pricing.test", outboundAuth),
                "http://pricing.test", "/api/v1/pricing/execute", "alice-token");
        expectBearer(config.approvalRoutingRestClient("http://routing.test", outboundAuth),
                "http://routing.test", "/api/v1/approvals", "alice-token");
        expectBearer(config.assetReadRestClient("http://cam.test", outboundAuth),
                "http://cam.test", "/api/v1/assets", "alice-token");
    }

    @Test
    @DisplayName("configuration-engine /internal evaluate gets quote-service's token, not the user's")
    void serviceTokenToInternal() {
        signInAlice();

        expectBearer(config.configurationEngineRestClient("http://ce.test", outboundAuth),
                "http://ce.test", "/internal/v1/configurations/evaluate", "quote-service-token");
    }
}
