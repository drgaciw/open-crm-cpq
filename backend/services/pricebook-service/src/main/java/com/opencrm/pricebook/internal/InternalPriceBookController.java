package com.opencrm.pricebook.internal;

import com.opencrm.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/price-books")
@RequiredArgsConstructor
public class InternalPriceBookController {

    private final PriceBookInternalResolutionService resolutionService;

    @PostMapping("/resolve-batch")
    public ApiResponse<ResolveBatchResponse> resolveBatch(@Valid @RequestBody ResolveBatchRequest request) {
        ResolveBatchResponse data = resolutionService.resolveBatch(
                request.getRegion(),
                request.getProductExternalIds());
        return ApiResponse.success(data);
    }
}
