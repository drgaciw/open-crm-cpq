package com.opencrm.quote.ramp;

import com.opencrm.quote.config.GlobalExceptionHandler;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Docker-free MockMvc slice for POST /ramp-schedule/extend.
 * Anonymous calls are refused by the quote-service security chain, not by this controller.
 */
@DisplayName("AC-532-8 ramp extend endpoint")
class RampScheduleControllerTest {

    private static final String EXTEND = "/api/v1/quotes/7/ramp-schedule/extend";

    private static final String VALID_BODY = """
            {
              "durationInMonths": 60,
              "steps": [{
                "sequence": 1,
                "startMonth": 1,
                "months": 36,
                "quantity": 2,
                "priceBasis": "ABSOLUTE",
                "unitPrice": 42.8000
              }],
              "escalator": {
                "startPrice": 42.80,
                "mode": "PERCENT",
                "amount": 7,
                "everyMonths": 12
              }
            }
            """;

    private final RampScheduleQueryService service = mock(RampScheduleQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RampScheduleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("returns ApiResponse of the preview schedule")
    void extendReturnsScheduleEnvelope() throws Exception {
        when(service.extend(eq(7L), any(RampExtendRequest.class))).thenReturn(RampScheduleDto.builder()
                .steps(List.of(RampStepDto.builder()
                        .sequence(4)
                        .startMonth(37)
                        .months(12)
                        .quantity(2)
                        .priceBasis(RampPriceBasis.ABSOLUTE)
                        .unitPrice(new BigDecimal("52.4318"))
                        .build()))
                .build());

        mvc.perform(post(EXTEND).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.steps[0].sequence").value(4))
                .andExpect(jsonPath("$.data.steps[0].startMonth").value(37))
                .andExpect(jsonPath("$.data.steps[0].priceBasis").value("ABSOLUTE"));

        verify(service).extend(eq(7L), any(RampExtendRequest.class));
    }

    @Test
    @DisplayName("an incomplete body is 400 with a validation reason and does not call the service")
    void incompleteBodyIsBadRequest() throws Exception {
        mvc.perform(post(EXTEND).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("durationInMonths")));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("a ramp rejection stays 422 with the schedule reason")
    void rampRejectionStaysUnprocessable() throws Exception {
        when(service.extend(eq(7L), any(RampExtendRequest.class)))
                .thenThrow(RampScheduleException.invalid(List.of(RampReasons.TERM_MISMATCH)));

        mvc.perform(post(EXTEND).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(RampReasons.SCHEDULE_INVALID))
                .andExpect(jsonPath("$.message").value(RampReasons.TERM_MISMATCH));
    }

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = RampExtendSecurityApp.class)
    @TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
    @DisplayName("unauthenticated extend is refused")
    class Unauthenticated {

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private RampScheduleQueryService rampScheduleQueryService;

        @Test
        @DisplayName("anonymous POST /extend is 401 and does not reach the service")
        void anonymousPostIsUnauthorized() throws Exception {
            MockMvc secured = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                    .build();

            secured.perform(post(EXTEND).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(rampScheduleQueryService);
        }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableWebMvc
@ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
@Import({TestJwtDecoderConfiguration.class, RampScheduleController.class, GlobalExceptionHandler.class})
class RampExtendSecurityApp {

    @Bean
    RampScheduleQueryService rampScheduleQueryService() {
        return mock(RampScheduleQueryService.class);
    }
}
