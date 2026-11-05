package com.opencrm.order.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import com.opencrm.order.service.OrderService;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit CI browser stage: real application/security/database, synthetic authority evidence. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"oms.scope-id=pilot","oms.intake-enabled=true","oms.trusted-source-scopes=pilot","oms.snapshot-consumer-enabled=false","spring.jpa.hibernate.ddl-auto=none"})
@Testcontainers
@Tag("integration")
class OrderLiveBrowserUat {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
 static RSAKey key; static HttpServer jwks;
 static {
  try {
   key=new RSAKeyGenerator(2048).keyID("browser-test").generate();
   byte[] bytes=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
   jwks=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   jwks.createContext("/jwks",x->{x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(200,bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}});jwks.start();
  } catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 @DynamicPropertySource static void settings(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",()->postgres.getJdbcUrl()+"&currentSchema=order_management");r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("spring.flyway.default-schema",()->"order_management");r.add("spring.flyway.schemas",()->"order_management");
  r.add("oms.security.issuer-uri",()->"https://browser.test");r.add("oms.security.jwk-set-uri",()->"http://127.0.0.1:"+jwks.getAddress().getPort()+"/jwks");
 }
 @AfterAll static void stop(){jwks.stop(0);}
 @Value("${local.server.port}") int port;
 @Autowired OrderService orders;
 @Autowired JdbcTemplate db;
 @Test void browserCompletesPhysicalOrderAndReconcilesLostResponse() throws Exception {
  Path root=Path.of("../../..").toAbsolutePath().normalize();
  String payload=Files.readString(root.resolve("docs/contracts/oms/accepted-snapshot-v1.fixture.json"));
  orders.acceptTrustedSnapshot(payload,OrderService.hash(payload));
  var claims=new JWTClaimsSet.Builder().subject("synthetic-browser-operator").issuer("https://browser.test").audience("order-management").expirationTime(Date.from(Instant.now().plusSeconds(600)))
   .claim("scope_id","pilot").claim("account_ids",List.of("fixture-account")).claim("permissions",List.of("oms:read","oms:submit","oms:hold","oms:cancel","oms:evidence","oms:finance","oms:recover","oms:assign")).build();
  var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("browser-test").build(),claims);jwt.sign(new RSASSASigner(key));
  Path output=Path.of("target").toAbsolutePath();Files.createDirectories(output);
  var builder=new ProcessBuilder("node",root.resolve("scripts/ci/oms-browser.mjs").toString()).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("oms-browser-live.log").toFile());
  builder.environment().put("OMS_TEST_API_TARGET","http://127.0.0.1:"+port);
  builder.environment().put("OMS_TEST_TOKEN",jwt.serialize());
  builder.environment().put("OMS_BROWSER_OUTPUT",output.toString());
  builder.environment().put("NODE_OPTIONS","--max-old-space-size=1536");
  Process process=builder.start();
  try {
   assertTrue(process.waitFor(240,TimeUnit.SECONDS),"Browser runner timed out; see target/oms-browser-live.log");
   assertEquals(0,process.exitValue(),()->"Browser failed: "+readLog(output.resolve("oms-browser-live.log")));
  } finally {if(process.isAlive()){
   var children=process.descendants().toList();children.forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();
   assertTrue(process.waitFor(10,TimeUnit.SECONDS),"Owned browser runner did not exit");
   for(var child:children)child.onExit().get(10,TimeUnit.SECONDS);
  }}
  assertEquals("COMPLETED",db.queryForObject("SELECT status FROM orders",String.class));
  assertEquals(10,db.queryForObject("SELECT delivered FROM order_lines",Integer.class));
  assertEquals(10,db.queryForObject("SELECT sum(quantity) FROM finance_acknowledgments WHERE outcome='ACKNOWLEDGED'",Integer.class));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM fulfillment_evidence_state WHERE status<>'APPLIED'",Integer.class));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM order_idempotency i LEFT JOIN order_command_audit a ON a.scope_id=i.scope_id AND a.operation_key=i.operation_key WHERE a.operation_key IS NULL",Integer.class));
 }
 private static String readLog(Path path){try{return Files.readString(path);}catch(Exception e){return e.getMessage();}}
}
