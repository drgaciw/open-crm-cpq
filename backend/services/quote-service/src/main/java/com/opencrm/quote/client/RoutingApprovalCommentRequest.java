package com.opencrm.quote.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Comment body sent to approval-routing. The quote version is the quote's, not the caller's. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingApprovalCommentRequest {

    private String body;
    private Long parentCommentId;
    private Long quoteVersion;
}
