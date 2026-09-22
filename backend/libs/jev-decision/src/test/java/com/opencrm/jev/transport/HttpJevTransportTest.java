package com.opencrm.jev.transport;

import com.opencrm.jev.error.JevException;
import com.opencrm.jev.question.JevQuestions;
import com.opencrm.jev.state.JevFeatureSchema;
import com.opencrm.jev.state.JevState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("HttpJevTransport")
class HttpJevTransportTest {

    private static final String URL = "https://jev.example.test/v1/decisions";
    private static final String ANSWERS = """
            {"answers":{"same":{"type":"noul","noul":0.9}},"usage":{"cost":0.00002}}""";

    private static final JevFeatureSchema SCHEMA =
            JevFeatureSchema.named("duplicate-pair", 1).number("similarity").bool("same_country").build();

    private MockRestServiceServer server;
    private RestClient restClient;
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    private HttpJevTransport transport(JevWireFormat format) {
        return new HttpJevTransport(restClient, URI.create(URL), "secret-token", format,
                new RetryPolicy(2, 500, 5_000, () -> 0.0), 65_536, sleeps::add);
    }

    private static JevTransport.Request request() {
        JevState state = JevState.builder(SCHEMA).put("similarity", 0.82).put("same_country", true).build();
        return new JevTransport.Request("duplicate-hints", "typesafe/jev-1.13", state,
                List.of(JevQuestions.noul("same", "Same entity?", "yes", "no")));
    }

    @Test
    @DisplayName("NATIVE sends the model, state as JSON text, and a bearer token")
    void nativeWireFormat() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-token"))
                .andExpect(content().json("""
                        {"model":"typesafe/jev-1.13",
                         "state":"{\\"same_country\\":true,\\"similarity\\":0.82}",
                         "questions":{"same":{"type":"noul","instructions":"Same entity?",
                                              "criteria":{"true":"yes","false":"no"}}}}"""))
                .andRespond(withSuccess(ANSWERS, MediaType.APPLICATION_JSON));

        JevTransport.Response response = transport(JevWireFormat.NATIVE).decide(request());

        assertThat(response.answers().path("same").path("noul").asDouble()).isEqualTo(0.9);
        assertThat(response.usage().path("cost").asDouble()).isEqualTo(0.00002);
        assertThat(response.attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("FACADE sends state as an object and omits the model")
    void facadeWireFormat() {
        server.expect(requestTo(URL))
                .andExpect(content().json("""
                        {"state":{"same_country":true,"similarity":0.82},
                         "questions":{"same":{"type":"noul"}}}"""))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request).getBodyAsString())
                        .doesNotContain("\"model\""))
                .andRespond(withSuccess(ANSWERS, MediaType.APPLICATION_JSON));

        transport(JevWireFormat.FACADE).decide(request());
    }

    @Test
    @DisplayName("AUTOJEV unwraps the code/message/data envelope")
    void autojevEnvelope() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\",\"data\":" + ANSWERS + "}",
                        MediaType.APPLICATION_JSON));

        JevTransport.Response response = transport(JevWireFormat.AUTOJEV).decide(request());

        assertThat(response.answers().has("same")).isTrue();
    }

    @Test
    @DisplayName("AUTOJEV treats a non-zero envelope code as a server failure")
    void autojevErrorCode() {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(withSuccess("{\"code\":5001,\"message\":\"busy\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> transport(JevWireFormat.AUTOJEV).decide(request()))
                .isInstanceOf(JevException.Server.class);
    }

    @Test
    @DisplayName("401 is an authentication failure and is not retried")
    void unauthorizedIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> transport(JevWireFormat.NATIVE).decide(request()))
                .isInstanceOf(JevException.Authentication.class);
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("422 is a bad request and is not retried")
    void unprocessableIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(422)));

        assertThatThrownBy(() -> transport(JevWireFormat.NATIVE).decide(request()))
                .isInstanceOf(JevException.BadRequest.class);
    }

    @Test
    @DisplayName("429 honours Retry-After and then succeeds")
    void rateLimitThenSuccess() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "2");
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(ANSWERS, MediaType.APPLICATION_JSON));

        JevTransport.Response response = transport(JevWireFormat.NATIVE).decide(request());

        assertThat(response.attempts()).isEqualTo(2);
        assertThat(sleeps).containsExactly(2_000L);
    }

    @Test
    @DisplayName("5xx is retried up to the limit and then surfaces")
    void serverErrorExhaustsRetries() {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> transport(JevWireFormat.NATIVE).decide(request()))
                .isInstanceOf(JevException.Server.class);
        assertThat(sleeps).containsExactly(500L, 1_000L);
    }

    @Test
    @DisplayName("a body that is not JSON is an invalid answer, not retried")
    void malformedBody() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> transport(JevWireFormat.NATIVE).decide(request()))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("a response without an answers object is an invalid answer")
    void missingAnswers() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("{\"usage\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> transport(JevWireFormat.NATIVE).decide(request()))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("an oversized request is refused before anything is sent")
    void oversizedRequest() {
        HttpJevTransport tiny = new HttpJevTransport(restClient, URI.create(URL), "secret-token",
                JevWireFormat.NATIVE, new RetryPolicy(0, 0, 0), 32, sleeps::add);

        assertThatThrownBy(() -> tiny.decide(request())).isInstanceOf(JevException.BadRequest.class);
    }
}
