package com.opencrm.order.controller;

import com.opencrm.order.finance.FinanceService;
import com.opencrm.order.finance.FinanceDtos.Acknowledgment;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.order.domain.OrderProblem;
import com.opencrm.order.fulfillment.EvidenceCommand;
import com.opencrm.order.fulfillment.EvidenceService;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderEvidenceController {
    private final EvidenceService evidence;
    private final FinanceService finance;
    public OrderEvidenceController(EvidenceService evidence, FinanceService finance) {
        this.evidence=evidence;this.finance=finance;
    }
    public record Evidence(long expectedVersion,String operationKey,String reason,String lineId,String kind,
            int quantity,String externalReference,Instant occurredAt,String allocationReference,
            String shipmentReference,Instant expiresAt,String requestKey) {}

    @PostMapping("/api/v1/orders/{id}/evidence")
    public ResponseEntity<? extends ApiResponse<?>> record(@PathVariable String id,@RequestBody Evidence command) {
        if ("FINANCE_ACK".equals(command.kind())) {
            var acknowledgment=new Acknowledgment(command.expectedVersion(),command.reason(),command.operationKey(),
                    command.lineId(),command.quantity(),command.externalReference(),command.occurredAt(),
                    command.requestKey(),"ACKNOWLEDGED");
            return ResponseEntity.ok(ApiResponse.success(finance.acknowledge(id,acknowledgment)));
        }
        var receipt=evidence.record(id,new EvidenceCommand(command.expectedVersion(),command.operationKey(),
                command.reason(),command.lineId(),command.kind(),command.quantity(),command.externalReference(),
                command.occurredAt(),command.allocationReference(),command.shipmentReference(),command.expiresAt()));
        return ResponseEntity.status("APPLIED".equals(receipt.status())?200:202).body(ApiResponse.success(receipt));
    }
    @ExceptionHandler(OrderProblem.class)
    public ResponseEntity<ApiResponse<Void>> problem(OrderProblem e) {
        return ResponseEntity.status(e.status()).body(ApiResponse.error(e.getMessage(),"OMS_"+e.status(),null));
    }
}
