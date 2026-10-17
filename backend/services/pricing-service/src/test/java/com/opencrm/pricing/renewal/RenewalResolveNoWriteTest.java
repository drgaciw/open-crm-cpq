package com.opencrm.pricing.renewal;

import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.pricing.config.GlobalExceptionHandler;
import com.opencrm.pricing.execution.PricingAuditLogRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
class RenewalResolveNoWriteTest {

    @Test
    void resolveTypesHaveNoRepositoryOutboxOrWriteTransaction() {
        assertThat(instanceFields(RenewalResolveController.class))
                .allMatch(field -> field.getType().equals(RenewalResolveService.class));
        assertThat(instanceFields(RenewalResolveService.class))
                .allMatch(field -> field.getType().equals(RenewalUpliftResolver.class));
        assertThat(RenewalResolveService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(RenewalResolveController.class.getAnnotation(Transactional.class)).isNull();
        for (Class<?> type : List.of(RenewalResolveController.class, RenewalResolveService.class, RenewalUpliftResolver.class)) {
            for (Field field : instanceFields(type)) {
                String name = field.getType().getName();
                assertThat(name).doesNotContain("Repository", "Outbox", "PricingAuditLog", "EntityManager", "Kafka");
            }
        }
        assertThat(RenewalUpliftResolver.class.getDeclaredFields())
                .allMatch(field -> Modifier.isStatic(field.getModifiers()));
    }

    @Test
    void aResolveCallDoesNotWriteAuditOutboxOrRepository() throws Exception {
        PricingAuditLogRepository audits = mock(PricingAuditLogRepository.class);
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        String body = new String(getClass().getResourceAsStream("/fixtures/pricing/renewal-resolve/same-request.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        MockMvcBuilders.standaloneSetup(new RenewalResolveController(new RenewalResolveService()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new RenewalResolveRequestConverter(), new JacksonJsonHttpMessageConverter())
                .build()
                .perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preview").value(true));
        verifyNoInteractions(audits, outbox);
    }

    private static List<Field> instanceFields(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
    }

}
