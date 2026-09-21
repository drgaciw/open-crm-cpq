package com.opencrm.order.recovery;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.order.domain.OrderProblem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.opencrm.order.recovery.RecoveryDtos.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1")
public class RecoveryController {
 private final RecoveryService recovery;
 public RecoveryController(RecoveryService recovery){this.recovery=recovery;}
 @GetMapping("/order-exceptions")
 public ApiResponse<List<ExceptionView>> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(required=false) String status,@RequestParam(required=false) String category,@RequestParam(required=false) String owner){return page(recovery.exceptions(page,size,status,category,owner));}
 @PostMapping("/order-exceptions/{id}/assign")
 public ResponseEntity<ApiResponse<Operation>> assign(@PathVariable String id,@RequestBody Assignment command){return accepted(recovery.assign(id,command));}
 @PostMapping("/order-exceptions/{id}/resolve")
 public ResponseEntity<ApiResponse<Operation>> resolve(@PathVariable String id,@RequestBody Resolution command){return accepted(recovery.resolve(id,command));}
 @GetMapping("/order-exceptions/{id}/operations/{key}")
 public ApiResponse<Operation> operation(@PathVariable String id,@PathVariable String key){return ApiResponse.success(recovery.operation(id,key));}
 @GetMapping("/order-outbox/exhausted")
 public ApiResponse<List<ExhaustedEvent>> exhausted(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return page(recovery.exhausted(page,size));}
 @PostMapping("/orders/{id}/outbox/{eventId}/recover")
 public ResponseEntity<ApiResponse<Operation>> outbox(@PathVariable String id,@PathVariable long eventId,@RequestBody RecoveryCommand command){return accepted(recovery.recoverOutbox(id,eventId,command));}
 @PostMapping("/orders/{id}/tasks/{taskId}/no-effect")
 public ResponseEntity<ApiResponse<Operation>> noEffect(@PathVariable String id,@PathVariable String taskId,@RequestBody NoEffect command){return accepted(recovery.noEffect(id,taskId,command));}
 @PostMapping("/orders/{id}/operations/{key}/abandon")
 public ResponseEntity<ApiResponse<Operation>> abandon(@PathVariable String id,@PathVariable String key,@RequestBody RecoveryCommand command){return accepted(recovery.abandon(id,key,command));}
 private <T> ApiResponse<List<T>> page(Page<T> result){return ApiResponse.success(result.content(),new ApiResponse.PageInfo(result.page(),result.size(),result.totalElements(),result.totalPages(),(long)(result.page()+1)*result.size()<result.totalElements(),result.page()>0));}
 private ResponseEntity<ApiResponse<Operation>> accepted(Operation result){return ResponseEntity.accepted().body(ApiResponse.success(result));}
 @ExceptionHandler(OrderProblem.class)
 public ResponseEntity<ApiResponse<Void>> problem(OrderProblem failure){return ResponseEntity.status(failure.status()).body(ApiResponse.error(failure.getMessage(),"OMS_"+failure.status(),null));}
}
