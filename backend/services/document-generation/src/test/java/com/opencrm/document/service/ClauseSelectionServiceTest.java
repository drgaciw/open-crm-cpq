package com.opencrm.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.document.domain.*;
import com.opencrm.document.dto.*;
import com.opencrm.document.repository.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClauseSelectionServiceTest {
    ClauseVersionRepository versions = mock(ClauseVersionRepository.class);
    ClauseTenant tenant = mock(ClauseTenant.class);
    ClauseConditionEvaluator evaluator = new ClauseConditionEvaluator();
    ClauseLibraryService library = new ClauseLibraryService(mock(ClauseDefinitionRepository.class), versions, evaluator, new ObjectMapper(), tenant);
    ClauseSelectionService service = new ClauseSelectionService(versions, library, evaluator, tenant);
    ClauseSelectionContext context = new ClauseSelectionContext("EU", "Enterprise", Map.of());
    ClauseVersion v(long clauseId, int revision, int order, String key, String condition) {
        return ClauseVersion.builder().id(clauseId * 10 + revision).clauseId(clauseId).clauseKey(key).versionNumber(revision)
                .title(key).bodyContent("v" + revision).sortOrder(order).status(ClauseStatus.APPROVED).conditionDefinition(condition).build();
    }
    @Test void newestApprovedVersionOnlyAndStableOrder() {
        when(tenant.requiredTenant()).thenReturn("tenant-a");
        when(versions.findByTenantIdAndStatusAndDeletedFalseOrderBySortOrderAscClauseKeyAscVersionNumberDesc("tenant-a", ClauseStatus.APPROVED))
                .thenReturn(List.of(v(1, 1, 0, "z", null), v(1, 2, 4, "z", null), v(2, 1, 4, "a", null)));
        List<SelectedClauseDto> selected = service.select(context);
        assertEquals(List.of("a", "z"), selected.stream().map(SelectedClauseDto::clauseKey).toList());
        assertEquals(2, selected.getLast().versionNumber());
    }
    @Test void newestConditionMismatchDoesNotFallBackToOldRevision() {
        when(tenant.requiredTenant()).thenReturn("tenant-a");
        when(versions.findByTenantIdAndStatusAndDeletedFalseOrderBySortOrderAscClauseKeyAscVersionNumberDesc("tenant-a", ClauseStatus.APPROVED))
                .thenReturn(List.of(v(1, 1, 0, "terms", null), v(1, 2, 0, "terms", "{\"operator\":\"EQUALS\",\"field\":\"region\",\"value\":\"US\"}")));
        assertTrue(service.select(context).isEmpty());
    }
    @Test void noApprovedClausesMeansEmptySelection() {
        when(tenant.requiredTenant()).thenReturn("tenant-a");
        assertTrue(service.select(context).isEmpty());
        verify(versions).findByTenantIdAndStatusAndDeletedFalseOrderBySortOrderAscClauseKeyAscVersionNumberDesc("tenant-a", ClauseStatus.APPROVED);
    }
}
