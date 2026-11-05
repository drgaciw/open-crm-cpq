package com.opencrm.quote.snapshot;
/** OMS alone authorizes a preexecution source revision; this carries no replacement commercial values. */
public record QuoteRevisionRequest(int schemaVersion,String eventId,String operationKey,String orderId,
        String quoteId,int acceptedRevision,String scopeId,String customerId,String reason) {}
