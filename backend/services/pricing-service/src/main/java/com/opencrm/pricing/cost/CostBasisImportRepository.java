package com.opencrm.pricing.cost;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CostBasisImportRepository extends JpaRepository<CostBasisImport, Long> {

    Page<CostBasisImport> findAllByOrderByIngestedAtDescIdDesc(Pageable pageable);
}
