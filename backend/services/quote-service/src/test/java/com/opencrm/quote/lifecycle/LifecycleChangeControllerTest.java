package com.opencrm.quote.lifecycle;

import com.opencrm.quote.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DoD 20: bean validation on the create body. The gateway Redis RequestRateLimiter
 * (50 rps replenish, 100 burst) is the limit; this slice adds none.
 */
@DisplayName("LifecycleChange controller validation")
class LifecycleChangeControllerTest {

    private final LifecycleChangeService service = mock(LifecycleChangeService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new LifecycleChangeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("a body missing required fields is 422 and does not reach the service")
    void beanValidationRejectsAnEmptyBody() throws Exception {
        mvc.perform(post("/api/v1/lifecycle-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("a valid body is passed through, including a tid the service must ignore")
    void validBodyReachesTheService() throws Exception {
        when(service.create(any(), isNull())).thenReturn(LifecycleChangeResult.created(
                LifecycleChangeResponse.builder()
                        .id(UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"))
                        .intent("AMEND")
                        .status("DRAFT")
                        .revision(1)
                        .version(0L)
                        .build()));

        mvc.perform(post("/api/v1/lifecycle-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intent": "AMEND",
                                  "initiationTrigger": "MANUAL",
                                  "actionDate": "2026-11-01",
                                  "contractEffectiveDate": "2026-11-01",
                                  "contractId": "6b1c2a10-0c3a-4b7e-9f11-2a6e0c9d4b21",
                                  "currencyCode": "USD",
                                  "tid": "attacker-tenant",
                                  "lines": [
                                    {
                                      "actionCode": "ADD",
                                      "quantitySemantics": "ABSOLUTE",
                                      "startQty": "0.0000",
                                      "deltaQty": "2.0000",
                                      "endQty": "2.0000",
                                      "effectiveFrom": "2026-11-01",
                                      "productExternalId": "sku-seat"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.data.intent").value("AMEND"));
        verify(service).create(any(), isNull());
    }
}
