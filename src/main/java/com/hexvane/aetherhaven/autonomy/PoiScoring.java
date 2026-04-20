package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PoiScoring {
    private static final float SCORE_EPS = 1e-4f;
    /** When any need meter falls below this (0..{@link VillagerNeeds#MAX}), work shift allows break POIs town-wide. */
    private static final float NEEDS_BREAK_THRESHOLD = 40f;

    private PoiScoring() {}

    /** True if the POI is for job activity (schedule {@code work} segment should prefer these over breaks). */
    public static boolean isWorkPoi(@Nonnull PoiEntry e) {
        if (e.getInteractionKind() == PoiInteractionKind.WORK_SURFACE) {
            return true;
        }
        return e.getTags().contains("WORK");
    }

    /** True when the villager should temporarily override a work shift to satisfy a low meter (eat / rest / fun). */
    public static boolean needsBreakForSchedule(@Nonnull VillagerNeeds needs) {
        return needs.getHunger() < NEEDS_BREAK_THRESHOLD
            || needs.getEnergy() < NEEDS_BREAK_THRESHOLD
            || needs.getFun() < NEEDS_BREAK_THRESHOLD;
    }

    static boolean isWorkScheduleSegment(@Nullable String scheduleLocation) {
        return scheduleLocation != null && VillagerScheduleResolver.LOC_WORK.equalsIgnoreCase(scheduleLocation.trim());
    }

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
        UUID preferredPlot = binding.getPreferredPlotId();
        boolean atWork = isWorkScheduleSegment(scheduleLocation);
        boolean breakOverride = atWork && needsBreakForSchedule(needs);
        boolean workOnlyShift = preferredPlot != null && atWork && !breakOverride;
        PoiEntry best = null;
        float bestScore = 0f;
        int bestUsed = Integer.MAX_VALUE;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (PoiEntry e : candidates) {
            if (workOnlyShift) {
                if (e.getPlotId() == null || !preferredPlot.equals(e.getPlotId())) {
                    continue;
                }
                if (!isWorkPoi(e)) {
                    continue;
                }
            } else if (preferredPlot != null && !(atWork && breakOverride)) {
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
            float sc = score(needs, e);
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
        if (best == null) {
            return null;
        }
        // Idle discretionary visits: require some unmet need so villagers do not crisscross town when already satisfied.
        // When the weekly schedule sets preferredPlotId, we must still pick a POI in that plot even if needs are full
        // (scores near zero); otherwise they never enter TRAVEL and stay on local wander (e.g. near Gaia after revival).
        if (preferredPlot == null && bestScore < 8f) {
            return null;
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
