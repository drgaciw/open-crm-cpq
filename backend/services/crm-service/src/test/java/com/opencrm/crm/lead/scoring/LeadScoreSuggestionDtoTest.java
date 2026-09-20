package com.opencrm.crm.lead.scoring;

import com.opencrm.crm.lead.Lead;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LeadScoreSuggestionDto")
class LeadScoreSuggestionDtoTest {

    @Test
    @DisplayName("is absent for a lead nobody scored, so the API omits the field")
    void absentWhenUnscored() {
        assertThat(LeadScoreSuggestionDto.from(Lead.builder().title("x").build())).isNull();
        assertThat(LeadScoreSuggestionDto.from(null)).isNull();
    }

    @Test
    @DisplayName("a manually set score without JEV provenance is not presented as a suggestion")
    void absentWithoutJevProvenance() {
        Lead lead = Lead.builder().title("x").score(70).build();

        assertThat(LeadScoreSuggestionDto.from(lead)).isNull();
    }

    @Test
    @DisplayName("exposes score, confidence, distribution and evaluator pins, always advisory")
    void presentWhenScoredByJev() {
        Lead lead = Lead.builder().title("x").score(80).build();
        lead.setScoreSource("JEV");
        lead.setScoreConfidence(new BigDecimal("0.7500"));
        lead.setScoreProbabilities("{\"0\":0.0,\"1\":0.0,\"2\":0.1,\"3\":0.6,\"4\":0.3}");
        lead.setScoreRubricVersion("lead-score-v1");
        lead.setScoreModel("typesafe/jev-1.13");
        lead.setScoredAt(Instant.parse("2026-09-20T12:00:00Z"));

        LeadScoreSuggestionDto dto = LeadScoreSuggestionDto.from(lead);

        assertThat(dto.value()).isEqualTo("80");
        assertThat(dto.confidence()).isEqualTo(0.75);
        assertThat(dto.probabilities()).containsEntry("3", 0.6).hasSize(5);
        assertThat(dto.rubricVersion()).isEqualTo("lead-score-v1");
        assertThat(dto.model()).isEqualTo("typesafe/jev-1.13");
        assertThat(dto.advisory()).isTrue();
    }

    @Test
    @DisplayName("unreadable stored probabilities degrade to an empty distribution, never an error")
    void toleratesCorruptProbabilities() {
        Lead lead = Lead.builder().title("x").score(80).build();
        lead.setScoreSource("JEV");
        lead.setScoreProbabilities("not json");

        assertThat(LeadScoreSuggestionDto.from(lead).probabilities()).isEmpty();
    }
}
