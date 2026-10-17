package com.opencrm.pricing.renewal;

import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.OutboxEventRepository;
import com.opencrm.pricing.config.GlobalExceptionHandler;
import com.opencrm.pricing.execution.PricingAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The resolve handler runs with the audit repository, the outbox repository, and the outbox publisher
 * present as beans. None of them is invoked.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = RenewalResolveNoWriteTest.App.class)
@Tag("unit")
class RenewalResolveNoWriteTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({RenewalResolveController.class, RenewalResolveService.class, RenewalResolveRequestConverter.class,
            GlobalExceptionHandler.class})
    static class App {
        @Bean
        PricingAuditLogRepository pricingAuditLogRepository() {
            return Mockito.mock(PricingAuditLogRepository.class);
        }

        @Bean
        OutboxEventRepository outboxEventRepository() {
            return Mockito.mock(OutboxEventRepository.class);
        }

        @Bean
        OutboxEventPublisher outboxEventPublisher() {
            return Mockito.mock(OutboxEventPublisher.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PricingAuditLogRepository audits;

    @Autowired
    private OutboxEventRepository outboxRows;

    @Autowired
    private OutboxEventPublisher outbox;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(audits, outboxRows, outbox);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void resolveDoesNotTouchAuditOrOutbox() throws Exception {
        String body = new String(getClass().getResourceAsStream("/fixtures/pricing/renewal-resolve/same-request.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        mvc.perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preview").value(true))
                .andExpect(jsonPath("$.data.resolution.resolution").value("PRICED"));
        verifyNoInteractions(audits, outboxRows, outbox);
    }
}
