package com.opencrm.gateway.config;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderTokenRelayTest {
 @Test void sessionTokenRelaysOnlyToOrders() {
  var manager=mock(ReactiveOAuth2AuthorizedClientManager.class);
  var registration=ClientRegistration.withRegistrationId("oms").clientId("pilot").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://crm.test/login/oauth2/code/oms").authorizationUri("https://id.test/authorize").tokenUri("https://id.test/token").build();
  var token=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"server-kept-token",Instant.now(),Instant.now().plusSeconds(60));
  when(manager.authorize(any())).thenReturn(Mono.just(new OAuth2AuthorizedClient(registration,"operator",token)));
  var beans=new StaticListableBeanFactory(Map.of("manager",manager));
  var filter=new OrderTokenRelay(new TokenRelayGatewayFilterFactory(beans.getBeanProvider(ReactiveOAuth2AuthorizedClientManager.class)));
  var principal=new OAuth2AuthenticationToken(new DefaultOAuth2User(List.of(),Map.of("sub","operator"),"sub"),List.of(),"oms");
  var protectedPaths=Set.of("/api/v1/orders/123","/api/v1/order-exceptions","/api/v1/order-outbox","/api/v1/order-outbox/123/recover");
  for(String path:List.of("/api/v1/orders/123","/api/v1/order-exceptions","/api/v1/order-outbox","/api/v1/order-outbox/123/recover","/api/v1/accounts","/api/v1/orders-evil","/api/v1/order-outbox-evil","/api/v1/order-outboxes/123")) {
   var exchange=MockServerWebExchange.from(MockServerHttpRequest.get(path)).mutate().principal(Mono.just(principal)).build();
   var forwarded=new AtomicReference<String>();filter.filter(exchange,e->{forwarded.set(e.getRequest().getHeaders().getFirst("Authorization"));return Mono.empty();}).block();
   assertEquals(protectedPaths.contains(path)?"Bearer server-kept-token":null,forwarded.get(),path);
  }
  verify(manager,times(4)).authorize(any());
 }
}
