package com.opencrm.pricing.ruletools;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.rule.RuleDto;
import com.opencrm.pricing.rule.RuleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleToolsControllerTest {

    @Mock
    private RuleConflictAnalyzer conflictAnalyzer;
    @Mock
    private RuleTemplateService templateService;
    @Mock
    private RuleTestSuiteService testSuiteService;

    @Test
    void flagOffReturnsFeatureDisabled() {
        RuleToolsController controller = new RuleToolsController(
                conflictAnalyzer, templateService, testSuiteService, false);

        assertThatThrownBy(() -> controller.conflicts(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("FEATURE_DISABLED");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void flagOnDelegatesConflictsAndTemplates() {
        RuleToolsController controller = new RuleToolsController(
                conflictAnalyzer, templateService, testSuiteService, true);
        when(conflictAnalyzer.analyze(9L)).thenReturn(RuleConflictReportDto.builder()
                .ruleSetId(9L).healthScore(100).build());
        when(templateService.apply(eq(RuleTemplateKind.VOLUME_DISCOUNT), any()))
                .thenReturn(RuleDto.builder().id(1L).status(RuleStatus.DRAFT).build());

        assertThat(controller.conflicts(9L).getData().getHealthScore()).isEqualTo(100);
        assertThat(controller.applyTemplate("volume-discount", RuleTemplateApplyRequest.builder()
                .ruleSetId(9L).build()).getData().getStatus()).isEqualTo(RuleStatus.DRAFT);
        verify(conflictAnalyzer).analyze(9L);
        verify(templateService).apply(eq(RuleTemplateKind.VOLUME_DISCOUNT), any());
    }
}
