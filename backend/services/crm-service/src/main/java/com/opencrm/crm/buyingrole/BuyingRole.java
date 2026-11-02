package com.opencrm.crm.buyingrole;

/**
 * Closed buying-committee role for one contact on one deal (DRG-518).
 * Not free text and not a tenant-configured option list.
 */
public enum BuyingRole {
    DECISION_MAKER,
    CHAMPION,
    ECONOMIC_BUYER,
    BILLING_CONTACT,
    TECHNICAL_EVALUATOR,
    BLOCKER;

    /**
     * Value stored in {@code deal_buying_roles.role}. Adding a constant fails compilation
     * until this switch lists it.
     */
    public String storageValue() {
        return switch (this) {
            case DECISION_MAKER -> "DECISION_MAKER";
            case CHAMPION -> "CHAMPION";
            case ECONOMIC_BUYER -> "ECONOMIC_BUYER";
            case BILLING_CONTACT -> "BILLING_CONTACT";
            case TECHNICAL_EVALUATOR -> "TECHNICAL_EVALUATOR";
            case BLOCKER -> "BLOCKER";
        };
    }

    /**
     * Reads a stored role. Unknown text is rejected.
     */
    public static BuyingRole fromStorage(String stored) {
        if (stored == null) {
            return null;
        }
        return switch (stored) {
            case "DECISION_MAKER" -> DECISION_MAKER;
            case "CHAMPION" -> CHAMPION;
            case "ECONOMIC_BUYER" -> ECONOMIC_BUYER;
            case "BILLING_CONTACT" -> BILLING_CONTACT;
            case "TECHNICAL_EVALUATOR" -> TECHNICAL_EVALUATOR;
            case "BLOCKER" -> BLOCKER;
            default -> throw new IllegalArgumentException("Unknown buying role");
        };
    }
}
