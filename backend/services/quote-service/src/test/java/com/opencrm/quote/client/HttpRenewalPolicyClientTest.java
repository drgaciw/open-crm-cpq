package com.opencrm.quote.client;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.quote.QuoteContinuationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("unit")
class HttpRenewalPolicyClientTest {

    @Test
    void fetchUsesTheInternalPathAndCannotSendATenant() throws Exception {
        for (Method method : RenewalPolicyClient.class.getDeclaredMethods()) {
            assertThat(method.getParameterCount()).isEqualTo(4);
            for (java.lang.reflect.Parameter parameter : method.getParameters()) {
                assertThat(parameter.getName()).doesNotContain("tenant");
            }
        }
        assertThat(HttpRenewalPolicyClient.class.getAnnotation(Component.class)).isNull();
        for (Constructor<?> constructor : QuoteContinuationService.class.getDeclaredConstructors()) {
            for (Class<?> type : constructor.getParameterTypes()) {
                assertThat(type.getName()).doesNotContain("RenewalPolicy");
            }
        }

        UUID account = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        var builder = RestClient.builder().baseUrl("http://cam.test").requestInterceptor((request, body, execution) -> {
            request.getHeaders().setBearerAuth("synthetic-service-token");
            return execution.execute(request, body);
        });
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://cam.test/internal/v1/renewal-policies?accountId=" + account + "&asOf=2026-11-01"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-service-token"))
                .andRespond(withSuccess("""
                        {"success":true,"data":[{"id":"dddddddd-dddd-dddd-dddd-dddddddddddd","scopeKind":"ACCOUNT",
                        "scopeRef":"cccccccc-cccc-cccc-cccc-cccccccccccc","policyVersion":1,"mode":"SAME"}]}
                        """, MediaType.APPLICATION_JSON));
        var views = new HttpRenewalPolicyClient(builder.build())
                .fetchRungs(account, null, null, LocalDate.of(2026, 11, 1));
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().mode()).isEqualTo("SAME");
        server.verify();
    }

    @Test
    void aRefusalDoesNotBecomeAnEmptyPolicy() {
        var builder = RestClient.builder().baseUrl("http://cam.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/v1/renewal-policies")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> new HttpRenewalPolicyClient(builder.build())
                .fetchRungs(UUID.randomUUID(), null, null, null))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }
}
