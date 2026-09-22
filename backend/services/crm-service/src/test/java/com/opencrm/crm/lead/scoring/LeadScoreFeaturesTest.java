package com.opencrm.crm.lead.scoring;

import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.lead.Lead;
import com.opencrm.crm.lead.LeadPriority;
import com.opencrm.crm.lead.LeadStatus;
import com.opencrm.jev.state.JevState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LeadScoreFeatures (ADR-015 data minimisation)")
class LeadScoreFeaturesTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    private static Lead lead() {
        Lead lead = Lead.builder()
                .title("Jane Doe wants 400 seats")
                .description("Call Jane on +1 555 0100 about the Acme renewal")
                .source("Referral")
                .sourceDetail("Introduced by John Smith")
                .campaign("Acme ABM Q3")
                .ownerId("user-42")
                .estimatedValue(new BigDecimal("30000"))
                .currency("usd")
                .status(LeadStatus.NEW)
                .priority(LeadPriority.HIGH)
                .nextAction("Email jane.doe@acme.example")
                .nextActionDate(NOW.plus(2, ChronoUnit.DAYS))
                .lastContactedAt(NOW.minus(3, ChronoUnit.DAYS))
                .build();
        lead.setCreatedAt(NOW.minus(10, ChronoUnit.DAYS));
        lead.setContact(new Contact());
        return lead;
    }

    @Test
    @DisplayName("sends only coarse, derived features")
    void sendsDerivedFeaturesOnly() {
        JevState state = LeadScoreFeatures.state(lead(), NOW);

        assertThat(state.canonicalJson()).isEqualTo("{\"currency\":\"USD\",\"days_since_created\":10,"
                + "\"days_since_last_contacted\":3,\"estimated_value_band\":\"25K_100K\",\"has_contact\":true,"
                + "\"has_next_action_date\":true,\"has_organization\":false,\"priority\":\"HIGH\","
                + "\"source\":\"REFERRAL\",\"status\":\"NEW\"}");
    }

    @Test
    @DisplayName("no name, contact detail, free text or owner reaches the payload")
    void nothingPersonalLeaks() {
        String payload = LeadScoreFeatures.state(lead(), NOW).canonicalJson().toLowerCase();

        assertThat(payload).doesNotContain("jane", "doe", "acme", "john", "smith", "555", "@", "user-42", "seats");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(value = {
            "Referral, REFERRAL",
            "cold call, COLD_CALL",
            "cold-call, COLD_CALL",
            "Met Jane Doe at the expo, OTHER",
            "jane.doe@acme.example, OTHER",
            "'', NULL",
            "NULL, NULL"
    }, nullValues = "NULL")
    @DisplayName("free-text source maps onto a fixed vocabulary")
    void sourceVocabulary(String raw, String expected) {
        assertThat(LeadScoreFeatures.source(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(value = {
            "NULL, NONE", "0, NONE", "-5, NONE", "4999.99, LT_5K", "5000, 5K_25K", "24999, 5K_25K",
            "25000, 25K_100K", "99999.99, 25K_100K", "100000, GT_100K"
    }, nullValues = "NULL")
    @DisplayName("estimated value is banded, never sent exactly")
    void valueBands(BigDecimal value, String expected) {
        assertThat(LeadScoreFeatures.valueBand(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("unknown dates stay unknown rather than becoming zero")
    void unknownDatesAreNull() {
        Lead lead = Lead.builder().title("x").build();

        JevState state = LeadScoreFeatures.state(lead, NOW);

        assertThat(state.values()).containsEntry("days_since_created", null)
                .containsEntry("days_since_last_contacted", null)
                .containsEntry("source", null)
                .containsEntry("estimated_value_band", "NONE");
    }
}
