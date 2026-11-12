package com.opencrm.authorization.dto;

/** The {@code Authorize(principal, action, resource)} query's answer. */
public record AuthorizationDecision(String principalId, String action, String resource, boolean allowed) {
}
