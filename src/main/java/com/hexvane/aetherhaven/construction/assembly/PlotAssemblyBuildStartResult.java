package com.hexvane.aetherhaven.construction.assembly;

/** Outcome of {@link PlotAssemblyService#startFromBuildClick}. */
public enum PlotAssemblyBuildStartResult {
    OK,
    ALREADY_ASSEMBLING,
    ASSEMBLY_ALREADY_ACTIVE,
    PASTE_CANCELLED,
    PAYMENT_FAILED,
    BUILDER_UNAVAILABLE
}
