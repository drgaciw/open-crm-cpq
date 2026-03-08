package com.opencrm.dealdesk.service;

import com.opencrm.dealdesk.dto.CpqSyncResponseDto;
import com.opencrm.dealdesk.dto.DealRequestDto;
import com.opencrm.dealdesk.dto.DealResponseDto;
import com.opencrm.dealdesk.model.DealStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface DealService {

    List<DealResponseDto> getAllDeals();

    List<DealResponseDto> getDeals(
            DealStatus status,
            String accountId,
            BigDecimal minValue,
            String salesforceOpportunityId,
            Boolean synced,
            Instant createdAfter
    );

    DealResponseDto getDealById(Long id);

    DealResponseDto createDeal(DealRequestDto request);

    DealResponseDto updateDeal(Long id, DealRequestDto request);

    void deleteDeal(Long id);

    DealResponseDto submitDeal(Long id);

    DealResponseDto approveDeal(Long id, String notes);

    DealResponseDto rejectDeal(Long id, String notes);

    DealResponseDto recalculatePricing(Long id);

    DealResponseDto syncPricing(Long id);

    CpqSyncResponseDto syncCpqQuote(Long id, String quoteId);

    CpqSyncResponseDto syncCpqPriceRules(Long id, String quoteId, String priceRuleId);

    DealResponseDto syncDeal(Long id);

    List<DealResponseDto> batchSyncDeals(List<Long> ids);
}
