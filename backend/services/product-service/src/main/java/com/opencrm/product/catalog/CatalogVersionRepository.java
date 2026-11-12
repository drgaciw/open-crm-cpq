package com.opencrm.product.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogVersionRepository extends JpaRepository<CatalogVersion, Long> {

    /**
     * Tenant-scoped version lookup. Callers must pass {@link ProductTenantResolver#currentTenantId()} so a
     * leaked UUID cannot resolve another tenant's version (DRG-421 review).
     */
    @Query("SELECT v FROM CatalogVersion v WHERE v.deleted = false AND v.uuid = :uuid AND "
           + "((:tenantId IS NULL AND v.tenantId IS NULL) OR v.tenantId = :tenantId)")
    Optional<CatalogVersion> findByUuidAndTenantIdAndDeletedFalse(
            @Param("uuid") String uuid, @Param("tenantId") String tenantId);

    /**
     * Serialize same-tenant PublishCatalog allocations for the rest of the transaction (Postgres).
     * {@code tenantKey} is {@code COALESCE(tenant_id, '')} so the null-tenant bucket shares one lock.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(CAST(:tenantKey AS text)))", nativeQuery = true)
    Integer lockTenantForPublish(@Param("tenantKey") String tenantKey);

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
