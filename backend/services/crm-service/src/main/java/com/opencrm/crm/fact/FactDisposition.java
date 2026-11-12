package com.opencrm.crm.fact;

/**
 * What the ledger does with one scored proposal. {@code writeRecord} is true only when an
 * agent may copy the value onto the subject column.
 */
public record FactDisposition(
        boolean stored,
        FactStatus status,
        FactBand band,
        boolean writeRecord,
        boolean humanProtected
) {

    public static FactDisposition notStored() {
        return new FactDisposition(false, null, null, false, false);
    }

    public static FactDisposition proposal(FactBand band, boolean humanProtected) {
        return new FactDisposition(true, FactStatus.PROPOSED, band, false, humanProtected);
    }
}
