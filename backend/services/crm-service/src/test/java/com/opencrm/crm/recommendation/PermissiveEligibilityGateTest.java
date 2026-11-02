package com.opencrm.crm.recommendation;

import com.opencrm.crm.deal.DealType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("PermissiveEligibilityGate")
class PermissiveEligibilityGateTest {

    private final RecommendationSubject subject = new RecommendationSubject(3L, 7L, "Software", DealType.NEW_BUSINESS);

    @Test
    @DisplayName("rejects nothing when no catalog client is configured")
    void rejectsNothingWithoutAClient() {
        ObjectProvider<CatalogEligibilityClient> clients = provider(null);

        Set<String> rejected = new PermissiveEligibilityGate(clients)
                .ineligibleProductIds(subject, List.of("addon-analytics", "services-onboarding"));

        assertThat(rejected).isEmpty();
    }

    @Test
    @DisplayName("rejects only candidate ids the catalog client marked")
    void rejectsOnlyWhatTheClientMarked() {
        CatalogEligibilityClient client = mock(CatalogEligibilityClient.class);
        Set<String> marked = new HashSet<>();
        marked.add("addon-analytics");
        marked.add("not-a-candidate");
        marked.add(null);
        when(client.ineligibleProductIds(subject, List.of("addon-analytics", "services-onboarding")))
                .thenReturn(marked);

        Set<String> rejected = new PermissiveEligibilityGate(provider(client))
                .ineligibleProductIds(subject, List.of("addon-analytics", "services-onboarding"));

        assertThat(rejected).containsExactly("addon-analytics");
    }

    @Test
    @DisplayName("does not ask the client when there are no candidates")
    void emptyCandidatesSkipTheClient() {
        CatalogEligibilityClient client = mock(CatalogEligibilityClient.class);

        assertThat(new PermissiveEligibilityGate(provider(client)).ineligibleProductIds(subject, List.of())).isEmpty();

        verifyNoInteractions(client);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<CatalogEligibilityClient> provider(CatalogEligibilityClient client) {
        ObjectProvider<CatalogEligibilityClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }
}
