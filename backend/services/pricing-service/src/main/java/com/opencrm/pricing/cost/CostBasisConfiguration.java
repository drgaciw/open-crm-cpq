package com.opencrm.pricing.cost;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Until a cost basis adapter is present, every lookup is empty, so margin fails closed. */
@Configuration(proxyBeanMethods = false)
class CostBasisConfiguration {

    @Bean
    @ConditionalOnMissingBean(CostBasisLookup.class)
    CostBasisLookup emptyCostBasisLookup() {
        return (productExternalId, effectiveOn, pricedOn) -> Optional.empty();
    }
}
