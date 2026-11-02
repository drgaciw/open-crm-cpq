package com.opencrm.crm.playbook;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Playbook definitions and next-step lookup (DRG-486).
 * <p>
 * The definition version is a local integer because BC-18 is not landed.
 * It starts at 1, including when the playbook is created with steps. It
 * increments when a later add or update changes steps or questions. A name
 * change does not increment it. There is no promotion workflow and no
 * buying-role coupling.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PlaybookService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_PROMPT_LENGTH = 4000;

    private final PlaybookRepository playbookRepository;
    private final PlaybookStepRepository playbookStepRepository;

    public PlaybookDto create(PlaybookCreateRequest request) {
        Playbook playbook = Playbook.builder()
                .name(requireName(request.getName()))
                .definitionVersion(1)
                .build();
        if (request.getSteps() != null) {
            for (PlaybookStepWriteRequest stepRequest : request.getSteps()) {
                playbook.addStep(newStep(playbook, stepRequest));
            }
        }
        playbook.setDefinitionVersion(1);
        Playbook saved = playbookRepository.save(playbook);
        log.info("Created playbook {} at definition version {}", saved.getId(), saved.getDefinitionVersion());
        return PlaybookMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public PlaybookDto getById(Long id) {
        return PlaybookMapper.toDto(requireActive(id));
    }

    public PlaybookDto update(Long id, PlaybookUpdateRequest request) {
        Playbook playbook = requireActive(id);
        String name = requireName(request.getName());
        if (name.equals(playbook.getName())) {
            return PlaybookMapper.toDto(playbook);
        }
        playbook.setName(name);
        Playbook saved = playbookRepository.save(playbook);
        log.info("Renamed playbook {} without changing definition version {}", saved.getId(), saved.getDefinitionVersion());
        return PlaybookMapper.toDto(saved);
    }

    public PlaybookDto addStep(Long id, PlaybookStepWriteRequest request) {
        Playbook playbook = requireActive(id);
        playbook.addStep(newStep(playbook, request));
        playbook.bumpDefinitionVersion();
        Playbook saved = playbookRepository.save(playbook);
        log.info("Added a step to playbook {} at definition version {}", saved.getId(), saved.getDefinitionVersion());
        return PlaybookMapper.toDto(saved);
    }

    public PlaybookDto updateStep(Long id, Long stepId, PlaybookStepWriteRequest request) {
        Playbook playbook = requireActive(id);
        PlaybookStep step = requireStep(playbook, stepId);
        PlaybookStage stage = PlaybookStage.fromKey(request.getStage());
        int sortOrder = resolveSortOrder(
                playbook,
                stage,
                request.getSortOrder() == null ? step.getSortOrder() : request.getSortOrder(),
                step.getId());
        List<QuestionSnapshot> questions = normalizeQuestions(request.getQuestions());
        boolean questionsChanged = !sameQuestions(step, questions);
        boolean changed = step.getStage() != stage || step.getSortOrder() != sortOrder || questionsChanged;
        if (!changed) {
            return PlaybookMapper.toDto(playbook);
        }
        step.setStage(stage);
        step.setSortOrder(sortOrder);
        if (questionsChanged) {
            step.replaceQuestions(toQuestionEntities(questions));
        }
        playbook.bumpDefinitionVersion();
        Playbook saved = playbookRepository.save(playbook);
        log.info("Updated step {} on playbook {} at definition version {}", stepId, saved.getId(), saved.getDefinitionVersion());
        return PlaybookMapper.toDto(saved);
    }

    /**
     * Next incomplete step for {@code stage}. A missing playbook is a 404.
     * When the playbook exists and the stage is exhausted, the result's step is null
     * and the caller responds with HTTP 200.
     */
    @Transactional(readOnly = true)
    public PlaybookNextStepResponse nextStep(Long id, String stageKey, String completedStepIds) {
        Playbook playbook = requireActive(id);
        PlaybookStage stage = PlaybookStage.fromKey(stageKey);
        Set<Long> completed = PlaybookCompletedStepIds.parse(completedStepIds);
        List<PlaybookStep> steps = playbookStepRepository.findActiveForStage(id, stage);
        PlaybookStep next = PlaybookNextStepSelector.next(steps, stage, completed).orElse(null);
        return PlaybookNextStepResponse.builder()
                .playbookId(playbook.getId())
                .version(playbook.getDefinitionVersion())
                .stage(stage.storedKey())
                .step(next == null ? null : PlaybookMapper.toStepDto(next))
                .build();
    }

    private Playbook requireActive(Long id) {
        return playbookRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Playbook", id));
    }

    private static PlaybookStep requireStep(Playbook playbook, Long stepId) {
        return activeSteps(playbook).stream()
                .filter(step -> stepId.equals(step.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Playbook step", stepId));
    }

    private PlaybookStep newStep(Playbook playbook, PlaybookStepWriteRequest request) {
        PlaybookStage stage = PlaybookStage.fromKey(request.getStage());
        int sortOrder = resolveSortOrder(playbook, stage, request.getSortOrder(), null);
        PlaybookStep step = PlaybookStep.builder()
                .stage(stage)
                .sortOrder(sortOrder)
                .build();
        for (PlaybookQuestion question : toQuestionEntities(normalizeQuestions(request.getQuestions()))) {
            step.addQuestion(question);
        }
        return step;
    }

    private static int resolveSortOrder(Playbook playbook, PlaybookStage stage, Integer requested, Long ignoreStepId) {
        if (requested == null) {
            return nextSortOrder(playbook, stage, ignoreStepId);
        }
        if (requested < 0) {
            throw new BusinessException("Step sort order must be zero or greater", "PLAYBOOK_STEP_ORDER");
        }
        boolean taken = activeSteps(playbook).stream()
                .filter(step -> step.getStage() == stage)
                .filter(step -> ignoreStepId == null || !ignoreStepId.equals(step.getId()))
                .anyMatch(step -> step.getSortOrder() == requested);
        if (taken) {
            throw new BusinessException(
                    "Step sort order " + requested + " is already used for " + stage.storedKey(),
                    "PLAYBOOK_STEP_ORDER");
        }
        return requested;
    }

    private static int nextSortOrder(Playbook playbook, PlaybookStage stage, Long ignoreStepId) {
        return activeSteps(playbook).stream()
                .filter(step -> step.getStage() == stage)
                .filter(step -> ignoreStepId == null || !ignoreStepId.equals(step.getId()))
                .map(PlaybookStep::getSortOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private static List<PlaybookStep> activeSteps(Playbook playbook) {
        if (playbook.getSteps() == null) {
            return List.of();
        }
        return playbook.getSteps().stream()
                .filter(step -> step != null && !step.isDeleted())
                .toList();
    }

    private static List<QuestionSnapshot> normalizeQuestions(List<PlaybookQuestionWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("A playbook step needs at least one question", "PLAYBOOK_QUESTIONS_REQUIRED");
        }
        List<QuestionSnapshot> snapshots = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < requests.size(); index++) {
            PlaybookQuestionWriteRequest request = requests.get(index);
            if (request == null || request.getPrompt() == null || request.getPrompt().isBlank()) {
                throw new BusinessException("Question prompt is required", "PLAYBOOK_QUESTION_PROMPT");
            }
            String prompt = request.getPrompt().trim();
            if (prompt.length() > MAX_PROMPT_LENGTH) {
                throw new BusinessException(
                        "Question prompt must not exceed 4000 characters",
                        "PLAYBOOK_QUESTION_PROMPT");
            }
            int sortOrder = request.getSortOrder() == null ? index : request.getSortOrder();
            if (sortOrder < 0) {
                throw new BusinessException("Question sort order must be zero or greater", "PLAYBOOK_QUESTION_ORDER");
            }
            if (!seen.add(sortOrder)) {
                throw new BusinessException(
                        "Question sort order " + sortOrder + " is duplicated",
                        "PLAYBOOK_QUESTION_ORDER");
            }
            snapshots.add(new QuestionSnapshot(sortOrder, prompt));
        }
        snapshots.sort(Comparator.comparingInt(QuestionSnapshot::sortOrder));
        return List.copyOf(snapshots);
    }

    private static boolean sameQuestions(PlaybookStep step, List<QuestionSnapshot> incoming) {
        List<PlaybookQuestion> current = step.getQuestions() == null
                ? List.of()
                : step.getQuestions().stream()
                .filter(question -> question != null && !question.isDeleted())
                .sorted(Comparator.comparingInt(PlaybookQuestion::getSortOrder)
                        .thenComparing(PlaybookQuestion::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (current.size() != incoming.size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index++) {
            PlaybookQuestion question = current.get(index);
            QuestionSnapshot snapshot = incoming.get(index);
            if (question.getSortOrder() != snapshot.sortOrder() || !question.getPrompt().equals(snapshot.prompt())) {
                return false;
            }
        }
        return true;
    }

    private static List<PlaybookQuestion> toQuestionEntities(List<QuestionSnapshot> snapshots) {
        List<PlaybookQuestion> questions = new ArrayList<>();
        for (QuestionSnapshot snapshot : snapshots) {
            questions.add(PlaybookQuestion.builder()
                    .prompt(snapshot.prompt())
                    .sortOrder(snapshot.sortOrder())
                    .build());
        }
        return questions;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Playbook name is required", "PLAYBOOK_NAME_REQUIRED");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("Playbook name must not exceed 255 characters", "PLAYBOOK_NAME_LENGTH");
        }
        return trimmed;
    }

    private record QuestionSnapshot(int sortOrder, String prompt) {
    }
}
