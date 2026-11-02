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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One pre-selected configuration option. {@code advisory} is always true: the configuration
 * engine remains the constraint solver, and this row does not complete a session.
 */
@Entity
@Table(name = "guided_selling_selections", uniqueConstraints = {
        @UniqueConstraint(name = "uq_guided_selection_option", columnNames = {"session_id", "option_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GuidedSellingSelection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GuidedSellingSession session;

    @Column(name = "option_key", nullable = false, length = 128)
    private String optionKey;

    @Column(name = "option_value", nullable = false, length = 255)
    private String optionValue;

    @Column(name = "advisory", nullable = false)
    @Builder.Default
    private boolean advisory = true;

    @Column(name = "rationale", length = 500)
    private String rationale;
}
