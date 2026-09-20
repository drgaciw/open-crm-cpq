package com.opencrm.jev.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.jev.error.JevException;
import com.opencrm.jev.question.JevQuestion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Sends decision requests over HTTPS. JSON is written and read as text with a private mapper so the
 * transport does not depend on which message converters the host service has registered.
 */
public final class HttpJevTransport implements JevTransport {

    /** Test seam for backoff waits. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final URI endpoint;
    private final String apiKey;
    private final JevWireFormat format;
    private final RetryPolicy retryPolicy;
    private final int maxBodyBytes;
    private final Sleeper sleeper;

    public HttpJevTransport(RestClient restClient, URI endpoint, String apiKey, JevWireFormat format,
                            RetryPolicy retryPolicy, int maxBodyBytes) {
        this(restClient, endpoint, apiKey, format, retryPolicy, maxBodyBytes, Thread::sleep);
    }

    public HttpJevTransport(RestClient restClient, URI endpoint, String apiKey, JevWireFormat format,
                            RetryPolicy retryPolicy, int maxBodyBytes, Sleeper sleeper) {
        this.restClient = restClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.format = format;
        this.retryPolicy = retryPolicy;
        this.maxBodyBytes = maxBodyBytes;
        this.sleeper = sleeper;
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public String wireFormat() {
        return format.name();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Response decide(Request request) {
        String body = requestBody(request);
        if (body.getBytes(StandardCharsets.UTF_8).length > maxBodyBytes) {
            throw new JevException.BadRequest("Request body exceeds " + maxBodyBytes + " bytes", 0);
        }
        for (int attempt = 0; ; attempt++) {
            try {
                return parse(post(body), attempt + 1);
            } catch (JevException failure) {
                if (!retryPolicy.shouldRetry(failure, attempt)) {
                    throw failure;
                }
                pause(retryPolicy.delayMs(failure, attempt), failure);
            }
        }
    }

    private void pause(long millis, JevException failure) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }

    private record RawReply(int status, String retryAfter, String body) {
    }

    private String post(String body) {
        RawReply reply;
        try {
            reply = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((req, res) -> new RawReply(
                            res.getStatusCode().value(),
                            res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                            readCapped(res.getBody())));
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                throw new JevException.Timeout("Jev request timed out", e);
            }
            throw new JevException.Connection("Jev upstream unreachable", e);
        } catch (RestClientException e) {
            throw new JevException.Connection("Jev request failed before a response", e);
        }
        int status = reply.status();
        if (status >= 200 && status < 300) {
            return reply.body();
        }
        // The upstream body is deliberately not echoed into the message: it may repeat request content.
        String message = "Jev upstream returned HTTP " + status;
        if (status == 401 || status == 403) {
            throw new JevException.Authentication(message, status);
        }
        if (status == 429) {
            throw new JevException.RateLimit(message, retryAfterSeconds(reply.retryAfter()));
        }
        if (status >= 500) {
            throw new JevException.Server(message, status);
        }
        throw new JevException.BadRequest(message, status);
    }

    private String readCapped(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        byte[] bytes = in.readNBytes(maxBodyBytes + 1);
        if (bytes.length > maxBodyBytes) {
            throw new IOException("Jev response exceeds " + maxBodyBytes + " bytes");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Long retryAfterSeconds(String header) {
        if (header == null) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    String requestBody(Request request) {
        ObjectNode root = JSON.createObjectNode();
        if (format != JevWireFormat.FACADE) {
            root.put("model", request.model());
        }
        if (format == JevWireFormat.NATIVE) {
            root.put("state", request.state().canonicalJson());
        } else {
            root.set("state", JSON.valueToTree(request.state().values()));
        }
        ObjectNode questions = root.putObject("questions");
        for (JevQuestion question : request.questions()) {
            questions.set(question.id(), questionNode(question));
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new JevException.BadRequest("Could not serialise Jev request", 0);
        }
    }

    private static ObjectNode questionNode(JevQuestion question) {
        ObjectNode node = JSON.createObjectNode();
        node.put("type", question.type());
        node.put("instructions", question.instructions());
        switch (question) {
            case JevQuestion.Choice choice -> {
                ObjectNode criteria = node.putObject("criteria");
                choice.criteria().forEach(criteria::put);
            }
            case JevQuestion.Noul noul -> {
                if (noul.trueCriterion() != null || noul.falseCriterion() != null) {
                    ObjectNode criteria = node.putObject("criteria");
                    if (noul.trueCriterion() != null) {
                        criteria.put("true", noul.trueCriterion());
                    }
                    if (noul.falseCriterion() != null) {
                        criteria.put("false", noul.falseCriterion());
                    }
                }
            }
            case JevQuestion.Score score -> {
                ArrayNode criteria = node.putArray("criteria");
                score.criteria().forEach(criteria::add);
            }
        }
        return node;
    }

    private Response parse(String body, int attempts) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (JsonProcessingException e) {
            throw new JevException.InvalidAnswer("Jev response is not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new JevException.InvalidAnswer("Jev response is not a JSON object");
        }
        JsonNode payload = root;
        if (format == JevWireFormat.AUTOJEV) {
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new JevException.Server("AutoJev envelope reported code " + code, 0);
            }
            payload = root.path("data");
        }
        JsonNode answers = payload.get("answers");
        if (answers == null || !answers.isObject()) {
            throw new JevException.InvalidAnswer("Jev response contains no answers object");
        }
        return new Response(answers, payload.get("usage"), attempts);
    }
}
