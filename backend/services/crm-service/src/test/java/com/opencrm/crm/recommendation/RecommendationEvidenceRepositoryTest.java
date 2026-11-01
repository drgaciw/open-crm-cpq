package com.opencrm.crm.recommendation;

import com.opencrm.crm.deal.DealStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendationEvidenceRepository")
class RecommendationEvidenceRepositoryTest {

    @Test
    @DisplayName("peer wins are a static JPQL query with bound parameters")
    void peerWinQueryIsStaticJpql() throws Exception {
        Query query = RecommendationEvidenceRepository.class
                .getMethod("findPeerWins", DealStage.class, Long.class, Long.class, String.class, Pageable.class)
                .getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isFalse();
        assertThat(query.value())
                .contains("d.deleted = false")
                .contains("o.deleted = false")
                .contains(":stage")
                .contains(":organizationId")
                .contains(":dealId")
                .contains(":industry")
                .contains("order by d.id asc")
                .doesNotContain("${")
                .doesNotContain("+");
    }
}
