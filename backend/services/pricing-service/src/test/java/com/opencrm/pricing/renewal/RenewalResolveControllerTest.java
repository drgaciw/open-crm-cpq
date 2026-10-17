package com.opencrm.pricing.renewal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.pricing.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
class RenewalResolveControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RenewalResolveService service = new RenewalResolveService();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new RenewalResolveController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new RenewalResolveRequestConverter(), new JacksonJsonHttpMessageConverter())
                .addFilters(new RenewalResolveBodyLimitFilter())
                .build();
    }

    @Test
    void sameFixtureIsAPreviewAtTheInternalPath() throws Exception {
        String body = new String(getClass().getResourceAsStream("/fixtures/pricing/renewal-resolve/same-request.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        mvc.perform(post(RenewalResolveController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.preview").value(true))
                .andExpect(jsonPath("$.data.apiVersion").value("v1"))
                .andExpect(jsonPath("$.data.resolution.resolution").value("PRICED"))
                .andExpect(jsonPath("$.data.resolution.unitPrice").value(40.0000))
                .andExpect(jsonPath("$.data.resolution.flatPeriodCount").value(1));
        assertThat(RenewalResolveController.PATH).startsWith("/internal/");
        assertThat(RenewalResolveController.PATH).doesNotContain("/api/v1/pricing");
    }

    @Test
    void sameBodyTwiceHasEqualSemanticData() throws Exception {
        String body = fixture();
        JsonNode first = data(postOk(body));
        JsonNode second = data(postOk(body));
        assertThat(first).isEqualTo(second);
        assertThat(first.get("preview").asBoolean()).isTrue();
    }

    @Test
    void parallelResolvesReturnIdenticalData() throws Exception {
        String body = fixture();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> data(postOk(body))));
            }
            JsonNode first = futures.get(0).get();
            for (Future<JsonNode> future : futures) {
                assertThat(future.get()).isEqualTo(first);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void missingExitNetIs422AndDoesNotInventAPrice() throws Exception {
        MvcResult result = mvc.perform(post(RenewalResolveController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture().replace("\"exitNet\": 40.0000", "\"exitNet\": null")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("unitPrice");
    }

    @Test
    void outOfSliceModesAre422() throws Exception {
        for (String mode : List.of("LIST", "UPLIFT_AMT", "OVERRIDE", "RENEGOTIATE")) {
            mvc.perform(post(RenewalResolveController.PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixture().replace("SAME", mode)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("MODE_OUT_OF_SLICE"))
                    .andExpect(jsonPath("$.message").value("Mode is outside this slice"));
        }
    }

    @Test
    void validationMatrixRejectsMalformedBodiesWithoutAStack() throws Exception {
        assertStatus(fixture().replace("\"USD\"", "\"usd\""), 422);
        assertStatus(fixture().replace("\"quantity\": 200", "\"quantity\": -1"), 422);
        assertStatus(fixture().replace("\"termMonths\": 12", "\"termMonths\": 0"), 422);
        assertStatus(fixture().replace("\"termMonths\": 12", "\"termMonths\": 121"), 422);
        String duplicate = fixture().replace(
                "\"rungs\": [",
                "\"rungs\": [{\"kind\":\"ACCOUNT\",\"sourceId\":\"other\",\"mode\":\"UPLIFT_PCT\",\"upliftPercentPoints\":1},");
        mvc.perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RENEWAL_REQUEST"));
        String percent = fixture().replace(
                "\"mode\": \"SAME\"",
                "\"mode\": \"UPLIFT_PCT\", \"upliftPercentPoints\": 100.01");
        mvc.perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(percent))
                .andExpect(status().isUnprocessableEntity());
        MvcResult unknown = mvc.perform(post(RenewalResolveController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture().replace("\"currency\"", "\"tenantId\": \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\", \"currency\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andReturn();
        assertThat(unknown.getResponse().getContentAsString()).doesNotContain("Exception", "at com.opencrm");
        byte[] oversize = new byte[RenewalResolveLimits.MAX_BODY_BYTES + 1];
        mvc.perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(oversize))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void upliftPercentStillPricesTheWorkedExample() throws Exception {
        String body = fixture().replace("\"mode\": \"SAME\"", "\"mode\": \"UPLIFT_PCT\", \"upliftPercentPoints\": 7");
        mvc.perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preview").value(true))
                .andExpect(jsonPath("$.data.resolution.unitPrice").value(42.8));
    }

    @Test
    void responseCannotClaimItIsNotAPreview() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RenewalResolveResponse(false, "v1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertStatus(String body, int status) throws Exception {
        mvc.perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status));
    }

    private MvcResult postOk(String body) throws Exception {
        return mvc.perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static JsonNode data(MvcResult result) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(result.getResponse().getContentAsString());
        root.remove("timestamp");
        return root.get("data");
    }

    private String fixture() throws Exception {
        return new String(getClass().getResourceAsStream("/fixtures/pricing/renewal-resolve/same-request.json")
                .readAllBytes(), StandardCharsets.UTF_8);
    }
}
