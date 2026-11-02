package com.opencrm.crm.territory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DRG-483 territory and segment rules")
class TerritoryAssignmentRulesTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("selections")
    void selectsTheMatchingRule(String name, AssignmentFacts facts, List<TerritoryAssignmentRule> rules, Long expectedId) {
        Optional<TerritoryAssignmentRule> selected = TerritoryAssignmentRules.select(facts, rules);
        if (expectedId == null) {
            assertThat(selected).as(name).isEmpty();
        } else {
            assertThat(selected).as(name).map(TerritoryAssignmentRule::getId).contains(expectedId);
        }
    }

    static Stream<Arguments> selections() {
        TerritoryAssignmentRule west = rule(1L, "US", "CA", "Software", 100, 500, "WEST", "MID_MARKET", 100);
        TerritoryAssignmentRule national = rule(2L, "US", null, "Software", 1, 10000, "NATIONAL", "COMMERCIAL", 100);
        TerritoryAssignmentRule enterprise = rule(3L, "US", "CA", "Software", 1000, 50000, "WEST", "ENTERPRISE", 100);
        TerritoryAssignmentRule manufacturing = rule(4L, "US", "CA", "Manufacturing", 100, 500, "WEST", "INDUSTRIAL", 100);
        TerritoryAssignmentRule priority = rule(5L, "US", null, "Software", 1, 100000, "PRIORITY", "STRATEGIC", 1);
        TerritoryAssignmentRule narrow = rule(6L, "US", "CA", "Software", 180, 220, "BAY", "GROWTH", 100);
        TerritoryAssignmentRule laterId = rule(8L, "DE", null, "Finance", 10, 20, "DACH", "B", 50);
        TerritoryAssignmentRule earlierId = rule(7L, "DE", null, "Finance", 10, 20, "DACH", "A", 50);
        TerritoryAssignmentRule inactive = rule(9L, "US", "CA", "Software", 100, 500, "WEST", "INACTIVE", 1);
        inactive.setActive(false);
        TerritoryAssignmentRule deleted = rule(10L, "US", "CA", "Software", 100, 500, "WEST", "DELETED", 1);
        deleted.setDeleted(true);
        TerritoryAssignmentRule anySize = rule(11L, "FR", null, "Retail", null, null, "FRANCE", "RETAIL", 100);
        TerritoryAssignmentRule openMax = rule(12L, "GB", null, "Media", 50, null, "UK", "MEDIA", 100);

        AssignmentFacts california = facts("US", "CA", "Software", 200);
        return Stream.of(
                args("geography, industry, and size all match", california, List.of(west), 1L),
                args("state-specific rule beats a country-wide rule at the same priority",
                        california, List.of(national, west), 1L),
                args("lower priority wins over a more specific rule",
                        california, List.of(west, priority), 5L),
                args("narrower employee window wins when priority and geography are equal",
                        california, List.of(west, narrow), 6L),
                args("lower id wins when the rules are otherwise equal",
                        facts("DE", null, "Finance", 15), List.of(laterId, earlierId), 7L),
                args("industry mismatch leaves the organization unassigned",
                        california, List.of(manufacturing), null),
                args("state mismatch leaves a state-specific rule unmatched",
                        facts("US", "NY", "Software", 200), List.of(west), null),
                args("country-wide rule matches any state, including a missing state",
                        facts("US", null, "Software", 200), List.of(national), 2L),
                args("employee count below the window does not match",
                        facts("US", "CA", "Software", 99), List.of(west), null),
                args("employee count above the window does not match",
                        facts("US", "CA", "Software", 501), List.of(west), null),
                args("bounds are inclusive",
                        facts("US", "CA", "Software", 100), List.of(west), 1L),
                args("upper bound is inclusive",
                        facts("US", "CA", "Software", 500), List.of(west), 1L),
                args("missing employee count does not match",
                        facts("US", "CA", "Software", null), List.of(west, anySize), null),
                args("negative employee count does not match",
                        facts("FR", null, "Retail", -1), List.of(anySize), null),
                args("open size bounds match a known non-negative count",
                        facts("FR", "IDF", "Retail", 0), List.of(anySize), 11L),
                args("open upper bound matches at the minimum",
                        facts("GB", null, "Media", 50), List.of(openMax), 12L),
                args("open upper bound rejects below the minimum",
                        facts("GB", null, "Media", 49), List.of(openMax), null),
                args("country and industry comparison ignores case and surrounding spaces",
                        facts(" us ", "ca", " software ", 200), List.of(west), 1L),
                args("inactive and deleted rules do not match",
                        california, List.of(inactive, deleted), null),
                args("partial matches do not combine into a winner",
                        california, List.of(manufacturing, enterprise), null),
                args("no rules leaves the organization unassigned",
                        california, List.of(), null),
                args("null facts leave the organization unassigned",
                        null, List.of(west), null));
    }

    private static Arguments args(String name, AssignmentFacts facts, List<TerritoryAssignmentRule> rules,
                                  Long expectedId) {
        return Arguments.of(name, facts, rules, expectedId);
    }

    private static AssignmentFacts facts(String country, String state, String industry, Integer employeeCount) {
        return new AssignmentFacts(country, state, industry, employeeCount);
    }

    private static TerritoryAssignmentRule rule(long id, String country, String state, String industry,
                                                Integer min, Integer max, String territory, String segment,
                                                int priority) {
        TerritoryAssignmentRule rule = TerritoryAssignmentRule.builder()
                .country(country)
                .state(state)
                .industry(industry)
                .minEmployeeCount(min)
                .maxEmployeeCount(max)
                .territoryCode(territory)
                .segmentCode(segment)
                .priority(priority)
                .active(true)
                .name("rule-" + id)
                .build();
        rule.setId(id);
        rule.setDeleted(false);
        return rule;
    }
}
