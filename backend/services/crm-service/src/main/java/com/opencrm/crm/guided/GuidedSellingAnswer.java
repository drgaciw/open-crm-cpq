package com.opencrm.crm.guided;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * The current answer for one needs question on a guided selling session.
 */
@Entity
@Table(name = "guided_selling_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uq_guided_answer_question", columnNames = {"session_id", "question_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GuidedSellingAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GuidedSellingSession session;

    @Column(name = "question_key", nullable = false, length = 64)
    private String questionKey;

    @Column(name = "answer_value", nullable = false, length = 255)
    private String answerValue;
}
