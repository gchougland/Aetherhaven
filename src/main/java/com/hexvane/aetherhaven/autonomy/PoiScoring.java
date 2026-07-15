package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityDefinition;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PoiScoring {
    private static final float SCORE_EPS = 1e-4f;
    /** When energy/fun fall below this (0..{@link VillagerNeeds#MAX}), work shift allows break POIs town-wide. */
    private static final float NEEDS_BREAK_THRESHOLD = 40f;
    /** Start a meal trip when hunger is below half of {@link VillagerNeeds#MAX}. */
    private static final float HUNGER_EAT_START_THRESHOLD = 50f;

    private PoiScoring() {}

    /** True if the POI is for job activity (schedule {@code work} segment should prefer these over breaks). */
    public static boolean isWorkPoi(@Nonnull PoiEntry e) {
        if (e.getInteractionKind() == PoiInteractionKind.WORK_SURFACE) {
            return true;
        }
        return e.getTags().contains("WORK");
    }

    public static boolean isBardWorkPoi(@Nonnull PoiEntry e) {
        return e.getTags().contains(AetherhavenConstants.POI_TAG_BARD);
    }

    /** Guild master desk and similar work spots; excludes the bard performance spot. */
    public static boolean isNonBardWorkPoi(@Nonnull PoiEntry e) {
        return isWorkPoi(e) && !isBardWorkPoi(e);
    }

    static boolean matchesWorkPoiForBindingKind(@Nonnull PoiEntry e, @Nonnull String bindingKind) {
        if (TownVillagerBinding.KIND_BARD.equals(bindingKind)
            || TownVillagerBinding.KIND_VISITOR_BARD.equals(bindingKind)) {
            return isBardWorkPoi(e);
        }
        if (TownVillagerBinding.KIND_GUILD_MASTER.equals(bindingKind)
            || TownVillagerBinding.KIND_VISITOR_GUILD_MASTER.equals(bindingKind)) {
            return isNonBardWorkPoi(e);
        }
        return isWorkPoi(e);
    }

    /** True when hunger is low enough to leave the current schedule for food (below 50%). */
    public static boolean needsHungerBreak(@Nonnull VillagerNeeds needs) {
        return needsHungerBreak(needs, false);
    }

    /**
     * Hunger break: start when hunger is below 50%, or keep eating while a fill session is active until the meter
     * is full.
     */
    public static boolean needsHungerBreak(@Nonnull VillagerNeeds needs, boolean fillingHungerSession) {
        if (!isHungerNotFull(needs)) {
            return false;
        }
        return fillingHungerSession || needs.getHunger() < HUNGER_EAT_START_THRESHOLD;
    }

    /** Hunger bar is not yet full (used to chain another eat POI trip). */
    public static boolean isHungerNotFull(@Nonnull VillagerNeeds needs) {
        return needs.getHunger() < VillagerNeeds.MAX - 0.25f;
    }

    /** Eat / feast spots used for hunger breaks. */
    public static boolean isEatPoi(@Nonnull PoiEntry e) {
        return e.getTags().contains("EAT") || e.getTags().contains(AetherhavenConstants.POI_TAG_FEAST);
    }

    /** True when the villager should temporarily override a work shift to satisfy a low meter (eat / rest / fun). */
    public static boolean needsBreakForSchedule(@Nonnull VillagerNeeds needs) {
        return needs.getHunger() < HUNGER_EAT_START_THRESHOLD
            || needs.getEnergy() < NEEDS_BREAK_THRESHOLD
            || needs.getFun() < NEEDS_BREAK_THRESHOLD;
    }

    public static boolean isWorkScheduleSegment(@Nullable String scheduleLocation) {
        return scheduleLocation != null && VillagerScheduleResolver.LOC_WORK.equalsIgnoreCase(scheduleLocation.trim());
    }

    static boolean isShopScheduleSegment(@Nullable String scheduleLocation) {
        return scheduleLocation != null && VillagerScheduleResolver.LOC_SHOP.equalsIgnoreCase(scheduleLocation.trim());
    }

    public static boolean isShopPoi(@Nonnull PoiEntry e) {
        return e.getTags().contains("SHOP");
    }

    public static float score(@Nonnull VillagerNeeds needs, @Nonnull PoiEntry poi) {
        return score(needs, poi, false);
    }

    public static float score(@Nonnull VillagerNeeds needs, @Nonnull PoiEntry poi, boolean townHasRestaurant) {
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
        if (townHasRestaurant
            && poi.getTags().contains(com.hexvane.aetherhaven.AetherhavenConstants.POI_TAG_RESTAURANT)
            && hungerDef > 0.01f) {
            s *= 2.5f;
        }
        return s;
    }

    /**
     * Averages leisure tag weights across all personalities on a townsfolk character (1.0 when none apply).
     */
    public static float townsfolkBlendedTagMultiplier(
        @Nonnull PoiEntry poi,
        @Nonnull TownsfolkPersonalityCatalog catalog,
        @Nonnull List<String> personalityIds
    ) {
        if (personalityIds.isEmpty()) {
            return 1f;
        }
        float sum = 0f;
        int count = 0;
        for (String pid : personalityIds) {
            TownsfolkPersonalityDefinition p = catalog.byId(pid);
            if (p == null) {
                continue;
            }
            float m = 1f;
            for (var e : p.getLeisurePoiTagWeights().entrySet()) {
                if (poi.getTags().contains(e.getKey())) {
                    m = Math.max(m, e.getValue().floatValue());
                }
            }
            sum += m;
            count++;
        }
        return count > 0 ? sum / count : 1f;
    }

    public static float scoreWithTownsfolkBlend(
        @Nonnull VillagerNeeds needs,
        @Nonnull PoiEntry poi,
        @Nonnull TownsfolkPersonalityCatalog catalog,
        @Nonnull List<String> personalityIds
    ) {
        return score(needs, poi) * townsfolkBlendedTagMultiplier(poi, catalog, personalityIds);
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
     * @param cellOccupancy {@link PoiOccupancy#cellOccupancyForTown} — counts NPCs per POI anchor cell {@code "x,y,z"}
     */
    @Nullable
    public static PoiEntry pickBest(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull Map<String, Integer> cellOccupancy
    ) {
        return pickBest(candidates, needs, binding, cellOccupancy, Double.NaN, Double.NaN, null);
    }

    /**
     * @param npcX world X of NPC (e.g. from {@link com.hypixel.hytale.server.core.modules.entity.component.TransformComponent}),
     *             or NaN to skip distance tie-breaking
     * @param npcZ world Z of NPC
     * @param scheduleLocation last applied schedule segment (e.g. {@link VillagerScheduleResolver#LOC_WORK}); when {@code
     *     work} and needs are satisfied, only {@link #isWorkPoi} on the preferred plot are considered; when needs are
     *     low, break POIs are allowed town-wide.
     */
    @Nullable
    public static PoiEntry pickBest(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ
    ) {
        return pickBest(candidates, needs, binding, cellOccupancy, npcX, npcZ, null);
    }

    @Nullable
    public static PoiEntry pickBest(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ,
        @Nullable String scheduleLocation
    ) {
        return pickBest(candidates, needs, binding, cellOccupancy, npcX, npcZ, scheduleLocation, false, false);
    }

    @Nullable
    public static PoiEntry pickBest(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ,
        @Nullable String scheduleLocation,
        boolean townHasRestaurant
    ) {
        return pickBest(candidates, needs, binding, cellOccupancy, npcX, npcZ, scheduleLocation, townHasRestaurant, false);
    }

    @Nullable
    public static PoiEntry pickBest(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ,
        @Nullable String scheduleLocation,
        boolean townHasRestaurant,
        boolean fillingHungerSession
    ) {
        UUID preferredPlot = binding.getPreferredPlotId();
        boolean atWork = isWorkScheduleSegment(scheduleLocation);
        boolean atShop = isShopScheduleSegment(scheduleLocation);
        boolean hungerBreak = needsHungerBreak(needs, fillingHungerSession);
        boolean breakOverride = atWork && needsBreakForSchedule(needs);
        boolean workOnlyShift = preferredPlot != null && atWork && !breakOverride && !hungerBreak;
        boolean shopBrowseShift = atShop && !hungerBreak;
        boolean allowTownWide =
            hungerBreak || (atWork && breakOverride);
        PoiEntry best = null;
        float bestScore = 0f;
        int bestUsed = Integer.MAX_VALUE;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (PoiEntry e : candidates) {
            if (e.getTags().contains(AetherhavenConstants.POI_TAG_QUEST_BOARD)) {
                continue;
            }
            if (hungerBreak) {
                if (!isEatPoi(e)) {
                    continue;
                }
            } else if (shopBrowseShift) {
                if (!isShopPoi(e)) {
                    continue;
                }
            } else if (workOnlyShift) {
                if (e.getPlotId() == null || !preferredPlot.equals(e.getPlotId())) {
                    continue;
                }
                if (!matchesWorkPoiForBindingKind(e, binding.getKind())) {
                    continue;
                }
            } else if (preferredPlot != null && !allowTownWide && !atShop) {
                if (e.getPlotId() != null && !preferredPlot.equals(e.getPlotId())) {
                    continue;
                }
            }
            int cap = Math.max(1, e.getCapacity());
            String cell = PoiOccupancy.cellKey(e.getX(), e.getY(), e.getZ());
            int used = cellOccupancy.getOrDefault(cell, 0);
            if (used >= cap) {
                continue;
            }
            float sc = score(needs, e, townHasRestaurant);
            double distSq = distSqToPoi(e, npcX, npcZ);
            if (best == null) {
                best = e;
                bestScore = sc;
                bestUsed = used;
                bestDistSq = distSq;
                continue;
            }
            if (sc > bestScore + SCORE_EPS) {
                best = e;
                bestScore = sc;
                bestUsed = used;
                bestDistSq = distSq;
                continue;
            }
            if (sc + SCORE_EPS < bestScore) {
                continue;
            }
            if (used < bestUsed) {
                best = e;
                bestScore = sc;
                bestUsed = used;
                bestDistSq = distSq;
                continue;
            }
            if (used > bestUsed) {
                continue;
            }
            if (!Double.isNaN(distSq) && distSq < bestDistSq - 1e-9) {
                best = e;
                bestScore = sc;
                bestUsed = used;
                bestDistSq = distSq;
            }
        }
        if (best == null && workOnlyShift && isBardBindingKind(binding.getKind()) && preferredPlot != null) {
            best = pickRelaxedWorkPoiOnPlot(candidates, preferredPlot, cellOccupancy, npcX, npcZ);
            if (best != null) {
                bestScore = score(needs, best);
            }
        }
        if (best == null) {
            return null;
        }
        // Idle discretionary visits: require some unmet need so villagers do not crisscross town when already satisfied.
        // When the weekly schedule sets preferredPlotId, we must still pick a POI in that plot even if needs are full
        // (scores near zero); otherwise they never enter TRAVEL and stay on local wander (e.g. near Gaia after revival).
        // Hunger breaks always allow a scored eat POI even without preferredPlot.
        if (preferredPlot == null && !shopBrowseShift && !hungerBreak && bestScore < 8f) {
            return null;
        }
        return best;
    }

    private static boolean isBardBindingKind(@Nonnull String bindingKind) {
        return TownVillagerBinding.KIND_BARD.equals(bindingKind)
            || TownVillagerBinding.KIND_VISITOR_BARD.equals(bindingKind);
    }

    /** When a guild-hall variant omits the BARD tag, still use a generic WORK spot on the assigned plot. */
    @Nullable
    private static PoiEntry pickRelaxedWorkPoiOnPlot(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull UUID preferredPlot,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ
    ) {
        PoiEntry best = null;
        int bestUsed = Integer.MAX_VALUE;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (PoiEntry e : candidates) {
            if (e.getPlotId() == null || !preferredPlot.equals(e.getPlotId()) || !isWorkPoi(e)) {
                continue;
            }
            int cap = Math.max(1, e.getCapacity());
            String cell = PoiOccupancy.cellKey(e.getX(), e.getY(), e.getZ());
            int used = cellOccupancy.getOrDefault(cell, 0);
            if (used >= cap) {
                continue;
            }
            double distSq = distSqToPoi(e, npcX, npcZ);
            if (best == null || used < bestUsed || (!Double.isNaN(distSq) && distSq < bestDistSq - 1e-9)) {
                best = e;
                bestUsed = used;
                bestDistSq = distSq;
            }
        }
        return best;
    }

    private static double distSqToPoi(@Nonnull PoiEntry e, double npcX, double npcZ) {
        if (Double.isNaN(npcX) || Double.isNaN(npcZ)) {
            return Double.NaN;
        }
        double px;
        double pz;
        if (e.hasInteractionTarget()) {
            Double tx = e.getInteractionTargetX();
            Double tz = e.getInteractionTargetZ();
            if (tx == null || tz == null) {
                px = e.getX() + 0.5;
                pz = e.getZ() + 0.5;
            } else {
                px = tx;
                pz = tz;
            }
        } else {
            px = e.getX() + 0.5;
            pz = e.getZ() + 0.5;
        }
        double dx = px - npcX;
        double dz = pz - npcZ;
        return dx * dx + dz * dz;
    }
}
