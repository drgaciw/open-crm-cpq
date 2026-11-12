package com.opencrm.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.document.domain.*;
import com.opencrm.document.dto.*;
import com.opencrm.document.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentRenditionServiceTest {
    @Mock DocumentRenditionRepository renditions;
    @Mock RenditionSelectedClauseRepository snapshots;
    @Mock DocumentGenerationJobRepository jobs;
    @Mock ClauseSelectionService selection;
    @Mock DocumentGenerationService generation;
    @Mock ClauseTenant tenant;
    DocumentRenditionService service;
    ClauseSelectionContext context = new ClauseSelectionContext("EU", "Enterprise", Map.of("total", 100));
    SelectedClauseDto selected = new SelectedClauseDto(1L, 12L, "terms", 2, "Terms", "Approved original text", 4);
    @BeforeEach void setup() {
        service = new DocumentRenditionService(renditions, snapshots, jobs, selection, generation, tenant, new ObjectMapper());
        when(tenant.requiredTenant()).thenReturn("tenant-a");
    }
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    void setupGenerate() {
        when(selection.select(context)).thenReturn(List.of(selected));
        when(jobs.save(any())).thenAnswer(i -> { DocumentGenerationJob j = i.getArgument(0); j.setId(10L); return j; });
        when(renditions.save(any())).thenAnswer(i -> { DocumentRendition r = i.getArgument(0); r.setId(20L); return r; });
    }
    GenerateRenditionRequest request() { return new GenerateRenditionRequest("Q-1", "C-1", DocumentType.QUOTE_PDF, context); }
    @Test void generationSnapshotsExactTextAndVersionBeforeAsyncDispatch() {
        setupGenerate();
        TransactionSynchronizationManager.initSynchronization();
        DocumentRenditionDto result = service.generate(request());
        assertEquals(List.of(selected), result.selectedClauses());
        assertEquals(JobStatus.PENDING, result.status());
        verifyNoInteractions(generation);
        ArgumentCaptor<Iterable<RenditionSelectedClause>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(snapshots).saveAll(captor.capture());
        RenditionSelectedClause snapshot = captor.getValue().iterator().next();
        assertEquals("tenant-a", snapshot.getTenantId());
        assertEquals(20L, snapshot.getRenditionId());
        assertEquals(12L, snapshot.getClauseVersionId());
        assertEquals(2, snapshot.getVersionNumber());
        assertEquals("Approved original text", snapshot.getBodyContent());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        verify(generation).generateDocumentAsync(10L);
    }
    @Test void rollbackDoesNotDispatchGeneration() {
        setupGenerate();
        TransactionSynchronizationManager.initSynchronization();
        service.generate(request());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
        verifyNoInteractions(generation);
    }
    @Test void retrievalReadsSnapshotNotCurrentLibrary() {
        when(renditions.findByIdAndTenantIdAndDeletedFalse(20L, "tenant-a")).thenReturn(Optional.of(
                DocumentRendition.builder().id(20L).jobId(10L).tenantId("tenant-a").build()));
        when(jobs.findById(10L)).thenReturn(Optional.of(DocumentGenerationJob.builder().id(10L).quoteId("Q-1")
                .status(JobStatus.COMPLETED).documentUrl("url").build()));
        when(snapshots.findByRenditionIdAndTenantIdOrderBySortOrderAscClauseKeyAsc(20L, "tenant-a"))
                .thenReturn(List.of(RenditionSelectedClause.builder().clauseId(1L).clauseVersionId(12L).clauseKey("terms")
                        .versionNumber(2).title("Terms").bodyContent("Approved original text").sortOrder(4).build()));
        DocumentRenditionDto result = service.get(20L);
        assertEquals(List.of(selected), result.selectedClauses());
        assertEquals(JobStatus.COMPLETED, result.status());
        verifyNoInteractions(selection);
    }
    @Test void otherTenantRenditionIsNotFoundBeforeJobOrSnapshotLookup() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(20L));
        verifyNoInteractions(jobs, snapshots, selection, generation);
    }
    @Test void emptySelectionStillCreatesRendition() {
        setupGenerate();
        when(selection.select(context)).thenReturn(List.of());
        assertTrue(service.generate(request()).selectedClauses().isEmpty());
        verify(generation).generateDocumentAsync(10L);
    }
}
