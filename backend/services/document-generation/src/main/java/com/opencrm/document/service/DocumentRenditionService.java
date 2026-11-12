package com.opencrm.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.*;
import com.opencrm.document.domain.*;
import com.opencrm.document.dto.*;
import com.opencrm.document.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class DocumentRenditionService {
    private final DocumentRenditionRepository renditions;
    private final RenditionSelectedClauseRepository selectedClauses;
    private final DocumentGenerationJobRepository jobs;
    private final ClauseSelectionService selection;
    private final DocumentGenerationService generation;
    private final ClauseTenant tenant;
    private final ObjectMapper objectMapper;

    @Transactional
    public DocumentRenditionDto generate(GenerateRenditionRequest request) {
        String tenantId = tenant.requiredTenant();
        List<SelectedClauseDto> selected = selection.select(request.context());
        String context;
        try { context = objectMapper.writeValueAsString(request.context()); }
        catch (JsonProcessingException ex) { throw new BusinessException("Invalid selection context", ex); }
        DocumentGenerationJob job = jobs.save(DocumentGenerationJob.builder().quoteId(request.quoteId())
                .customerId(request.customerId()).documentType(request.documentType()).status(JobStatus.PENDING)
                .idempotencyKey("rendition-" + UUID.randomUUID()).build());
        DocumentRendition rendition = renditions.save(DocumentRendition.builder().tenantId(tenantId)
                .jobId(job.getId()).selectionContext(context).build());
        selectedClauses.saveAll(selected.stream().map(s -> RenditionSelectedClause.builder()
                .tenantId(tenantId).renditionId(rendition.getId()).clauseId(s.clauseId()).clauseVersionId(s.versionId())
                .clauseKey(s.clauseKey()).versionNumber(s.versionNumber()).title(s.title()).bodyContent(s.bodyContent())
                .sortOrder(s.sortOrder()).build()).toList());
        Long jobId = job.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { generation.generateDocumentAsync(jobId); }
            });
        } else { generation.generateDocumentAsync(jobId); }
        return new DocumentRenditionDto(rendition.getId(), jobId, job.getQuoteId(), JobStatus.PENDING, null, selected);
    }

    @Transactional(readOnly = true)
    public DocumentRenditionDto get(Long id) {
        String tenantId = tenant.requiredTenant();
        DocumentRendition rendition = renditions.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentRendition", id));
        DocumentGenerationJob job = jobs.findById(rendition.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("DocumentGenerationJob", rendition.getJobId()));
        List<SelectedClauseDto> selected = selectedClauses.findByRenditionIdAndTenantIdOrderBySortOrderAscClauseKeyAsc(id, tenantId)
                .stream().map(s -> new SelectedClauseDto(s.getClauseId(), s.getClauseVersionId(), s.getClauseKey(),
                        s.getVersionNumber(), s.getTitle(), s.getBodyContent(), s.getSortOrder())).toList();
        return new DocumentRenditionDto(id, job.getId(), job.getQuoteId(), job.getStatus(), job.getDocumentUrl(), selected);
    }
}
