package com.opencrm.dealdesk.mapper;

import com.opencrm.dealdesk.dto.DealRequestDto;
import com.opencrm.dealdesk.dto.DealResponseDto;
import com.opencrm.dealdesk.model.Deal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DealMapper {

    public Deal toEntity(DealRequestDto request) {
        Deal deal = new Deal();
        deal.setName(request.getName());
        deal.setDescription(request.getDescription());
        deal.setValue(request.getValue());
        deal.setStatus(request.getStatus());
        deal.setSalesforceOpportunityId(request.getSalesforceOpportunityId());
        deal.setAccountId(request.getAccountId());
        deal.setAccountName(request.getAccountName());
        deal.setNotes(request.getNotes());
        deal.setComponents(request.getComponents());
        deal.setPricingModel(request.getPricingModel());
        deal.setAdjustments(request.getAdjustments());
        deal.setContingentRevenue(request.getContingentRevenue());
        deal.setTcvCalculation(request.getTcvCalculation());
        if (request.getProductIds() != null) {
            deal.getProductIds().addAll(request.getProductIds());
        }
        return deal;
    }

    public void updateEntity(DealRequestDto request, Deal deal) {
        deal.setName(request.getName());
        deal.setDescription(request.getDescription());
        deal.setValue(request.getValue());
        deal.setStatus(request.getStatus());
        deal.setSalesforceOpportunityId(request.getSalesforceOpportunityId());
        deal.setAccountId(request.getAccountId());
        deal.setAccountName(request.getAccountName());
        deal.setNotes(request.getNotes());
        deal.setComponents(request.getComponents());
        deal.setPricingModel(request.getPricingModel());
        deal.setAdjustments(request.getAdjustments());
        deal.setContingentRevenue(request.getContingentRevenue());
        deal.setTcvCalculation(request.getTcvCalculation());
        Set<String> productIds = deal.getProductIds();
        productIds.clear();
        if (request.getProductIds() != null) {
            productIds.addAll(request.getProductIds());
        }
    }

    public DealResponseDto toDto(Deal deal) {
        return DealResponseDto.builder()
                .id(deal.getId())
                .uuid(deal.getUuid())
                .name(deal.getName())
                .description(deal.getDescription())
                .value(deal.getValue())
                .status(deal.getStatus())
                .salesforceOpportunityId(deal.getSalesforceOpportunityId())
                .accountId(deal.getAccountId())
                .accountName(deal.getAccountName())
                .notes(deal.getNotes())
                .components(deal.getComponents())
                .pricingModel(deal.getPricingModel())
                .adjustments(deal.getAdjustments())
                .contingentRevenue(deal.getContingentRevenue())
                .tcvCalculation(deal.getTcvCalculation())
                .synced(deal.isSynced())
                .productIds(toList(deal.getProductIds()))
                .createdAt(deal.getCreatedAt())
                .updatedAt(deal.getUpdatedAt())
                .build();
    }

    public List<DealResponseDto> toDtoList(List<Deal> deals) {
        return deals.stream().map(this::toDto).toList();
    }

    private List<String> toList(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(values);
    }
}
