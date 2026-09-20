package com.opencrm.quote.snapshot;
import com.opencrm.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/oms/quotes") @RequiredArgsConstructor
public class SourceTermsController {
    private final SourceTermsService service;
    @PostMapping("/{id}/source-terms")
    public ApiResponse<Void> capture(@PathVariable Long id, @RequestBody SourceTermsRequest request, Authentication authentication) {
        service.capture(id, request, authentication);
        return ApiResponse.success(null);
    }
}
