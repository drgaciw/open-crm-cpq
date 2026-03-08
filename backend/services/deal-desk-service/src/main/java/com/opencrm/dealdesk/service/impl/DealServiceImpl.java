package com.opencrm.dealdesk.service.impl;

import com.opencrm.dealdesk.dto.CpqSyncResponseDto;
import com.opencrm.dealdesk.dto.DealRequestDto;
import com.opencrm.dealdesk.dto.DealResponseDto;
import com.opencrm.dealdesk.exception.DealNotFoundException;
import com.opencrm.dealdesk.exception.InvalidDealStateException;
import com.opencrm.dealdesk.exception.SalesforceIntegrationException;
import com.opencrm.dealdesk.exception.SalesforceUpdateException;
import com.opencrm.dealdesk.mapper.DealMapper;
import com.opencrm.dealdesk.model.Deal;
import com.opencrm.dealdesk.model.DealStatus;
import com.opencrm.dealdesk.repository.DealRepository;
import com.opencrm.dealdesk.rules.service.TCVRuleExecutorService;
import com.opencrm.dealdesk.service.DealService;
import com.opencrm.dealdesk.service.SalesforceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DealServiceImpl implements DealService {

    private final DealRepository dealRepository;
    private final DealMapper dealMapper;
    private final SalesforceService salesforceService;
    private final TCVRuleExecutorService tcvRuleExecutorService;

    public DealServiceImpl(
            DealRepository dealRepository,
            DealMapper dealMapper,
            SalesforceService salesforceService,
            TCVRuleExecutorService tcvRuleExecutorService
    ) {
        this.dealRepository = dealRepository;
        this.dealMapper = dealMapper;
        this.salesforceService = salesforceService;
        this.tcvRuleExecutorService = tcvRuleExecutorService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DealResponseDto> getAllDeals() {
        return getDeals(null, null, null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DealResponseDto> getDeals(
            DealStatus status,
            String accountId,
            BigDecimal minValue,
            String salesforceOpportunityId,
            Boolean synced,
            Instant createdAfter
    ) {
        return dealMapper.toDtoList(dealRepository.findAllByFilters(
                status,
                normalizeFilterValue(accountId),
                minValue,
                normalizeFilterValue(salesforceOpportunityId),
                synced,
                createdAfter
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public DealResponseDto getDealById(Long id) {
        Deal deal = dealRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new DealNotFoundException(id));
        return dealMapper.toDto(deal);
    }

    @Override
    @Transactional
    public DealResponseDto createDeal(DealRequestDto request) {
        Deal deal = dealMapper.toEntity(request);
        recalculatePricingInMemory(deal);
        Deal saved = dealRepository.save(deal);
        return dealMapper.toDto(saved);
    }

    @Override
    @Transactional
    public DealResponseDto updateDeal(Long id, DealRequestDto request) {
        Deal deal = getActiveDeal(id);
        dealMapper.updateEntity(request, deal);
        recalculatePricingInMemory(deal);
        Deal saved = dealRepository.save(deal);
        return dealMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteDeal(Long id) {
        Deal deal = getActiveDeal(id);
        deal.softDelete();
        dealRepository.save(deal);
    }

    @Override
    @Transactional
    public DealResponseDto submitDeal(Long id) {
        return transitionDeal(
                id,
                DealStatus.SUBMITTED,
                EnumSet.of(DealStatus.DRAFT, DealStatus.REJECTED),
                null,
                null
        );
    }

    @Override
    @Transactional
    public DealResponseDto approveDeal(Long id, String notes) {
        return transitionDeal(
                id,
                DealStatus.APPROVED,
                EnumSet.of(DealStatus.SUBMITTED, DealStatus.IN_REVIEW),
                "Approved",
                notes
        );
    }

    @Override
    @Transactional
    public DealResponseDto rejectDeal(Long id, String notes) {
        return transitionDeal(
                id,
                DealStatus.REJECTED,
                EnumSet.of(DealStatus.SUBMITTED, DealStatus.IN_REVIEW),
                "Rejected",
                notes
        );
    }

    @Override
    @Transactional
    public DealResponseDto recalculatePricing(Long id) {
        Deal deal = getActiveDeal(id);
        recalculatePricingInMemory(deal);
        Deal saved = dealRepository.save(deal);
        return dealMapper.toDto(saved);
    }

    @Override
    @Transactional(noRollbackFor = SalesforceIntegrationException.class)
    public DealResponseDto syncPricing(Long id) {
        return syncPricingInternal(id);
    }

    @Override
    @Transactional(noRollbackFor = SalesforceIntegrationException.class)
    public CpqSyncResponseDto syncCpqQuote(Long id, String quoteId) {
        return syncCpqQuoteInternal(id, quoteId);
    }

    @Override
    @Transactional(noRollbackFor = SalesforceIntegrationException.class)
    public CpqSyncResponseDto syncCpqPriceRules(Long id, String quoteId, String priceRuleId) {
        return syncCpqPriceRulesInternal(id, quoteId, priceRuleId);
    }

    @Override
    @Transactional(noRollbackFor = SalesforceIntegrationException.class)
    public DealResponseDto syncDeal(Long id) {
        return syncDealInternal(id);
    }

    @Override
    public List<DealResponseDto> batchSyncDeals(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw new IllegalArgumentException("Deal batch sync ids must not contain null values");
            }
            uniqueIds.add(id);
        }

        List<DealResponseDto> results = new ArrayList<>(uniqueIds.size());
        for (Long id : uniqueIds) {
            results.add(syncDealInternal(id));
        }

        return results;
    }

    private DealResponseDto syncDealInternal(Long id) {
        Deal deal = getActiveDeal(id);
        recalculatePricingInMemory(deal);

        try {
            if (salesforceService.isEnabled() && StringUtils.hasText(deal.getSalesforceOpportunityId())) {
                salesforceService.updateOpportunity(deal.getSalesforceOpportunityId(), buildSalesforceSyncFields(deal));
            }

            deal.setSynced(true);
            Deal saved = dealRepository.save(deal);
            return dealMapper.toDto(saved);
        } catch (SalesforceIntegrationException ex) {
            deal.setSynced(false);
            dealRepository.save(deal);
            throw ex;
        } catch (Exception ex) {
            deal.setSynced(false);
            dealRepository.save(deal);
            throw new SalesforceUpdateException("Failed to sync deal " + id + " to Salesforce", ex);
        }
    }

    private DealResponseDto syncPricingInternal(Long id) {
        Deal deal = getActiveDeal(id);
        recalculatePricingInMemory(deal);

        try {
            if (salesforceService.isEnabled() && StringUtils.hasText(deal.getSalesforceOpportunityId())) {
                salesforceService.updateOpportunity(deal.getSalesforceOpportunityId(), buildSalesforcePricingSyncFields(deal));
                deal.setSynced(true);
            }

            Deal saved = dealRepository.save(deal);
            return dealMapper.toDto(saved);
        } catch (SalesforceIntegrationException ex) {
            deal.setSynced(false);
            dealRepository.save(deal);
            throw ex;
        } catch (Exception ex) {
            deal.setSynced(false);
            dealRepository.save(deal);
            throw new SalesforceUpdateException("Failed to sync pricing for deal " + id + " to Salesforce", ex);
        }
    }

    private CpqSyncResponseDto syncCpqQuoteInternal(Long id, String quoteId) {
        Deal deal = getActiveDeal(id);
        recalculatePricingInMemory(deal);

        boolean salesforceEnabled = salesforceService.isEnabled();
        String resolvedQuoteId = normalizeFilterValue(quoteId);

        if (!salesforceEnabled) {
            dealRepository.save(deal);
            return buildCpqSyncResponse(
                    "quote-sync",
                    resolvedQuoteId,
                    "SKIPPED_SALESFORCE_DISABLED",
                    false,
                    List.of()
            );
        }

        if (!StringUtils.hasText(resolvedQuoteId) && !StringUtils.hasText(deal.getSalesforceOpportunityId())) {
            dealRepository.save(deal);
            return buildCpqSyncResponse(
                    "quote-sync",
                    null,
                    "SKIPPED_MISSING_OPPORTUNITY_ID",
                    true,
                    List.of()
            );
        }

        try {
            resolvedQuoteId = ensureQuoteId(deal, resolvedQuoteId);
            if (!StringUtils.hasText(resolvedQuoteId)) {
                throw new SalesforceUpdateException("Failed to resolve Salesforce CPQ quote id for deal " + id);
            }

            salesforceService.syncQuotePricing(resolvedQuoteId, buildSalesforcePricingSyncFields(deal));

            List<Map<String, Object>> evaluationResult = salesforceService.evaluatePriceRules(resolvedQuoteId);

            deal.setSynced(true);
            dealRepository.save(deal);

            return buildCpqSyncResponse(
                    "quote-sync",
                    resolvedQuoteId,
                    "SUCCESS",
                    true,
                    toEvaluationRows(evaluationResult, "evaluatePriceRules")
            );
        } catch (SalesforceIntegrationException ex) {
            deal.setSynced(false);
            dealRepository.save(deal);
            throw ex;
        } catch (Exception ex) {
            deal.setSynced(false);
            dealRepository.save(deal);
            throw new SalesforceUpdateException("Failed to sync CPQ quote for deal " + id, ex);
        }
    }

    private CpqSyncResponseDto syncCpqPriceRulesInternal(Long id, String quoteId, String priceRuleId) {
        Deal deal = getActiveDeal(id);
        recalculatePricingInMemory(deal);

        boolean salesforceEnabled = salesforceService.isEnabled();
        String resolvedQuoteId = normalizeFilterValue(quoteId);
        String resolvedPriceRuleId = normalizeFilterValue(priceRuleId);

        if (!salesforceEnabled) {
            dealRepository.save(deal);
            return buildCpqSyncResponse(
                    "price-rules-sync",
                    resolvedQuoteId,
                    "SKIPPED_SALESFORCE_DISABLED",
                    false,
                    List.of()
            );
        }

        if (!StringUtils.hasText(resolvedQuoteId) && !StringUtils.hasText(deal.getSalesforceOpportunityId())) {
            dealRepository.save(deal);
            return buildCpqSyncResponse(
                    "price-rules-sync",
                    null,
                    "SKIPPED_MISSING_OPPORTUNITY_ID",
                    true,
                    List.of()
            );
        }

        try {
            resolvedQuoteId = ensureQuoteId(deal, resolvedQuoteId);
            if (!StringUtils.hasText(resolvedQuoteId)) {
                throw new SalesforceUpdateException("Failed to resolve Salesforce CPQ quote id for deal " + id);
            }

            salesforceService.syncPriceRules(resolvedPriceRuleId, resolvedQuoteId);
            salesforceService.validatePriceRules(resolvedQuoteId);
            salesforceService.applyPriceRules(resolvedQuoteId);
            List<Map<String, Object>> evaluationResult = salesforceService.evaluatePriceRules(resolvedQuoteId);

            List<Map<String, Object>> evaluationRows = new ArrayList<>();
            evaluationRows.add(validationRow(resolvedQuoteId));
            evaluationRows.addAll(toEvaluationRows(evaluationResult, "evaluatePriceRules"));

            deal.setSynced(true);
            dealRepository.save(deal);

            return buildCpqSyncResponse(
                    "price-rules-sync",
                    resolvedQuoteId,
                    "SUCCESS",
                    true,
                    evaluationRows
            );
        } catch (SalesforceIntegrationException ex) {
            deal.setSynced(false);
            dealRepository.save(deal);
            throw ex;
        } catch (Exception ex) {
            deal.setSynced(false);
            dealRepository.save(deal);
            throw new SalesforceUpdateException("Failed to sync CPQ price rules for deal " + id, ex);
        }
    }

    private void recalculatePricingInMemory(Deal deal) {
        tcvRuleExecutorService.executeTCVRules(deal);
        deal.setSynced(false);
    }

    private String normalizeFilterValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String ensureQuoteId(Deal deal, String quoteId) {
        if (StringUtils.hasText(quoteId)) {
            return quoteId;
        }

        return normalizeFilterValue(salesforceService.createQuote(
                deal.getSalesforceOpportunityId(),
                buildSalesforceQuotePayload(deal)
        ));
    }

    private DealResponseDto transitionDeal(
            Long id,
            DealStatus targetStatus,
            Set<DealStatus> allowedCurrentStates,
            String notePrefix,
            String noteDetails
    ) {
        Deal deal = getActiveDeal(id);
        DealStatus currentStatus = deal.getStatus();

        if (!allowedCurrentStates.contains(currentStatus)) {
            throw new InvalidDealStateException(
                    "Deal " + id + " cannot transition from " + currentStatus + " to " + targetStatus
            );
        }

        deal.setStatus(targetStatus);
        deal.setSynced(false);
        appendWorkflowNote(deal, notePrefix, noteDetails);

        Deal saved = dealRepository.save(deal);
        return dealMapper.toDto(saved);
    }

    private Deal getActiveDeal(Long id) {
        return dealRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new DealNotFoundException(id));
    }

    private void appendWorkflowNote(Deal deal, String notePrefix, String noteDetails) {
        if (!StringUtils.hasText(notePrefix)) {
            return;
        }

        String entry = StringUtils.hasText(noteDetails)
                ? "[" + Instant.now() + "] " + notePrefix + ": " + noteDetails.trim()
                : "[" + Instant.now() + "] " + notePrefix;

        if (!StringUtils.hasText(deal.getNotes())) {
            deal.setNotes(entry);
            return;
        }

        deal.setNotes(deal.getNotes() + System.lineSeparator() + entry);
    }

    private Map<String, Object> buildSalesforceSyncFields(Deal deal) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Name", deal.getName());
        fields.put("StageName", deal.getStatus().name());

        if (deal.getValue() != null) {
            fields.put("Amount", deal.getValue());
        }

        if (StringUtils.hasText(deal.getDescription())) {
            fields.put("Description", deal.getDescription());
        }

        return fields;
    }

    private Map<String, Object> buildSalesforcePricingSyncFields(Deal deal) {
        Map<String, Object> fields = new LinkedHashMap<>();

        if (deal.getValue() != null) {
            fields.put("Amount", deal.getValue());
        }

        if (deal.getTcvCalculation() != null && deal.getTcvCalculation().getFinalValue() != null) {
            fields.put("Amount", deal.getTcvCalculation().getFinalValue());
        }

        return fields;
    }

    private Map<String, Object> buildSalesforceQuotePayload(Deal deal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dealId", deal.getId());
        payload.put("dealUuid", deal.getUuid());
        payload.put("dealName", deal.getName());
        payload.put("opportunityId", deal.getSalesforceOpportunityId());

        if (deal.getValue() != null) {
            payload.put("amount", deal.getValue());
        }

        if (deal.getTcvCalculation() != null && deal.getTcvCalculation().getFinalValue() != null) {
            payload.put("tcvFinalValue", deal.getTcvCalculation().getFinalValue());
        }

        return payload;
    }

    private CpqSyncResponseDto buildCpqSyncResponse(
            String operation,
            String quoteId,
            String status,
            boolean salesforceEnabled,
            List<Map<String, Object>> evaluationRows
    ) {
        return CpqSyncResponseDto.builder()
                .operation(operation)
                .quoteId(quoteId)
                .status(status)
                .salesforceEnabled(salesforceEnabled)
                .evaluationRows(evaluationRows)
                .build();
    }

    private Map<String, Object> validationRow(String quoteId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", "validatePriceRules");
        row.put("quoteId", quoteId);
        row.put("status", "VALIDATED");
        return row;
    }

    private List<Map<String, Object>> toEvaluationRows(Object result, String source) {
        if (result == null) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        if (result instanceof Collection<?> collection) {
            for (Object item : collection) {
                rows.add(coerceEvaluationRow(item, source));
            }
            return rows;
        }

        rows.add(coerceEvaluationRow(result, source));
        return rows;
    }

    private Map<String, Object> coerceEvaluationRow(Object item, String source) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", source);

        if (item == null) {
            row.put("value", null);
            return row;
        }

        if (item instanceof Map<?, ?> itemMap) {
            for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                row.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return row;
        }

        row.put("value", item);
        return row;
    }
}
