package com.opencrm.quote.client;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

/**
 * HTTP client for approval-routing (canonical approval writer for S2C-12).
 */
@Slf4j
@Component
public class ApprovalRoutingClient {

    private static final ParameterizedTypeReference<ApiResponse<RoutingApprovalDto>> SINGLE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<RoutingApprovalDto>>> LIST =
            new ParameterizedTypeReference<>() {};

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
                    .body(request)
                    .retrieve()
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

    public RoutingApprovalDto approve(Long approvalId, String approverId) {
        log.debug("Approving routing approval {} by {}", approvalId, approverId);
        try {
            ApiResponse<RoutingApprovalDto> response = approvalRoutingRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/approvals/{id}/approve")
                            .queryParam("approverId", approverId)
                            .build(approvalId))
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("ApprovalRequest", approvalId);
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

    public RoutingApprovalDto reject(Long approvalId, String approverId, String reason) {
        log.debug("Rejecting routing approval {} by {}", approvalId, approverId);
        String safeReason = (reason == null || reason.isBlank()) ? "Rejected via quote BFF" : reason;
        try {
            ApiResponse<RoutingApprovalDto> response = approvalRoutingRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/approvals/{id}/reject")
                            .queryParam("approverId", approverId)
                            .queryParam("reason", safeReason)
                            .build(approvalId))
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ResourceNotFoundException("ApprovalRequest", approvalId);
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

    private static RoutingApprovalDto requireData(ApiResponse<RoutingApprovalDto> response, String errorCode) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null && response.getMessage() != null
                    ? response.getMessage()
                    : "approval-routing returned an empty response";
            throw new BusinessException(message, HttpStatus.BAD_GATEWAY, errorCode);
        }
        return response.getData();
    }
}
