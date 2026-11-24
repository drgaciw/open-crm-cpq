package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client for approval-routing (canonical approval writer for S2C-12).
 * <p>
 * It sends no identity: approval-routing records the caller of the relayed user token as requester or approver, and
 * decides whether their role may decide the tier (ADR-016 decisions 6 and 7). Its refusals (401, 403, 409) reach the
 * caller with approval-routing's status, code and message instead of turning into a 502.
 */
@Slf4j
@Component
public class ApprovalRoutingClient {

    private static final ParameterizedTypeReference<ApiResponse<RoutingApprovalDto>> SINGLE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<RoutingApprovalDto>>> LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<RoutingApprovalHistoryEntry>>> HISTORY =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<RoutingApprovalHistoryEntry>> HISTORY_ENTRY =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<QuotePolicyVerdictDto>> VERDICT =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<RoutingPolicyExceptionDto>> EXCEPTION =
            new ParameterizedTypeReference<>() {};
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient approvalRoutingRestClient;

    public ApprovalRoutingClient(
            @Qualifier("approvalRoutingRestClient") RestClient approvalRoutingRestClient) {
        this.approvalRoutingRestClient = approvalRoutingRestClient;
    }

    public RoutingApprovalDto submit(RoutingApprovalSubmitRequest request) {
        log.debug("Submitting approval to routing for quoteId={} tier={}", request.getQuoteId(), request.getTier());
        try {
            ApiResponse<RoutingApprovalDto> response = approvalRoutingRestClient.post()
                    .uri("/api/v1/approvals")
                    .headers(headers -> {
                        if (request.getIdempotencyKey() != null) {
                            headers.set("Idempotency-Key", request.getIdempotencyKey());
                        }
                    })
                    .body(request)
                    .retrieve()
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "APPROVAL_ROUTING_SUBMIT_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing submit failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_SUBMIT_FAILED"
                        );
                    })
                    .body(SINGLE);
            return requireData(response, "APPROVAL_ROUTING_SUBMIT_FAILED");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing submit: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    public QuotePolicyVerdictDto evaluateQuote(QuotePolicyEvaluationRequest request) {
        log.debug("Evaluating quote {} version {} against commercial policy", request.getQuoteId(), request.getQuoteVersion());
        try {
            ApiResponse<QuotePolicyVerdictDto> response = approvalRoutingRestClient.post()
                    .uri("/api/v1/commercial-policies/evaluate")
                    .body(request)
                    .retrieve()
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "POLICY_EVALUATION_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing evaluation failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "POLICY_EVALUATION_FAILED"
                        );
                    })
                    .body(VERDICT);
            if (response == null || !response.isSuccess() || response.getData() == null
                    || response.getData().getVerdict() == null) {
                throw new BusinessException(
                        "approval-routing evaluation returned an empty verdict",
                        HttpStatus.BAD_GATEWAY,
                        "POLICY_EVALUATION_FAILED");
            }
            return response.getData();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing evaluation: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    /** Canonical RecordException write. Identity stays on the relayed token. */
    public RoutingPolicyExceptionDto recordPolicyException(RoutingRecordPolicyExceptionRequest request) {
        log.debug("Recording policy exception for quote {} version {}", request.getQuoteId(), request.getQuoteVersion());
        try {
            ApiResponse<RoutingPolicyExceptionDto> response = approvalRoutingRestClient.post()
                    .uri("/api/v1/policy-exceptions")
                    .body(request)
                    .retrieve()
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "POLICY_EXCEPTION_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing exception record failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "POLICY_EXCEPTION_FAILED"
                        );
                    })
                    .body(EXCEPTION);
            if (response == null || !response.isSuccess() || response.getData() == null || response.getData().getId() == null) {
                throw new BusinessException(
                        "approval-routing exception record returned an empty body",
                        HttpStatus.BAD_GATEWAY,
                        "POLICY_EXCEPTION_FAILED");
            }
            return response.getData();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing exception record: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    public RoutingApprovalDto getById(Long approvalId) {
        log.debug("Fetching approval {} from routing", approvalId);
        try {
            ApiResponse<RoutingApprovalDto> response = approvalRoutingRestClient.get()
                    .uri("/api/v1/approvals/{id}", approvalId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("ApprovalRequest", approvalId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing get failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_GET_FAILED"
                        );
                    })
                    .body(SINGLE);
            return requireData(response, "APPROVAL_ROUTING_GET_FAILED");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing get: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    public List<RoutingApprovalDto> getByQuoteId(Long quoteId) {
        log.debug("Fetching approval history for quote {} from routing", quoteId);
        try {
            ApiResponse<List<RoutingApprovalDto>> response = approvalRoutingRestClient.get()
                    .uri("/api/v1/approvals/quote/{quoteId}", quoteId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing history failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_HISTORY_FAILED"
                        );
                    })
                    .body(LIST);
            if (response == null || !response.isSuccess()) {
                String message = response != null && response.getMessage() != null
                        ? response.getMessage()
                        : "approval-routing history returned an empty response";
                throw new BusinessException(message, HttpStatus.BAD_GATEWAY, "APPROVAL_ROUTING_HISTORY_FAILED");
            }
            return response.getData() != null ? response.getData() : Collections.emptyList();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing history: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    public List<RoutingApprovalDto> getPending() {
        log.debug("Fetching pending approvals from routing");
        try {
            ApiResponse<List<RoutingApprovalDto>> response = approvalRoutingRestClient.get()
                    .uri("/api/v1/approvals/pending")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing pending failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_PENDING_FAILED"
                        );
                    })
                    .body(LIST);
            if (response == null || !response.isSuccess()) {
                String message = response != null && response.getMessage() != null
                        ? response.getMessage()
                        : "approval-routing pending returned an empty response";
                throw new BusinessException(message, HttpStatus.BAD_GATEWAY, "APPROVAL_ROUTING_PENDING_FAILED");
            }
            return response.getData() != null ? response.getData() : Collections.emptyList();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing pending: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    public List<RoutingApprovalDto> submitMatrix(RoutingMatrixSubmitRequest request) {
        log.debug("Submitting matrix approval for quoteId={}", request.getQuoteId());
        try {
            ApiResponse<List<RoutingApprovalDto>> response = approvalRoutingRestClient.post()
                    .uri("/api/v1/approvals/matrix-submissions")
                    .body(request)
                    .retrieve()
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "APPROVAL_ROUTING_SUBMIT_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing matrix submit failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_SUBMIT_FAILED");
                    })
                    .body(LIST);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new BusinessException("approval-routing matrix submit returned an empty response",
                        HttpStatus.BAD_GATEWAY, "APPROVAL_ROUTING_SUBMIT_FAILED");
            }
            return response.getData();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing matrix submit: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE");
        }
    }

    public List<RoutingApprovalDto> listInbox(String tier) {
        log.debug("Fetching approval inbox tier={}", tier);
        try {
            ApiResponse<List<RoutingApprovalDto>> response = approvalRoutingRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/v1/approvals/inbox");
                        if (tier != null && !tier.isBlank()) {
                            uriBuilder.queryParam("tier", tier);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "APPROVAL_ROUTING_PENDING_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing inbox failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_PENDING_FAILED");
                    })
                    .body(LIST);
            if (response == null || !response.isSuccess()) {
                throw new BusinessException("approval-routing inbox returned an empty response",
                        HttpStatus.BAD_GATEWAY, "APPROVAL_ROUTING_PENDING_FAILED");
            }
            return response.getData() != null ? response.getData() : Collections.emptyList();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing inbox: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE");
        }
    }

    public RoutingApprovalDto approve(Long approvalId) {
        return approve(approvalId, null);
    }

    public RoutingApprovalDto approve(Long approvalId, String comment) {
        return approve(approvalId, comment, null);
    }

    public RoutingApprovalDto approve(Long approvalId, String comment, Long quoteVersion) {
        log.debug("Approving routing approval {}", approvalId);
        try {
            ApiResponse<RoutingApprovalDto> response = approvalRoutingRestClient.post()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/v1/approvals/{id}/approve");
                        if (comment != null && !comment.isBlank()) {
                            builder.queryParam("comment", comment);
                        }
                        if (quoteVersion != null) {
                            builder.queryParam("quoteVersion", quoteVersion);
                        }
                        return builder.build(approvalId);
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("ApprovalRequest", approvalId);
                    })
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "APPROVAL_ROUTING_APPROVE_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing approve failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_APPROVE_FAILED"
                        );
                    })
                    .body(SINGLE);
            return requireData(response, "APPROVAL_ROUTING_APPROVE_FAILED");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing approve: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    public RoutingApprovalDto reject(Long approvalId, String reason) {
        return reject(approvalId, reason, null);
    }

    public RoutingApprovalDto reject(Long approvalId, String reason, Long quoteVersion) {
        log.debug("Rejecting routing approval {}", approvalId);
        String safeReason = (reason == null || reason.isBlank()) ? "Rejected via quote BFF" : reason;
        try {
            ApiResponse<RoutingApprovalDto> response = approvalRoutingRestClient.post()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/api/v1/approvals/{id}/reject")
                                .queryParam("reason", safeReason);
                        if (quoteVersion != null) {
                            builder.queryParam("quoteVersion", quoteVersion);
                        }
                        return builder.build(approvalId);
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("ApprovalRequest", approvalId);
                    })
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "APPROVAL_ROUTING_REJECT_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing reject failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_REJECT_FAILED"
                        );
                    })
                    .body(SINGLE);
            return requireData(response, "APPROVAL_ROUTING_REJECT_FAILED");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing reject: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    public RoutingApprovalDto escalate(Long approvalId) {
        return escalate(approvalId, null);
    }

    public RoutingApprovalDto escalate(Long approvalId, Long quoteVersion) {
        log.debug("Escalating routing approval {}", approvalId);
        try {
            ApiResponse<RoutingApprovalDto> response = approvalRoutingRestClient.post()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/v1/approvals/{id}/escalate");
                        if (quoteVersion != null) {
                            builder.queryParam("quoteVersion", quoteVersion);
                        }
                        return builder.build(approvalId);
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("ApprovalRequest", approvalId);
                    })
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "APPROVAL_ROUTING_ESCALATE_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing escalate failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_ESCALATE_FAILED"
                        );
                    })
                    .body(SINGLE);
            return requireData(response, "APPROVAL_ROUTING_ESCALATE_FAILED");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing escalate: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    /** GetApprovalHistory. Refusals (401, 403) pass through. The quote BFF has already checked the quote tenant. */
    public List<RoutingApprovalHistoryEntry> getApprovalHistory(Long quoteId) {
        log.debug("Fetching approval timeline for quote {} from routing", quoteId);
        try {
            ApiResponse<List<RoutingApprovalHistoryEntry>> response = approvalRoutingRestClient.get()
                    .uri("/api/v1/approvals/quote/{quoteId}/history", quoteId)
                    .retrieve()
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "APPROVAL_ROUTING_HISTORY_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing history failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_HISTORY_FAILED"
                        );
                    })
                    .body(HISTORY);
            if (response == null || !response.isSuccess()) {
                String message = response != null && response.getMessage() != null
                        ? response.getMessage()
                        : "approval-routing history returned an empty response";
                throw new BusinessException(message, HttpStatus.BAD_GATEWAY, "APPROVAL_ROUTING_HISTORY_FAILED");
            }
            return response.getData() != null ? response.getData() : Collections.emptyList();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing history: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    public RoutingApprovalHistoryEntry addComment(Long approvalId, RoutingApprovalCommentRequest request) {
        log.debug("Adding a case comment on routing approval {}", approvalId);
        try {
            ApiResponse<RoutingApprovalHistoryEntry> response = approvalRoutingRestClient.post()
                    .uri("/api/v1/approvals/{id}/comments", approvalId)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("ApprovalRequest", approvalId);
                    })
                    .onStatus(ApprovalRoutingClient::isRefusal, (req, res) -> {
                        throw refusal(res, "APPROVAL_ROUTING_COMMENT_FAILED");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BusinessException(
                                "approval-routing comment failed with HTTP " + res.getStatusCode().value(),
                                HttpStatus.BAD_GATEWAY,
                                "APPROVAL_ROUTING_COMMENT_FAILED"
                        );
                    })
                    .body(HISTORY_ENTRY);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                String message = response != null && response.getMessage() != null
                        ? response.getMessage()
                        : "approval-routing returned an empty comment";
                throw new BusinessException(message, HttpStatus.BAD_GATEWAY, "APPROVAL_ROUTING_COMMENT_FAILED");
            }
            return response.getData();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call approval-routing comment: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "APPROVAL_ROUTING_UNAVAILABLE"
            );
        }
    }

    private static RoutingApprovalDto requireData(ApiResponse<RoutingApprovalDto> response, String errorCode) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null && response.getMessage() != null
                    ? response.getMessage()
                    : "approval-routing returned an empty response";
            throw new BusinessException(message, HttpStatus.BAD_GATEWAY, errorCode);
        }
        return response.getData();
    }

    /** 401, 403 and 409 are approval-routing's decisions about the caller or the request, not a gateway failure. */
    private static boolean isRefusal(HttpStatusCode status) {
        int code = status.value();
        return code == 401 || code == 403 || code == 409;
    }

    /** approval-routing's refusal with its status, error code and message, e.g. 403 {@code APPROVER_ROLE_REQUIRED}. */
    private static BusinessException refusal(ClientHttpResponse response, String fallbackCode) throws IOException {
        HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
        String message = "approval-routing refused the request (HTTP " + status.value() + ")";
        String code = fallbackCode;
        try {
            JsonNode body = JSON.readTree(response.getBody());
            if (body != null && body.hasNonNull("message")) {
                message = body.get("message").asText();
            }
            if (body != null && body.hasNonNull("errorCode")) {
                code = body.get("errorCode").asText();
            }
        } catch (IOException unreadable) {
            log.debug("approval-routing refusal without a readable body", unreadable);
        }
        return new BusinessException(message, status, code);
    }
}
