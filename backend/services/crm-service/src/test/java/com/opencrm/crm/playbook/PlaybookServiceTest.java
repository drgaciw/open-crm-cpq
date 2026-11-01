package com.opencrm.crm.playbook;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaybookServiceTest {

    @Mock
    private PlaybookRepository playbookRepository;
    @Mock
    private PlaybookStepRepository playbookStepRepository;

    private PlaybookService playbookService;

    @BeforeEach
    void setUp() {
        playbookService = new PlaybookService(playbookRepository, playbookStepRepository);
        lenient().when(playbookRepository.save(any(Playbook.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createWithStepsStaysAtDefinitionVersionOne() {
        PlaybookDto created = playbookService.create(PlaybookCreateRequest.builder()
                .name("  Discovery  ")
                .steps(List.of(
                        stepRequest("QUALIFICATION", null, "Budget confirmed?"),
                        stepRequest("PROSPECTING", null, "Who is the buyer?")))
                .build());

        assertThat(created.getName()).isEqualTo("Discovery");
        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getSteps()).extracting(PlaybookStepDto::getStage)
                .containsExactly("PROSPECTING", "QUALIFICATION");
        assertThat(created.getSteps().get(0).getQuestions()).extracting(PlaybookQuestionDto::getPrompt)
                .containsExactly("Who is the buyer?");
    }

    @Test
    void addStepIncrementsTheLocalVersion() {
        Playbook playbook = existingPlaybook();
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.of(playbook));

        PlaybookDto updated = playbookService.addStep(4L, stepRequest("PROSPECTING", null, "What is the timeline?"));

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(playbook.getDefinitionVersion()).isEqualTo(2);
        assertThat(updated.getSteps()).hasSize(2);
        assertThat(updated.getSteps().get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void changingAQuestionIncrementsTheVersion() {
        Playbook playbook = existingPlaybook();
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.of(playbook));

        PlaybookDto updated = playbookService.updateStep(4L, 1L, stepRequest("PROSPECTING", 0, "Who signs?"));

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getSteps().get(0).getQuestions()).extracting(PlaybookQuestionDto::getPrompt)
                .containsExactly("Who signs?");
    }

    @Test
    void renamingDoesNotIncrementTheVersion() {
        Playbook playbook = existingPlaybook();
        playbook.setDefinitionVersion(3);
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.of(playbook));

        PlaybookDto updated = playbookService.update(4L, PlaybookUpdateRequest.builder().name("Renamed").build());

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getVersion()).isEqualTo(3);
        assertThat(playbook.getDefinitionVersion()).isEqualTo(3);
    }

    @Test
    void identicalStepWriteDoesNotSaveOrBump() {
        Playbook playbook = existingPlaybook();
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.of(playbook));

        PlaybookDto updated = playbookService.updateStep(4L, 1L, stepRequest("PROSPECTING", 0, " Who is the buyer? "));

        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(playbook.getSteps().get(0).getQuestions().get(0).getId()).isEqualTo(10L);
        verify(playbookRepository, never()).save(any());
    }

    @Test
    void duplicateSortOrderIsRejected() {
        Playbook playbook = existingPlaybook();
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.of(playbook));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class,
                () -> playbookService.addStep(4L, stepRequest("PROSPECTING", 0, "Another opening question")));

        assertThat(ex.getErrorCode()).isEqualTo("PLAYBOOK_STEP_ORDER");
        assertThat(playbook.getDefinitionVersion()).isEqualTo(1);
        verify(playbookRepository, never()).save(any());
    }

    @Test
    @DisplayName("skips a completed step and does not leak a different stage")
    void nextStepSkipsCompletedStepsAndDropsOtherStages() {
        Playbook playbook = existingPlaybook();
        playbook.setDefinitionVersion(3);
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.of(playbook));
        PlaybookStep later = step(2L, PlaybookStage.PROSPECTING, 1, "What is the timeline?");
        PlaybookStep otherStage = step(9L, PlaybookStage.QUALIFICATION, 0, "Budget confirmed?");
        when(playbookStepRepository.findActiveForStage(4L, PlaybookStage.PROSPECTING))
                .thenReturn(List.of(playbook.getSteps().get(0), later, otherStage));

        PlaybookNextStepResponse response = playbookService.nextStep(4L, "PROSPECTING", "1,9");

        assertThat(response.getPlaybookId()).isEqualTo(4L);
        assertThat(response.getVersion()).isEqualTo(3);
        assertThat(response.getStage()).isEqualTo("PROSPECTING");
        assertThat(response.getStep().getId()).isEqualTo(2L);
        assertThat(response.getStep().getStage()).isEqualTo("PROSPECTING");
        assertThat(response.getStep().getQuestions()).extracting(PlaybookQuestionDto::getPrompt)
                .containsExactly("What is the timeline?");
        verify(playbookStepRepository).findActiveForStage(4L, PlaybookStage.PROSPECTING);
    }

    @Test
    @DisplayName("an exhausted stage is a null step, not a not-found error")
    void exhaustedStageReturnsANullStep() {
        Playbook playbook = existingPlaybook();
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.of(playbook));
        when(playbookStepRepository.findActiveForStage(4L, PlaybookStage.PROSPECTING))
                .thenReturn(List.of(playbook.getSteps().get(0)));

        PlaybookNextStepResponse response = playbookService.nextStep(4L, "PROSPECTING", "1");

        assertThat(response.getStep()).isNull();
        assertThat(response.getStage()).isEqualTo("PROSPECTING");
        assertThat(response.getVersion()).isEqualTo(1);
    }

    @Test
    void missingPlaybookIsNotFoundBeforeStageLookup() {
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = catchThrowableOfType(
                ResourceNotFoundException.class,
                () -> playbookService.nextStep(4L, "NOT_A_STAGE", "1,2"));

        assertThat(ex.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        verifyNoInteractions(playbookStepRepository);
    }

    @Test
    void unknownStageDoesNotQuerySteps() {
        when(playbookRepository.findActiveById(4L)).thenReturn(Optional.of(existingPlaybook()));

        BusinessException ex = catchThrowableOfType(
                BusinessException.class, () -> playbookService.nextStep(4L, "CLOSED", null));

        assertThat(ex.getErrorCode()).isEqualTo("PLAYBOOK_STAGE_UNKNOWN");
        verifyNoInteractions(playbookStepRepository);
    }

    private static Playbook existingPlaybook() {
        Playbook playbook = Playbook.builder()
                .id(4L)
                .name("Discovery")
                .definitionVersion(1)
                .build();
        playbook.addStep(step(1L, PlaybookStage.PROSPECTING, 0, "Who is the buyer?"));
        return playbook;
    }

    private static PlaybookStep step(Long id, PlaybookStage stage, int sortOrder, String prompt) {
        PlaybookStep step = PlaybookStep.builder()
                .id(id)
                .stage(stage)
                .sortOrder(sortOrder)
                .build();
        step.addQuestion(PlaybookQuestion.builder().id(id * 10).prompt(prompt).sortOrder(0).build());
        return step;
    }

    private static PlaybookStepWriteRequest stepRequest(String stage, Integer sortOrder, String prompt) {
        return PlaybookStepWriteRequest.builder()
                .stage(stage)
                .sortOrder(sortOrder)
                .questions(List.of(PlaybookQuestionWriteRequest.builder().prompt(prompt).build()))
                .build();
    }
}
