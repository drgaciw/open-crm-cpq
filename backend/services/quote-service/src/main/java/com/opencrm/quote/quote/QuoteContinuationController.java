package com.opencrm.quote.quote;

import com.opencrm.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/quotes/continuations") @RequiredArgsConstructor
public class QuoteContinuationController {
    private final QuoteContinuationService continuations;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuoteDto> author(@Valid @RequestBody QuoteContinuationRequest request) {
        return ApiResponse.success(continuations.author(request),"Continuation draft created; calculate before submission");
    }
    @PostMapping("/preview")
    public ApiResponse<QuoteContinuationPolicy.Plan> preview(@Valid @RequestBody QuoteContinuationRequest request) {
        return ApiResponse.success(continuations.preview(request));
    }
}
