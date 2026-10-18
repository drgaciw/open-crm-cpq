package com.opencrm.pricing.renewal;

import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stateless preview of one renewal price. The resolver is not a Spring bean. This service writes nothing.
 */
@Service
public class RenewalResolveService {

    private final RenewalUpliftResolver resolver = new RenewalUpliftResolver();

    public RenewalResolveResponse resolve(RenewalResolveRequest request) {
        rejectOutOfRangePercents(request);
        try {
            RenewalUpliftResult result = resolver.resolve(request.toUpliftRequest());
            if (result.resolution() != RenewalUpliftResolution.PRICED
                    && result.resolution() != RenewalUpliftResolution.PRICE_PROTECTION_CONFLICT) {
                throw new BusinessException(
                        "Renewal price is unresolved",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        result.resolution().name());
            }
            return RenewalResolveResponse.previewOf(result);
        } catch (RenewalUpliftModeOutOfSliceException ex) {
            throw new BusinessException(
                    "Mode is outside this slice",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "MODE_OUT_OF_SLICE");
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage(), HttpStatus.BAD_REQUEST, "INVALID_RENEWAL_REQUEST");
        }
    }

    private static void rejectOutOfRangePercents(RenewalResolveRequest request) {
        if (request.rungs() == null) {
            return;
        }
        for (RenewalResolveRung rung : request.rungs()) {
            BigDecimal percent = rung.upliftPercentPoints();
            if (percent != null && (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new BusinessException(
                        "Renewal uplift percent points must be between 0 and 100",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "VALIDATION_ERROR");
            }
        }
    }
}
