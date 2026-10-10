package com.opencrm.quote.client;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@Tag("unit")
class SubscriptionScheduleClientTest {
    @Test void internalBoundaryUsesServiceIdentityAndDecodesAdditiveScheduleFields() {
        var auth=org.mockito.Mockito.mock(com.opencrm.security.client.OutboundAuth.class);
        org.mockito.Mockito.when(auth.serviceToken()).thenReturn((request,body,execution) -> {
            request.getHeaders().setBearerAuth("synthetic-service-token");return execution.execute(request,body);
        });
        var builder=new com.opencrm.quote.config.RestClientConfig().subscriptionScheduleRestClient("http://subscription.test",auth).mutate();
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://subscription.test/internal/v1/subscriptions/by-number/SUB-R3/ramp-schedule"))
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION,"Bearer synthetic-service-token"))
                .andRespond(withSuccess("""
                    {"success":true,"data":{"subscriptionNumber":"SUB-R3","customerId":"synthetic-customer","currencyCode":"USD",
                    "commercialRevision":7,"status":"ACTIVE","startDate":"2027-01-01","endDate":"2029-12-31","futureAdditive":true,
                    "lines":[{"acceptedLineUuid":"source-line","productExternalId":"synthetic-product","lineItemType":"SAAS_SEAT_NAMED",
                    "productType":"SAAS_SEAT_NAMED","billingFrequency":"ANNUAL","billingAnchorDate":"2027-01-01","provenance":"ACCEPTED_PRICED",
                    "periods":[{"sequence":1,"startMonth":1,"months":36,"quantity":50,"netUnitPrice":40,"netMonthly":2000,"periodAmount":72000,
                    "startDate":"2027-01-01","endDate":"2029-12-31","contractedEndDate":"2029-12-31","applied":true,"retired":false}]}]}}
                    """,MediaType.APPLICATION_JSON));
        var result=new SubscriptionScheduleClient(builder.build()).byNumber("SUB-R3");
        assertThat(result.getCommercialRevision()).isEqualTo(7L);
        assertThat(result.getLines().getFirst().getBillingFrequency()).isEqualTo("ANNUAL");
        assertThat(result.getLines().getFirst().getPeriods().getFirst().getContractedEndDate()).isEqualTo(java.time.LocalDate.of(2029,12,31));
        server.verify();
        org.mockito.Mockito.verify(auth).serviceToken();
        org.mockito.Mockito.verify(auth,org.mockito.Mockito.never()).relayUserToken();
    }
    @Test void missingOrMalformedTargetDoesNotBecomeAnEmptyLegacySchedule() {
        for(String body:new String[]{"{\"success\":false,\"data\":null}","not-json","{\"success\":true,\"data\":null}"}) {
            var builder=RestClient.builder().baseUrl("http://subscription.test");var server=MockRestServiceServer.bindTo(builder).build();
            server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> new SubscriptionScheduleClient(builder.build()).byNumber("SUB-R3")).isInstanceOf(BusinessException.class);
            server.verify();
        }
    }
    @Test void transportRefusalFailsClosed() {
        var builder=RestClient.builder().baseUrl("http://subscription.test");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> new SubscriptionScheduleClient(builder.build()).byNumber("SUB-R3")).isInstanceOf(BusinessException.class);
        server.verify();
    }
}
