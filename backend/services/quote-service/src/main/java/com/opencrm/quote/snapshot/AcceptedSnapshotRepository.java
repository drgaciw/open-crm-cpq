package com.opencrm.quote.snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
public interface AcceptedSnapshotRepository extends JpaRepository<AcceptedSnapshot, Long> {
    @Query("select s from AcceptedSnapshot s where s.snapshotId = :snapshotId and s.acceptedRevision = :revision and (s.quoteId = :quoteIdentity or s.quoteNumber = :quoteIdentity)")
    Optional<AcceptedSnapshot> lookup(String snapshotId, String quoteIdentity, int revision);
}
