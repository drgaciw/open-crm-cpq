package com.opencrm.approval.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A comment or thread reply on an approval case. The author is the caller.
 * {@code quoteVersion} is stamped by the quote BFF from the quote, not chosen by the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCommentRequest {

    @NotBlank(message = "Comment body is required")
    private String body;

    private Long parentCommentId;

    private Long quoteVersion;
}
