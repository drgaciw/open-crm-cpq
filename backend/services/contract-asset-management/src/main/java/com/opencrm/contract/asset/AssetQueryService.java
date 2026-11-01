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
        Asset asset = assets.findById(id)
                .filter(row -> !row.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
        return mapper.toDto(asset);
    }

    public List<AssetDto> list(String accountId, Long subscriptionId, String contractLineId) {
        List<Asset> rows;
        if (subscriptionId != null && present(contractLineId)) {
            rows = assets.findBySubscriptionIdAndContractLineIdAndDeletedFalse(subscriptionId, contractLineId)
                    .map(List::of).orElseGet(List::of);
        } else if (subscriptionId != null) {
            rows = assets.findBySubscriptionIdAndDeletedFalse(subscriptionId);
        } else if (present(contractLineId)) {
            rows = assets.findByContractLineIdAndDeletedFalse(contractLineId);
        } else if (present(accountId)) {
            rows = assets.findByAccountIdAndDeletedFalse(accountId);
        } else {
            rows = assets.findByDeletedFalse();
        }
        if (present(accountId)) {
            rows = rows.stream().filter(asset -> accountId.equals(asset.getAccountId())).toList();
        }
        return mapper.toDtoList(rows);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
