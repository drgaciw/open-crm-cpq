package com.opencrm.crm.merge;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountMergeSafetyTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void productionHoldRejectsAnAuthorizedHumanBeforeReadingAccounts() {
        authenticate("user", true);
        var repo = mock(com.opencrm.crm.organization.OrganizationRepository.class);
        var service = new AccountMergeService(repo, null, null, null, null, null, null, new AccountMergeReadiness(), null);
        assertThatThrownBy(() -> service.merge(AccountMergeRequest.builder().survivorId(1L).mergedAwayId(2L).build()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_MERGE_HOLD");
                });
        verifyNoInteractions(repo);
    }
    @Test void anonymousOrdinaryAndServiceActorsAreRejectedEvenIfReady() {
        var ready = mock(AccountMergeReadiness.class);
        var service = new AccountMergeService(null, null, null, null, null, null, null, ready, null);
        assertForbidden(service);
        authenticate("user", false); assertForbidden(service);
        authenticate("app", true); assertForbidden(service);
        authenticate("ambiguous", true); assertForbidden(service);
        verifyNoInteractions(ready);
    }
    private static void authenticate(String kind, boolean role) {
        var builder = Jwt.withTokenValue("test").header("alg", "none").subject("actor");
        if (kind.equals("app")) builder.claim("idtyp", "app"); else if (kind.equals("user")) builder.claim("scp", "user");
        var authorities = role ? List.of(new SimpleGrantedAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE")) : List.<SimpleGrantedAuthority>of();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }
    private static void assertForbidden(AccountMergeService service) {
        assertThatThrownBy(() -> service.merge(AccountMergeRequest.builder().survivorId(1L).mergedAwayId(2L).build()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
