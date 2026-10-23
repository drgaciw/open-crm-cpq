package com.opencrm.quote.ramp;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.quote.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Preview expansion. Reads the quote so a missing id is a 404, and writes nothing.
 */
@Service
@RequiredArgsConstructor
public class RampScheduleQueryService {

    private final QuoteRepository quoteRepository;
    private final RampPricingProperties properties;
    private final RampEscalatorExpander expander;

    @Transactional(readOnly = true)
    public RampScheduleDto expand(Long quoteId, RampExpandRequest request) {
        if (quoteRepository.findByIdAndDeletedFalse(quoteId).isEmpty()) {
            throw new ResourceNotFoundException("Quote", quoteId);
        }
        if (!properties.isEnabled()) {
            throw RampScheduleException.notSupported();
        }
        int duration = request.getDurationInMonths() == null ? 0 : request.getDurationInMonths();
        List<RampStepDto> steps = expander.expand(duration, request.getEscalator(), request.getQuantity());
        if (steps.size() > properties.getMaxPeriods()) {
            throw RampScheduleException.invalid(List.of(RampReasons.TOO_MANY_PERIODS));
        }
        return RampScheduleDto.builder()
                .steps(steps)
                .escalator(request.getEscalator())
                .build();
    }

    /**
     * Preview an extension. Reads the quote so a missing id is a 404, and writes nothing.
     * Existing steps stay as submitted; new steps continue from the last absolute unit.
     */
    @Transactional(readOnly = true)
    public RampScheduleDto extend(Long quoteId, RampExtendRequest request) {
        if (quoteRepository.findByIdAndDeletedFalse(quoteId).isEmpty()) {
            throw new ResourceNotFoundException("Quote", quoteId);
        }
        if (!properties.isEnabled()) {
            throw RampScheduleException.notSupported();
        }
        int duration = request.getDurationInMonths() == null ? 0 : request.getDurationInMonths();
        List<RampStepDto> steps = expander.extend(
                request.getSteps() == null ? List.of() : request.getSteps(),
                duration,
                request.getEscalator());
        if (steps.size() > properties.getMaxPeriods()) {
            throw RampScheduleException.invalid(List.of(RampReasons.TOO_MANY_PERIODS));
        }
        return RampScheduleDto.builder()
                .steps(steps)
                .escalator(request.getEscalator())
                .build();
    }
}
