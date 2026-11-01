package com.opencrm.quote.lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LifecycleChangeRepository extends JpaRepository<LifecycleChange, UUID> {

    Optional<LifecycleChange> findByIdAndTid(UUID id, String tid);

    Optional<LifecycleChange> findByTidAndIdempotencyKey(String tid, String idempotencyKey);
}
