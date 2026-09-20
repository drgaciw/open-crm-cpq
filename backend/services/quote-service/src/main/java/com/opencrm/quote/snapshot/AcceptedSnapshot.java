package com.opencrm.quote.snapshot;
import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Immutable;
@Entity @Table(name = "accepted_order_snapshots") @Immutable
@Getter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class AcceptedSnapshot extends BaseEntity {
    @Column(nullable=false, unique=true) private String snapshotId;
    @Column(nullable=false) private String quoteId;
    @Column(nullable=false) private String quoteNumber;
    @Column(nullable=false) private int acceptedRevision;
    @Column(nullable=false) private String scopeId;
    @Column(nullable=false) private String customerId;
    @Column(nullable=false, columnDefinition="TEXT") private String payload;
    @Column(nullable=false, length=64) private String snapshotHash;
    @Column(nullable=false, columnDefinition="TEXT") private String holds;
}
