package com.opencrm.document.mapper;

import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.dto.DocumentGenerationJobDto;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DocumentGenerationJobMapper {

    DocumentGenerationJobDto toDto(DocumentGenerationJob job);

    List<DocumentGenerationJobDto> toDtoList(List<DocumentGenerationJob> jobs);
}
