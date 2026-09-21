package com.opencrm.order.recovery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RecoveryMetricsTest {
 @Test void scrapesNeverQueryDatabaseAndInitialStateIsExplicit(){var daily=mock(DailyReconciliation.class);var registry=new SimpleMeterRegistry();new RecoveryMetrics(daily,registry);assertEquals(Double.POSITIVE_INFINITY,registry.get("oms.reconciliation.sample.age.seconds").gauge().value());assertEquals(Double.POSITIVE_INFINITY,registry.get("oms.reconciliation.since.completion.seconds").gauge().value());verifyNoInteractions(daily);}
 @Test void failedRefreshPreservesLastGoodSampleAndMakesFailureVisible(){var daily=mock(DailyReconciliation.class);var registry=new SimpleMeterRegistry();var metrics=new RecoveryMetrics(daily,registry);when(daily.health()).thenReturn(new DailyReconciliation.ScanHealth(2,100,3600));metrics.refresh();clearInvocations(daily);assertEquals(2,registry.get("oms.reconciliation.incomplete.runs").gauge().value());assertEquals(2,registry.get("oms.reconciliation.incomplete.runs").gauge().value());verifyNoInteractions(daily);when(daily.health()).thenThrow(new DataAccessResourceFailureException("database down"));assertDoesNotThrow(metrics::refresh);clearInvocations(daily);assertEquals(2,registry.get("oms.reconciliation.incomplete.runs").gauge().value());assertEquals(1,registry.get("oms.reconciliation.refresh.failures").gauge().value());assertFalse(Double.isNaN(registry.get("oms.reconciliation.oldest.run.age.seconds").gauge().value()));verifyNoInteractions(daily);}
}
