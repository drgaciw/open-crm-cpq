package com.opencrm.approval.domain;

/**
 * Lifecycle of one approval-matrix version (BC-04-03). A new version deprecates its predecessor; rows are not edited.
 */
public enum MatrixStatus {
    ACTIVE,
    DEPRECATED
}
