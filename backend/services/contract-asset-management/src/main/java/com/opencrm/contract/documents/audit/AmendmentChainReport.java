package com.opencrm.contract.documents.audit;

import java.util.List;

/** Result of verifying every document that has at least one event. */
public record AmendmentChainReport(int documentsChecked, List<AmendmentChainBreak> breaks) {

    public AmendmentChainReport {
        breaks = List.copyOf(breaks);
    }

    public boolean intact() {
        return breaks.isEmpty();
    }
}
