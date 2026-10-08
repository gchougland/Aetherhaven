package com.hexvane.aetherhaven.production;

/** Preserve catalog cursors and stored stock while restricting explicitly adopted physical production. */
public final class PhysicalProductionOutputPolicy {
    private PhysicalProductionOutputPolicy() {}

    public static boolean available(PlotProductionState state, String item) {
        if (state.usesPhysicalHerd()) return BarnAnimalCatalog.output(item);
        if (state.usesPhysicalField()) return NativeFarmCrops.supportedOutput(item);
        return !state.usesPhysicalTimber() || NativeTimberGrowth.species(item) != null
            || NativeTimberProducts.supported(item) || TimberCharring.OUTPUT.equals(item);
    }

    public static String selectionIssue(PlotProductionState state, int slot, String item) {
        if (slot < 0 || slot >= WorkplaceProductionUpgrades.slotCount(state)) return "This material slot is locked.";
        if (!available(state, item)) return "This production setup cannot produce this material yet. Existing stock can still be collected.";
        var planted = state.timber().trees().get(slot);
        if (planted != null && !planted.tree().output().equals(item)) return "Finish this tree before changing its timber.";
        return null;
    }
}
