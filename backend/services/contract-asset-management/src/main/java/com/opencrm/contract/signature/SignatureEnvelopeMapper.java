package com.opencrm.contract.signature;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SignatureEnvelopeMapper {

    @Mapping(target = "envelopeId", source = "uuid")
    SignatureEnvelopeDto toDto(SignatureEnvelope envelope);
}
