package com.opencrm.order.dto;
import com.opencrm.common.order.AcceptedOrderSnapshot;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public final class OrderDtos {
 private OrderDtos() {}
 public record FromQuote(@NotBlank String snapshotId,@NotBlank String quoteId,@Min(1) int acceptedRevision) {}
 public record Command(@Min(0) long expectedVersion,@NotBlank String reason,@NotBlank String operationKey) {}
 public record Hold(@Min(0) long expectedVersion,@NotBlank String reason,@NotBlank String operationKey,boolean active) {}
 public record Cancellation(@Min(0) long expectedVersion,@NotBlank String reason,@NotBlank String operationKey,@NotBlank String lineId,@Min(1) int quantity) {}
 public record Line(String id,String description,int ordered,int cancelled,int shipped,int delivered,int remaining,int allocated,String status) {}
 public record View(String id,String orderNumber,String quoteId,String quoteNumber,String customerId,String scopeId,String status,long version,AcceptedOrderSnapshot snapshot,List<Line> lines,List<String> holds,String financeStatus) {}
 public record Task(String id,String lineId,String kind,int templateVersion,String status,Instant dispatchedAt) {}
 public record Timeline(String actor,Instant at,String action,String reason,long version,String evidenceReference) {}
}
