package com.opencrm.contract.documents.apply;

import com.opencrm.contract.controller.DocumentApplyController;
import com.opencrm.contract.documents.AmendmentDocumentService;
import com.opencrm.contract.documents.events.AmendmentEventWriter;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.documents.AmendmentApplicationUnitRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
import com.opencrm.contract.repository.documents.DocumentApplyLedgerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * One Docker-free wiring check per new bean. A stereotype with two constructors and no
 * {@code @Autowired} marker fails here.
 */
@Tag("unit")
class DocumentApplyWiringContextTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NewBeans.class);

    @Test
    void newBeansLoadWithTheConstructorsSpringUses() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded).hasSingleBean(DocumentApplyOrchestrator.class);
            assertThat(loaded).hasSingleBean(JdbcDocumentApplyGateway.class);
            assertThat(loaded).hasSingleBean(DocumentApplyController.class);
            assertThat(loaded).hasSingleBean(EntityManager.class);
            assertThat(loaded.getBean(DocumentApplyOrchestrator.class)).isNotNull();
            assertThat(loaded.getBean(JdbcDocumentApplyGateway.class)).isNotNull();
            assertThat(loaded.getBean(DocumentApplyController.class)).isNotNull();
        });
    }

    /** Explicitly imported by the runner. Not component-scanned into the CAM application. */
    @TestConfiguration(proxyBeanMethods = false)
    @Import({DocumentApplyOrchestrator.class, JdbcDocumentApplyGateway.class, DocumentApplyController.class})
    static class NewBeans {
        @Bean
        AmendmentDocumentRepository amendmentDocumentRepository() {
            return mock(AmendmentDocumentRepository.class);
        }

        @Bean
        AmendmentApplicationUnitRepository amendmentApplicationUnitRepository() {
            return mock(AmendmentApplicationUnitRepository.class);
        }

        @Bean
        DocumentApplyLedgerRepository documentApplyLedgerRepository() {
            return mock(DocumentApplyLedgerRepository.class);
        }

        @Bean
        SubscriptionRepository subscriptionRepository() {
            return mock(SubscriptionRepository.class);
        }

        @Bean
        SubscriptionRatePeriodRepository subscriptionRatePeriodRepository() {
            return mock(SubscriptionRatePeriodRepository.class);
        }

        @Bean
        AmendmentEventWriter amendmentEventWriter() {
            return mock(AmendmentEventWriter.class);
        }

        @Bean
        AmendmentDocumentService amendmentDocumentService() {
            return mock(AmendmentDocumentService.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        EntityManager entityManager() {
            return mock(EntityManager.class);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        PlatformTransactionManager platformTransactionManager() {
            return new DirectTransactions();
        }
    }

    private static final class DirectTransactions extends AbstractPlatformTransactionManager {
        private DirectTransactions() {
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
