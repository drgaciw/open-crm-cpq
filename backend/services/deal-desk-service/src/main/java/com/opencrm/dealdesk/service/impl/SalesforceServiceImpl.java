package com.opencrm.dealdesk.service.impl;

import com.opencrm.dealdesk.config.SalesforceProperties;
import com.opencrm.dealdesk.exception.SalesforceIntegrationException;
import com.opencrm.dealdesk.exception.SalesforceUpdateException;
import com.opencrm.dealdesk.service.SalesforceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SalesforceServiceImpl implements SalesforceService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_EXPIRY_SKEW = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final RetryTemplate retryTemplate;
    private final SalesforceProperties salesforceProperties;
    private final Object tokenLock = new Object();
    private volatile CachedAccessToken cachedAccessToken;

    public SalesforceServiceImpl(
            @Qualifier("salesforceRestClient") RestClient restClient,
            @Qualifier("salesforceRetryTemplate") RetryTemplate retryTemplate,
            SalesforceProperties salesforceProperties
    ) {
        this.restClient = restClient;
        this.retryTemplate = retryTemplate;
        this.salesforceProperties = salesforceProperties;
    }

    @Override
    public boolean isEnabled() {
        return salesforceProperties.isEnabled();
    }

    @Override
    public String getAccessToken() {
        if (!salesforceProperties.isEnabled()) {
            return "";
        }
        return getOrRefreshAccessToken().accessToken();
    }

    @Override
    public Map<String, Object> getOpportunity(String opportunityId) {
        if (!salesforceProperties.isEnabled()) {
            return Collections.emptyMap();
        }

        return retryTemplate.execute(context -> {
            CachedAccessToken token = getOrRefreshAccessToken();
            URI uri = buildOpportunityUri(token, opportunityId);
            try {
                Map<String, Object> response = restClient.get()
                        .uri(uri)
                        .headers(headers -> headers.setBearerAuth(token.accessToken()))
                        .retrieve()
                        .body(MAP_TYPE);
                return response != null ? response : Collections.emptyMap();
            } catch (RestClientResponseException ex) {
                if (isUnauthorized(ex.getStatusCode())) {
                    invalidateCachedToken();
                }
                throw new SalesforceIntegrationException(
                        buildHttpErrorMessage("fetch Salesforce opportunity " + opportunityId, ex),
                        ex
                );
            } catch (RestClientException ex) {
                throw new SalesforceIntegrationException(
                        "Failed to fetch Salesforce opportunity " + opportunityId,
                        ex
                );
            }
        });
    }

    @Override
    public void updateOpportunity(String opportunityId, Map<String, Object> fields) {
        if (!salesforceProperties.isEnabled()) {
            return;
        }

        retryTemplate.execute(context -> {
            CachedAccessToken token = getOrRefreshAccessToken();
            URI uri = buildOpportunityUri(token, opportunityId);
            try {
                restClient.patch()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> headers.setBearerAuth(token.accessToken()))
                        .body(fields != null ? fields : Collections.emptyMap())
                        .retrieve()
                        .toBodilessEntity();
                return null;
            } catch (RestClientResponseException ex) {
                if (isUnauthorized(ex.getStatusCode())) {
                    invalidateCachedToken();
                }
                throw new SalesforceUpdateException(
                        buildHttpErrorMessage("update Salesforce opportunity " + opportunityId, ex),
                        ex
                );
            } catch (RestClientException ex) {
                throw new SalesforceUpdateException(
                        "Failed to update Salesforce opportunity " + opportunityId,
                        ex
                );
            }
        });
    }

    @Override
    public String createQuote(String opportunityId, Map<String, Object> quotePayload) {
        if (!salesforceProperties.isEnabled()) {
            return "";
        }

        validateRequiredId("Salesforce opportunity id", opportunityId);
        return "stub-quote-" + opportunityId;
    }

    @Override
    public void syncQuotePricing(String quoteId, Map<String, Object> pricingFields) {
        if (!salesforceProperties.isEnabled()) {
            return;
        }

        validateRequiredId("Salesforce quote id", quoteId);
    }

    @Override
    public List<Map<String, Object>> evaluatePriceRules(String quoteId) {
        if (!salesforceProperties.isEnabled()) {
            return Collections.emptyList();
        }

        validateRequiredId("Salesforce quote id", quoteId);

        Map<String, Object> evaluationRow = new LinkedHashMap<>();
        evaluationRow.put("quoteId", quoteId);
        evaluationRow.put("status", "STUB");
        evaluationRow.put("ruleName", "CPQ_PRICE_RULE_EVAL_STUB");
        evaluationRow.put("message", "CPQ price rule evaluation is stubbed in deal-desk-service");
        return Collections.singletonList(evaluationRow);
    }

    @Override
    public void syncPriceRules(String priceRuleId, String quoteId) {
        if (!salesforceProperties.isEnabled()) {
            return;
        }

        validateRequiredId("Salesforce price rule id", priceRuleId);
        validateRequiredId("Salesforce quote id", quoteId);
    }

    @Override
    public void validatePriceRules(String quoteId) {
        if (!salesforceProperties.isEnabled()) {
            return;
        }

        validateRequiredId("Salesforce quote id", quoteId);
    }

    @Override
    public void applyPriceRules(String quoteId) {
        if (!salesforceProperties.isEnabled()) {
            return;
        }

        validateRequiredId("Salesforce quote id", quoteId);
    }

    private CachedAccessToken getOrRefreshAccessToken() {
        CachedAccessToken current = cachedAccessToken;
        if (isTokenUsable(current)) {
            return current;
        }

        synchronized (tokenLock) {
            current = cachedAccessToken;
            if (isTokenUsable(current)) {
                return current;
            }

            CachedAccessToken refreshed = requestAccessToken();
            cachedAccessToken = refreshed;
            return refreshed;
        }
    }

    private CachedAccessToken requestAccessToken() {
        return retryTemplate.execute(context -> {
            try {
                SalesforceTokenResponse response = restClient.post()
                        .uri(URI.create(salesforceProperties.getAuth().getTokenUrl()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(buildTokenRequestForm())
                        .retrieve()
                        .body(SalesforceTokenResponse.class);

                if (response == null || !StringUtils.hasText(response.accessToken())) {
                    throw new SalesforceIntegrationException("Salesforce token response did not include an access token");
                }

                String resolvedBaseUrl = resolveBaseUrl(response.instanceUrl());
                Instant expiresAt = calculateExpiry(response);

                return new CachedAccessToken(response.accessToken(), resolvedBaseUrl, expiresAt);
            } catch (RestClientResponseException ex) {
                throw new SalesforceIntegrationException(
                        buildHttpErrorMessage("obtain Salesforce access token", ex),
                        ex
                );
            } catch (RestClientException ex) {
                throw new SalesforceIntegrationException("Failed to obtain Salesforce access token", ex);
            }
        });
    }

    private MultiValueMap<String, String> buildTokenRequestForm() {
        SalesforceProperties.Auth auth = salesforceProperties.getAuth();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", auth.getGrantType());
        form.add("client_id", auth.getClientId());
        form.add("client_secret", auth.getClientSecret());
        form.add("username", auth.getUsername());
        form.add("password", safe(auth.getPassword()) + safe(auth.getSecurityToken()));
        return form;
    }

    private URI buildOpportunityUri(CachedAccessToken token, String opportunityId) {
        validateRequiredId("Salesforce opportunity id", opportunityId);

        String apiVersion = salesforceProperties.getApiVersion();
        if (!StringUtils.hasText(apiVersion)) {
            throw new SalesforceIntegrationException("Salesforce API version must not be blank");
        }

        return URI.create(token.baseUrl() + "/services/data/" + apiVersion + "/sobjects/Opportunity/" + opportunityId);
    }

    private String resolveBaseUrl(String tokenInstanceUrl) {
        String configuredBaseUrl = salesforceProperties.getBaseUrl();
        String baseUrl = StringUtils.hasText(configuredBaseUrl) ? configuredBaseUrl : tokenInstanceUrl;
        if (!StringUtils.hasText(baseUrl)) {
            throw new SalesforceIntegrationException(
                    "Salesforce base URL is not configured and token response did not provide instance_url"
            );
        }
        return trimTrailingSlash(baseUrl);
    }

    private Instant calculateExpiry(SalesforceTokenResponse response) {
        Instant now = Instant.now();
        Long expiresInSeconds = response.expiresIn();
        Instant issuedAt = parseIssuedAt(response.issuedAt());

        Instant expiry = expiresInSeconds != null && expiresInSeconds > 0
                ? issuedAt.plusSeconds(expiresInSeconds)
                : now.plus(DEFAULT_TOKEN_TTL);

        Instant adjusted = expiry.minus(TOKEN_EXPIRY_SKEW);
        return adjusted.isAfter(now) ? adjusted : now.plusSeconds(5);
    }

    private Instant parseIssuedAt(String issuedAt) {
        if (!StringUtils.hasText(issuedAt)) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(issuedAt));
        } catch (NumberFormatException ex) {
            return Instant.now();
        }
    }

    private boolean isTokenUsable(CachedAccessToken token) {
        return token != null && StringUtils.hasText(token.accessToken()) && Instant.now().isBefore(token.expiresAt());
    }

    private void invalidateCachedToken() {
        cachedAccessToken = null;
    }

    private boolean isUnauthorized(HttpStatusCode statusCode) {
        return statusCode != null && statusCode.value() == 401;
    }

    private String buildHttpErrorMessage(String action, RestClientResponseException ex) {
        return "Failed to " + action + " (status " + ex.getStatusCode().value() + ")";
    }

    private void validateRequiredId(String label, String value) {
        if (!StringUtils.hasText(value)) {
            throw new SalesforceIntegrationException(label + " must not be blank");
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record CachedAccessToken(String accessToken, String baseUrl, Instant expiresAt) {
    }

    private record SalesforceTokenResponse(
            String access_token,
            String instance_url,
            String issued_at,
            Long expires_in
    ) {
        String accessToken() {
            return access_token;
        }

        String instanceUrl() {
            return instance_url;
        }

        String issuedAt() {
            return issued_at;
        }

        Long expiresIn() {
            return expires_in;
        }
    }
}
