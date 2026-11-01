package com.opencrm.quote.ramp;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes/{quoteId}/ramp-schedule")
@RequiredArgsConstructor
@Tag(name = "Quote ramp schedule", description = "Ramp authoring preview. Expansion and extension do not persist.")
public class RampScheduleController {

    private final RampScheduleQueryService rampScheduleQueryService;

    @PostMapping("/expand")
    @Operation(summary = "Expand an escalator into explicit periods without saving them")
    public ApiResponse<RampScheduleDto> expand(
            @PathVariable Long quoteId,
            @RequestBody RampExpandRequest request) {
        return ApiResponse.success(rampScheduleQueryService.expand(quoteId, request));
    }

    @PostMapping("/extend")
    @Operation(summary = "Append periods from the last unit price without saving them or the quote term")
    public ApiResponse<RampScheduleDto> extend(
            @PathVariable Long quoteId,
            @RequestBody RampExtendRequest request) {
        if (request == null
                || request.getSteps() == null
                || request.getDurationInMonths() == null
                || request.getEscalator() == null) {
            throw new BusinessException(
                    "Ramp extend request needs steps, durationInMonths, and escalator",
                    "VALIDATION_ERROR");
        }
        return ApiResponse.success(rampScheduleQueryService.extend(quoteId, request));
    }
}
