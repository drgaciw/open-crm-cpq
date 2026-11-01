package com.opencrm.crm.buyingrole;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Assigns one closed buying role to a contact on the deal in the path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignBuyingRoleRequest {

    @NotNull(message = "Contact is required")
    @Positive(message = "Contact id must be positive")
    private Long contactId;

    @NotNull(message = "Buying role is required")
    private BuyingRole role;
}
