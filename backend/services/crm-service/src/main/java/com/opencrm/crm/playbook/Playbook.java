package com.opencrm.crm.playbook;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.common.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Sales playbook definition (DRG-486 / BC-13-02).
 * <p>
 * {@link #definitionVersion} is a local integer starting at 1. BC-18 document
 * versioning is not landed, so this is not a promoted revision and previous
 * step sets are not kept. {@link BaseEntity#getVersion()} remains the optimistic
 * lock and is not the playbook version exposed on the API.
 */
@Entity
@Table(name = "playbooks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Playbook extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "definition_version", nullable = false)
    @Builder.Default
    private Integer definitionVersion = 1;

    @OneToMany(mappedBy = "playbook", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @SQLRestriction("is_deleted = false")
    @Builder.Default
    private List<PlaybookStep> steps = new ArrayList<>();

    @PrePersist
    void ensureDefinitionVersion() {
        if (definitionVersion == null || definitionVersion < 1) {
            definitionVersion = 1;
        }
    }

    public void addStep(PlaybookStep step) {
        if (steps == null) {
            steps = new ArrayList<>();
        }
        step.setPlaybook(this);
        steps.add(step);
    }

    /**
     * Increases the local definition integer. Called only after a step or question
     * change on a playbook that already exists. Creating the initial definition
     * stays at 1.
     */
    public void bumpDefinitionVersion() {
        int current = definitionVersion == null || definitionVersion < 1 ? 1 : definitionVersion;
        if (current == Integer.MAX_VALUE) {
            throw new BusinessException("Playbook version cannot increase further", "PLAYBOOK_VERSION_LIMIT");
        }
        this.definitionVersion = current + 1;
    }
}
