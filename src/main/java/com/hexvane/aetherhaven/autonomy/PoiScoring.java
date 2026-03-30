package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PoiScoring {
    private PoiScoring() {}

    public static float score(@Nonnull VillagerNeeds needs, @Nonnull PoiEntry poi) {
        float hungerDef = VillagerNeeds.MAX - needs.getHunger();
        float energyDef = VillagerNeeds.MAX - needs.getEnergy();
        float funDef = VillagerNeeds.MAX - needs.getFun();
        float s = 0f;
        PoiInteractionKind k = poi.getInteractionKind();
        if (k == PoiInteractionKind.SLEEP || poi.getTags().contains("SLEEP") || poi.getTags().contains("ENERGY")) {
            s += energyDef * 0.55f;
        }
        if (k == PoiInteractionKind.USE_CONTAINER || poi.getTags().contains("EAT")) {
            s += hungerDef * 0.5f;
        }
        if (k == PoiInteractionKind.SIT || poi.getTags().contains("SIT")) {
            s += funDef * 0.45f;
        } else if (k == PoiInteractionKind.USE_BENCH && !poi.getTags().contains("EAT")) {
            s += funDef * 0.45f;
        } else if (poi.getTags().contains("FUN") && k != PoiInteractionKind.USE_BENCH) {
            s += funDef * 0.45f;
        }
        if (k == PoiInteractionKind.WORK_SURFACE || poi.getTags().contains("WORK")) {
            s += hungerDef * 0.15f + energyDef * 0.12f;
        }
        if (k == PoiInteractionKind.NONE && s < 0.01f) {
            s = funDef * 0.2f + hungerDef * 0.1f;
        }
        return s;
    }

    @Nullable
    public static PoiEntry pickBest(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding
    ) {
        return pickBest(candidates, needs, binding, Map.of());
    }

    /**
     * @param occupancy POI id → count of town NPCs already traveling to or using that POI (see {@link com.hexvane.aetherhaven.poi.PoiOccupancy})
     */
    @Nullable
    public static PoiEntry pickBest(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull Map<UUID, Integer> occupancy
    ) {
        UUID preferredPlot = binding.getPreferredPlotId();
        PoiEntry best = null;
        float bestScore = 0f;
        for (PoiEntry e : candidates) {
            if (preferredPlot != null && e.getPlotId() != null && !preferredPlot.equals(e.getPlotId())) {
                continue;
            }
            int cap = Math.max(1, e.getCapacity());
            int used = occupancy.getOrDefault(e.getId(), 0);
            if (used >= cap) {
                continue;
            }
            float sc = score(needs, e);
            if (best == null || sc > bestScore) {
                best = e;
                bestScore = sc;
            }
        }
        if (bestScore < 8f) {
            return null;
        }
        return best;
    }
}
