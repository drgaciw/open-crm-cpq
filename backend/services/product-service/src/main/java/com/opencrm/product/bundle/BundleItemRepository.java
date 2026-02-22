package com.opencrm.product.bundle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BundleItemRepository extends JpaRepository<BundleItem, Long> {

    Optional<BundleItem> findByIdAndDeletedFalse(Long id);

    List<BundleItem> findByBundleIdAndDeletedFalse(Long bundleId);
}
