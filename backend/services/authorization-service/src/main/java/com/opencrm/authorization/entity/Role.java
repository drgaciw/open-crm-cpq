package com.opencrm.authorization.entity;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * DRG-307 / BC-19-03. A named role of the role/permission model: {@code SalesRep}, {@code DealDesk},
 * {@code Approver}, {@code CatalogAdmin}, {@code PricingAdmin}, {@code Finance}, {@code PartnerUser} (initial set,
 * seeded by {@code V1_6__seed_initial_roles.sql}). This is a separate, internally-managed permission catalog from the
 * Entra app roles {@code libs/security} maps into {@code ROLE_*} authorities (ADR-016 decision 7) — it is what a
 * context asks {@link com.opencrm.authorization.service.AuthorizationQueryService#authorize} about, not what
 * authenticates the caller.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "permissions")
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    public void grant(Permission permission) {
        this.permissions.add(permission);
    }
}
