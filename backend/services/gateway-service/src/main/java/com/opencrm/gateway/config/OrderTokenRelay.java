package com.opencrm.gateway.config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
/** Keeps session access tokens server-side and forwards only to the OMS boundary. */
@Component
@ConditionalOnProperty(name="oms.browser-login-enabled",havingValue="true")
public class OrderTokenRelay implements GlobalFilter,Ordered {
 private final GatewayFilter relay;
 public OrderTokenRelay(TokenRelayGatewayFilterFactory factory) {this.relay=factory.apply();}
 public int getOrder(){return -100;}
 public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain) {
  String path=exchange.getRequest().getPath().value();
  return path.equals("/api/v1/orders")||path.startsWith("/api/v1/orders/")||path.equals("/api/v1/order-exceptions")||path.startsWith("/api/v1/order-exceptions/")?relay.filter(exchange,chain):chain.filter(exchange);
 }
}
