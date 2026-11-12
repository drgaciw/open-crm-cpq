package com.opencrm.crm.fact;

import java.math.BigDecimal;

/**
 * Versioned evidence weights. The ledger stores the version next to each fact so a later
 * calibration does not rewrite history.
 */
public interface EvidenceWeights {

    String version();

    BigDecimal weight(EvidenceKind kind);

    boolean primary(EvidenceKind kind);
}
