package com.opencrm.contract.asset;

import com.opencrm.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssetQueryService {
    private final AssetRepository assets;
    private final AssetMapper mapper;

    public AssetQueryService(AssetRepository assets, AssetMapper mapper) {
        this.assets = assets;
        this.mapper = mapper;
    }

    public AssetDto get(Long id) {
        InstalledBaseReadScope scope = InstalledBaseReadScope.current();
        if (!scope.open()) {
            throw new ResourceNotFoundException("Asset", id);
        }
        Asset asset = assets.findOne(InstalledBaseQueries.matching(scope.tenantId(), null, null, null, id))
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
        return mapper.toDto(asset);
    }

    public List<AssetDto> list(String accountId, Long subscriptionId, String contractLineId) {
        InstalledBaseReadScope scope = InstalledBaseReadScope.current();
        if (!scope.open()) {
            return List.of();
        }
        return mapper.toDtoList(assets.findAll(
                InstalledBaseQueries.matching(scope.tenantId(), accountId, subscriptionId, contractLineId, null)));
    }
}
