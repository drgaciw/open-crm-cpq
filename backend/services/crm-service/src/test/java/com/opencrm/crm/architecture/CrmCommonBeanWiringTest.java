package com.opencrm.crm.architecture;

import com.opencrm.common.event.EventActorResolver;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.OutboxEventRepository;
import com.opencrm.common.event.OutboxRelayPublisher;
import com.opencrm.crm.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-load guard for the {@code com.opencrm.common} types CRM pulls in by component scan,
 * entity scan, and repository scan. AccountId and Money are value types and must not be beans.
 *
 * <p>Boot auto-configurations under {@code com.opencrm.common.event} also register beans through
 * {@code AutoConfiguration.imports}. The scanned set stays the outbox trio; the auto-config set is
 * asserted separately against the DRG-699 per-service inventory
 * ({@code docs/plans/drg-699-common-autoconfig-inventory.md}).
 */
@DisplayName("CRM common bean wiring")
class CrmCommonBeanWiringTest extends BaseIntegrationTest {

    private static final String PUBLISHER = "com.opencrm.common.event.OutboxEventPublisher";
    private static final String REPOSITORY = "com.opencrm.common.event.OutboxEventRepository";
    private static final String ENTITY = "com.opencrm.common.event.OutboxEvent";

    /**
     * CRM default profile (relay off, inbox on, Kafka + outbox repository present). Matches the
     * APPLY rows for crm-service in the DRG-699 inventory.
     */
    private static final Set<String> CRM_EVENT_AUTOCONFIG_BEANS = Set.of(
            "com.opencrm.common.event.DeadLetterMetrics",
            "com.opencrm.common.event.EventCorrelationRecordInterceptor",
            "com.opencrm.common.event.KafkaInbox",
            "com.opencrm.common.event.KafkaInboxAutoConfiguration$KafkaInboxPurge",
            "com.opencrm.common.event.OutboxBacklogProbe",
            "com.opencrm.common.event.OutboxHealthIndicator",
            "com.opencrm.common.event.OutboxMetrics",
            "org.springframework.kafka.listener.DefaultErrorHandler");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("scanned common beans and entities are only the outbox publisher, repository, and entity")
    void scannedCommonTypesAreOnlyTheOutboxTrio() {
        ConfigurableApplicationContext configurable = (ConfigurableApplicationContext) applicationContext;
        Set<String> scanned = new TreeSet<>();
        for (String name : configurable.getBeanFactory().getBeanDefinitionNames()) {
            BeanDefinition definition = configurable.getBeanFactory().getBeanDefinition(name);
            // @Bean methods belong to Boot auto-configurations; covered by the auto-config test below.
            if (definition.getFactoryMethodName() != null) {
                continue;
            }
            Class<?> resolved = applicationContext.getType(name);
            if (resolved != null) {
                Class<?> userType = ClassUtils.getUserClass(resolved);
                if (isDomainCommonType(userType)) {
                    scanned.add(userType.getName());
                }
            }
            String className = definition.getBeanClassName();
            if (className != null && className.startsWith("com.opencrm.common.") && isDomainCommonType(className)) {
                scanned.add(className);
            }
            if (definition instanceof RootBeanDefinition root) {
                for (ValueHolder holder : root.getConstructorArgumentValues().getGenericArgumentValues()) {
                    addDomainType(scanned, holder.getValue());
                }
                for (ValueHolder holder : root.getConstructorArgumentValues().getIndexedArgumentValues().values()) {
                    addDomainType(scanned, holder.getValue());
                }
            }
        }

        assertThat(scanned).containsExactlyInAnyOrder(PUBLISHER, REPOSITORY);

        Set<String> entities = new TreeSet<>();
        for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
            String name = entity.getJavaType().getName();
            if (name.startsWith("com.opencrm.common.")) {
                entities.add(name);
            }
        }
        assertThat(entities).containsExactly(ENTITY);

        assertThat(applicationContext.getBeansOfType(OutboxEventPublisher.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(OutboxEventRepository.class)).hasSize(1);
    }

    @Test
    @DisplayName("common.event auto-configurations wire exactly the CRM inventory allowlist")
    void eventAutoConfigurationsWireTheCrmInventoryAllowlist() {
        assertThat(beansFromCommonEventAutoConfigurations())
                .as("beans from com.opencrm.common.event.*AutoConfiguration @Bean methods")
                .containsExactlyInAnyOrderElementsOf(CRM_EVENT_AUTOCONFIG_BEANS);

        assertThat(applicationContext.getBeansOfType(EventActorResolver.class))
                .as("security supersedes EventActorAutoConfiguration; exactly one resolver")
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(OutboxRelayPublisher.class))
                .as("OutboxRelayAutoConfiguration stays off (opencrm.outbox.relay.enabled unset)")
                .isEmpty();
    }

    private static void addDomainType(Set<String> scanned, Object value) {
        if (value instanceof Class<?> type && isDomainCommonType(type)) {
            scanned.add(type.getName());
        } else if (value instanceof String className && className.startsWith("com.opencrm.common.")
                && isDomainCommonType(className)) {
            scanned.add(className);
        }
    }

    private static boolean isDomainCommonType(String className) {
        try {
            return isDomainCommonType(Class.forName(className));
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    /**
     * Component-scanned domain types. Auto-configurations and property holders in
     * {@code com.opencrm.common.event} are Boot infrastructure, not CRM aggregates.
     */
    private static boolean isDomainCommonType(Class<?> type) {
        if (!type.getName().startsWith("com.opencrm.common.")) {
            return false;
        }
        return !type.isAnnotationPresent(AutoConfiguration.class)
                && !type.isAnnotationPresent(Configuration.class)
                && !type.isAnnotationPresent(ConfigurationProperties.class);
    }

    /**
     * Bean types produced by {@code @Bean} methods whose factory bean is a
     * {@code com.opencrm.common.event.*AutoConfiguration}. Property holders and the
     * auto-configuration classes themselves are excluded.
     */
    private Set<String> beansFromCommonEventAutoConfigurations() {
        ConfigurableApplicationContext configurable = (ConfigurableApplicationContext) applicationContext;
        Set<String> wired = new TreeSet<>();
        for (String name : configurable.getBeanFactory().getBeanDefinitionNames()) {
            BeanDefinition definition = configurable.getBeanFactory().getBeanDefinition(name);
            String factoryBeanName = definition.getFactoryBeanName();
            if (factoryBeanName == null || definition.getFactoryMethodName() == null) {
                continue;
            }
            Class<?> factoryType = applicationContext.getType(factoryBeanName);
            if (factoryType == null) {
                continue;
            }
            Class<?> factoryUser = ClassUtils.getUserClass(factoryType);
            if (!factoryUser.getName().startsWith("com.opencrm.common.event.")
                    || !factoryUser.getSimpleName().endsWith("AutoConfiguration")) {
                continue;
            }
            Class<?> beanType = applicationContext.getType(name);
            if (beanType == null) {
                continue;
            }
            Class<?> userType = ClassUtils.getUserClass(beanType);
            if (userType.isAnnotationPresent(ConfigurationProperties.class)
                    || userType.isAnnotationPresent(AutoConfiguration.class)
                    || userType.isAnnotationPresent(Configuration.class)) {
                continue;
            }
            wired.add(userType.getName());
        }
        return wired;
    }
}
