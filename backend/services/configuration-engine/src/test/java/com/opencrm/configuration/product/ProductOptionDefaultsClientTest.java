package com.opencrm.configuration.product;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ProductOptionDefaultsClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ProductOptionDefaultsClient client = new ProductOptionDefaultsClient(builder.build());

    @Test
    void readsAuthoritativeExistingGroupContractAndToleratesUnrelatedFields() {
        knownProduct();
        server.expect(requestTo("http://catalog.test/api/v1/product-options/groups/product/10"))
                .andRespond(withSuccess("""
                    {"success":true,"data":[{"id":1,"productId":10,"name":"Support",
                      "selectionType":"REQUIRED_SINGLE","minSelections":1,"maxSelections":1,
                      "options":[{"id":11,"productId":20,"name":"Standard","defaultOption":true}]}]}
                    """, MediaType.APPLICATION_JSON));
        assertThat(client.getGroups(10L)).containsExactly(new ProductOptionGroupSnapshot(1L, 10L,
                "REQUIRED_SINGLE", 1, 1, java.util.List.of(new ProductOptionGroupSnapshot.Option(11L, 20L, true))));
        server.verify();
    }

    @Test
    void unavailableCatalogFailsClosedRatherThanPretendingThereAreNoDefaults() {
        knownProduct();
        server.expect(requestTo("http://catalog.test/api/v1/product-options/groups/product/10"))
                .andRespond(withServiceUnavailable());
        assertThatThrownBy(() -> client.getGroups(10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(503));
    }

    @Test
    void malformedSuccessEnvelopeFailsClosed() {
        knownProduct();
        server.expect(requestTo("http://catalog.test/api/v1/product-options/groups/product/10"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getGroups(10L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void unknownProductFailsBeforeEmptyGroupResponseCanBeMistakenForValidConfiguration() {
        server.expect(requestTo("http://catalog.test/api/v1/products/10")).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.getGroups(10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DEFAULTS_PRODUCT_NOT_FOUND"));
        server.verify();
    }

    @Test
    void mismatchedProductIdentityIsRefused() {
        server.expect(requestTo("http://catalog.test/api/v1/products/10"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"id\":99}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getGroups(10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("INVALID_DEFAULTS_CATALOG"));
        server.verify();
    }

    @Test
    void existingDefaultTargetIsConfirmedWithoutFetchingItsGroups() {
        server.expect(requestTo("http://catalog.test/api/v1/products/20"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"id\":20}}", MediaType.APPLICATION_JSON));
        client.requireProduct(20L);
        server.verify();
    }

    @Test
    void deletedDefaultTargetFailsClosedOnTheNonDeletedProductLookup() {
        server.expect(requestTo("http://catalog.test/api/v1/products/20")).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.requireProduct(20L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DEFAULTS_PRODUCT_NOT_FOUND"));
        server.verify();
    }

    private void knownProduct() {
        server.expect(requestTo("http://catalog.test/api/v1/products/10"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"id\":10,\"name\":\"Root\"}}", MediaType.APPLICATION_JSON));
    }
}
