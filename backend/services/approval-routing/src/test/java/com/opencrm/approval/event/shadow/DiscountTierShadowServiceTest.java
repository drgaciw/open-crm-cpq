package com.opencrm.approval.event.shadow;

import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.advisory.JevAdvisoryRunner;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscountTierShadowService")
class DiscountTierShadowServiceTest {

    private static final String DIRECTOR = """
            {"tier":{"type":"choice","choice":"DIRECTOR","confidence":0.9,
                     "probabilities":{"MANAGER":0.05,"DIRECTOR":0.9,"VP_SALES":0.05}}}""";
    private static final String VP_SALES = """
            {"tier":{"type":"choice","choice":"VP_SALES","confidence":0.6,
                     "probabilities":{"MANAGER":0.1,"DIRECTOR":0.3,"VP_SALES":0.6}}}""";

    @Mock
    private JevAdvisoryRunner runner;

    private final List<JevDecisionRecord> logged = new ArrayList<>();
    private final JevDecisionLogStore store = logged::add;

    private final DiscountApprovalRequiredEvent event = new DiscountApprovalRequiredEvent(
            "Q-1", "L-1", new BigDecimal("25"), new BigDecimal("18.5"), "USD");

    @BeforeEach
    void runInline() {
        lenient().doAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return null;
        }).when(runner).submitAfterCommit(anyString(), any());
    }

    private static JevProperties properties(boolean useCaseEnabled) {
        JevProperties properties = new JevProperties();
        properties.setEnabled(true);
        properties.setModel("typesafe/jev-1.13");
        properties.getDiscountTierShadow().setEnabled(useCaseEnabled);
        return properties;
    }

    private DiscountTierShadowService service(FakeJevTransport transport, boolean flag) {
        return new DiscountTierShadowService(new JevClient(properties(flag), transport), runner, store, flag);
    }

    @Test
    @DisplayName("flag off: nothing is scheduled, sent or logged")
    void flagOffDoesNothing() {
        FakeJevTransport transport = new FakeJevTransport();

        service(transport, false).recordAfterCommit(7L, event, "DIRECTOR");

        verifyNoInteractions(runner);
        assertThat(transport.requests()).isEmpty();
        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("no-op transport: nothing is scheduled even with the flag on")
    void noOpTransportDoesNothing() {
        new DiscountTierShadowService(new JevClient(properties(true), new NoOpJevTransport()), runner, store, true)
                .recordAfterCommit(7L, event, "DIRECTOR");

        verifyNoInteractions(runner);
        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("logs agreement with the rule and sends only the allow-listed numbers")
    void logsAgreement() {
        FakeJevTransport transport = new FakeJevTransport().reply(DIRECTOR);

        service(transport, true).recordAfterCommit(7L, event, "DIRECTOR");

        assertThat(logged).singleElement().satisfies(row -> {
            assertThat(row.useCase()).isEqualTo("discount-tier-shadow");
            assertThat(row.subjectType()).isEqualTo("approval_request");
            assertThat(row.subjectId()).isEqualTo("7");
            assertThat(row.answerValue()).isEqualTo("DIRECTOR");
            assertThat(row.authoritativeOutcome()).isEqualTo("DIRECTOR");
            assertThat(row.authoritativeSource()).isEqualTo(JevDecisionRecord.AuthoritativeSource.RULE);
            assertThat(row.agreement()).isTrue();
            assertThat(row.rubricVersion()).isEqualTo("tier-v1-thresholds");
            assertThat(row.evaluatorModel()).isEqualTo("typesafe/jev-1.13");
            assertThat(row.status()).isEqualTo("OK");
        });
        String sent = transport.requests().get(0).state().canonicalJson();
        assertThat(sent).isEqualTo("{\"currency_code\":\"USD\",\"margin_delta\":25.0,\"requested_discount\":18.5}");
        assertThat(sent).doesNotContain("Q-1").doesNotContain("L-1");
    }

    @Test
    @DisplayName("logs disagreement without touching the authoritative tier")
    void logsDisagreement() {
        service(new FakeJevTransport().reply(VP_SALES), true).recordAfterCommit(7L, event, "DIRECTOR");

        assertThat(logged).singleElement().satisfies(row -> {
            assertThat(row.answerValue()).isEqualTo("VP_SALES");
            assertThat(row.authoritativeOutcome()).isEqualTo("DIRECTOR");
            assertThat(row.agreement()).isFalse();
        });
    }

    @Test
    @DisplayName("a Jev failure is logged as a status, with no answer and no agreement")
    void logsFailure() {
        service(new FakeJevTransport().fail(new IllegalStateException("upstream down")), true)
                .recordAfterCommit(7L, event, "DIRECTOR");

        assertThat(logged).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("ERROR");
            assertThat(row.answerValue()).isNull();
            assertThat(row.agreement()).isNull();
            assertThat(row.authoritativeOutcome()).isEqualTo("DIRECTOR");
        });
    }

    @Test
    @DisplayName("never throws into the Kafka handler, even if scheduling blows up")
    void neverThrows() {
        doThrow(new IllegalStateException("executor gone")).when(runner).submitAfterCommit(anyString(), any());

        assertThatCode(() -> service(new FakeJevTransport(), true).recordAfterCommit(7L, event, "DIRECTOR"))
                .doesNotThrowAnyException();
        assertThat(logged).isEmpty();
    }
}
