package com.opencrm.contract.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findBySubscriptionIdAndContractLineIdAndDeletedFalse(Long subscriptionId, String contractLineId);

    List<Asset> findBySubscriptionIdAndDeletedFalse(Long subscriptionId);

    List<Asset> findByAccountIdAndDeletedFalse(String accountId);

    List<Asset> findByContractLineIdAndDeletedFalse(String contractLineId);

    List<Asset> findByDeletedFalse();
}
