package com.opencrm.order.controller;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.service.OrderService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
/** Durable command lookup: absence never authorizes a new operation identity. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderOperationController {
 public record Operation(String operationKey,String orderId,String status,long orderVersion) {}
 private final OrderService orders;private final JdbcTemplate db;
 public OrderOperationController(OrderService orders,JdbcTemplate db){this.orders=orders;this.db=db;}
 @GetMapping("/{id}/operations/{key}")
 public ApiResponse<Operation> get(@PathVariable String id,@PathVariable String key){
  var order=orders.get(id);
  if(key.isBlank()||key.length()>200) throw OrderProblem.invalid("Invalid operation key");
  var row=db.queryForMap("SELECT o.version, i.operation_key,t.operation_key AS abandoned_key FROM orders o LEFT JOIN order_idempotency i ON i.scope_id=o.scope_id AND i.order_id=o.id AND i.operation_key=? LEFT JOIN recovery_command_tombstones t ON t.scope_id=o.scope_id AND t.order_id=o.id AND t.operation_key=i.operation_key WHERE o.id=? AND o.scope_id=? AND o.customer_id=? AND o.is_deleted=false",key,id,order.scopeId(),order.customerId());
  return ApiResponse.success(new Operation(key,id,row.get("abandoned_key")!=null?"ABANDONED":row.get("operation_key")!=null?"SUCCEEDED":"NOT_FOUND",((Number)row.get("version")).longValue()));
 }
 @ExceptionHandler(OrderProblem.class)
 public ResponseEntity<ApiResponse<Void>> problem(OrderProblem e){return ResponseEntity.status(e.status()).body(ApiResponse.error(e.getMessage(),"OMS_"+e.status(),null));}
}
