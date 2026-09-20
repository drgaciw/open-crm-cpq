package com.opencrm.order.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import com.opencrm.order.service.OrderService;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"oms.scope-id=pilot","oms.intake-enabled=true","oms.trusted-source-scopes=pilot","oms.snapshot-consumer-enabled=false","spring.jpa.hibernate.ddl-auto=none"})
@Testcontainers
@Tag("integration")
class OrderHttpIntegrationTest {
    @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:16-alpine");
    static RSAKey key; static HttpServer jwks;
    static {
        try {
            key=new RSAKeyGenerator(2048).keyID("pilot-test").generate();
            byte[] bytes=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            jwks=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            jwks.createContext("/jwks",x->{x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(200,bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}});jwks.start();
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void settings(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->db.getJdbcUrl()+"&currentSchema=order_management");r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);
        r.add("spring.flyway.default-schema",()->"order_management");r.add("spring.flyway.schemas",()->"order_management");
        r.add("oms.security.issuer-uri",()->"https://pilot.test");r.add("oms.security.jwk-set-uri",()->"http://127.0.0.1:"+jwks.getAddress().getPort()+"/jwks");
    }
    @AfterAll static void stop(){jwks.stop(0);}
    @Value("${local.server.port}") int port;
    @Autowired OrderService orders;
    @Autowired ObjectMapper json;
    final HttpClient client=HttpClient.newHttpClient();
    String token(String account, List<String> permissions) throws Exception {
        var claims=new JWTClaimsSet.Builder().subject("operator-test").issuer("https://pilot.test").audience("order-management").expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .claim("scope_id","pilot").claim("account_ids",List.of(account)).claim("permissions",permissions).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("pilot-test").build(),claims);jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    HttpResponse<String> send(String path,String method,String body,String token) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).header("Content-Type","application/json").header("Idempotency-Key","http-conversion");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return client.send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void realHttpEnforcesJwtAndAccountScopesAndPersistsCommands() throws Exception {
        String payload=Files.readString(Path.of("../../../docs/contracts/oms/accepted-snapshot-v1.fixture.json"));
        orders.acceptTrustedSnapshot(payload,OrderService.hash(payload));
        assertEquals(401,send("/orders","GET","",null).statusCode());
        assertEquals(401,send("/orders","GET","","invalid").statusCode());
        String reader=token("fixture-account",List.of("oms:read"));
        String actor=token("fixture-account",List.of("oms:read","oms:submit"));
        String body="{\"snapshotId\":\"11111111-1111-4111-8111-111111111111\",\"quoteId\":\"Q-OMS-001\",\"acceptedRevision\":1}";
        assertEquals(403,send("/orders/from-quote","POST",body,reader).statusCode());
        var created=send("/orders/from-quote","POST",body,actor);assertEquals(200,created.statusCode(),created.body());
        String id=json.readTree(created.body()).path("data").path("id").asText();assertFalse(id.isBlank());
        assertEquals(404,send("/orders/"+id,"GET","",token("other",List.of("oms:read"))).statusCode());
        assertEquals(200,send("/orders/"+id+"/fulfillment-plan","GET","",reader).statusCode());
        var submit=send("/orders/"+id+"/submit","POST","{\"expectedVersion\":0,\"reason\":\"approved pilot\",\"operationKey\":\"submit-http\"}",actor);
        assertEquals(200,submit.statusCode(),submit.body());
        assertEquals(409,send("/orders/"+id+"/submit","POST","{\"expectedVersion\":0,\"reason\":\"stale\",\"operationKey\":\"stale-http\"}",actor).statusCode());
    }
}
