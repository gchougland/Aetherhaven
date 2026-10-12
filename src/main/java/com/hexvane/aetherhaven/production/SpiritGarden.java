package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;

/** The Druid's Spirit Planters. Their bindings and growth live in {@link SpiritGardenState}, inside the town save. */
public final class SpiritGarden {
    private SpiritGarden() {}
    public static final String CONSTRUCTION = "plot_druid";
    public static final String KIND = "druid";
    public static final String BLOCK = "Aetherhaven_Spirit_Planter";
    public static final String PAGE = "AetherhavenSpiritPlanter";
    public static final String RITE_PARTICLE = "Aetherhaven_Garden_Rite";
    public static final String BLOOM_PARTICLE = "Aetherhaven_Garden_Bloom";
    public static final String RITE_SOUND = "Aetherhaven_Garden_Rite";
    public static final String BLOOM_SOUND = "Aetherhaven_Garden_Bloom";
    public static final String LANG = "aetherhaven_garden.";

    public static boolean is(String blockId) { return BLOCK.equals(blockId); }

    public static boolean isPlot(PlotInstance plot) {
        var plugin = AetherhavenPlugin.get();
        return plot != null && (CONSTRUCTION.equals(plot.getConstructionId()) || plugin != null
            && plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(), CONSTRUCTION));
    }

    /** A Conservatory's production, with anything bought under the old ingot upgrades carried onto the garden's tracks. */
    public static PlotProductionState production(TownRecord town, PlotInstance plot) {
        var production = town.getOrCreatePlotProduction(plot.getPlotId());
        GardenUpgrades.mapLegacy(production);
        return production;
    }
}
