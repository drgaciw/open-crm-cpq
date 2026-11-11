package com.opencrm.approval.mapper;

import com.opencrm.approval.domain.CommercialPolicy;
import com.opencrm.approval.domain.PolicyThreshold;
import com.opencrm.approval.dto.CommercialPolicyDto;
import com.opencrm.approval.dto.PolicyThresholdDto;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CommercialPolicyMapper {

    CommercialPolicyDto toDto(CommercialPolicy policy);

    List<CommercialPolicyDto> toDtoList(List<CommercialPolicy> policies);

    PolicyThresholdDto toThresholdDto(PolicyThreshold threshold);

    List<PolicyThresholdDto> toThresholdDtoList(List<PolicyThreshold> thresholds);
}
