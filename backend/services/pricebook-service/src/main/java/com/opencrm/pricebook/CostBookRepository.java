package com.opencrm.pricebook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CostBookRepository extends JpaRepository<CostBook, Long> {

    Optional<CostBook> findByIdAndDeletedFalse(Long id);

    Page<CostBook> findByDeletedFalse(Pageable pageable);
}
