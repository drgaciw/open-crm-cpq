package com.opencrm.order.fulfillment;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.order.domain.OrderProblem;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/orders")
public class FulfillmentController {
    private final EvidenceService evidence;
    public FulfillmentController(EvidenceService evidence){this.evidence=evidence;}
    @PostMapping("/{id}/fulfillment-evidence")
    public ResponseEntity<ApiResponse<EvidenceReceipt>> record(@PathVariable String id,@Valid @RequestBody EvidenceCommand command){
        var result=evidence.record(id,command);
        int status=switch(result.status()){case "CONFLICT","PENDING"->202;default->200;};
        return ResponseEntity.status(status).body(ApiResponse.success(result));
    }
    @GetMapping("/{id}/fulfillment-evidence")
    public ApiResponse<List<EvidenceService.EvidenceView>> ledger(@PathVariable String id){return ApiResponse.success(evidence.ledger(id));}
    @GetMapping("/{id}/allocations")
    public ApiResponse<List<EvidenceService.AllocationView>> allocations(@PathVariable String id){return ApiResponse.success(evidence.allocations(id));}
    @ExceptionHandler(OrderProblem.class)
    public ResponseEntity<ApiResponse<Void>> problem(OrderProblem problem){return ResponseEntity.status(problem.status()).body(ApiResponse.error(problem.getMessage()));}
}
