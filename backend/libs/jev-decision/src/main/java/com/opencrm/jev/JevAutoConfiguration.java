package com.opencrm.jev;

import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.shadow.JdbcJevDecisionLogStore;
import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.transport.JevTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

/**
 * Registered through {@code META-INF/spring/...AutoConfiguration.imports} because services do not
 * component-scan {@code com.opencrm.jev}. With no properties set this contributes a no-op transport and
 * nothing that talks to the network (ADR-015).
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
@EnableConfigurationProperties(JevProperties.class)
public class JevAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JevTransport jevTransport(JevProperties properties) {
        return JevTransportFactory.create(properties, RestClient.builder());
    }

    @Bean
    @ConditionalOnMissingBean
    JevClient jevClient(JevProperties properties, JevTransport transport) {
        return new JevClient(properties, transport);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    JevAdvisoryRunner jevAdvisoryRunner(JevProperties properties) {
        return new JevAdvisoryRunner(properties.getMaxConcurrent());
    }

    /** JDBC-backed when the service has a DataSource, otherwise decisions are not recorded. */
    @Bean
    @ConditionalOnMissingBean
    JevDecisionLogStore jevDecisionLogStore(ObjectProvider<DataSource> dataSource) {
        DataSource available = dataSource.getIfAvailable();
        if (available == null) {
            return JevDecisionLogStore.NONE;
        }
        // spring-jdbc is an optional dependency; it is only touched when a DataSource exists.
        return new JdbcJevDecisionLogStore(new NamedParameterJdbcTemplate(available));
    }
}
