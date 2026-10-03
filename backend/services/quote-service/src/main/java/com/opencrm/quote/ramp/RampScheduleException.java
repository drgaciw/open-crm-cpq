package com.opencrm.quote.ramp;

import com.opencrm.common.exception.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 422 for ramp authoring. {@code errorCode} is the failure class. {@code reasons}
 * are the machine-readable FR-02 codes (or {@link RampReasons#NOT_SUPPORTED}).
 */
@Getter
public class RampScheduleException extends BusinessException {

    private final List<String> reasons;

    public RampScheduleException(String errorCode, List<String> reasons) {
        super(reasons.isEmpty() ? errorCode : String.join(",", reasons),
                HttpStatus.UNPROCESSABLE_CONTENT,
                errorCode);
        this.reasons = List.copyOf(reasons);
    }

    public static RampScheduleException notSupported() {
        return new RampScheduleException(RampReasons.NOT_SUPPORTED, List.of(RampReasons.NOT_SUPPORTED));
    }

    public static RampScheduleException invalid(List<String> reasons) {
        return new RampScheduleException(RampReasons.SCHEDULE_INVALID, reasons);
    }

    public static RampScheduleException stale() {
        return new RampScheduleException(RampReasons.SCHEDULE_STALE, List.of(RampReasons.SCHEDULE_STALE));
    }
}
