package com.opencrm.pricing.ruletools;

import com.opencrm.pricing.rule.RuleDto;
import com.opencrm.pricing.rule.RuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleConflictAnalyzerTest {

    @Mock
    private RuleService ruleService;

    @InjectMocks
    private RuleConflictAnalyzer analyzer;

    @Test
    void emptyRuleSetScoresPerfectHealth() {
        when(ruleService.listByRuleSet(eq(9L), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of()));

        RuleConflictReportDto report = analyzer.analyze(9L);

        assertThat(report.getTotalRules()).isZero();
        assertThat(report.getTotalConflicts()).isZero();
        assertThat(report.getHealthScore()).isEqualTo(100);
        assertThat(report.getConflicts()).isEmpty();
    }

    @Test
    void opposingDiscountAndPremiumOnSameCategory() {
        RuleDto discount = RuleDto.builder()
                .id(1L).name("Volume Discount").priority(620)
                .content("discount volume seats").build();
        RuleDto premium = RuleDto.builder()
                .id(2L).name("Volume Premium").priority(610)
                .content("premium volume surcharge").build();
        when(ruleService.listByRuleSet(eq(9L), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(discount, premium)));

        RuleConflictReportDto report = analyzer.analyze(9L);

        assertThat(report.getTotalConflicts()).isGreaterThanOrEqualTo(1);
        assertThat(report.getConflicts()).extracting(RuleConflictDto::getConflictType)
                .contains("OPPOSING_EFFECTS", "SALIENCE_CONFLICT");
        assertThat(report.getHealthScore()).isLessThan(100);
    }

    @Test
    void similarNamesFlagRedundant() {
        RuleDto a = RuleDto.builder().id(1L).name("Volume Discount A").priority(600)
                .content("volume discount").build();
        RuleDto b = RuleDto.builder().id(2L).name("Volume Discount B").priority(600)
                .content("volume discount").build();
        when(ruleService.listByRuleSet(eq(9L), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(a, b)));

        RuleConflictReportDto report = analyzer.analyze(9L);

        assertThat(report.getConflicts()).extracting(RuleConflictDto::getConflictType)
                .contains("REDUNDANT_RULES");
    }
}
