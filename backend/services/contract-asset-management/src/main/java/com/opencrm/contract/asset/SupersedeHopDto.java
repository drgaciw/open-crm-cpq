package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** One step along a supersede chain. Distance 1 is the adjacent asset. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupersedeHopDto {
    private String assetId;
    private LocalDate effectiveDate;
    private int distance;
}
