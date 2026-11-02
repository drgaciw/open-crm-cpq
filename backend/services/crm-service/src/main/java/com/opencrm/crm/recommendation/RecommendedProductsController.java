package com.opencrm.crm.recommendation;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Advisory cross-sell for a deal. The response does not change the deal, a quote, or eligibility configuration.
 */
@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
@Tag(name = "Deal recommendations", description = "Advisory product suggestions")
public class RecommendedProductsController {

    private final RecommendedProductsService recommendedProductsService;

    @GetMapping("/{dealId}/recommended-products")
    @Operation(summary = "Suggest products for a deal",
            description = "Advisory only. Deterministic ranking over the local catalog corpus, account history, and won deals. "
                    + "Drops product ids the eligibility gate marks ineligible. "
                    + "Does not change the deal stage, any quote, or eligibility configuration.")
    public ApiResponse<List<RecommendedProductDto>> recommendedProducts(@PathVariable Long dealId) {
        return ApiResponse.success(recommendedProductsService.recommend(dealId));
    }
}
