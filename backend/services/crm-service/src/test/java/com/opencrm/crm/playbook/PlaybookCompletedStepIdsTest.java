package com.opencrm.crm.playbook;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PlaybookCompletedStepIdsTest {

    @Test
    void blankInputMeansNothingIsCompleted() {
        assertThat(PlaybookCompletedStepIds.parse(null)).isEmpty();
        assertThat(PlaybookCompletedStepIds.parse("")).isEmpty();
        assertThat(PlaybookCompletedStepIds.parse("  ")).isEmpty();
        assertThat(PlaybookCompletedStepIds.parse(",")).isEmpty();
    }

    @Test
    void parsesACommaSeparatedList() {
        assertThat(PlaybookCompletedStepIds.parse("1,2")).containsExactlyInAnyOrder(1L, 2L);
        assertThat(PlaybookCompletedStepIds.parse(" 1, 2 ,")).containsExactlyInAnyOrder(1L, 2L);
        assertThat(PlaybookCompletedStepIds.parse("4,4")).containsExactly(4L);
    }

    @Test
    void rejectsNonIntegersAndNonPositiveIds() {
        BusinessException text = catchThrowableOfType(
                BusinessException.class, () -> PlaybookCompletedStepIds.parse("1,abc"));
        assertThat(text.getErrorCode()).isEqualTo("PLAYBOOK_COMPLETED_STEP_ID");

        BusinessException zero = catchThrowableOfType(
                BusinessException.class, () -> PlaybookCompletedStepIds.parse("0"));
        assertThat(zero.getErrorCode()).isEqualTo("PLAYBOOK_COMPLETED_STEP_ID");

        BusinessException negative = catchThrowableOfType(
                BusinessException.class, () -> PlaybookCompletedStepIds.parse("-3"));
        assertThat(negative.getErrorCode()).isEqualTo("PLAYBOOK_COMPLETED_STEP_ID");
    }
}
