package com.opencrm.document.service;

import com.opencrm.document.domain.*;
import com.opencrm.document.dto.*;
import com.opencrm.document.repository.ClauseVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ClauseSelectionService {
    private final ClauseVersionRepository versions;
    private final ClauseLibraryService library;
    private final ClauseConditionEvaluator evaluator;
    private final ClauseTenant tenant;

    /** Select newest approved revision first, then evaluate it. Never fall back to stale text on a mismatch. */
    @Transactional(readOnly = true)
    public List<SelectedClauseDto> select(ClauseSelectionContext context) {
        Map<Long, ClauseVersion> newest = new HashMap<>();
        for (ClauseVersion version : versions.findByTenantIdAndStatusAndDeletedFalseOrderBySortOrderAscClauseKeyAscVersionNumberDesc(
                tenant.requiredTenant(), ClauseStatus.APPROVED)) {
            newest.merge(version.getClauseId(), version, (a, b) -> a.getVersionNumber() > b.getVersionNumber() ? a : b);
        }
        return newest.values().stream()
                .filter(v -> evaluator.matches(library.decode(v.getConditionDefinition()), context))
                .sorted(Comparator.comparingInt(ClauseVersion::getSortOrder).thenComparing(ClauseVersion::getClauseKey))
                .map(v -> new SelectedClauseDto(v.getClauseId(), v.getId(), v.getClauseKey(), v.getVersionNumber(),
                        v.getTitle(), v.getBodyContent(), v.getSortOrder())).toList();
    }
}
