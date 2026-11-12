package com.opencrm.crm.fact;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FactDispositionDeciderTest {

    @Test
    void verifiedAgentAppliesWhenAPrimarySourceClearsTheThreshold() {
        FactDisposition disposition = decide("0.95", true, false, false, FactPrincipalKind.AGENT, false);

        assertThat(disposition.stored()).isTrue();
        assertThat(disposition.status()).isEqualTo(FactStatus.APPLIED);
        assertThat(disposition.band()).isEqualTo(FactBand.VERIFIED);
        assertThat(disposition.writeRecord()).isTrue();
    }

    @Test
    void aHighScoreWithoutAPrimarySourceStaysAProposal() {
        FactDisposition disposition = decide("0.95", false, false, false, FactPrincipalKind.AGENT, false);

        assertThat(disposition.status()).isEqualTo(FactStatus.PROPOSED);
        assertThat(disposition.band()).isEqualTo(FactBand.PROBABLE);
        assertThat(disposition.writeRecord()).isFalse();
    }

    @Test
    void probableIsAProposalAndPossibleIsProvenanceOnly() {
        FactDisposition probable = decide("0.80", true, false, false, FactPrincipalKind.AGENT, false);
        assertThat(probable.status()).isEqualTo(FactStatus.PROPOSED);
        assertThat(probable.band()).isEqualTo(FactBand.PROBABLE);

        FactDisposition possible = decide("0.40", false, false, false, FactPrincipalKind.AGENT, false);
        assertThat(possible.status()).isEqualTo(FactStatus.PROVENANCE);
        assertThat(possible.band()).isEqualTo(FactBand.POSSIBLE);
        assertThat(possible.writeRecord()).isFalse();
        assertThat(possible.status().shownInInbox()).isFalse();
    }

    @Test
    void aScoreBelowThirtyHundredthsIsNotStored() {
        FactDisposition disposition = decide("0.29", false, false, false, FactPrincipalKind.AGENT, false);

        assertThat(disposition.stored()).isFalse();
        assertThat(disposition.writeRecord()).isFalse();
    }

    @Test
    void sensitiveFieldsNeedOneBandHigher() {
        FactDisposition wouldHaveBeenVerified = decide("0.95", true, true, false, FactPrincipalKind.AGENT, false);
        assertThat(wouldHaveBeenVerified.status()).isEqualTo(FactStatus.PROPOSED);
        assertThat(wouldHaveBeenVerified.band()).isEqualTo(FactBand.PROBABLE);
        assertThat(wouldHaveBeenVerified.writeRecord()).isFalse();

        FactDisposition wouldHaveBeenProbable = decide("0.80", true, true, false, FactPrincipalKind.AGENT, false);
        assertThat(wouldHaveBeenProbable.status()).isEqualTo(FactStatus.PROVENANCE);

        FactDisposition wouldHaveBeenPossible = decide("0.40", false, true, false, FactPrincipalKind.AGENT, false);
        assertThat(wouldHaveBeenPossible.stored()).isFalse();
    }

    @Test
    void aHumanPrincipalNeverAutoApplies() {
        FactDisposition disposition = decide("0.99", true, false, false, FactPrincipalKind.HUMAN, false);

        assertThat(disposition.status()).isEqualTo(FactStatus.PROPOSED);
        assertThat(disposition.writeRecord()).isFalse();
    }

    @Test
    void aHumanOwnedFieldIsNotOverwritten() {
        FactDisposition disposition = decide("0.95", true, false, false, FactPrincipalKind.AGENT, true);

        assertThat(disposition.status()).isEqualTo(FactStatus.PROPOSED);
        assertThat(disposition.writeRecord()).isFalse();
        assertThat(disposition.humanProtected()).isTrue();
    }

    @Test
    void contradictingEvidenceIsUnresolvedRegardlessOfScore() {
        FactDisposition strong = decide("0.99", true, true, true, FactPrincipalKind.AGENT, false);
        assertThat(strong.stored()).isTrue();
        assertThat(strong.status()).isEqualTo(FactStatus.UNRESOLVED);
        assertThat(strong.writeRecord()).isFalse();
        assertThat(strong.status().shownInInbox()).isTrue();

        FactDisposition weak = decide("0.10", false, false, true, FactPrincipalKind.AGENT, false);
        assertThat(weak.stored()).isTrue();
        assertThat(weak.status()).isEqualTo(FactStatus.UNRESOLVED);
        assertThat(weak.band()).isNull();
    }

    private static FactDisposition decide(
            String score,
            boolean primary,
            boolean sensitive,
            boolean contradicting,
            FactPrincipalKind principal,
            boolean humanOwns) {
        return FactDispositionDecider.decide(
                new BigDecimal(score),
                primary,
                sensitive,
                contradicting,
                principal,
                humanOwns);
    }
}
