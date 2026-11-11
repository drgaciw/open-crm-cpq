package com.opencrm.quote.comparison;

import com.opencrm.quote.quote.Quote;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Separate read/preference boundary; existing quote lifecycle repositories are unchanged. */
public interface QuoteComparisonRepository extends Repository<Quote, Long> {
    Optional<Quote> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id and q.tenantId = :tenant and q.deleted = false")
    Optional<Quote> lockAnchor(@Param("id") Long id, @Param("tenant") String tenant);

    @Query("select q from Quote q where q.tenantId = :tenant and q.dealExternalId = :deal "
            + "and q.deleted = false order by q.id")
    List<Quote> alternatives(@Param("tenant") String tenant, @Param("deal") String deal, Pageable limit);
}
