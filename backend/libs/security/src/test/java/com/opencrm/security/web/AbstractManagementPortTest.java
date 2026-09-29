package com.opencrm.security.web;

import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * A real servlet container with the actuator and the Prometheus registry, for the questions MockMvc cannot answer:
 * which of two ports serves and which chain governs it. The service has one user endpoint, {@code /api/v1/ping}; the
 * database, JPA and Kafka auto-configurations are excluded because nothing here needs them. Subclasses set
 * {@code management.server.port}, or leave it unset.
 */
abstract class AbstractManagementPortTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
            "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
    })
    @Import({TestJwtDecoderConfiguration.class, Api.class})
    static class App {
    }

    @RestController
    static class Api {
        @GetMapping("/api/v1/ping")
        String ping() {
            return "pong";
        }
    }

    /** The properties every subclass sets: a tenant issuer, and the endpoint list the services ship. */
    static final String ISSUER = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER;
    static final String EXPOSURE = "management.endpoints.web.exposure.include=health,info,metrics,prometheus";

    @Autowired
    private Environment environment;

    /** The port of the service itself. */
    String servicePort() {
        return environment.getProperty("local.server.port");
    }

    /** The management port; the service port when the actuator is not on a port of its own. */
    String managementPort() {
        return environment.getProperty("local.management.port", servicePort());
    }

    static String userToken() {
        return TestTokens.user("alice", "CPQ.User").bearer();
    }

    /** {@code GET} on {@code localhost:<port><path>}, with an optional {@code Authorization} header. */
    static Response get(String port, String path, String authorization) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body() == null ? "" : response.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    record Response(int status, String body) {
    }
}