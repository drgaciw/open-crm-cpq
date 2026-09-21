package com.opencrm.order.recovery;
import java.time.Instant;
import java.util.List;
public final class RecoveryDtos {
 private RecoveryDtos() {}
 public record RecoveryCommand(long expectedVersion,String operationKey,String reason) {}
 public record Assignment(long expectedVersion,String operationKey,String reason,String owner) {}
 public record Resolution(long expectedVersion,String operationKey,String reason,String action) {}
 public record NoEffect(long expectedVersion,String operationKey,String reason,String evidenceReference,Instant occurredAt) {}
 public record Operation(String operationKey,String orderId,String status,long orderVersion) {}
 public record ExceptionView(String id,String orderId,String orderNumber,String customerId,String taskId,String category,String severity,String status,String owner,String nextAction,String reason,Instant firstOccurredAt,Instant lastOccurredAt,long version) {}
 public record Page<T>(List<T> content,int page,int size,long totalElements,int totalPages) {}
 public record ExhaustedEvent(long id,String eventId,String orderId,String eventType,int attempts,Instant exhaustedAt) {}
}
