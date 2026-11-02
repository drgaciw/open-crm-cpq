package com.opencrm.crm.playbook;

import java.util.Comparator;
import java.util.List;

/**
 * Maps playbook entities to read models. Definition version is exposed as {@code version}.
 * Steps are returned in pipeline order, then sort order. Deleted rows are omitted.
 */
public final class PlaybookMapper {

    private static final Comparator<PlaybookStep> STEP_ORDER = Comparator
            .comparingInt((PlaybookStep step) -> step.getStage() == null ? Integer.MAX_VALUE : step.getStage().ordinal())
            .thenComparingInt(PlaybookStep::getSortOrder)
            .thenComparing(PlaybookStep::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<PlaybookQuestion> QUESTION_ORDER = Comparator
            .comparingInt(PlaybookQuestion::getSortOrder)
            .thenComparing(PlaybookQuestion::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private PlaybookMapper() {
    }

    public static PlaybookDto toDto(Playbook playbook) {
        return PlaybookDto.builder()
                .id(playbook.getId())
                .uuid(playbook.getUuid())
                .name(playbook.getName())
                .version(playbook.getDefinitionVersion())
                .steps(stepsOf(playbook).stream()
                        .sorted(STEP_ORDER)
                        .map(PlaybookMapper::toStepDto)
                        .toList())
                .createdAt(playbook.getCreatedAt())
                .updatedAt(playbook.getUpdatedAt())
                .build();
    }

    public static PlaybookStepDto toStepDto(PlaybookStep step) {
        String stage = step.getStage() == null ? null : step.getStage().storedKey();
        return PlaybookStepDto.builder()
                .id(step.getId())
                .stage(stage)
                .sortOrder(step.getSortOrder())
                .questions(questionsOf(step).stream()
                        .sorted(QUESTION_ORDER)
                        .map(PlaybookMapper::toQuestionDto)
                        .toList())
                .build();
    }

    private static PlaybookQuestionDto toQuestionDto(PlaybookQuestion question) {
        return PlaybookQuestionDto.builder()
                .id(question.getId())
                .prompt(question.getPrompt())
                .sortOrder(question.getSortOrder())
                .build();
    }

    private static List<PlaybookStep> stepsOf(Playbook playbook) {
        if (playbook.getSteps() == null) {
            return List.of();
        }
        return playbook.getSteps().stream()
                .filter(step -> step != null && !step.isDeleted())
                .toList();
    }

    private static List<PlaybookQuestion> questionsOf(PlaybookStep step) {
        if (step.getQuestions() == null) {
            return List.of();
        }
        return step.getQuestions().stream()
                .filter(question -> question != null && !question.isDeleted())
                .toList();
    }
}
