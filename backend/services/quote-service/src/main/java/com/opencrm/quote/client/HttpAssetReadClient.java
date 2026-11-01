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

/**
 * HTTP read of CAM {@code /api/v1/assets}. The {@link RestClient} is supplied by the caller and must already
 * relay the rep's token. This type does not query the asset database and does not import CAM classes.
 */
public final class HttpAssetReadClient implements AssetReadClient {

    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public HttpAssetReadClient(RestClient client) {
        this.client = client;
    }

    @Override
    public List<InstalledAssetView> installedBase(String accountId, LocalDate asOf) {
        if (accountId == null || accountId.isBlank() || asOf == null) {
            throw new BusinessException("Account and as-of date are required", HttpStatus.BAD_REQUEST, "ASSET_QUERY_REQUIRED");
        }
        String path = UriComponentsBuilder.fromPath("/api/v1/assets")
                .queryParam("accountId", accountId)
                .queryParam("asOf", asOf)
                .build()
                .toUriString();
        if (path.contains("tenant")) {
            throw new BusinessException("Asset client must not send a tenant", HttpStatus.BAD_REQUEST, "TENANT_OVERRIDE");
        }
        try {
            String raw = client.get().uri(path).retrieve().body(String.class);
            ApiResponse<List<InstalledAssetView>> response = json.readValue(raw, new TypeReference<>() {});
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new IllegalArgumentException("Missing installed base");
            }
            return List.copyOf(response.getData());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    "Cannot read active assets; retry or choose a valid account",
                    HttpStatus.BAD_GATEWAY,
                    "ASSET_READ_UNAVAILABLE");
        }
    }
}
