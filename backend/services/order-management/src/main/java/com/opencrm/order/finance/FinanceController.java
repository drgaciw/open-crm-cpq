package com.opencrm.order.finance;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.order.dto.OrderDtos.View;
import com.opencrm.order.domain.OrderProblem;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
import static com.opencrm.order.finance.FinanceDtos.*;
@RestController
@RequestMapping("/api/v1/orders")
public class FinanceController {
 private final FinanceService finance;
 public FinanceController(FinanceService finance){this.finance=finance;}
 @GetMapping("/{id}/finance-requests") public ApiResponse<List<RequestView>> list(@PathVariable String id){return ApiResponse.success(finance.list(id));}
 @PostMapping("/{id}/finance-requests") public ApiResponse<View> prepare(@PathVariable String id,@RequestBody Prepare request){return ApiResponse.success(finance.prepare(id,request.lineId(),request.command()));}
 @PostMapping("/{id}/finance-acknowledgments") public ApiResponse<View> acknowledge(@PathVariable String id,@RequestBody Acknowledgment request){return ApiResponse.success(finance.acknowledge(id,request));}
 @ExceptionHandler(OrderProblem.class) public ResponseEntity<ApiResponse<Void>> problem(OrderProblem e){return ResponseEntity.status(e.status()).body(ApiResponse.error(e.getMessage(),"OMS_"+e.status(),null));}
}
