package com.opencrm.order.recovery;

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
class RecoveryHttpIntegrationTest {
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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void recoveryHttpRequiresPermissionAndReturnsPaginatedScopedExceptions() throws Exception {
        String payload=Files.readString(Path.of("../../../docs/contracts/oms/accepted-snapshot-v1.fixture.json"));
        orders.acceptTrustedSnapshot(payload,OrderService.hash(payload));
        String reader=token("fixture-account",List.of("oms:read"));
        String actor=token("fixture-account",List.of("oms:read","oms:submit","oms:recover","oms:assign"));
        String body="{\"snapshotId\":\"11111111-1111-4111-8111-111111111111\",\"quoteId\":\"Q-OMS-001\",\"acceptedRevision\":1}";
        var created=send("/orders/from-quote","POST",body,actor);assertEquals(200,created.statusCode(),created.body());
        String id=json.readTree(created.body()).path("data").path("id").asText();
        String exception=UUID.randomUUID().toString();jdbc.update("INSERT INTO order_exceptions(id,order_id,category,severity,next_action,reason) VALUES(?,?,'REVIEW_NOTE','LOW','RECONCILE','operator review')",exception,id);
        assertEquals(401,send("/order-exceptions","GET","",null).statusCode());
        var list=send("/order-exceptions?page=0&size=1","GET","",reader);assertEquals(200,list.statusCode(),list.body());
        var tree=json.readTree(list.body());assertTrue(tree.path("data").isArray(),list.body());assertEquals(1,tree.path("pagination").path("totalElements").asLong());
        String command="{\"expectedVersion\":0,\"operationKey\":\"abandon-http\",\"reason\":\"confirmed absent receipt\"}";
        assertEquals(403,send("/orders/"+id+"/operations/lost/abandon","POST",command,reader).statusCode());
        var abandoned=send("/orders/"+id+"/operations/lost/abandon","POST",command,actor);assertEquals(202,abandoned.statusCode(),abandoned.body());assertEquals("ABANDONED",json.readTree(abandoned.body()).path("data").path("status").asText());
        var invisible=send("/order-exceptions","GET","",token("other",List.of("oms:read")));assertEquals(200,invisible.statusCode());assertEquals(0,json.readTree(invisible.body()).path("data").size());
        String assignment="{\"expectedVersion\":0,\"operationKey\":\"assign-http\",\"reason\":\"triage\",\"owner\":\"operator-owner\"}";
        assertEquals(202,send("/order-exceptions/"+exception+"/assign","POST",assignment,actor).statusCode());
        var receipt=send("/order-exceptions/"+exception+"/operations/assign-http","GET","",reader);assertEquals("SUCCEEDED",json.readTree(receipt.body()).path("data").path("status").asText());
        assertEquals(404,send("/order-exceptions/"+exception+"/operations/assign-http","GET","",token("other",List.of("oms:read"))).statusCode());
    }
}
