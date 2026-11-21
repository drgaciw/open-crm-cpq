package com.opencrm.pricing.metricshistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetricsSnapshotRepository extends JpaRepository<MetricsSnapshot, Long> {

    List<MetricsSnapshot> findByCustomerExternalIdAndSnapshotAtGreaterThanEqualOrderBySnapshotAtDesc(
            String customerExternalId, Instant cutoff);

    Optional<MetricsSnapshot> findFirstByCustomerExternalIdOrderBySnapshotAtDesc(String customerExternalId);
}
