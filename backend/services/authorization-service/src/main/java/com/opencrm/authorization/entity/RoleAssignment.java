package com.opencrm.authorization.entity;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * DRG-307 / BC-19-03. One {@code AssignRole} outcome: a {@link Role} held by a principal (a person's actor id, or
 * {@code service:<client-id>} — see {@code com.opencrm.security.Caller}), from {@code assignedAt} until it is
 * revoked. An active assignment has {@code revokedAt == null}; {@code Authorize} only ever considers active
 * assignments. {@code assignedBy}/{@code revokedBy} are the verified caller's actor id (never a request field —
 * ADR-016 decision 6).
 */
@Entity
@Table(name = "role_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RoleAssignment extends BaseEntity {

    @Column(name = "principal_id", nullable = false, length = 255)
    private String principalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "assigned_by", nullable = false, length = 255)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "revoked_by", length = 255)
    private String revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
