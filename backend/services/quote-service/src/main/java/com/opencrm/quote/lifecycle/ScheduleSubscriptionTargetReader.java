package com.opencrm.quote.lifecycle;

import com.opencrm.quote.client.SubscriptionScheduleClient;
import com.opencrm.quote.client.SubscriptionScheduleView;
import org.springframework.stereotype.Component;

/**
 * Uses the existing internal schedule read. That route is by subscription number.
 * Phase A passes the seller's target reference through it and does not add a client.
 */
@Component
public class ScheduleSubscriptionTargetReader implements SubscriptionTargetReader {

    private final SubscriptionScheduleClient schedules;

    public ScheduleSubscriptionTargetReader(SubscriptionScheduleClient schedules) {
        this.schedules = schedules;
    }

    @Override
    public SubscriptionTarget read(String targetSubscriptionId) {
        SubscriptionScheduleView view = schedules.byNumber(targetSubscriptionId);
        return new SubscriptionTarget(targetSubscriptionId, view.getStatus(), view.getCommercialRevision());
    }
}
