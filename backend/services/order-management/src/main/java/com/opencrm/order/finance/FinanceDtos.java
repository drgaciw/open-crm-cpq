package com.opencrm.order.finance;
import com.opencrm.order.dto.OrderDtos.Command;
import java.time.Instant;
public final class FinanceDtos {
 private FinanceDtos() {}
 public record Prepare(long expectedVersion,String reason,String operationKey,String lineId) {
  public Command command(){return new Command(expectedVersion,reason,operationKey);}
 }
 public record Acknowledgment(long expectedVersion,String reason,String operationKey,String lineId,int quantity,String externalReference,Instant occurredAt,String requestKey,String outcome) {}
 public record RequestView(String requestKey,String lineId,int quantity,int acknowledgedQuantity,String status,String payload) {}
}
