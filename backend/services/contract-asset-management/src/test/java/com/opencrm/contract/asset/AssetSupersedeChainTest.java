package com.opencrm.contract.asset;

import com.opencrm.contract.asset.fixtures.AssetSupersededFixture;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** A three-link supersede chain is walked in both directions from any asset. */
class AssetSupersedeChainTest {

    private static final String A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2";
    private static final String C = "cccccccc-cccc-4ccc-8ccc-ccccccccccc3";
    private static final String ACCOUNT = "cust-200";

    @Test
    void chainWalksPredecessorsAndSuccessorsFromAnyAsset() throws Exception {
        AssetHistoryProjection projection = new AssetHistoryProjection(new InMemoryAssetHistoryStore());
        AssetQueryController api = new AssetQueryController(projection);

        GetMapping mapping = AssetQueryController.class
                .getMethod("supersedeChain", String.class, String.class)
                .getAnnotation(GetMapping.class);
        assertThat(mapping.value()).containsExactly("/{assetId}/supersede-chain");
        RequestParam direction = AssetQueryController.class
                .getMethod("supersedeChain", String.class, String.class)
                .getParameters()[1].getAnnotation(RequestParam.class);
        assertThat(direction.name()).isEqualTo("direction");
        assertThat(direction.defaultValue()).isEqualTo("both");

        projection.apply(AssetSupersededFixture.of(
                "s1", A, ACCOUNT, LocalDate.parse("2026-03-01"), "amd-a", "1", B));
        projection.apply(AssetSupersededFixture.of(
                "s2", B, ACCOUNT, LocalDate.parse("2026-06-01"), "amd-b", "1", C));

        SupersedeChainDto fromB = api.supersedeChain(B, "both").getData();
        assertThat(fromB.getAssetId()).isEqualTo(B);
        assertThat(fromB.getDirection()).isEqualTo("both");
        assertThat(hops(fromB.getPredecessors())).containsExactly(tuple(A, LocalDate.parse("2026-03-01"), 1));
        assertThat(hops(fromB.getSuccessors())).containsExactly(tuple(C, LocalDate.parse("2026-06-01"), 1));

        SupersedeChainDto fromA = api.supersedeChain(A, "successors").getData();
        assertThat(fromA.getDirection()).isEqualTo("successors");
        assertThat(fromA.getPredecessors()).isEmpty();
        assertThat(hops(fromA.getSuccessors())).containsExactly(
                tuple(B, LocalDate.parse("2026-03-01"), 1),
                tuple(C, LocalDate.parse("2026-06-01"), 2));

        SupersedeChainDto fromC = api.supersedeChain(C, "predecessors").getData();
        assertThat(fromC.getDirection()).isEqualTo("predecessors");
        assertThat(fromC.getSuccessors()).isEmpty();
        assertThat(hops(fromC.getPredecessors())).containsExactly(
                tuple(B, LocalDate.parse("2026-06-01"), 1),
                tuple(A, LocalDate.parse("2026-03-01"), 2));

        SupersedeChainDto defaults = api.supersedeChain(B, null).getData();
        assertThat(defaults.getDirection()).isEqualTo("both");
        assertThat(defaults.getPredecessors()).extracting(SupersedeHopDto::getAssetId).containsExactly(A);
        assertThat(defaults.getSuccessors()).extracting(SupersedeHopDto::getAssetId).containsExactly(C);
    }

    private static List<Tuple> hops(List<SupersedeHopDto> hops) {
        return hops.stream()
                .map(hop -> tuple(hop.getAssetId(), hop.getEffectiveDate(), hop.getDistance()))
                .toList();
    }
}
