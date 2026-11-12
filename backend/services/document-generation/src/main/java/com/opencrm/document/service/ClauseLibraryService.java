package com.opencrm.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.*;
import com.opencrm.document.domain.*;
import com.opencrm.document.dto.*;
import com.opencrm.document.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClauseLibraryService {
    private final ClauseDefinitionRepository definitions;
    private final ClauseVersionRepository versions;
    private final ClauseConditionEvaluator evaluator;
    private final ObjectMapper objectMapper;
    private final ClauseTenant tenant;

    @Transactional
    public ClauseDto addClause(AddClause request) {
        String tenantId = tenant.requiredTenant();
        evaluator.validate(request.condition());
        if (definitions.existsByTenantIdAndClauseKey(tenantId, request.clauseKey()))
            throw new BusinessException("Clause key already exists", HttpStatus.CONFLICT);
        ClauseDefinition definition = definitions.save(ClauseDefinition.builder()
                .tenantId(tenantId).clauseKey(request.clauseKey()).build());
        return createVersion(definition, 1, new ClauseRevisionRequest(request.title(), request.bodyContent(),
                request.category(), request.condition(), request.sortOrder()));
    }

    @Transactional
    public ClauseDto revise(Long clauseId, ClauseRevisionRequest request) {
        String tenantId = tenant.requiredTenant();
        evaluator.validate(request.condition());
        ClauseDefinition definition = definitions.lockByTenant(clauseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Clause", clauseId));
        int number = versions.findFirstByClauseIdAndTenantIdOrderByVersionNumberDesc(clauseId, tenantId)
                .map(v -> v.getVersionNumber() + 1).orElse(1);
        return createVersion(definition, number, request);
    }

    @Transactional
    public ClauseDto approve(Long versionId) {
        ClauseVersion version = findVersion(versionId);
        if (version.getStatus() != ClauseStatus.DRAFT) throw new IllegalStateException("Only DRAFT clauses can be approved");
        version.setStatus(ClauseStatus.APPROVED);
        version.setApprovedBy(tenant.requiredActor());
        version.setApprovedAt(Instant.now());
        return toDto(versions.save(version));
    }

    @Transactional
    public ClauseDto retire(Long versionId) {
        ClauseVersion version = findVersion(versionId);
        if (version.getStatus() == ClauseStatus.RETIRED) throw new IllegalStateException("Clause is already retired");
        version.setStatus(ClauseStatus.RETIRED);
        return toDto(versions.save(version));
    }

    public List<ClauseDto> list() {
        return versions.findByTenantIdAndDeletedFalseOrderByClauseKeyAscVersionNumberDesc(tenant.requiredTenant())
                .stream().map(this::toDto).toList();
    }
    public ClauseDto get(Long versionId) { return toDto(findVersion(versionId)); }

    private ClauseVersion findVersion(Long id) {
        return versions.findByIdAndTenantIdAndDeletedFalse(id, tenant.requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("ClauseVersion", id));
    }
    private ClauseDto createVersion(ClauseDefinition d, int number, ClauseRevisionRequest r) {
        return toDto(versions.save(ClauseVersion.builder().tenantId(d.getTenantId()).clauseId(d.getId())
                .clauseKey(d.getClauseKey()).versionNumber(number).title(r.title()).bodyContent(r.bodyContent())
                .category(r.category()).status(ClauseStatus.DRAFT).conditionDefinition(encode(r.condition()))
                .sortOrder(r.sortOrder()).build()));
    }
    public ClauseCondition decode(String value) {
        if (value == null) return null;
        try { return objectMapper.readValue(value, ClauseCondition.class); }
        catch (JsonProcessingException ex) { throw new BusinessException("Invalid stored clause condition", ex); }
    }
    private String encode(ClauseCondition c) {
        if (c == null) return null;
        try { return objectMapper.writeValueAsString(c); }
        catch (JsonProcessingException ex) { throw new BusinessException("Invalid clause condition", ex); }
    }
    private ClauseDto toDto(ClauseVersion v) {
        return new ClauseDto(v.getClauseId(), v.getId(), v.getClauseKey(), v.getVersionNumber(), v.getTitle(),
                v.getBodyContent(), v.getCategory(), v.getStatus(), v.getApprovedBy(), v.getApprovedAt(),
                decode(v.getConditionDefinition()), v.getSortOrder());
    }
}
