package com.opencrm.crm.architecture;

import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.OutboxEventRepository;
import com.opencrm.crm.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.TreeSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-load guard for the {@code com.opencrm.common} types CRM pulls in by component scan,
 * entity scan, and repository scan. AccountId and Money are value types and must not be beans.
 *
 * <p>Boot auto-configurations under {@code com.opencrm.common.event} (outbox health, metrics, actor)
 * register additional beans through {@code AutoConfiguration.imports}. Those {@code @Bean} methods are
 * owned outside this story. This test checks the scan set, which is the set the CRM application
 * pulls in itself: {@code OutboxEventPublisher}, {@code OutboxEventRepository}, and the
 * {@code OutboxEvent} entity.
 */
@DisplayName("CRM common bean wiring")
class CrmCommonBeanWiringTest extends BaseIntegrationTest {

    private static final String PUBLISHER = "com.opencrm.common.event.OutboxEventPublisher";
    private static final String REPOSITORY = "com.opencrm.common.event.OutboxEventRepository";
    private static final String ENTITY = "com.opencrm.common.event.OutboxEvent";

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
            if (definition.getFactoryMethodName() != null) {
                continue;
            }
            String className = definition.getBeanClassName();
            if (className != null && className.startsWith("com.opencrm.common.")) {
                scanned.add(className);
            }
            Object repositoryInterface = definition.getAttribute("factoryBeanObjectType");
            if (repositoryInterface instanceof Class<?> type && type.getName().startsWith("com.opencrm.common.")) {
                scanned.add(type.getName());
            } else if (repositoryInterface instanceof String typeName && typeName.startsWith("com.opencrm.common.")) {
                scanned.add(typeName);
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
}
