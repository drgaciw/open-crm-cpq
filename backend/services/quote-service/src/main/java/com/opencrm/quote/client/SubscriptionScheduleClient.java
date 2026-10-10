package com.opencrm.quote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Direct internal schedule read authenticated by the quote service identity. */
@Component
public class SubscriptionScheduleClient {
    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public SubscriptionScheduleClient(@Qualifier("subscriptionScheduleRestClient") RestClient client) { this.client = client; }
    public SubscriptionScheduleView byNumber(String number) {
        try {
            String raw = client.get().uri("/internal/v1/subscriptions/by-number/{number}/ramp-schedule", number)
                    .retrieve().body(String.class);
            ApiResponse<SubscriptionScheduleView> response = json.readValue(raw, new TypeReference<>() {});
            if (response == null || !response.isSuccess() || response.getData() == null) throw new IllegalArgumentException("Missing schedule data");
            return response.getData();
        } catch (Exception ex) {
            throw new BusinessException("Cannot read the target subscription schedule; retry or choose a valid target",
                    HttpStatus.BAD_GATEWAY, "SUBSCRIPTION_SCHEDULE_UNAVAILABLE");
        }
    }
}
