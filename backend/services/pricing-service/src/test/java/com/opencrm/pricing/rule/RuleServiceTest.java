package com.opencrm.pricing.rule;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.execution.PricingAuditLogRepository;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.ruleset.RuleSetStatus;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleService")
class RuleServiceTest {

    private static final String MINIMAL_DRL = """
            package com.example
            rule "ok"
            when
            then
            end
            """;

    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private RuleSetRepository ruleSetRepository;
    @Mock
    private PricingAuditLogRepository pricingAuditLogRepository;
    @Mock
    private RuleMapper ruleMapper;
    @Spy
    private DroolsRuleCompiler droolsRuleCompiler = new DroolsRuleCompiler();

    @InjectMocks
    private RuleService ruleService;

    @Test
    void create_savesWhenRuleSetDraft() {
        RuleCreateRequest request = RuleCreateRequest.builder()
                .ruleSetId(9L).name("r1").ruleType(RuleType.PRICING).priority(100).content(MINIMAL_DRL).build();
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.DRAFT).build();
        ruleSet.setId(9L);
        Rule entity = Rule.builder().name("r1").ruleSetId(9L).build();
        Rule saved = Rule.builder().name("r1").ruleSetId(9L).build();
        saved.setId(3L);
        RuleDto dto = RuleDto.builder().id(3L).name("r1").build();
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        when(ruleMapper.toEntity(request)).thenReturn(entity);
        when(ruleRepository.save(entity)).thenReturn(saved);
        when(ruleMapper.toDto(saved)).thenReturn(dto);
        assertThat(ruleService.create(request).getId()).isEqualTo(3L);
    }

    @Test
    void create_rejectsMissingRuleSet() {
        RuleCreateRequest request = RuleCreateRequest.builder().ruleSetId(9L).name("r1").build();
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ruleService.create(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_rejectsActiveRuleSet() {
        RuleCreateRequest request = RuleCreateRequest.builder().ruleSetId(9L).name("r1").build();
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.ACTIVE).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        assertThatThrownBy(() -> ruleService.create(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void create_rejectsInvalidDrl() {
        RuleCreateRequest request = RuleCreateRequest.builder()
                .ruleSetId(9L).name("r1").ruleType(RuleType.PRICING).priority(100).content("not a drl").build();
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.DRAFT).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        assertThatThrownBy(() -> ruleService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to compile rule");
    }

    @Test
    void getById_returnsDto() {
        Rule rule = Rule.builder().name("r1").build();
        RuleDto dto = RuleDto.builder().id(1L).name("r1").build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        when(ruleMapper.toDto(rule)).thenReturn(dto);
        assertThat(ruleService.getById(1L).getName()).isEqualTo("r1");
    }

    @Test
    void getById_missing() {
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ruleService.getById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_savesWhenDraft() {
        RuleUpdateRequest request = new RuleUpdateRequest();
        Rule rule = Rule.builder().name("old").ruleSetId(9L).content(MINIMAL_DRL).build();
        rule.setId(1L);
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.DRAFT).build();
        RuleDto dto = RuleDto.builder().id(1L).name("new").build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        when(ruleRepository.save(rule)).thenReturn(rule);
        when(ruleMapper.toDto(rule)).thenReturn(dto);
        assertThat(ruleService.update(1L, request).getName()).isEqualTo("new");
        verify(ruleMapper).updateEntity(request, rule);
    }

    @Test
    void update_rejectsActiveRuleSet() {
        Rule rule = Rule.builder().name("old").ruleSetId(9L).build();
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.ACTIVE).build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        assertThatThrownBy(() -> ruleService.update(1L, new RuleUpdateRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_rejectsInvalidDrl() {
        Rule rule = Rule.builder().name("old").ruleSetId(9L).content("not a drl").build();
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.DRAFT).build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        assertThatThrownBy(() -> ruleService.update(1L, new RuleUpdateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to compile rule");
    }

    @Test
    void delete_softDeletesWhenDraft() {
        Rule rule = Rule.builder().name("r").ruleSetId(9L).build();
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.DRAFT).build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        ruleService.delete(1L);
        assertThat(rule.isDeleted()).isTrue();
        verify(ruleRepository).save(rule);
    }

    @Test
    void delete_rejectsActiveRuleSet() {
        Rule rule = Rule.builder().name("r").ruleSetId(9L).build();
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.ACTIVE).build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        assertThatThrownBy(() -> ruleService.delete(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void listByRuleSet_mapsPage() {
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.DRAFT).build();
        Rule rule = Rule.builder().name("r").build();
        RuleDto dto = RuleDto.builder().name("r").build();
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        when(ruleRepository.findByRuleSetIdAndDeletedFalse(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rule)));
        when(ruleMapper.toDto(rule)).thenReturn(dto);
        Page<RuleDto> page = ruleService.listByRuleSet(9L, Pageable.unpaged());
        assertThat(page.getContent()).containsExactly(dto);
    }

    @Test
    void validate_rejectsEmptyContent() {
        Rule rule = Rule.builder().name("r").content("  ").build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        assertThatThrownBy(() -> ruleService.validate(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Rule content cannot be empty");
    }

    @Test
    void validate_rejectsInvalidDrl() {
        Rule rule = Rule.builder().name("r").content("not a drl").build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        assertThatThrownBy(() -> ruleService.validate(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid DRL syntax");
    }

    @Test
    void validate_rejectsUnknownFactType() {
        Rule rule = Rule.builder().name("r").content("""
                package com.example
                rule "unknown"
                when
                    UnknownFact(id == 1)
                then
                end
                """).build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        assertThatThrownBy(() -> ruleService.validate(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid DRL syntax");
    }

    @Test
    void validate_rejectsMissingRule() {
        when(ruleRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ruleService.validate(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validate_acceptsMinimalDrl() {
        Rule rule = Rule.builder().name("r").content(MINIMAL_DRL).build();
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        ruleService.validate(1L);
    }

    @Test
    void getImpact_buildsDto() {
        Rule rule = Rule.builder().name("r").ruleSetId(9L).ruleType(RuleType.PRICING)
                .status(RuleStatus.ACTIVE).priority(100).build();
        rule.setId(1L);
        RuleSet ruleSet = RuleSet.builder().name("set").status(RuleSetStatus.ACTIVE).build();
        ruleSet.setId(9L);
        when(ruleRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(rule));
        when(ruleSetRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(ruleSet));
        when(ruleRepository.countByRuleSetIdAndDeletedFalse(9L)).thenReturn(4L);
        when(ruleRepository.countByRuleSetIdAndStatusAndDeletedFalse(9L, RuleStatus.ACTIVE)).thenReturn(2L);
        when(pricingAuditLogRepository.countByRuleSetId(9L)).thenReturn(10L);
        when(pricingAuditLogRepository.countByRuleSetIdAndExecutedAtGreaterThanEqual(eq(9L), any()))
                .thenReturn(3L);
        RuleImpactDto impact = ruleService.getImpact(1L);
        assertThat(impact.getRuleId()).isEqualTo(1L);
        assertThat(impact.isActiveRuleSet()).isTrue();
        assertThat(impact.getTotalRulesInRuleSet()).isEqualTo(4L);
    }
}
