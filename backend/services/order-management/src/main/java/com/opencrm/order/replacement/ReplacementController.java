package com.opencrm.order.replacement;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.Command;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class ReplacementController {
 private final ReplacementService service;
 public ReplacementController(ReplacementService service) {this.service=service;}
 @PostMapping("/{id}/replacement-requests")
 public ResponseEntity<ApiResponse<ReplacementService.Receipt>> request(@PathVariable String id,@Valid @RequestBody Command command) {
  return ResponseEntity.accepted().body(ApiResponse.success(service.request(id,command)));
 }
 @ExceptionHandler(OrderProblem.class)
 public ResponseEntity<ApiResponse<Void>> problem(OrderProblem error) {
  return ResponseEntity.status(error.status()).body(ApiResponse.error(error.getMessage(),"OMS_"+error.status(),null));
 }
}
