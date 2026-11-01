package com.opencrm.contract.asset;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read side of the asset Open Host Service. Creation happens on accepted-quote activation. */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "Installed-base asset Open Host Service")
public class AssetController {
    private final AssetQueryService assets;

    @GetMapping
    @Operation(summary = "List assets by account, subscription, or contract line")
    public ApiResponse<List<AssetDto>> list(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) Long subscriptionId,
            @RequestParam(required = false) String contractLineId) {
        return ApiResponse.success(assets.list(accountId, subscriptionId, contractLineId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an asset by id")
    public ApiResponse<AssetDto> get(@PathVariable Long id) {
        return ApiResponse.success(assets.get(id));
    }
}
