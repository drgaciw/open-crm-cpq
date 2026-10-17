package com.opencrm.pricing.renewal;

/** The merged mode is named by the ADR and is not priced by this slice. */
public final class RenewalUpliftModeOutOfSliceException extends IllegalArgumentException {
    private final RenewalUpliftMode mode;

    public RenewalUpliftModeOutOfSliceException(RenewalUpliftMode mode) {
        super("Mode " + mode + " is outside the ContractedNet UpliftPct slice (DRG-549 owns the mode catalog)");
        this.mode = mode;
    }

    public RenewalUpliftMode mode() {
        return mode;
    }
}
