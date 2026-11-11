package com.opencrm.crm.activity;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ActivityMapper {
    ActivityDto toDto(Activity activity);
}
