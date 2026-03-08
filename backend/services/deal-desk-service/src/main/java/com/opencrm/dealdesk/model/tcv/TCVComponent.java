package com.opencrm.dealdesk.model.tcv;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TCVComponent {

    private String id;
    private String name;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String currencyCode;
    private ComponentType type;

    public enum ComponentType {
        PRODUCT,
        SERVICE,
        SUBSCRIPTION,
        IMPLEMENTATION,
        MAINTENANCE,
        SUPPORT
    }

    public BigDecimal calculateValue() {
        return quantity != null && unitPrice != null
                ? quantity.multiply(unitPrice)
                : BigDecimal.ZERO;
    }
}
