package com.opencrm.contract.asset;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Read-only asset history, supersede chain, and as-of installed base. */
@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "Asset history", description = "Asset history, supersede chain, and as-of installed base")
public class AssetQueryController {

    private final AssetHistoryProjection history;

    public AssetQueryController(AssetHistoryProjection history) {
        this.history = history;
    }

    @GetMapping("/{assetId}/history")
    @Operation(summary = "Full history of an asset")
    public ApiResponse<List<AssetHistoryEntryDto>> history(@PathVariable String assetId) {
        return ApiResponse.success(history.history(assetId));
    }

    @GetMapping("/{assetId}/supersede-chain")
    @Operation(summary = "Supersede chain in one or both directions")
    public ApiResponse<SupersedeChainDto> supersedeChain(
            @PathVariable String assetId,
            @RequestParam(name = "direction", defaultValue = "both") String direction) {
        return ApiResponse.success(history.chain(assetId, direction));
    }

    @GetMapping(params = "asOf")
    @Operation(summary = "Installed base of an account on a date")
    public ApiResponse<List<InstalledAssetDto>> installedBase(
            @RequestParam String accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.success(history.installedBase(accountId, asOf));
    }
}
