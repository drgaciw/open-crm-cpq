package com.opencrm.quote.client;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ApprovalRoutingClient")
class ApprovalRoutingClientTest {

    private MockRestServiceServer server;
    private ApprovalRoutingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://routing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ApprovalRoutingClient(builder.build());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }


    @Test
    void quoteHistoryUsesScopedEndpointAndRetainsDecisions() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/quote/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"success":true,"data":[
                          {"id":1,"quoteId":"42","status":"PENDING","approvalTier":"DEAL_DESK"},
                          {"id":2,"quoteId":"42","status":"APPROVED","approvalTier":"DEAL_DESK"},
                          {"id":3,"quoteId":"42","status":"REJECTED","approvalTier":"CFO"}]}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.getByQuoteId(42L)).extracting(RoutingApprovalDto::getStatus)
                .containsExactly("PENDING", "APPROVED", "REJECTED");
    }

    @Test
    void quoteHistoryTreatsMissingEndpointAsUpstreamFailure() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/quote/42"))
                .andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.getByQuoteId(42L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("APPROVAL_ROUTING_HISTORY_FAILED");
    }

    @Test
    void quoteHistoryRejectsUnsuccessfulEnvelope() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/quote/42"))
                .andRespond(withSuccess("""
                        {"success":false,"message":"Unavailable"}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getByQuoteId(42L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("APPROVAL_ROUTING_HISTORY_FAILED");
    }

    @Test
    void quoteHistoryAcceptsEmptyData() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/quote/42"))
                .andRespond(withSuccess("""
                        {"success":true,"data":[]}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.getByQuoteId(42L)).isEmpty();
    }

    @Test
    @DisplayName("submit POSTs to /api/v1/approvals and parses PENDING")
    void submitParsesCreated() {
        server.expect(requestTo("http://routing.test/api/v1/approvals"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"quoteId":"42","tier":"DEAL_DESK","reason":"Quote approval submitted via BFF"}
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Approval request created",
                          "data": {
                            "id": 9001,
                            "uuid": "u-1",
                            "quoteId": "42",
                            "status": "PENDING",
                            "approvalTier": "DEAL_DESK",
                            "requesterId": "agent"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RoutingApprovalDto dto = client.submit(RoutingApprovalSubmitRequest.builder()
                .quoteId("42")
                .tier("DEAL_DESK")
                .reason("Quote approval submitted via BFF")
                .build());

        assertThat(dto.getId()).isEqualTo(9001L);
        assertThat(dto.getQuoteId()).isEqualTo("42");
        assertThat(dto.getStatus()).isEqualTo("PENDING");
        assertThat(dto.getApprovalTier()).isEqualTo("DEAL_DESK");
    }

    @Test
    void submitIncludesRampBindingWhenPresent() {
        String fingerprint = "a".repeat(64);
        server.expect(requestTo("http://routing.test/api/v1/approvals"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"quoteId":"42","tier":"SALES_VP","reason":"Ramp governance",
                         "pricingFingerprint":"%s","governingPeriod":2}
                        """.formatted(fingerprint), true))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":9002,"quoteId":"42","status":"PENDING",
                         "approvalTier":"SALES_VP","pricingFingerprint":"%s","governingPeriod":2}}
                        """.formatted(fingerprint), MediaType.APPLICATION_JSON));
        RoutingApprovalDto dto = client.submit(RoutingApprovalSubmitRequest.builder()
                .quoteId("42").tier("SALES_VP").reason("Ramp governance")
                .pricingFingerprint(fingerprint).governingPeriod(2).build());
        assertThat(dto.getPricingFingerprint()).isEqualTo(fingerprint);
        assertThat(dto.getGoverningPeriod()).isEqualTo(2);
    }

    @Test
    @DisplayName("getPending parses list payload")
    void getPendingParsesList() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/pending"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {"id": 1, "quoteId": "7", "status": "PENDING", "approvalTier": "CFO"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RoutingApprovalDto> pending = client.getPending();
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().getApprovalTier()).isEqualTo("CFO");
    }

    @Test
    @DisplayName("getById maps 404 to ResourceNotFoundException")
    void getByIdNotFound() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/55"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.getById(55L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("approve maps transport/API failure to BusinessException")
    void approveMapsEmptyBody() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/9/approve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success": false, "message": "boom"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.approve(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("APPROVAL_ROUTING_APPROVE_FAILED");
    }

    @Test
    @DisplayName("ADR-016 step 4: approval-routing's 403 reaches the caller as 403 with its code, not a 502")
    void refusalsPassThrough() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/9/reject?reason=too%20steep"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
                        {"success":false,"message":"Deciding a CFO approval needs one of the roles [ROLE_CFO]",
                         "errorCode":"APPROVER_ROLE_REQUIRED"}
                        """));

        assertThatThrownBy(() -> client.reject(9L, "too steep"))
                .isInstanceOfSatisfying(BusinessException.class, refused -> {
                    assertThat(refused.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(refused.getErrorCode()).isEqualTo("APPROVER_ROLE_REQUIRED");
                    assertThat(refused.getMessage()).contains("ROLE_CFO");
                });
    }

    @Test
    @DisplayName("ADR-016 step 4: approve and reject send no approver; approval-routing takes it from the token")
    void decisionsSendNoIdentity() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/9/approve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":9,"quoteId":"42","status":"APPROVED","approverId":"oid-1"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.approve(9L).getApproverId()).isEqualTo("oid-1");
    }
}
