package com.opencrm.contract.service;

import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Same identity, different commercial body. There is no HTTP write route; callers still see a conflict. */
public class RenewalPolicyConflictException extends BusinessException {

    public RenewalPolicyConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
    }
}
