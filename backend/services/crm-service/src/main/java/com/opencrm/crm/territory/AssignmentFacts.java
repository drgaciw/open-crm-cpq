package com.opencrm.crm.territory;

/**
 * Organization attributes a territory rule can match: geography, industry, and size.
 */
public record AssignmentFacts(String country, String state, String industry, Integer employeeCount) {
}
