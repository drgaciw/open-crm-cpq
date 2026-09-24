package com.opencrm.security.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Real RS256-signed JWTs shaped like Entra ID access tokens, for ADR-016 tests. Nothing here is mocked: the tokens go
 * through the same Nimbus decoder and validators as production, only the signing key is a per-JVM test key.
 *
 * <pre>{@code
 * String header = TestTokens.user("alice", "CPQ.User").bearer();
 * String internal = TestTokens.service("quote-service", "Internal.Invoke").bearer();
 * String stale = TestTokens.user("alice").expiresAt(Instant.now().minusSeconds(600)).bearer();
 * }</pre>
 */
public final class TestTokens {

    /** A tenant-specific Entra ID v2.0 issuer (a made-up tenant id). */
    public static final String ISSUER = "https://login.microsoftonline.com/00000000-0000-4000-8000-00000000c0de/v2.0";

    /** The API audience of ADR-016 decision 1. */
    public static final String AUDIENCE = "api://opencrm-api";

    /** The client a user token was issued to ({@code azp}): the gateway's login. */
    public static final String USER_CLIENT_ID = "opencrm-gateway-test";

    /** The delegated scope user tokens carry ({@code scp}). */
    public static final String USER_SCOPE = "access_as_user";

    private static final String KEY_ID = "opencrm-test-key";
    private static final RSAKey KEY = rsaKey();
    private static final RSAKey FOREIGN_KEY = rsaKey();

    private TestTokens() {
    }

    /**
     * A user token shaped like Entra's: {@code sub} is the given name (pairwise in Entra), {@code oid} is
     * {@link #oidOf(String)}, {@code scp} is {@link #USER_SCOPE}, {@code azp} the gateway, {@code roles} the app roles.
     */
    public static Builder user(String subject, String... roles) {
        return new Builder(subject, List.of(roles), false);
    }

    /**
     * A client-credentials token of a calling service: {@code azp} is its client id, {@code idtyp=app}, no {@code scp},
     * and {@code sub}/{@code oid} its service principal, {@link #oidOf(String)} of {@code service:<clientId>}.
     */
    public static Builder service(String clientId, String... roles) {
        return new Builder(clientId, List.of(roles), true);
    }

    /** The {@code oid} of a test principal: a stable UUID derived from the name. */
    public static String oidOf(String name) {
        return UUID.nameUUIDFromBytes(("oid:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** The public key that {@link #user} and {@link #service} tokens verify against. */
    public static RSAPublicKey publicKey() {
        try {
            return KEY.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The JWKS document ({@code {"keys":[...]}}) an issuer would serve, public half only. */
    public static String jwkSetJson() {
        return new JWKSet(KEY.toPublicJWK()).toString();
    }

    /** Claims and signing key of one token; every field has an Entra-like default. */
    public static final class Builder {
        private final String subject;
        private final List<String> roles;
        private final boolean appOnly;
        private String issuer = ISSUER;
        private String audience = AUDIENCE;
        private Instant issuedAt = Instant.now().minusSeconds(5);
        private Instant expiresAt = issuedAt.plus(Duration.ofMinutes(10));
        private RSAKey signingKey = KEY;

        private Builder(String subject, List<String> roles, boolean appOnly) {
            this.subject = subject;
            this.roles = roles;
            this.appOnly = appOnly;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        /** Moves the whole validity window so that it ends at {@code expiresAt}. */
        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            this.issuedAt = expiresAt.minus(Duration.ofMinutes(10));
            return this;
        }

        /** Signed with a different private key under the same key id: a forged token. */
        public Builder signedByForeignKey() {
            this.signingKey = FOREIGN_KEY;
            return this;
        }

        /** The compact serialized JWT. */
        public String mint() {
            String servicePrincipal = oidOf("service:" + subject);
            JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(appOnly ? servicePrincipal : subject)
                    .audience(List.of(audience))
                    .issuedAt(issuedAt)
                    .notBefore(issuedAt)
                    .expiresAt(expiresAt)
                    .claim("tid", "00000000-0000-4000-8000-00000000c0de")
                    .claim("ver", "2.0");
            if (appOnly) {
                claims.claim("oid", servicePrincipal).claim("azp", subject).claim("idtyp", "app");
            } else {
                claims.claim("oid", oidOf(subject)).claim("azp", USER_CLIENT_ID).claim("scp", USER_SCOPE);
            }
            if (!roles.isEmpty()) {
                claims.claim("roles", roles);
            }
            JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey)));
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).type("JWT").build();
            return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        }

        /** {@code Bearer <jwt>}, the {@code Authorization} header value. */
        public String bearer() {
            return "Bearer " + mint();
        }
    }

    private static RSAKey rsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(KEY_ID)
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
