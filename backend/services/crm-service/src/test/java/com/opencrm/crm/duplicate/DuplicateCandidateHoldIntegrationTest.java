package com.opencrm.crm.duplicate;

import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full CRM context and PostgreSQL; the base class disables HTTP security filters. */
class DuplicateCandidateHoldIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizedHumanIsHeldBeforeAnyCandidateCanBeReturned() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-only")
                .header("alg", "none")
                .subject("authorized-human")
                .claim("scp", "access_as_user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE"))));

        for (String path : List.of("/api/v1/organizations/999999/duplicate-candidates",
                "/api/v1/contacts/999999/duplicate-candidates")) {
            mvc.perform(get(path)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("DUPLICATE_CANDIDATES_HOLD"));
        }
    }
}
