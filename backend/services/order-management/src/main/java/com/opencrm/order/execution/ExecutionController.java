package com.opencrm.order.execution;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.dto.OrderDtos.Command;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/orders")
public class ExecutionController {
    public record Receipt(String orderId, String status) {}
    private final ExecutionService execution;
    public ExecutionController(ExecutionService execution) { this.execution = execution; }
    @PostMapping("/{id}/tasks/{taskId}/retry")
    public ResponseEntity<ApiResponse<Receipt>> retry(@PathVariable String id, @PathVariable String taskId, @Valid @RequestBody Command command) {
        execution.retry(id, taskId, command);
        return ResponseEntity.accepted().body(ApiResponse.success(new Receipt(id, "ACCEPTED")));
    }
    @PostMapping("/{id}/reconcile")
    public ResponseEntity<ApiResponse<Receipt>> reconcile(@PathVariable String id, @Valid @RequestBody Command command) {
        execution.reconcile(id, command);
        return ResponseEntity.accepted().body(ApiResponse.success(new Receipt(id, "ACCEPTED")));
    }
    @ExceptionHandler(OrderProblem.class)
    public ResponseEntity<ApiResponse<Void>> problem(OrderProblem failure) {
        return ResponseEntity.status(failure.status()).body(ApiResponse.error(failure.getMessage()));
    }
}
