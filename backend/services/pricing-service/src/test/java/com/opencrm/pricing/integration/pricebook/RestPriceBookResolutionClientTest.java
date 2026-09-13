package com.opencrm.pricing.integration.pricebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("RestPriceBookResolutionClient")
class RestPriceBookResolutionClientTest {

    private MockRestServiceServer server;
    private RestPriceBookResolutionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://pricebook.local");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestPriceBookResolutionClient(builder.build(), new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void resolveBatch_returnsData() {
        server.expect(requestTo("http://pricebook.local/internal/v1/price-books/resolve-batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"success":true,"data":{"priceBookId":9,"priceBookCurrency":"USD",
                        "entries":{"PROD-1":{"entryMatched":true,"listPrice":10.00,"currency":"USD"}}}}
                        """,
                        MediaType.APPLICATION_JSON));

        ResolveBatchResponse response = client.resolveBatch("NA", List.of("PROD-1"));
        assertThat(response.getPriceBookId()).isEqualTo(9L);
        assertThat(response.getEntries().get("PROD-1").getListPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        server.verify();
    }

    @Test
    void resolveBatch_nullProductIds_sendsEmptyList() {
        server.expect(requestTo("http://pricebook.local/internal/v1/price-books/resolve-batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"success":true,"data":{"priceBookId":1,"priceBookCurrency":"USD","entries":{}}}
                        """,
                        MediaType.APPLICATION_JSON));

        ResolveBatchResponse response = client.resolveBatch("EU", null);
        assertThat(response.getEntries()).isEmpty();
        server.verify();
    }

    @Test
    void resolveBatch_errorStatus_mapsBusinessException() {
        server.expect(requestTo("http://pricebook.local/internal/v1/price-books/resolve-batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withResourceNotFound().body(
                        """
                        {"success":false,"message":"No book","errorCode":"PRICEBOOK_NOT_FOUND"}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.resolveBatch("NA", List.of("X")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("PRICEBOOK_NOT_FOUND"));
        server.verify();
    }

    @Test
    void resolveBatch_emptyData_throws() {
        server.expect(requestTo("http://pricebook.local/internal/v1/price-books/resolve-batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.resolveBatch("NA", List.of("X")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("PRICEBOOK_CLIENT_ERROR"));
        server.verify();
    }
}
