package com.opencrm.pricing.metricshistory;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.waterfall.PricingContext;
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
class MetricsHistoryControllerTest {

    @Mock
    private MetricsHistoryService historyService;

    @Test
    void flagOffReturnsFeatureDisabled() {
        MetricsHistoryController controller = new MetricsHistoryController(historyService, false);
        assertThatThrownBy(() -> controller.history("cust-1", 30))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("FEATURE_DISABLED"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void flagOnDelegatesSnapshotAndChurnRisk() {
        MetricsHistoryController controller = new MetricsHistoryController(historyService, true);
        when(historyService.snapshot(eq("cust-1"), any()))
                .thenReturn(MetricsSnapshotDto.builder().id(1L).build());
        when(historyService.churnRisk("cust-1"))
                .thenReturn(ChurnRiskDto.builder().churnScore(12).build());

        assertThat(controller.snapshot("cust-1", new PricingContext()).getData().getId()).isEqualTo(1L);
        assertThat(controller.churnRisk("cust-1").getData().getChurnScore()).isEqualTo(12);
        verify(historyService).snapshot(eq("cust-1"), any());
    }
}
