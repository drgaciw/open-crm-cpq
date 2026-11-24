package com.opencrm.contract.asset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "opencrm.assets.effective-activation.enabled", havingValue = "true")
class EffectiveAssetActivationJob {
    private static final int BATCH_SIZE = 100;

    private final EffectiveAssetActivation activation;

    EffectiveAssetActivationJob(EffectiveAssetActivation activation) {
        this.activation = activation;
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    void projectDueActivations() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int applied = 0; applied < BATCH_SIZE; applied++) {
            if (!activation.applyNext(today)) {
                return;
            }
        }
    }
}
