package com.opencrm.contract.asset;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    AssetDto toDto(Asset asset);

    List<AssetDto> toDtoList(List<Asset> assets);
}
