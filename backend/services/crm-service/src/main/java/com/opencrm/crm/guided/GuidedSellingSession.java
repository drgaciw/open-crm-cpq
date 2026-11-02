package com.opencrm.crm.guided;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One guided selling session (DRG-487). Answers describe needs. A derived solution stores
 * advisory option selections and, when configuration-engine is reachable, its session id.
 */
@Entity
@Table(name = "guided_selling_sessions", indexes = {
        @Index(name = "idx_guided_sessions_deal", columnList = "deal_id"),
        @Index(name = "idx_guided_sessions_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GuidedSellingSession extends BaseEntity {

    @Column(name = "deal_id")
    private Long dealId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GuidedSessionStatus status;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "configuration_session_id")
    private Long configurationSessionId;

    @Column(name = "recorded_locally", nullable = false)
    private boolean recordedLocally;

    @Column(name = "seller_actor", nullable = false)
    private String sellerActor;

    @Column(name = "rationale", length = 500)
    private String rationale;

    @Column(name = "derived_at")
    private Instant derivedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @SQLRestriction("is_deleted = false")
    @Builder.Default
    private List<GuidedSellingAnswer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @SQLRestriction("is_deleted = false")
    @Builder.Default
    private List<GuidedSellingSelection> selections = new ArrayList<>();

    public void putAnswer(String questionKey, String answerValue) {
        for (GuidedSellingAnswer existing : answers) {
            if (questionKey.equals(existing.getQuestionKey())) {
                existing.setAnswerValue(answerValue);
                return;
            }
        }
        GuidedSellingAnswer created = new GuidedSellingAnswer();
        created.setUuid(UUID.randomUUID().toString());
        created.setSession(this);
        created.setQuestionKey(questionKey);
        created.setAnswerValue(answerValue);
        answers.add(created);
    }

    public Map<String, String> answerMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (GuidedSellingAnswer answer : answers) {
            map.put(answer.getQuestionKey(), answer.getAnswerValue());
        }
        return map;
    }

    public void replaceSelections(Map<String, String> chosen, String selectionRationale) {
        selections.clear();
        if (chosen == null) {
            return;
        }
        chosen.forEach((key, value) -> {
            GuidedSellingSelection selection = new GuidedSellingSelection();
            selection.setUuid(UUID.randomUUID().toString());
            selection.setSession(this);
            selection.setOptionKey(key);
            selection.setOptionValue(value);
            selection.setAdvisory(true);
            selection.setRationale(selectionRationale);
            selections.add(selection);
        });
    }

    public Map<String, String> selectionMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (GuidedSellingSelection selection : selections) {
            map.put(selection.getOptionKey(), selection.getOptionValue());
        }
        return map;
    }
}
