package com.opencrm.crm.lead.scoring;

import com.opencrm.crm.lead.Lead;
import com.opencrm.crm.lead.LeadRepository;
import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.answer.JevAnswer;
import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.jev.testing.FakeJevTransport;
import com.opencrm.jev.transport.NoOpJevTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeadScoreAdvisor")
class LeadScoreAdvisorTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Expected level 0.1*2 + 0.6*3 + 0.3*4 = 3.2 of 4 -> 80. */
    private static final String LIKELY = """
            {"qualify":{"type":"score","score":3.2,"confidence":0.75,
                        "probabilities":{"0":0.0,"1":0.0,"2":0.1,"3":0.6,"4":0.3}}}""";
    private static final String UNSURE = """
            {"qualify":{"type":"score","score":2.0,"confidence":0.3,
                        "probabilities":{"0":0.2,"1":0.2,"2":0.2,"3":0.2,"4":0.2}}}""";

    @Mock
    private JevAdvisoryRunner runner;
    @Mock
    private LeadRepository leadRepository;

    private final List<JevDecisionRecord> logged = new ArrayList<>();
    private final JevDecisionLogStore store = logged::add;

    @BeforeEach
    void runInline() {
        lenient().doAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return null;
        }).when(runner).submitAfterCommit(anyString(), any());
    }

    private static Lead savedLead() {
        Lead lead = Lead.builder().title("Private title").source("web").build();
        lead.setId(11L);
        return lead;
    }

    private static JevProperties properties(boolean useCaseEnabled) {
        JevProperties properties = new JevProperties();
        properties.setEnabled(true);
        properties.setModel("typesafe/jev-1.13");
        properties.getLeadScore().setEnabled(useCaseEnabled);
        return properties;
    }

    private LeadScoreAdvisor advisor(FakeJevTransport transport, boolean flag) {
        return new LeadScoreAdvisor(new JevClient(properties(flag), transport), runner, store, leadRepository,
                flag, CLOCK);
    }

    @Test
    @DisplayName("flag off: nothing is scheduled, sent, logged or written")
    void flagOffDoesNothing() {
        FakeJevTransport transport = new FakeJevTransport();

        advisor(transport, false).requestScore(savedLead());

        verifyNoInteractions(runner, leadRepository);
        assertThat(transport.requests()).isEmpty();
        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("no-op transport: nothing happens even with the flag on")
    void noOpTransportDoesNothing() {
        new LeadScoreAdvisor(new JevClient(properties(true), new NoOpJevTransport()), runner, store,
                leadRepository, true, CLOCK).requestScore(savedLead());

        verifyNoInteractions(runner, leadRepository);
    }

    @Test
    @DisplayName("an unsaved lead is ignored")
    void unsavedLeadIsIgnored() {
        advisor(new FakeJevTransport(), true).requestScore(Lead.builder().title("x").build());

        verifyNoInteractions(runner, leadRepository);
    }

    @Test
    @DisplayName("a confident answer is written as a 0-100 score with its provenance")
    void writesScore() {
        advisor(new FakeJevTransport().reply(LIKELY), true).requestScore(savedLead());

        verify(leadRepository).applyAdvisoryScore(eq(11L), eq(80), eq("JEV"), eq(new BigDecimal("0.7500")),
                eq("{\"0\":0.0,\"1\":0.0,\"2\":0.1,\"3\":0.6,\"4\":0.3}"), eq("lead-score-v1"),
                eq("typesafe/jev-1.13"), eq(NOW));
        assertThat(logged).singleElement().satisfies(row -> {
            assertThat(row.subjectType()).isEqualTo("lead");
            assertThat(row.subjectId()).isEqualTo("11");
            assertThat(row.status()).isEqualTo("OK");
            assertThat(row.authoritativeSource()).isEqualTo(JevDecisionRecord.AuthoritativeSource.NONE);
            assertThat(row.agreement()).isNull();
            assertThat(row.featuresJson()).doesNotContain("Private title");
        });
    }

    @Test
    @DisplayName("below minimum confidence: logged as ABSTAINED, score left untouched")
    void abstains() {
        advisor(new FakeJevTransport().reply(UNSURE), true).requestScore(savedLead());

        verifyNoInteractions(leadRepository);
        assertThat(logged).singleElement().satisfies(row -> assertThat(row.status()).isEqualTo("ABSTAINED"));
    }

    @Test
    @DisplayName("a Jev failure is logged and the score left untouched")
    void failureLeavesScoreAlone() {
        advisor(new FakeJevTransport().fail(new IllegalStateException("down")), true).requestScore(savedLead());

        verifyNoInteractions(leadRepository);
        assertThat(logged).singleElement().satisfies(row -> assertThat(row.status()).isEqualTo("ERROR"));
    }

    @Test
    @DisplayName("never throws into LeadService")
    void neverThrows() {
        doThrow(new IllegalStateException("executor gone")).when(runner).submitAfterCommit(anyString(), any());

        assertThatCode(() -> advisor(new FakeJevTransport(), true).requestScore(savedLead()))
                .doesNotThrowAnyException();
        assertThatCode(() -> advisor(new FakeJevTransport(), true).requestScore(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("score is the expected rubric level scaled to 0-100")
    void scoreScaling() {
        assertThat(LeadScoreAdvisor.toScore(new JevAnswer.Score(0, 1, List.of(1.0, 0.0, 0.0, 0.0, 0.0)))).isZero();
        assertThat(LeadScoreAdvisor.toScore(new JevAnswer.Score(4, 1, List.of(0.0, 0.0, 0.0, 0.0, 1.0)))).isEqualTo(100);
        assertThat(LeadScoreAdvisor.toScore(new JevAnswer.Score(2, 1, List.of(0.0, 0.0, 1.0, 0.0, 0.0)))).isEqualTo(50);
    }
}
