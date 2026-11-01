package com.opencrm.quote.lifecycle;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lifecycle_changes")
@Getter
@Setter
@NoArgsConstructor
public class LifecycleChange implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tid", nullable = false, length = 64)
    private String tid;

    @Column(name = "intent", nullable = false, length = 32)
    private String intent;

    @Column(name = "initiation_trigger", nullable = false, length = 32)
    private String initiationTrigger;

    @Column(name = "request_identifier", length = 200)
    private String requestIdentifier;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Column(name = "idempotency_body_hash", length = 64)
    private String idempotencyBodyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LifecycleChangeStatus status;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "version", nullable = false)
    private Long version;

    /** Response-shape validation only, not proof of a trusted pricing decision. Null requires reprice. */
    @Column(name = "pricing_validation_version")
    private Integer pricingValidationVersion;

    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    @Column(name = "contract_effective_date", nullable = false)
    private LocalDate contractEffectiveDate;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "output_mode", nullable = false, length = 32)
    private String outputMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @OneToMany(mappedBy = "lifecycleChange", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<ChangeLine> lines = new ArrayList<>();

    @Transient
    private boolean newly = true;

    @Override
    public boolean isNew() {
        return newly;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newly = false;
    }
}
