package com.opencrm.approval.mapper;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.dto.ApprovalRequestDto;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ApprovalRequestMapper {

    ApprovalRequestDto toDto(ApprovalRequest approvalRequest);

    List<ApprovalRequestDto> toDtoList(List<ApprovalRequest> approvalRequests);
}
