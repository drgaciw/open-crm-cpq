package com.opencrm.crm.guided;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Configuration session starter")
class HttpConfigurationSessionStarterTest {

    @Test
    @DisplayName("a blank base URL records the product and options and does not call HTTP")
    void blankBaseUrlRecordsIntent() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://configuration.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpConfigurationSessionStarter starter = new HttpConfigurationSessionStarter("  ", builder.build());

        StartedConfiguration started = starter.start("prod-starter", Map.of("edition", "standard"));

        assertThat(started.recordedLocally()).isTrue();
        assertThat(started.configurationSessionId()).isNull();
        assertThat(started.productId()).isEqualTo("prod-starter");
        assertThat(started.selections()).containsExactly(Map.entry("edition", "standard"));
        assertThat(starter.recordedIntents()).singleElement().satisfies(intent -> {
            assertThat(intent.productId()).isEqualTo("prod-starter");
            assertThat(intent.selections()).containsEntry("edition", "standard");
        });
        server.verify();
    }

    @Test
    @DisplayName("a configured base URL creates a session and posts each pre-selected option")
    void remoteStartPostsCreateAndSelections() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://configuration.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://configuration.test/api/v1/configurations?productId=prod-governed-suite"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":42,"productId":"prod-governed-suite","status":"IN_PROGRESS"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                "http://configuration.test/api/v1/configurations/42/selections?optionKey=edition&optionValue=enterprise"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":42,"selections":{"edition":"enterprise"}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                "http://configuration.test/api/v1/configurations/42/selections?optionKey=support&optionValue=premium"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":42,"selections":{"edition":"enterprise","support":"premium"}}}
                        """, MediaType.APPLICATION_JSON));

        Map<String, String> selections = new LinkedHashMap<>();
        selections.put("edition", "enterprise");
        selections.put("support", "premium");
        HttpConfigurationSessionStarter starter =
                new HttpConfigurationSessionStarter("http://configuration.test", builder.build());

        StartedConfiguration started = starter.start("prod-governed-suite", selections);

        assertThat(started.recordedLocally()).isFalse();
        assertThat(started.configurationSessionId()).isEqualTo(42L);
        assertThat(started.selections()).containsExactly(
                Map.entry("edition", "enterprise"),
                Map.entry("support", "premium"));
        assertThat(starter.recordedIntents()).singleElement()
                .extracting(RecordedSessionIntent::productId)
                .isEqualTo("prod-governed-suite");
        server.verify();
    }

    @Test
    @DisplayName("a configuration HTTP failure is reported and nothing is recorded")
    void remoteFailureDoesNotRecord() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://configuration.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://configuration.test/api/v1/configurations?productId=prod-starter"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        HttpConfigurationSessionStarter starter =
                new HttpConfigurationSessionStarter("http://configuration.test", builder.build());

        assertThatThrownBy(() -> starter.start("prod-starter", Map.of("edition", "standard")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CONFIGURATION_SESSION_FAILED");
        assertThat(starter.recordedIntents()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("a response without a session id fails closed")
    void missingSessionIdFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://configuration.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://configuration.test/api/v1/configurations?productId=prod-starter"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":false,\"data\":{}}", MediaType.APPLICATION_JSON));
        HttpConfigurationSessionStarter starter =
                new HttpConfigurationSessionStarter("http://configuration.test/", builder.build());

        assertThatThrownBy(() -> starter.start("prod-starter", Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CONFIGURATION_SESSION_FAILED");
        server.verify();
    }
}
