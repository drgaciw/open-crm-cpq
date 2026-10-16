package com.opencrm.pricing.renewal;

/** Basis the renewal percent hits. This slice prices {@link #CONTRACTED_NET} only. */
public enum RenewalUpliftBasis {
    CONTRACTED_NET,
    CURRENT_LIST,
    LIST_LESS_CONTRACTED_DISCOUNT
}
