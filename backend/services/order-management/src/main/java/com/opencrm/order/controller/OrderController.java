package com.opencrm.order.controller;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.order.dto.OrderDtos.*;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
 private final OrderService orders;
 public OrderController(OrderService orders) {this.orders=orders;}
 @PostMapping("/from-quote") public ApiResponse<View> convert(@Valid @RequestBody FromQuote request,@RequestHeader("Idempotency-Key") String key) {return ApiResponse.success(orders.fromQuote(request,key));}
 @GetMapping public ApiResponse<List<View>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(required=false) String customerId) {var result=orders.list(page,size,q,status,customerId);return ApiResponse.success(result.orders(),new ApiResponse.PageInfo(page,size,result.total(),(int)((result.total()+size-1)/size),(long)(page+1)*size<result.total(),page>0));}
 @GetMapping("/{id}") public ApiResponse<View> get(@PathVariable String id) {return ApiResponse.success(orders.get(id));}
 @GetMapping({"/{id}/fulfillment-plan","/{id}/plan"}) public ApiResponse<List<Task>> plan(@PathVariable String id) {return ApiResponse.success(orders.plan(id));}
 @GetMapping("/{id}/timeline") public ApiResponse<List<Timeline>> timeline(@PathVariable String id) {return ApiResponse.success(orders.timeline(id));}
 @PostMapping("/{id}/submit") public ApiResponse<View> submit(@PathVariable String id,@Valid @RequestBody Command command) {return ApiResponse.success(orders.submit(id,command));}
 @PostMapping("/{id}/holds") public ApiResponse<View> hold(@PathVariable String id,@Valid @RequestBody Hold command) {return ApiResponse.success(orders.hold(id,command));}
 @PostMapping("/{id}/cancellation-requests") public ApiResponse<View> cancel(@PathVariable String id,@Valid @RequestBody Cancellation command) {return ApiResponse.success(orders.cancel(id,command));}
 @ExceptionHandler(OrderProblem.class) public ResponseEntity<ApiResponse<Void>> problem(OrderProblem e) {return ResponseEntity.status(e.status()).body(ApiResponse.error(e.getMessage()));}
}
