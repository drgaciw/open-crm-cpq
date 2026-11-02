package com.opencrm.crm.playbook;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * One ordered step inside a playbook stage. The stage is a DealStage name.
 * Questions are ordered prompt text. There is no buying-role field.
 */
@Entity
@Table(name = "playbook_steps", uniqueConstraints = {
        @UniqueConstraint(name = "uq_playbook_steps_stage_order", columnNames = {"playbook_id", "stage", "sort_order"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PlaybookStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playbook_id", nullable = false)
    private Playbook playbook;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 30)
    private PlaybookStage stage;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @SQLRestriction("is_deleted = false")
    @Builder.Default
    private List<PlaybookQuestion> questions = new ArrayList<>();

    public void addQuestion(PlaybookQuestion question) {
        if (questions == null) {
            questions = new ArrayList<>();
        }
        question.setStep(this);
        questions.add(question);
    }

    /**
     * Replaces the question set in place so orphan removal deletes the previous rows.
     * The collection instance is kept for the persistence context.
     */
    public void replaceQuestions(List<PlaybookQuestion> replacements) {
        if (questions == null) {
            questions = new ArrayList<>();
        }
        questions.clear();
        if (replacements == null) {
            return;
        }
        for (PlaybookQuestion question : replacements) {
            addQuestion(question);
        }
    }
}
