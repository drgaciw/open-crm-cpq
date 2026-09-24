package com.opencrm.security.client;

import com.opencrm.security.web.OpenCrmSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-016 D5: the service identity comes from workload identity, with no secret in production")
class ServiceTokenProvidersTest {

    private static final String TENANT = "00000000-0000-4000-8000-00000000c0de";

    private final OpenCrmSecurityProperties properties = new OpenCrmSecurityProperties();
    private final MockEnvironment environment = new MockEnvironment();

    /** What the AKS workload identity webhook injects into a pod. */
    private void workloadIdentity() {
        environment.setProperty("AZURE_CLIENT_ID", "11111111-1111-4111-8111-111111111111");
        environment.setProperty("AZURE_TENANT_ID", TENANT);
        environment.setProperty("AZURE_AUTHORITY_HOST", "https://login.microsoftonline.com/");
        environment.setProperty("AZURE_FEDERATED_TOKEN_FILE", "/var/run/secrets/azure/tokens/azure-identity-token");
    }

    @Test
    @DisplayName("no client id: no service identity (local development)")
    void noIdentity() {
        ServiceTokenProvider provider = ServiceTokenProviders.create(properties, environment, false);

        assertThat(provider.token()).isEmpty();
    }

    @Test
    @DisplayName("the workload identity environment alone configures the client, with no opencrm properties")
    void fromWorkloadIdentityEnvironment() {
        workloadIdentity();

        ServiceTokenProvider provider = ServiceTokenProviders.create(properties, environment, true);

        assertThat(provider).isInstanceOf(ClientCredentialsServiceTokenProvider.class);
        ClientCredentialsServiceTokenProvider client = (ClientCredentialsServiceTokenProvider) provider;
        assertThat(client.clientId()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(client.tokenUri())
                .isEqualTo(URI.create("https://login.microsoftonline.com/" + TENANT + "/oauth2/v2.0/token"));
    }

    @Test
    @DisplayName("a client secret is refused under the production marker")
    void secretRefusedInProduction() {
        properties.getClient().setClientId("c");
        properties.getClient().setClientSecret("s");
        environment.setProperty("opencrm.env", "prod");

        assertThatThrownBy(() -> ServiceTokenProviders.create(properties, environment, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-secret is not allowed");
    }

    @Test
    @DisplayName("a client id without a credential is no identity, not a crash")
    void clientIdWithoutCredential() {
        environment.setProperty("AZURE_CLIENT_ID", "c");

        assertThat(ServiceTokenProviders.create(properties, environment, false).token()).isEmpty();
    }

    @Test
    @DisplayName("token endpoint: explicit, else workload identity, else the issuer's tenant (v2.0 and v1.0)")
    void tokenUri() {
        OpenCrmSecurityProperties.Client client = properties.getClient();
        OpenCrmSecurityProperties.Jwt jwt = properties.getJwt();

        jwt.setIssuerUri("https://login.microsoftonline.com/" + TENANT + "/v2.0");
        assertThat(ServiceTokenProviders.tokenUri(client, jwt, environment))
                .hasToString("https://login.microsoftonline.com/" + TENANT + "/oauth2/v2.0/token");

        jwt.setIssuerUri("https://sts.windows.net/" + TENANT + "/");
        assertThat(ServiceTokenProviders.tokenUri(client, jwt, environment))
                .hasToString("https://login.microsoftonline.com/" + TENANT + "/oauth2/v2.0/token");

        environment.setProperty("AZURE_TENANT_ID", "other-tenant");
        environment.setProperty("AZURE_AUTHORITY_HOST", "https://login.microsoftonline.us");
        assertThat(ServiceTokenProviders.tokenUri(client, jwt, environment))
                .hasToString("https://login.microsoftonline.us/other-tenant/oauth2/v2.0/token");

        client.setTokenUri("https://idp.example.com/token");
        assertThat(ServiceTokenProviders.tokenUri(client, jwt, environment)).hasToString("https://idp.example.com/token");
    }

    @Test
    @DisplayName("no way to find the token endpoint is a startup error")
    void noTokenUri() {
        assertThatThrownBy(() -> ServiceTokenProviders.tokenUri(properties.getClient(), properties.getJwt(),
                environment)).isInstanceOf(IllegalStateException.class).hasMessageContaining("token-uri");
    }

    @Test
    @DisplayName("default scope: the first accepted audience plus /.default")
    void defaultScope() {
        assertThat(ServiceTokenProviders.defaultScope(List.of("api://opencrm-api")))
                .isEqualTo("api://opencrm-api/.default");
        assertThat(ServiceTokenProviders.defaultScope(List.of(" ", "22222222-2222-4222-8222-222222222222")))
                .isEqualTo("22222222-2222-4222-8222-222222222222/.default");
        assertThat(ServiceTokenProviders.defaultScope(List.of("api://x/.default"))).isEqualTo("api://x/.default");
    }
}
