package com.opencrm.pricebook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PriceBookRepository extends JpaRepository<PriceBook, Long> {

    Optional<PriceBook> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PriceBook> findWithLockByIdAndDeletedFalse(Long id);

    Page<PriceBook> findByDeletedFalse(Pageable pageable);

    Page<PriceBook> findByRegionAndDeletedFalse(String region, Pageable pageable);

    Optional<PriceBook> findByRegionAndDeletedFalse(String region);

    Optional<PriceBook> findByIsDefaultTrueAndDeletedFalse();
}
