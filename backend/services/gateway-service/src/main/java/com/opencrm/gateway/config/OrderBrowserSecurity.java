package com.opencrm.gateway.config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.*;
import org.springframework.security.web.server.csrf.*;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
/** Optional browser login; existing non-OMS routes retain their existing security policy. */
@Configuration
@ConditionalOnProperty(name="oms.browser-login-enabled",havingValue="true")
public class OrderBrowserSecurity {
 @Bean @Order(-1)
 SecurityWebFilterChain orderBrowserChain(ServerHttpSecurity http,ReactiveClientRegistrationRepository registrations) {
  var resolver=new DefaultServerOAuth2AuthorizationRequestResolver(registrations);
  resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
  return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/orders","/api/v1/orders/**","/api/v1/order-exceptions","/api/v1/order-exceptions/**","/api/v1/order-outbox","/api/v1/order-outbox/**","/oauth2/authorization/oms","/login/oauth2/code/oms","/oms/logout"))
   .csrf(c->c.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()).requireCsrfProtectionMatcher(OrderBrowserSecurity::cookieSessionMutation))
   .authorizeExchange(a->a.pathMatchers("/oauth2/authorization/oms","/login/oauth2/code/oms").permitAll().anyExchange().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
   .oauth2Login(o->o.authorizationRequestResolver(resolver).authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler("/orders")))
   .oauth2Client(c->{})
   .oauth2ResourceServer(o->o.jwt(j->{}))
   .logout(l->l.logoutUrl("/oms/logout"))
   .build();
 }
 /**
  * CSRF guards only state-changing requests a browser can make with its session cookie. A bearer token is never sent
  * by the browser on its own, so a bearer-authenticated mutation is not forgeable this way and is exempt.
  */
 static reactor.core.publisher.Mono<ServerWebExchangeMatcher.MatchResult> cookieSessionMutation(ServerWebExchange exchange) {
  var method=exchange.getRequest().getMethod();
  boolean safe=HttpMethod.GET.equals(method)||HttpMethod.HEAD.equals(method)||HttpMethod.OPTIONS.equals(method)||HttpMethod.TRACE.equals(method);
  String authorization=exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
  boolean bearer=authorization!=null&&authorization.regionMatches(true,0,"Bearer ",0,7);
  return !safe&&!bearer?ServerWebExchangeMatcher.MatchResult.match():ServerWebExchangeMatcher.MatchResult.notMatch();
 }
 @Bean @Order(-99)
 org.springframework.web.server.WebFilter orderCsrfCookie() {
  return (exchange,chain)->{
   reactor.core.publisher.Mono<CsrfToken> token=exchange.getAttribute(CsrfToken.class.getName());
   return token==null?chain.filter(exchange):token.then(reactor.core.publisher.Mono.defer(()->chain.filter(exchange)));
  };
 }
}
