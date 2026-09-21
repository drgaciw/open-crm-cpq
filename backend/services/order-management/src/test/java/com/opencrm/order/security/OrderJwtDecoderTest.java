package com.opencrm.order.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;

class OrderJwtDecoderTest {
    HttpServer server; RSAKey key; JwtDecoder decoder;
    @BeforeEach void setup() throws Exception {
        key=new RSAKeyGenerator(2048).keyID("test").generate();
        byte[] body=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/jwks",x->{x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(200,body.length);try(var out=x.getResponseBody()){out.write(body);}});
        server.start();
        decoder=new OrderSecurityConfiguration().orderJwtDecoder("https://issuer.test", "http://127.0.0.1:"+server.getAddress().getPort()+"/jwks", "order-management");
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    String token(RSAKey signing,String issuer,String audience,Instant expiry) throws Exception {
        var claims=new JWTClaimsSet.Builder().subject("operator").issuer(issuer).audience(audience).expirationTime(Date.from(expiry)).issueTime(Date.from(Instant.now().minusSeconds(600))).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(),claims);
        jwt.sign(new RSASSASigner(signing));return jwt.serialize();
    }
    @Test void acceptsOnlyVerifiedIssuerAudienceSignatureAndTime() throws Exception {
        assertEquals("operator",decoder.decode(token(key,"https://issuer.test","order-management",Instant.now().plusSeconds(300))).getSubject());
        var other=new RSAKeyGenerator(2048).keyID("test").generate();
        String wrongSignature=token(other,"https://issuer.test","order-management",Instant.now().plusSeconds(300));
        String wrongIssuer=token(key,"https://attacker.test","order-management",Instant.now().plusSeconds(300));
        String wrongAudience=token(key,"https://issuer.test","other",Instant.now().plusSeconds(300));
        String expired=token(key,"https://issuer.test","order-management",Instant.now().minusSeconds(300));
        for(String bad:new String[]{wrongSignature,wrongIssuer,wrongAudience,expired}) assertThrows(JwtException.class,()->decoder.decode(bad));
    }
    @Test void bypassConfigurationIsRejected() {
        assertThrows(IllegalStateException.class,()->new OrderSecurityConfiguration().orderSecurity(null,true,"pilot"));
    }
}
