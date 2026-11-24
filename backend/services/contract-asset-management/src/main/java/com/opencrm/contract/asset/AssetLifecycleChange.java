package com.opencrm.contract.asset;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One durable asset lifecycle change, ordered by effective date then {@code sequence_no}.
 * {@code change_uuid} is the BaseEntity uuid column under that name (ADR-039, flat in this package).
 */
@Entity
@Table(name = "asset_lifecycle_changes")
@AttributeOverride(name = "uuid", column = @Column(name = "change_uuid", nullable = false, updatable = false, length = 36))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AssetLifecycleChange extends BaseEntity {

    public static final int LAST_ERROR_LIMIT = 500;

    /** Where the change was requested. Activation and document apply are the only sources in this wave. */
    public enum Source {
        ACTIVATION,
        DOCUMENT_APPLY
    }

    @Column(name = "dedupe_key", nullable = false, updatable = false, length = 200)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetLifecycleChangeKind kind;

    @Column(name = "asset_uuid", length = 36)
    private String assetUuid;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "contract_line_id", nullable = false, length = 64)
    private String contractLineId;

    @Column(name = "account_id", nullable = false, length = 255)
    private String accountId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private Long sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private Source sourceType;

    @Column(name = "source_amendment_id", length = 36)
    private String sourceAmendmentId;

    @Column(name = "source_apply_unit", length = 64)
    private String sourceApplyUnit;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetLifecycleChangeStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = LAST_ERROR_LIMIT)
    private String lastError;

    @Column(name = "applied_event_id", length = 36)
    private String appliedEventId;

    @Column(name = "applied_at")
    private Instant appliedAt;

    public String getChangeUuid() {
        return getUuid();
    }

    public void markApplied(Instant when) {
        this.status = AssetLifecycleChangeStatus.APPLIED;
        this.appliedAt = when;
    }

    public void markFailed(String error) {
        this.status = AssetLifecycleChangeStatus.FAILED;
        this.attempts = this.attempts + 1;
        this.lastError = clip(error);
    }

    private static String clip(String error) {
        String text = error == null || error.isBlank() ? "failed" : error.strip();
        return text.length() <= LAST_ERROR_LIMIT ? text : text.substring(0, LAST_ERROR_LIMIT);
    }
}
