package com.opencrm.contract.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Natural acceptance identity independent of a Kafka delivery header. */
@Entity @Table(name = "applied_quote_acceptances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class AppliedQuoteAcceptance extends BaseEntity {
    @Column(name = "accepted_quote_id", nullable = false, unique = true, length = 255)
    private String acceptedQuoteId;
    @Column(name = "subscription_id", nullable = false) private Long subscriptionId;
    @Column(nullable = false, length = 20) private String operation;
    @Column(name = "commercial_digest", nullable = false, length = 64) private String commercialDigest;
}
