package com.opencrm.crm.duplicate;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.organization.OrganizationRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DuplicateCandidateServiceSecurityTest {
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final ContactRepository contacts = mock(ContactRepository.class);
    private final DuplicateCandidateService candidates =
            new DuplicateCandidateService(organizations, contacts, new DuplicateCandidateAccess());

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void directCallsRejectAnonymousOrdinaryAndAppOnlyCallersBeforeRepositoryReads() {
        assertDenied(HttpStatus.FORBIDDEN, "DUPLICATE_CANDIDATES_FORBIDDEN");
        authenticate(false, false);
        assertDenied(HttpStatus.FORBIDDEN, "DUPLICATE_CANDIDATES_FORBIDDEN");
        authenticate(true, true);
        assertDenied(HttpStatus.FORBIDDEN, "DUPLICATE_CANDIDATES_FORBIDDEN");
        verifyNoInteractions(organizations, contacts);
    }

    @Test
    void directCallsHoldAuthorizedHumanBeforeRepositoryReads() {
        authenticate(false, true);
        assertDenied(HttpStatus.SERVICE_UNAVAILABLE, "DUPLICATE_CANDIDATES_HOLD");
        verifyNoInteractions(organizations, contacts);
    }

    private void assertDenied(HttpStatus status, String code) {
        assertThatThrownBy(() -> candidates.organizationCandidates(7L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getErrorCode()).isEqualTo(code);
                });
        assertThatThrownBy(() -> candidates.contactCandidates(4L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getErrorCode()).isEqualTo(code);
                });
    }

    private static void authenticate(boolean appOnly, boolean role) {
        var builder = Jwt.withTokenValue("test-only").header("alg", "none").subject("actor");
        if (appOnly) {
            builder.claim("idtyp", "app");
        } else {
            builder.claim("scp", "access_as_user");
        }
        var authorities = role ? List.of(new SimpleGrantedAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE"))
                : List.<SimpleGrantedAuthority>of();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }
}
