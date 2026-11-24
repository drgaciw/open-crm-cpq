package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Commercial policy blocked a quote. {@code POLICY_BLOCKED} stays the HTTP error code. {@link #reasonCodes}
 * are the machine-readable Blocked causes, such as {@code MARGIN_UNKNOWN} and {@code MARGIN_BELOW_FLOOR}.
 */
@Getter
public class PolicyBlockedException extends BusinessException {

    private final List<String> reasonCodes;

    public PolicyBlockedException(String message, List<String> reasonCodes) {
        super(message, HttpStatus.CONFLICT, "POLICY_BLOCKED");
        this.reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
