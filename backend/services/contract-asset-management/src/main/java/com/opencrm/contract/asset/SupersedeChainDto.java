package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Supersede walk from one asset. Empty lists mean that direction has no further assets. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupersedeChainDto {
    private String assetId;
    private String direction;
    private List<SupersedeHopDto> predecessors;
    private List<SupersedeHopDto> successors;
}
