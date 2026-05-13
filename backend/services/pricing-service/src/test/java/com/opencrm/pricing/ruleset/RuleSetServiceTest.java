package com.opencrm.pricing.ruleset;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.rule.Rule;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.rule.RuleStatus;
import com.opencrm.pricing.rule.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rule Set Service")
class RuleSetServiceTest {

    @Mock
    private RuleSetRepository ruleSetRepository;
    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private RuleSetMapper ruleSetMapper;

    private RuleSetService ruleSetService;

    @BeforeEach
    void setUp() {
        ruleSetService = new RuleSetService(ruleSetRepository, ruleRepository, ruleSetMapper);
    }

    @Test
    void activateRejectsDraftWithoutActiveValidatedRules() {
        RuleSet draft = draftRuleSet(2L);

        when(ruleSetRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(draft));
        when(ruleRepository.findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(2L, RuleStatus.ACTIVE))
                .thenReturn(List.of());

        assertThatThrownBy(() -> ruleSetService.activate(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot promote a rule set without at least one ACTIVE rule.");
    }

    @Test
    void activateRejectsInvalidActiveRuleContent() {
        RuleSet draft = draftRuleSet(2L);
        Rule invalidRule = activeRule(10L, "rule \"Broken\"\nwhen\nthen\n    not valid\nend");

        when(ruleSetRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(draft));
        when(ruleRepository.findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(2L, RuleStatus.ACTIVE))
                .thenReturn(List.of(invalidRule));

        assertThatThrownBy(() -> ruleSetService.activate(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot promote rule set because rule 10 has invalid DRL syntax");
    }

    @Test
    void activateDeprecatesExistingActiveRuleSetsBeforePublishingDraft() {
        RuleSet existingProduction = activeRuleSet(1L);
        RuleSet draft = draftRuleSet(2L);
        Rule validRule = activeRule(10L, """
                package com.opencrm.pricing.rules.test;

                rule "Valid promotion rule"
                when
                then
                end
                """);
        RuleSetDto dto = RuleSetDto.builder().id(2L).status(RuleSetStatus.ACTIVE).build();

        when(ruleSetRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(draft));
        when(ruleRepository.findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(2L, RuleStatus.ACTIVE))
                .thenReturn(List.of(validRule));
        when(ruleSetRepository.findByStatusAndDeletedFalse(RuleSetStatus.ACTIVE)).thenReturn(List.of(existingProduction));
        when(ruleSetRepository.save(draft)).thenReturn(draft);
        when(ruleSetMapper.toDto(draft)).thenReturn(dto);

        RuleSetDto result = ruleSetService.activate(2L);

        assertEquals(RuleSetStatus.DEPRECATED, existingProduction.getStatus());
        assertEquals(RuleSetStatus.ACTIVE, draft.getStatus());
        assertEquals(RuleSetStatus.ACTIVE, result.getStatus());
        verify(ruleSetRepository).save(draft);
    }

    private static RuleSet draftRuleSet(Long id) {
        return RuleSet.builder()
                .id(id)
                .name("Draft rules")
                .status(RuleSetStatus.DRAFT)
                .effectiveDate(LocalDate.now())
                .build();
    }

    private static RuleSet activeRuleSet(Long id) {
        return RuleSet.builder()
                .id(id)
                .name("Production rules")
                .status(RuleSetStatus.ACTIVE)
                .effectiveDate(LocalDate.now().minusDays(1))
                .build();
    }

    private static Rule activeRule(Long id, String content) {
        return Rule.builder()
                .id(id)
                .ruleSetId(2L)
                .name("Generated rule")
                .ruleType(RuleType.PRICING)
                .priority(100)
                .status(RuleStatus.ACTIVE)
                .content(content)
                .build();
    }
}
