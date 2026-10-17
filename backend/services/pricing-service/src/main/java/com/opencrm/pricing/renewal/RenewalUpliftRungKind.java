package com.opencrm.pricing.renewal;

/** D-04 order is clause, then line, then agreement, then account, then the rules default. */
public enum RenewalUpliftRungKind {
    CLAUSE,
    LINE,
    AGREEMENT,
    ACCOUNT,
    RULES
}
