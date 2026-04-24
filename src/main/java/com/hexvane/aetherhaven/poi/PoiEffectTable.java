package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import javax.annotation.Nonnull;

/** Need restoration and USE duration by {@link PoiInteractionKind}. */
public final class PoiEffectTable {
    private PoiEffectTable() {}

    public static float useDurationSeconds(@Nonnull PoiInteractionKind kind) {
        return switch (kind) {
            case SLEEP -> 14f;
            case SIT, USE_BENCH -> 10f;
            case WORK_SURFACE -> 8f;
            case USE_CONTAINER -> 9f;
            case NONE -> 6f;
        };
    }

    /** Apply a single USE completion tick to needs (called once when USE phase ends). */
    public static void applyUseComplete(@Nonnull VillagerNeeds needs, @Nonnull PoiEntry poi) {
        PoiInteractionKind k = poi.getInteractionKind();
        switch (k) {
            case SLEEP -> {
                needs.setEnergy(Math.min(VillagerNeeds.MAX, needs.getEnergy() + 28f));
                needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + 6f));
            }
            case SIT -> needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + 22f));
            case USE_BENCH -> {
                if (poi.getTags().contains(AetherhavenConstants.POI_TAG_FEAST)) {
                    needs.setHunger(VillagerNeeds.MAX);
                } else if (poi.getTags().contains("EAT")) {
                    needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + 30f));
                } else {
                    needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + 22f));
                    needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + 4f));
                }
            }
            case WORK_SURFACE -> {
                needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + 6f));
                needs.setEnergy(Math.min(VillagerNeeds.MAX, needs.getEnergy() + 5f));
            }
            case USE_CONTAINER -> needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + 18f));
            default -> needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + 8f));
        }
    }
}
