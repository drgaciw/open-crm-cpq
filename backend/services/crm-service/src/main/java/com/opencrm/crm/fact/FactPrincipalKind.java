package com.opencrm.crm.fact;

/**
 * Who is calling ProposeFact. Only a service principal may auto-apply a VERIFIED fact.
 * A person principal always leaves a proposal (ADR-028 D-05).
 */
public enum FactPrincipalKind {
    HUMAN,
    AGENT
}
