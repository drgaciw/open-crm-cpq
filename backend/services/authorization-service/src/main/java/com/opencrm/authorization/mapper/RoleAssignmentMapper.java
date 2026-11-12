package com.opencrm.authorization.mapper;

import com.opencrm.authorization.dto.RoleAssignmentDto;
import com.opencrm.authorization.entity.RoleAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** ADR-006: the entity never crosses the API boundary; this is the one place {@link RoleAssignment} is mapped. */
@Mapper
public interface RoleAssignmentMapper {

    @Mapping(target = "roleName", source = "role.name")
    RoleAssignmentDto toDto(RoleAssignment entity);
}
