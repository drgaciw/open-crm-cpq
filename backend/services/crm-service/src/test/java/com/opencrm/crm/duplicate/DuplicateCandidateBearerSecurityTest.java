package com.opencrm.crm.duplicate;

import com.opencrm.crm.config.GlobalExceptionHandler;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.contact.ContactStatus;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = DuplicateCandidateBearerSecurityTest.App.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
class DuplicateCandidateBearerSecurityTest {
    private static final String[] PATHS = {
            "/api/v1/organizations/7/duplicate-candidates",
            "/api/v1/contacts/4/duplicate-candidates"
    };

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, GlobalExceptionHandler.class})
    static class App {
        @Bean OrganizationRepository organizations() { return mock(OrganizationRepository.class); }
        @Bean ContactRepository contacts() { return mock(ContactRepository.class); }
        @Bean DuplicateCandidateAccess access() { return new DuplicateCandidateAccess(); }
        @Bean DuplicateCandidateService candidates(OrganizationRepository organizations, ContactRepository contacts,
                                                    DuplicateCandidateAccess access) {
            return spy(new DuplicateCandidateService(organizations, contacts, access));
        }
        @Bean DuplicateCandidateController controller(DuplicateCandidateService candidates, DuplicateCandidateAccess access) {
            return new DuplicateCandidateController(candidates, access);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired OrganizationRepository organizations;
    @Autowired ContactRepository contacts;
    @Autowired DuplicateCandidateService candidates;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(organizations, contacts, candidates);
        when(organizations.findById(7L)).thenReturn(Optional.of(
                Organization.builder().id(7L).websiteDomain("acme.com").build()));
        when(contacts.findById(4L)).thenReturn(Optional.of(
                Contact.builder().id(4L).status(ContactStatus.ACTIVE).email("alice@acme.com").build()));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void anonymousCallersAreRejectedByTheRealSecurityChain() throws Exception {
        for (String path : PATHS) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(candidates, organizations, contacts);
    }

    @Test
    void forgedAndWrongAudienceTokensAreRejectedBeforeRepositoryReads() throws Exception {
        for (String path : PATHS) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,
                    TestTokens.user("alice", "SalesOps.AccountMerge").audience("api://wrong").bearer()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,
                    TestTokens.user("alice", "SalesOps.AccountMerge").signedByForeignKey().bearer()))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(candidates, organizations, contacts);
    }

    @Test
    void ordinaryAndAppOnlyCallersCannotReadCandidates() throws Exception {
        for (String path : PATHS) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,
                    TestTokens.user("alice", "CPQ.User").bearer())).andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,
                    TestTokens.service("automation", "SalesOps.AccountMerge").bearer()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(candidates, organizations, contacts);
    }

    @Test
    void authorizedHumanIsHeldUntilTenantAndAccountScopeIsImplemented() throws Exception {
        for (String path : PATHS) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,
                    TestTokens.user("alice", "SalesOps.AccountMerge").bearer()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("DUPLICATE_CANDIDATES_HOLD"));
        }
        verifyNoInteractions(candidates, organizations, contacts);
    }
}
