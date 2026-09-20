package com.opencrm.ai.selection;

import static com.opencrm.ai.selection.SelectionTypes.*;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelectionEngineTest {
    private final Instant now = Instant.parse("2026-09-19T12:00:00Z");
    private final SelectionEngine engine = new SelectionEngine();
    private final TaskProfile profile = new TaskProfile("cpq-advice", "1", Set.of("response_format"),
            1000, 500, d("0.8"), d("0.9"), 2000, d("0.1"), Set.of("allowed"), 2,
            Duration.ofHours(24), Duration.ofHours(1));

    @Test void cheaperUnqualifiedCandidateCannotWin() {
        var cheap = endpoint("cheap"); var qualified = endpoint("qualified");
        var report = select(List.of(cheap, qualified), List.of(evidence(cheap, "0.0001", "0.2", true),
                evidence(qualified, "0.02", "0.95", true)));
        assertThat(report.proposedPolicy().routes()).extracting(r -> r.endpoint().modelId()).containsExactly("qualified");
        assertThat(report.rejected()).containsEntry(cheap.key(), "QUALITY_OR_LATENCY");
    }

    @Test void measuredTotalIncludesFailuresAndOverheadAndReplaysDeterministically() {
        var a = endpoint("a"); var b = endpoint("b");
        var evidence = List.of(evidence(a, "0.06", "0.95", true), evidence(b, "0.04", "0.95", true));
        var first = select(List.of(a,b), evidence);
        assertThat(first.proposedPolicy().routes().getFirst().endpoint()).isEqualTo(b);
        assertThat(first.proposedPolicy().routes().getFirst().expectedCostPerSuccess()).isEqualByComparingTo("0.02");
        assertThat(select(List.of(a,b), evidence)).isEqualTo(first);
    }

    @Test void incompleteCoverageCannotPublishWinnerAndZeroSuccessesCannotWin() {
        var e = endpoint("a");
        assertThat(engine.select(profile, catalog(List.of(e)), List.of(), now,"p1",null,false).proposedPolicy()).isNull();
        var failed = new EvaluationEvidence(e.key(), profile.id(), profile.version(), "catalog-1", "b1", 2,0,
                d("0.01"), d("1"),100,true,now,verdict(true));
        assertThat(select(List.of(e), List.of(failed)).proposedPolicy()).isNull();
    }

    @Test void tiesUseQualityLatencyThenStableModelId() {
        var a = endpoint("a"); var b = endpoint("b");
        assertThat(select(List.of(b,a),List.of(evidence(b,"0.02","0.95",true),evidence(a,"0.02","0.95",true)))
                .proposedPolicy().routes().getFirst().endpoint()).isEqualTo(a);
    }

    @Test void missingPricesAndStaleCatalogRejectWhileExplicitZeroIsValid() {
        var missing = new Endpoint("missing","allowed",Set.of("response_format"),2000,500,
                new PriceRates(null,d("0"),d("0"),null,null),true);
        assertThat(select(List.of(missing),List.of(evidence(missing,"0.02","0.95",true))).proposedPolicy()).isNull();
        var free = endpoint("free");
        assertThat(select(List.of(free), List.of(evidence(free,"0","0.95",true))).proposedPolicy()).isNotNull();
        assertThat(engine.select(profile,catalog(List.of(free)),List.of(evidence(free,"0","0.95",true)),
                now.plusSeconds(3600),"p1",null,true).proposedPolicy()).isNull();
    }

    @Test void wrongVersionUnknownProviderAndJevRejectionFailClosed() {
        var e = endpoint("a");
        var wrong = new EvaluationEvidence(e.key(),profile.id(),"old","catalog-1","b1",2,2,
                d("0.02"),d("0.95"),100,true,now,verdict(true));
        assertThat(select(List.of(e),List.of(wrong)).rejected()).containsEntry(e.key(),"EVIDENCE_VERSION_MISMATCH");
        assertThat(select(List.of(e),List.of(evidence(e,"0.02","0.95",false))).proposedPolicy()).isNull();
        var restricted = new Endpoint("a","blocked",e.supportedParameters(),2000,500,e.prices(),true);
        assertThat(SelectionEngine.eligible(restricted,profile,catalog(List.of(restricted)),now)).isFalse();
    }

    @Test void malformedDistributionAndContradictoryDecisionCannotBeConstructed() {
        assertThatThrownBy(() -> new JevVerdict(true,d("1"),Map.of("suitable",d("0.2"),"unsuitable",d("0.8")),
                "jev-1","rubric-1",List.of("b1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JevVerdict(true,d("1"),Map.of("unknown",d("1")),
                "jev-1","rubric-1",List.of("b1"))).isInstanceOf(IllegalArgumentException.class);
    }

    private SelectionReport select(List<Endpoint> endpoints, List<EvaluationEvidence> evidence) {
        return engine.select(profile,catalog(endpoints),evidence,now,"p1",null,true);
    }
    private CatalogSnapshot catalog(List<Endpoint> endpoints) {
        return new CatalogSnapshot("catalog-1",now.minusSeconds(1),now.plusSeconds(3600),endpoints,"hash");
    }
    private Endpoint endpoint(String name) {
        return new Endpoint(name,"allowed",Set.of("response_format"),2000,500,new PriceRates(d("0"),d("0"),d("0"),null,null),true);
    }
    private EvaluationEvidence evidence(Endpoint endpoint,String cost,String quality,boolean suitable) {
        return new EvaluationEvidence(endpoint.key(),profile.id(),profile.version(),"catalog-1","b1",3,2,
                d(cost),d(quality),100,true,now,verdict(suitable));
    }
    private JevVerdict verdict(boolean suitable) {
        return new JevVerdict(suitable,d("0.95"),Map.of("suitable",d(suitable?"0.95":"0.05"),
                "unsuitable",d(suitable?"0.05":"0.95")),"jev-1","rubric-1",List.of("b1"));
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
