package com.opencrm.contract.documents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(classes = AmendmentPricingHttpHoldTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"opencrm.kafka.inbox.enabled=false", "opencrm.outbox.relay.enabled=false", "opencrm.security.mode=dev"})
class AmendmentPricingHttpHoldTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class})
    @Import(AmendmentController.class)
    static class App { }

    @LocalServerPort int port;
    @MockitoBean AmendmentDocumentService documents;
    @MockitoBean AmendmentRevisionService revisions;

    @Test
    void directHttpCannotInstallUnverifiedPricing() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/amendments/"
                        + UUID.randomUUID() + "/revisions/1/pricing"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "unverified")
                .header("X-Tenant-Id", UUID.randomUUID().toString())
                .header("X-Caller-Id", "caller")
                .header("X-Actor-Id", "seller")
                .header("X-Actor-Type", "USER")
                .header("X-Actor-Role", "seller")
                .header("X-Correlation-Id", "corr")
                .POST(HttpRequest.BodyPublishers.ofString("{\"version\":0,\"lifecycleChangeRevision\":1}"))
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
        verifyNoInteractions(documents, revisions);
    }
}
