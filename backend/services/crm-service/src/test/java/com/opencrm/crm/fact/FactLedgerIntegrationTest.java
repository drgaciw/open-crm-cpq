package com.opencrm.crm.fact;

import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.fields.FixtureTenants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postgres path for the ledger. Docker is required; the unit profile excludes this tag.
 */
@Tag("integration")
@Tag("docker")
class FactLedgerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FactLedgerService factLedgerService;
    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private CrmFactRepository factRepository;

    @BeforeEach
    void clearFacts() {
        factRepository.deleteAll();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiedJobTitleIsAppliedWithAServerScore() {
        authenticateService();
        Contact contact = contactRepository.saveAndFlush(contact(null));

        FactResponse response = factLedgerService.propose(
                FactSubjectType.CONTACT,
                contact.getId(),
                FixtureTenants.A,
                proposal("jobTitle", "Analyst", EvidenceKind.EXACT_EMAIL_MATCH));

        assertThat(response.isStored()).isTrue();
        assertThat(response.getScore()).isEqualByComparingTo("0.9500");
        assertThat(response.getStatus()).isEqualTo(FactStatus.APPLIED);
        assertThat(response.isAppliedToRecord()).isTrue();
        Contact stored = contactRepository.findById(contact.getId()).orElseThrow();
        assertThat(stored.getJobTitle()).isEqualTo("Analyst");
        assertThat(factRepository.findById(response.getId())).isPresent();
    }

    @Test
    void aPersonWrittenTitleIsLeftInPlace() {
        authenticatePerson();
        Contact contact = contact(null);
        contact.setJobTitle("Director");
        contact = contactRepository.saveAndFlush(contact);
        authenticateService();

        FactResponse response = factLedgerService.propose(
                FactSubjectType.CONTACT,
                contact.getId(),
                FixtureTenants.A,
                proposal("jobTitle", "VP Sales", EvidenceKind.EXACT_EMAIL_MATCH));

        assertThat(response.getStatus()).isEqualTo(FactStatus.PROPOSED);
        assertThat(response.isAppliedToRecord()).isFalse();
        assertThat(contactRepository.findById(contact.getId()).orElseThrow().getJobTitle()).isEqualTo("Director");
        assertThat(factLedgerService.listProposals(FactSubjectType.CONTACT, contact.getId(), FixtureTenants.A))
                .extracting(FactResponse::getValue)
                .containsExactly("VP Sales");
    }

    private static Contact contact(String jobTitle) {
        Contact contact = new Contact();
        contact.setFirstName("Ada");
        contact.setLastName("Lovelace");
        contact.setJobTitle(jobTitle);
        return contact;
    }

    private static ProposeFactRequest proposal(String fieldKey, String value, EvidenceKind kind) {
        EvidenceRequest evidence = new EvidenceRequest();
        evidence.setKind(kind);
        evidence.setUrl("https://example.com/profile");
        ProposeFactRequest request = new ProposeFactRequest();
        request.setFieldKey(fieldKey);
        request.setValue(value);
        request.setMethod("enrichment");
        request.setEvidence(new ArrayList<>(List.of(evidence)));
        return request;
    }

    private static void authenticateService() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("enrichment-agent")
                .claim("idtyp", "app")
                .claim("azp", "enrichment-agent")
                .claim("tid", FixtureTenants.A)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static void authenticatePerson() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("person-oid")
                .claim("oid", "person-oid")
                .claim("scp", "user")
                .claim("tid", FixtureTenants.A)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
