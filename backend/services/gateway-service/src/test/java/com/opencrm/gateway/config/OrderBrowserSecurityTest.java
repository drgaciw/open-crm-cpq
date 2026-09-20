package com.opencrm.gateway.config;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class OrderBrowserSecurityTest {
 @Configuration @EnableWebFlux @EnableWebFluxSecurity @Import(OrderBrowserSecurity.class)
 static class Config {
  @Bean ReactiveClientRegistrationRepository registrations(){return new InMemoryReactiveClientRegistrationRepository(ClientRegistration.withRegistrationId("oms").clientId("pilot").clientSecret("test-only").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://crm.test/login/oauth2/code/oms").authorizationUri("https://id.test/authorize").tokenUri("https://id.test/token").userInfoUri("https://id.test/userinfo").userNameAttributeName("sub").build());}
  @Bean ReactiveJwtDecoder decoder(){return value->"test".equals(value)?Mono.just(Jwt.withTokenValue(value).header("alg","RS256").subject("operator").build()):Mono.error(new BadJwtException("invalid"));}
  @Bean @Order(1) SecurityWebFilterChain fallback(ServerHttpSecurity http){return http.authorizeExchange(a->a.anyExchange().permitAll()).build();}
  @Bean Endpoint endpoint(){return new Endpoint();}
 }
 @RestController static class Endpoint {@GetMapping({"/api/v1/orders","/api/v1/accounts"}) String get(){return "ok";} @PostMapping("/api/v1/orders") String post(){return "ok";}}
 @Test void loginUsesPkceWhileAnonymousOmsRemainsDeniedEvenWithPermissiveFallback(){
  try(var context=new AnnotationConfigApplicationContext()){
   context.getEnvironment().getSystemProperties().put("oms.browser-login-enabled","true");
   try {context.register(Config.class);context.refresh();var client=WebTestClient.bindToApplicationContext(context).build();
    client.get().uri("/api/v1/orders").exchange().expectStatus().isUnauthorized();
    client.get().uri("/api/v1/accounts").exchange().expectStatus().isOk();
    var read=client.get().uri("/api/v1/orders").header("Authorization","Bearer test").exchange().expectStatus().isOk().returnResult(String.class);
    var csrf=read.getResponseCookies().getFirst("XSRF-TOKEN");assertNotNull(csrf);
    client.post().uri("/api/v1/orders").exchange().expectStatus().isForbidden();
    client.post().uri("/api/v1/orders").cookie("XSRF-TOKEN",csrf.getValue()).header("X-XSRF-TOKEN",csrf.getValue()).exchange().expectStatus().isUnauthorized();
    var result=client.get().uri("/oauth2/authorization/oms").exchange().expectStatus().is3xxRedirection().returnResult(Void.class);
    String location=result.getResponseHeaders().getLocation().toString();assertTrue(location.startsWith("https://id.test/authorize?"));assertTrue(location.contains("code_challenge="));assertTrue(location.contains("code_challenge_method=S256"));
   }finally {context.getEnvironment().getSystemProperties().remove("oms.browser-login-enabled");}
  }
 }
}
