package com.opencrm.crm.guided;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Guided selling API. Recommendations returned by derive are advisory.
 */
@RestController
@RequestMapping("/api/v1/guided-sessions")
@RequiredArgsConstructor
@Tag(name = "Guided selling", description = "Needs-based solution derivation")
public class GuidedSellingController {

    private final GuidedSellingService guidedSellingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "StartGuidedSession", description = "Opens a needs-based session for the authenticated seller")
    public ApiResponse<GuidedSessionDto> start(
            @Valid @RequestBody(required = false) StartGuidedSessionRequest request) {
        return ApiResponse.success(guidedSellingService.start(request), "Guided session started");
    }

    @GetMapping("/questions")
    @Operation(summary = "List the needs questions the derivation map understands")
    public ApiResponse<List<GuidedQuestionDto>> questions() {
        return ApiResponse.success(guidedSellingService.questions());
    }

    @PostMapping("/{id}/answers")
    @Operation(summary = "AnswerQuestion", description = "Records or replaces one needs answer")
    public ApiResponse<GuidedSessionDto> answer(
            @PathVariable Long id,
            @Valid @RequestBody AnswerQuestionRequest request) {
        return ApiResponse.success(guidedSellingService.answer(id, request), "Answer recorded");
    }

    @PostMapping("/{id}/derive")
    @Operation(summary = "DeriveSolution",
            description = "Derives an advisory product and options and starts a configuration session. "
                    + "Does not complete the configuration and does not mark a deal won.")
    public ApiResponse<GuidedSessionDto> derive(@PathVariable Long id) {
        return ApiResponse.success(guidedSellingService.derive(id), "Advisory solution derived");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a guided selling session")
    public ApiResponse<GuidedSessionDto> get(@PathVariable Long id) {
        return ApiResponse.success(guidedSellingService.get(id));
    }
}
