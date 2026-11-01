package com.opencrm.quote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HTTP read of CAM {@code /internal/v1/renewal-policies}. The {@link RestClient} is supplied by the caller and
 * must already carry the service identity. This type is not a {@code @Component}; {@code RestClientConfig} exposes
 * the bean used by from-active-assets renewals.
 */
public final class HttpRenewalPolicyClient implements RenewalPolicyClient {

    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public HttpRenewalPolicyClient(RestClient client) {
        this.client = client;
    }

    @Override
    public List<RenewalPolicyView> fetchRungs(UUID accountId, UUID agreementId, UUID lineId, LocalDate asOf) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/internal/v1/renewal-policies");
        if (accountId != null) {
            uri.queryParam("accountId", accountId);
        }
        if (agreementId != null) {
            uri.queryParam("agreementId", agreementId);
        }
        if (lineId != null) {
            uri.queryParam("lineId", lineId);
        }
        if (asOf != null) {
            uri.queryParam("asOf", asOf);
        }
        String path = uri.build().toUriString();
        if (path.contains("tenant")) {
            throw new BusinessException("Renewal policy client must not send a tenant", HttpStatus.BAD_REQUEST, "TENANT_OVERRIDE");
        }
        try {
            String raw = client.get().uri(path).retrieve().body(String.class);
            ApiResponse<List<RenewalPolicyView>> response = json.readValue(raw, new TypeReference<>() {});
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new IllegalArgumentException("Missing renewal policy data");
            }
            return List.copyOf(response.getData());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    "Cannot read renewal policy; retry or choose a valid target",
                    HttpStatus.BAD_GATEWAY,
                    "RENEWAL_POLICY_UNAVAILABLE");
        }
    }
}
