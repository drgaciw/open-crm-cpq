package com.opencrm.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.*;
import com.opencrm.document.domain.*;
import com.opencrm.document.dto.*;
import com.opencrm.document.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ClauseLibraryServiceTest {
    @Mock ClauseDefinitionRepository definitions;
    @Mock ClauseVersionRepository versions;
    @Mock ClauseTenant tenant;
    ClauseLibraryService service;
    @BeforeEach void setup() {
        service = new ClauseLibraryService(definitions, versions, new ClauseConditionEvaluator(), new ObjectMapper(), tenant);
        when(tenant.requiredTenant()).thenReturn("tenant-a");
    }
    ClauseVersion version(ClauseStatus status) {
        return ClauseVersion.builder().id(10L).tenantId("tenant-a").clauseId(1L).clauseKey("terms")
                .versionNumber(1).title("Terms").bodyContent("Original wording").category("legal").status(status).build();
    }
    @Test void addCreatesDraftAndRoundTripsStructuredCondition() {
        when(definitions.save(any())).thenAnswer(i -> { ClauseDefinition d = i.getArgument(0); d.setId(1L); return d; });
        when(versions.save(any())).thenAnswer(i -> { ClauseVersion v = i.getArgument(0); v.setId(10L); return v; });
        ClauseCondition c = new ClauseCondition(ClauseCondition.Operator.EQUALS, "region", "EU", null);
        ClauseDto result = service.addClause(new AddClause("terms", "Terms", "Original wording", "legal", c, 2));
        assertEquals(ClauseStatus.DRAFT, result.status());
        assertEquals(1, result.versionNumber());
        assertEquals(c, result.condition());
        assertNull(result.approvedBy());
        verify(definitions).existsByTenantIdAndClauseKey("tenant-a", "terms");
    }
    @Test void duplicateKeyConflicts() {
        when(definitions.existsByTenantIdAndClauseKey("tenant-a", "terms")).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.addClause(new AddClause("terms", "T", "body", "legal", null, 0)));
        verify(definitions, never()).save(any());
    }
    @Test void revisionsLockDefinitionAndNeverMutateApprovedVersion() {
        ClauseVersion old = version(ClauseStatus.APPROVED);
        when(definitions.lockByTenant(1L, "tenant-a")).thenReturn(Optional.of(ClauseDefinition.builder().id(1L).tenantId("tenant-a").clauseKey("terms").build()));
        when(versions.findFirstByClauseIdAndTenantIdOrderByVersionNumberDesc(1L, "tenant-a")).thenReturn(Optional.of(old));
        when(versions.save(any())).thenAnswer(i -> i.getArgument(0));
        ClauseDto result = service.revise(1L, new ClauseRevisionRequest("Terms 2", "New text", "legal", null, 0));
        assertEquals(2, result.versionNumber());
        assertEquals(ClauseStatus.DRAFT, result.status());
        assertEquals("Original wording", old.getBodyContent());
        assertEquals(ClauseStatus.APPROVED, old.getStatus());
    }
    @Test void approvalCapturesAuthenticatedActorAndTimestamp() {
        ClauseVersion v = version(ClauseStatus.DRAFT);
        when(versions.findByIdAndTenantIdAndDeletedFalse(10L, "tenant-a")).thenReturn(Optional.of(v));
        when(tenant.requiredActor()).thenReturn("verified-user");
        when(versions.save(v)).thenReturn(v);
        ClauseDto result = service.approve(10L);
        assertEquals(ClauseStatus.APPROVED, result.status());
        assertEquals("verified-user", result.approvedBy());
        assertNotNull(result.approvedAt());
    }
    @Test void approvedOrRetiredRevisionsCannotBeReapproved() {
        for (ClauseStatus status : List.of(ClauseStatus.APPROVED, ClauseStatus.RETIRED)) {
            when(versions.findByIdAndTenantIdAndDeletedFalse(10L, "tenant-a")).thenReturn(Optional.of(version(status)));
            assertThrows(IllegalStateException.class, () -> service.approve(10L));
        }
        verify(versions, never()).save(any());
    }
    @Test void retireRemovesEligibilityButPreservesText() {
        ClauseVersion v = version(ClauseStatus.APPROVED);
        when(versions.findByIdAndTenantIdAndDeletedFalse(10L, "tenant-a")).thenReturn(Optional.of(v));
        when(versions.save(v)).thenReturn(v);
        assertEquals(ClauseStatus.RETIRED, service.retire(10L).status());
        assertEquals("Original wording", v.getBodyContent());
    }
    @Test void crossTenantVersionAndDefinitionAppearMissing() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(10L));
        assertThrows(ResourceNotFoundException.class, () -> service.revise(1L, new ClauseRevisionRequest("T", "B", "legal", null, 0)));
        verify(versions).findByIdAndTenantIdAndDeletedFalse(10L, "tenant-a");
        verify(definitions).lockByTenant(1L, "tenant-a");
    }
    @Test void listIsTenantScoped() {
        when(versions.findByTenantIdAndDeletedFalseOrderByClauseKeyAscVersionNumberDesc("tenant-a")).thenReturn(List.of(version(ClauseStatus.DRAFT)));
        assertEquals(1, service.list().size());
    }
}
