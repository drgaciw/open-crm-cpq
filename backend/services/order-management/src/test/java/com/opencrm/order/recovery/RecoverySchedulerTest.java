package com.opencrm.order.recovery;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RecoverySchedulerTest {
 @Test void defaultsProvideHeadroomForOneHundredThousandOrdersDaily()throws Exception{
  var daily=mock(DailyReconciliation.class);new RecoveryScheduler(daily).scan();verify(daily).scan(100);
  String delay=RecoveryScheduler.class.getMethod("scan").getAnnotation(Scheduled.class).fixedDelayString();
  assertEquals("${oms.recovery.scan-delay-ms:5000}",delay);
  // Budget 60 seconds of database work per batch in addition to the configured five-second delay.
  assertTrue((86400L/(60+5))*100>=100000);
 }

 @Test void configuredBatchIsBoundedAndUsed(){var daily=mock(DailyReconciliation.class);assertThrows(IllegalArgumentException.class,()->new RecoveryScheduler(daily,0));assertThrows(IllegalArgumentException.class,()->new RecoveryScheduler(daily,101));new RecoveryScheduler(daily,7).scan();verify(daily).scan(7);}
}
