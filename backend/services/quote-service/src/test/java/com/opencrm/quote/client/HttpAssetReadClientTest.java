package com.opencrm.quote.client;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("unit")
class HttpAssetReadClientTest {

    @Test
    void readsTheAccountInstalledBaseAsOfADate() {
        assertThat(HttpAssetReadClient.class.getAnnotation(Component.class)).isNull();
        var builder = RestClient.builder().baseUrl("http://cam.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://cam.test/api/v1/assets?accountId=synthetic-customer&asOf=2029-12-31"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"success":true,"data":[
                          {"assetId":"asset-active","accountId":"synthetic-customer","contractLineId":"source-line",
                           "quantity":7.0000,"status":"ACTIVE"},
                          {"assetId":"asset-dead","accountId":"synthetic-customer","contractLineId":"source-line",
                           "quantity":9,"status":"SUPERSEDED"}]}
                        """, MediaType.APPLICATION_JSON));
        var rows = new HttpAssetReadClient(builder.build())
                .installedBase("synthetic-customer", LocalDate.of(2029, 12, 31));
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().assetId()).isEqualTo("asset-active");
        assertThat(rows.getFirst().quantity()).isEqualByComparingTo("7");
        assertThat(rows.get(1).status()).isEqualTo("SUPERSEDED");
        server.verify();
    }

    @Test
    void aRefusalDoesNotBecomeAnEmptyInstalledBase() {
        var builder = RestClient.builder().baseUrl("http://cam.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v1/assets")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> new HttpAssetReadClient(builder.build())
                .installedBase("synthetic-customer", LocalDate.of(2029, 12, 31)))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }
}
