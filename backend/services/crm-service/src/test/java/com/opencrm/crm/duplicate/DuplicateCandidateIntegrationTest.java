package com.opencrm.crm.duplicate;

import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.contact.ContactStatus;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationCreateRequest;
import com.opencrm.crm.organization.OrganizationRepository;
import com.opencrm.crm.organization.OrganizationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL coverage for DRG-517 AC1–AC5. Counts only when this class actually runs.
 */
@DisplayName("DRG-517 duplicate candidate integration")
class DuplicateCandidateIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("AC1: rawWebsiteDomainStillMatchesNormalizedDomain")
    void rawWebsiteDomainStillMatchesNormalizedDomain() throws Exception {
        long raw = createViaApi("Raw Acme", "https://www.Acme.com/about", null);
        long plain = createViaApi("Plain Acme", "acme.com", null);
        long fallback = saveOrganization("Website Only", null, "https://www.Acme.com/about").getId();
        long otherDomain = saveOrganization("Other", "other.com", "https://www.acme.com").getId();

        String stored = jdbc.queryForObject(
                "select website_domain from organizations where id = ?", String.class, raw);
        assertThat(stored).isEqualTo("https://www.Acme.com/about");

        assertThat(candidateIds(organizationCandidates(raw))).containsExactly(plain, fallback);
        assertThat(candidateIds(organizationCandidates(plain))).containsExactly(raw, fallback);
        assertThat(candidateIds(organizationCandidates(fallback))).containsExactly(raw, plain);
        assertThat(candidateIds(organizationCandidates(otherDomain))).isEmpty();
        assertThat(organizationCandidates(raw).get(0).get("reason").asString()).isEqualTo("DOMAIN");
        assertThat(organizationCandidates(raw).get(0).get("matchedValue").asString()).isEqualTo("acme.com");
        assertThat(organizationCandidates(raw).get(0).get("candidateType").asString()).isEqualTo("ORGANIZATION");
    }

    @Test
    @DisplayName("AC2: freeMailOrganizationYieldsNoCandidates")
    void freeMailOrganizationYieldsNoCandidates() throws Exception {
        long gmail = saveOrganization("Gmail One", "gmail.com", null).getId();
        saveOrganization("Gmail Two", "https://www.gmail.com/about", null);
        long yahoo = saveOrganization("Yahoo", "mail.yahoo.com", null).getId();
        long notGmail = saveOrganization("Not Gmail", "notgmail.com", null).getId();
        long notGmailPeer = saveOrganization("Not Gmail Peer", "notgmail.com", null).getId();
        long company = saveOrganization("Company Gmail", "gmail.company.com", null).getId();
        long companyPeer = saveOrganization("Company Gmail Peer", "https://www.gmail.company.com/", null).getId();

        assertThat(candidateIds(organizationCandidates(gmail))).isEmpty();
        assertThat(candidateIds(organizationCandidates(yahoo))).isEmpty();
        assertThat(candidateIds(organizationCandidates(notGmail))).containsExactly(notGmailPeer);
        assertThat(candidateIds(organizationCandidates(company))).containsExactly(companyPeer);
    }

    @Test
    @DisplayName("AC3-AC4: contactEmailThenSameOrganizationName")
    void contactEmailThenSameOrganizationName() throws Exception {
        Organization acme = saveOrganization("Acme", "acme.com", null);
        Organization other = saveOrganization("Other", "other.com", null);
        Contact ann = saveContact("  Ann   Marie ", "Smith", "  Ann@Acme.com ", acme, ContactStatus.ACTIVE);
        Contact annPeer = saveContact("ann marie", "smith", "b@acme.com", acme, ContactStatus.ACTIVE);
        Contact emailPeer = saveContact("Bea", "Jones", "ann@acme.com", other, ContactStatus.ACTIVE);
        Contact plus = saveContact("Cara", "Plus", "ann+news@acme.com", acme, ContactStatus.ACTIVE);
        Contact crossName = saveContact("Ann", "Smith", "c@other.com", other, ContactStatus.ACTIVE);
        Contact gmail = saveContact("Jane", "Doe", "jane@gmail.com", acme, ContactStatus.ACTIVE);
        Contact yahoo = saveContact("Jane", "Doe", "jane@yahoo.com", other, ContactStatus.ACTIVE);

        JsonNode emailMatches = contactCandidates(ann.getId());
        assertThat(candidateIds(emailMatches)).containsExactly(emailPeer.getId());
        assertThat(emailMatches.get(0).get("reason").asString()).isEqualTo("EMAIL");
        assertThat(emailMatches.get(0).get("matchedValue").asString()).isEqualTo("ann@acme.com");
        assertThat(emailMatches.get(0).get("candidateType").asString()).isEqualTo("CONTACT");
        assertThat(candidateIds(contactCandidates(emailPeer.getId()))).containsExactly(ann.getId());

        JsonNode nameMatches = contactCandidates(annPeer.getId());
        assertThat(candidateIds(nameMatches)).containsExactly(ann.getId());
        assertThat(nameMatches.get(0).get("reason").asString()).isEqualTo("NAME_SAME_ORG");
        assertThat(nameMatches.get(0).get("matchedValue").asString()).isEqualTo("ann marie smith");

        assertThat(candidateIds(contactCandidates(plus.getId()))).isEmpty();
        assertThat(candidateIds(contactCandidates(crossName.getId()))).isEmpty();
        assertThat(candidateIds(contactCandidates(gmail.getId()))).isEmpty();
        assertThat(candidateIds(contactCandidates(yahoo.getId()))).isEmpty();
    }

    @Test
    @DisplayName("AC5: deletedOrMergedAwayAndArchivedRecordsAreNeverCandidates")
    void deletedOrMergedAwayAndArchivedRecordsAreNeverCandidates() throws Exception {
        Organization survivor = saveOrganization("Survivor", "acme.com", null);
        Organization mergedAway = saveOrganization("Merged Away", "acme.com", null);
        mergedAway.softDelete();
        organizationRepository.save(mergedAway);
        Organization activePeer = saveOrganization("Active Peer", "acme.com", null);

        assertThat(candidateIds(organizationCandidates(survivor.getId()))).containsExactly(activePeer.getId());
        mockMvc.perform(get("/api/v1/organizations/" + mergedAway.getId() + "/duplicate-candidates"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        Organization acme = saveOrganization("People", "people.example", null);
        Contact source = saveContact("Ann", "Smith", "ann@people.example", acme, ContactStatus.ACTIVE);
        Contact deleted = saveContact("Bea", "Deleted", "ann@people.example", acme, ContactStatus.ACTIVE);
        deleted.softDelete();
        contactRepository.save(deleted);
        Contact archived = saveContact("Cara", "Archived", "ann@people.example", acme, ContactStatus.ARCHIVED);
        Contact inactive = saveContact("Dee", "Inactive", "ann@people.example", acme, ContactStatus.INACTIVE);
        Contact live = saveContact("Eve", "Live", "ann@people.example", acme, ContactStatus.ACTIVE);

        assertThat(candidateIds(contactCandidates(source.getId()))).containsExactly(live.getId());
        assertThat(candidateIds(contactCandidates(source.getId()))).doesNotContain(
                deleted.getId(), archived.getId(), inactive.getId());
        mockMvc.perform(get("/api/v1/contacts/" + deleted.getId() + "/duplicate-candidates"))
                .andExpect(status().isNotFound());
        assertThat(candidateIds(contactCandidates(archived.getId()))).isEmpty();
    }

    @Test
    @DisplayName("AC5: unknownIdIs404EmptyListIs200AndSourceIsExcluded")
    void unknownIdIs404EmptyListIs200AndSourceIsExcluded() throws Exception {
        Organization alone = saveOrganization("Alone", "alone.example", null);
        JsonNode empty = readOk("/api/v1/organizations/" + alone.getId() + "/duplicate-candidates");
        assertThat(empty.get("success").asBoolean()).isTrue();
        assertThat(empty.get("data").isEmpty()).isTrue();

        mockMvc.perform(get("/api/v1/organizations/999999/duplicate-candidates"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/contacts/999999/duplicate-candidates"))
                .andExpect(status().isNotFound());

        Organization peer = saveOrganization("Peer", "alone.example", null);
        assertThat(candidateIds(organizationCandidates(alone.getId()))).containsExactly(peer.getId());
        assertThat(candidateIds(organizationCandidates(alone.getId()))).doesNotContain(alone.getId());
    }

    @Test
    @DisplayName("AC5: suggestionCallLeavesRowsAndOutboxUnchanged")
    void suggestionCallLeavesRowsAndOutboxUnchanged() throws Exception {
        Organization left = saveOrganization("Left", "stable.example", null);
        Organization right = saveOrganization("Right", "stable.example", null);
        long organizations = count("organizations");
        long contacts = count("contacts");
        long outbox = count("outbox_events");
        long merges = count("account_merges");
        Long version = jdbc.queryForObject("select version from organizations where id = ?", Long.class, left.getId());
        Boolean deleted = jdbc.queryForObject("select is_deleted from organizations where id = ?", Boolean.class, left.getId());
        String domain = jdbc.queryForObject(
                "select website_domain from organizations where id = ?", String.class, left.getId());

        assertThat(candidateIds(organizationCandidates(left.getId()))).containsExactly(right.getId());

        assertThat(count("organizations")).isEqualTo(organizations);
        assertThat(count("contacts")).isEqualTo(contacts);
        assertThat(count("outbox_events")).isEqualTo(outbox);
        assertThat(count("account_merges")).isEqualTo(merges);
        assertThat(jdbc.queryForObject("select version from organizations where id = ?", Long.class, left.getId()))
                .isEqualTo(version);
        assertThat(jdbc.queryForObject("select is_deleted from organizations where id = ?", Boolean.class, left.getId()))
                .isEqualTo(deleted);
        assertThat(jdbc.queryForObject("select website_domain from organizations where id = ?", String.class, left.getId()))
                .isEqualTo(domain);
    }

    @Test
    @DisplayName("AC5: candidateQueryIsCappedAtFifty")
    void candidateQueryIsCappedAtFifty() throws Exception {
        Organization source = saveOrganization("Source", "cap.example", null);
        List<Long> others = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            others.add(saveOrganization("Cap " + i, "cap.example", null).getId());
        }

        List<Long> ids = candidateIds(organizationCandidates(source.getId()));
        assertThat(ids).hasSize(DuplicateCandidateService.MAX_CANDIDATES);
        assertThat(ids).containsExactlyElementsOf(others.subList(0, 50));
        assertThat(ids).doesNotContain(others.get(50));
        assertThat(ids).doesNotContain(source.getId());
    }

    private long createViaApi(String name, String websiteDomain, String website) throws Exception {
        OrganizationCreateRequest request = OrganizationCreateRequest.builder()
                .name(name)
                .websiteDomain(websiteDomain)
                .website(website)
                .type(OrganizationType.PROSPECT)
                .build();
        String body = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private Organization saveOrganization(String name, String websiteDomain, String website) {
        return organizationRepository.save(Organization.builder()
                .name(name)
                .websiteDomain(websiteDomain)
                .website(website)
                .type(OrganizationType.PROSPECT)
                .deleted(false)
                .build());
    }

    private Contact saveContact(String first, String last, String email, Organization organization, ContactStatus status) {
        return contactRepository.save(Contact.builder()
                .firstName(first)
                .lastName(last)
                .email(email)
                .organization(organization)
                .status(status)
                .deleted(false)
                .build());
    }

    private JsonNode organizationCandidates(long id) throws Exception {
        return readOk("/api/v1/organizations/" + id + "/duplicate-candidates").get("data");
    }

    private JsonNode contactCandidates(long id) throws Exception {
        return readOk("/api/v1/contacts/" + id + "/duplicate-candidates").get("data");
    }

    private JsonNode readOk(String path) throws Exception {
        String body = mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static List<Long> candidateIds(JsonNode data) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : data) {
            ids.add(node.get("candidateId").asLong());
        }
        return ids;
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0L : count;
    }
}
