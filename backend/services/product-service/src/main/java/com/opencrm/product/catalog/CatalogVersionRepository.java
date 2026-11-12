package com.opencrm.product.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogVersionRepository extends JpaRepository<CatalogVersion, Long> {

    Optional<CatalogVersion> findByUuidAndDeletedFalse(String uuid);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM CatalogVersion v WHERE v.deleted = false AND "
           + "((:tenantId IS NULL AND v.tenantId IS NULL) OR v.tenantId = :tenantId)")
    int findMaxVersionNumberByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT v FROM CatalogVersion v WHERE v.deleted = false AND "
           + "((:tenantId IS NULL AND v.tenantId IS NULL) OR v.tenantId = :tenantId) "
           + "ORDER BY v.versionNumber DESC")
    List<CatalogVersion> findAllByTenantIdOrderByVersionNumberDesc(@Param("tenantId") String tenantId);

    default Optional<CatalogVersion> findLatestByTenantId(String tenantId) {
        return findAllByTenantIdOrderByVersionNumberDesc(tenantId).stream().findFirst();
    }
}
