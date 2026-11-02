package com.opencrm.crm.playbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookNextStepSelectorTest {

    @Test
    @DisplayName("skips a completed step and returns the next incomplete step in that stage")
    void skipsACompletedStep() {
        PlaybookStep first = step(1L, PlaybookStage.PROSPECTING, 0);
        PlaybookStep second = step(2L, PlaybookStage.PROSPECTING, 1);

        Optional<PlaybookStep> next = PlaybookNextStepSelector.next(
                List.of(first, second), PlaybookStage.PROSPECTING, Set.of(1L));

        assertThat(next).contains(second);
    }

    @Test
    @DisplayName("does not leak a step from a different stage")
    void doesNotLeakAnotherStage() {
        PlaybookStep qualification = step(2L, PlaybookStage.QUALIFICATION, 0);
        PlaybookStep prospecting = step(1L, PlaybookStage.PROSPECTING, 5);

        Optional<PlaybookStep> next = PlaybookNextStepSelector.next(
                List.of(qualification, prospecting), PlaybookStage.PROSPECTING, Set.of());

        assertThat(next).contains(prospecting);
    }

    @Test
    @DisplayName("a completed id from another stage does not hide this stage")
    void completedIdFromAnotherStageDoesNotHideThisStage() {
        PlaybookStep prospecting = step(1L, PlaybookStage.PROSPECTING, 0);
        PlaybookStep qualification = step(9L, PlaybookStage.QUALIFICATION, 0);

        Optional<PlaybookStep> next = PlaybookNextStepSelector.next(
                List.of(qualification, prospecting), PlaybookStage.PROSPECTING, Set.of(9L));

        assertThat(next).contains(prospecting);
    }

    @Test
    void returnsTheEarliestIncompleteStepWhenALaterOneIsCompleted() {
        PlaybookStep first = step(1L, PlaybookStage.NEGOTIATION, 0);
        PlaybookStep second = step(2L, PlaybookStage.NEGOTIATION, 1);

        Optional<PlaybookStep> next = PlaybookNextStepSelector.next(
                List.of(second, first), PlaybookStage.NEGOTIATION, Set.of(2L));

        assertThat(next).contains(first);
    }

    @Test
    void returnsEmptyWhenEveryStepInTheStageIsComplete() {
        PlaybookStep first = step(1L, PlaybookStage.PROPOSAL, 0);
        PlaybookStep second = step(2L, PlaybookStage.PROPOSAL, 1);

        Optional<PlaybookStep> next = PlaybookNextStepSelector.next(
                List.of(first, second), PlaybookStage.PROPOSAL, Set.of(1L, 2L));

        assertThat(next).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheStageHasNoSteps() {
        PlaybookStep other = step(4L, PlaybookStage.WON, 0);

        Optional<PlaybookStep> next = PlaybookNextStepSelector.next(
                List.of(other), PlaybookStage.LOST, Set.of());

        assertThat(next).isEmpty();
    }

    @Test
    void skipsADeletedStep() {
        PlaybookStep deleted = step(1L, PlaybookStage.PROSPECTING, 0);
        deleted.setDeleted(true);
        PlaybookStep live = step(2L, PlaybookStage.PROSPECTING, 1);

        Optional<PlaybookStep> next = PlaybookNextStepSelector.next(
                List.of(deleted, live), PlaybookStage.PROSPECTING, Set.of());

        assertThat(next).contains(live);
    }

    @Test
    void ordersBySortOrderRatherThanListPosition() {
        PlaybookStep later = step(8L, PlaybookStage.QUALIFICATION, 3);
        PlaybookStep earlier = step(3L, PlaybookStage.QUALIFICATION, 0);

        Optional<PlaybookStep> next = PlaybookNextStepSelector.next(
                List.of(later, earlier), PlaybookStage.QUALIFICATION, null);

        assertThat(next).contains(earlier);
    }

    @Test
    void rejectsAMissingStage() {
        assertThatThrownBy(() -> PlaybookNextStepSelector.next(List.of(), null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PlaybookStep step(Long id, PlaybookStage stage, int sortOrder) {
        return PlaybookStep.builder()
                .id(id)
                .stage(stage)
                .sortOrder(sortOrder)
                .build();
    }
}
