package com.opencrm.configuration.mapper;

import com.opencrm.configuration.dto.ConfigurationSessionDto;
import com.opencrm.configuration.session.ConfigurationSession;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ConfigurationSessionMapper {

    ConfigurationSessionDto toDto(ConfigurationSession session);

    List<ConfigurationSessionDto> toDtoList(List<ConfigurationSession> sessions);
}
