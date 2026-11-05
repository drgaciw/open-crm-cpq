package com.opencrm.quote.snapshot;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AcceptedSnapshotControllerTest {
    final AcceptedSnapshotRepository repository = mock(AcceptedSnapshotRepository.class);
    final OmsQuoteProperties properties = new OmsQuoteProperties();
    final AcceptedSnapshotController controller = new AcceptedSnapshotController(repository,properties);
    JwtAuthenticationToken auth(String permission, List<String> scopes, List<String> accounts) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","test").claim("permissions", List.of(permission))
            .claim("scope_ids",scopes).claim("account_ids",accounts).build(), List.of());
    }
    @Test void deniesAnonymousAndNonJwtEvenWhenDevWebSecurityPermitsPublicApis() {
        assertThatThrownBy(() -> controller.get("s","q",1,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.get("s","q",1,UsernamePasswordAuthenticationToken.authenticated("u","p",List.of())))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(repository);
    }
    @Test void deniesMissingServicePermissionAndScopeBeforeReadingRepository() {
        properties.setScopeId("pilot");
        assertThatThrownBy(() -> controller.get("s","q",1,auth("oms:read",List.of("pilot"),List.of("a")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.get("s","q",1,auth("oms:snapshot:read",List.of("other"),List.of("a")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(repository);
    }
    @Test void lookupBindsAliasRevisionAndSnapshotAndReturnsVerbatimAfterIntakeDisabled() {
        properties.setScopeId("pilot");
        var stored = AcceptedSnapshot.builder().scopeId("pilot").customerId("a").payload("{  \"unaltered\":true }").snapshotHash("hash").holds("[]").build();
        when(repository.lookup("s","Q-001",2)).thenReturn(Optional.of(stored));
        var result = controller.get("s","Q-001",2,auth("oms:snapshot:read",List.of("pilot"),List.of("a")));
        assertThat(result.getData().payload()).isEqualTo(stored.getPayload());
        assertThat(result.getData().snapshotHash()).isEqualTo("hash");
    }
    @Test void otherAccountGetsNonDisclosingNotFound() {
        properties.setScopeId("pilot");
        when(repository.lookup("s","q",1)).thenReturn(Optional.of(AcceptedSnapshot.builder().scopeId("pilot").customerId("secret").build()));
        assertThatThrownBy(() -> controller.get("s","q",1,auth("oms:snapshot:read",List.of("pilot"),List.of("a"))))
            .isInstanceOf(com.opencrm.common.exception.ResourceNotFoundException.class);
    }
}
