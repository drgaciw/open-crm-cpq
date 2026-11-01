package com.opencrm.quote.lifecycle;

import com.opencrm.quote.client.SubscriptionScheduleClient;
import com.opencrm.quote.client.SubscriptionScheduleView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Schedule subscription target reader")
class ScheduleSubscriptionTargetReaderTest {

    @Test
    @DisplayName("status and observed revision come from the existing schedule client")
    void readsTheSchedule() {
        SubscriptionScheduleClient client = mock(SubscriptionScheduleClient.class);
        when(client.byNumber("SUB-1")).thenReturn(SubscriptionScheduleView.builder()
                .subscriptionNumber("SUB-1")
                .status("ACTIVE")
                .commercialRevision(4L)
                .build());

        SubscriptionTarget target = new ScheduleSubscriptionTargetReader(client).read("SUB-1");

        assertThat(target.subscriptionId()).isEqualTo("SUB-1");
        assertThat(target.status()).isEqualTo("ACTIVE");
        assertThat(target.commercialRevision()).isEqualTo(4L);
        verify(client).byNumber("SUB-1");
    }
}
