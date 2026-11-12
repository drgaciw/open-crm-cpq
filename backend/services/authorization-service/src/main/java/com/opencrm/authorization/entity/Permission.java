package com.opencrm.authorization.entity;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * DRG-307 / BC-19-03. One grantable permission: an {@code action} on a {@code resource} (either may be {@code "*"},
 * meaning any). {@code GrantPermission} finds-or-creates this row and links it to a {@link Role} via
 * {@code role_permissions}; {@code Authorize} matches a requested action/resource against the permissions of the
 * principal's active roles.
 */
@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(name = "uk_permissions_action_resource",
        columnNames = {"action", "resource"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Permission extends BaseEntity {

    /** What may be done, e.g. {@code "quote:approve"}, or {@code "*"} for any action. */
    @Column(name = "action", nullable = false, length = 150)
    private String action;

    /** What it may be done to, e.g. {@code "quote:*"}, or {@code "*"} for any resource. */
    @Column(name = "resource", nullable = false, length = 150)
    private String resource;
}
