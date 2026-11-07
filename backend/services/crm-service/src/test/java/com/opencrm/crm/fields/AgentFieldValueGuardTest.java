package com.opencrm.crm.fields;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentFieldValueGuardTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aServiceCallerCannotWriteFieldValues() {
        authenticateService();
        CurrentActor actor = new CurrentActor("service:crm-service", true);

        assertThatThrownBy(() -> AgentFieldValueGuard.requireHumanWriter(actor))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("AGENT_FIELD_VALUE_FORBIDDEN");
                });
    }

    @Test
    void aPersonCallerCanWriteFieldValuesAndIsTheRecordedActor() {
        authenticatePerson("person-oid");
        CurrentActor actor = new CurrentActor("service:crm-service", false);

        AgentFieldValueGuard.requireHumanWriter(actor);

        assertThat(AgentFieldValueGuard.actorId(actor)).isEqualTo("person-oid");
    }

    @Test
    void jwtModeWithoutACallerIsRejectedAndDevModeWithoutACallerIsAllowed() {
        CurrentActor jwtMode = new CurrentActor("service:crm-service", false);
        assertThatThrownBy(() -> AgentFieldValueGuard.requireHumanWriter(jwtMode))
                .isInstanceOf(BusinessException.class);

        CurrentActor devMode = new CurrentActor("service:crm-service", true);
        AgentFieldValueGuard.requireHumanWriter(devMode);
        assertThat(AgentFieldValueGuard.actorId(devMode)).isEqualTo("service:crm-service");
    }

    private static void authenticatePerson(String oid) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(oid)
                .claim("oid", oid)
                .claim("scp", "user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static void authenticateService() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("agent-client")
                .claim("idtyp", "app")
                .claim("azp", "enrichment-agent")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
