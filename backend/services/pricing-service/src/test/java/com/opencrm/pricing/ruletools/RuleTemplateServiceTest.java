package com.opencrm.pricing.ruletools;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.rule.RuleCreateRequest;
import com.opencrm.pricing.rule.RuleDto;
import com.opencrm.pricing.rule.RuleService;
import com.opencrm.pricing.rule.RuleStatus;
import com.opencrm.pricing.rule.RuleType;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleTemplateServiceTest {

    @Mock
    private RuleService ruleService;

    @InjectMocks
    private RuleTemplateService templateService;

    @Test
    void applyCreatesDraftViaRuleServiceAndNeverActivates() {
        when(ruleService.create(any())).thenReturn(RuleDto.builder()
                .id(3L).status(RuleStatus.DRAFT).name("Volume Discount").build());

        RuleDto created = templateService.apply(
                RuleTemplateKind.VOLUME_DISCOUNT,
                RuleTemplateApplyRequest.builder()
                        .ruleSetId(9L)
                        .name("Volume Discount")
                        .parameters(Map.of("minQuantity", 250, "discountRate", "0.12"))
                        .build());

        ArgumentCaptor<RuleCreateRequest> captor = ArgumentCaptor.forClass(RuleCreateRequest.class);
        verify(ruleService).create(captor.capture());
        RuleCreateRequest request = captor.getValue();
        assertThat(request.getRuleSetId()).isEqualTo(9L);
        assertThat(request.getRuleType()).isEqualTo(RuleType.PRICING);
        assertThat(request.getContent()).contains("250").contains("0.12").contains("QuoteLineItemFact");
        assertThat(created.getStatus()).isEqualTo(RuleStatus.DRAFT);
        verify(ruleService, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(RuleTemplateKind.class)
    void generatedDrlCompiles(RuleTemplateKind kind) {
        String drl = templateService.render(kind, "Template " + kind.path(), Map.of());
        assertThat(new DroolsRuleCompiler().validateDrl(drl).isValid())
                .as("kind %s DRL should compile: %s", kind, drl)
                .isTrue();
    }

    @Test
    void unknownKindRejected() {
        assertThatThrownBy(() -> RuleTemplateKind.fromPath("ai-cache"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown template kind");
    }

    @Test
    void rejectsNonNumericParameters() {
        assertThatThrownBy(() -> templateService.apply(
                RuleTemplateKind.VOLUME_DISCOUNT,
                RuleTemplateApplyRequest.builder()
                        .ruleSetId(9L)
                        .parameters(Map.of("discountRate", "0.07; drop table"))
                        .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be numeric");
        verify(ruleService, never()).create(any());
    }
}
