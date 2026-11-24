package com.opencrm.quote.approval;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A deal-desk comment or thread reply. The quote version is taken from the quote, not this body. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCommentRequest {

    @NotBlank(message = "Comment body is required")
    private String body;

    private Long parentCommentId;
}
