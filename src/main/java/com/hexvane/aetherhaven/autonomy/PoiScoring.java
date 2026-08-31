package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.poi.PoiEffectTable;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityDefinition;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PoiScoring {
    /** Inn hearth and shared beds top out here; restaurant and assigned housing fill to {@link VillagerNeeds#MAX}. */
    public static final float INN_UTILITY_NEED_CAP = 80f;

    private static final float SCORE_EPS = 1e-4f;
    /** Soft penalty so workers rotate among multiple work spots on the same plot. */
    private static final float LAST_USED_POI_PENALTY = 14f;
    /** Tiny random boost among equal work candidates. */
    private static final float WORK_POI_JITTER = 2.5f;
    /** When energy/fun fall below this (0..{@link VillagerNeeds#MAX}), work shift allows break POIs town-wide. */
    private static final float NEEDS_BREAK_THRESHOLD = 40f;
    /** Start a rest trip when energy is below 30% of {@link VillagerNeeds#MAX}. */
    public static final float ENERGY_REST_START_THRESHOLD = 30f;
    /** Start a meal trip when hunger is below half of {@link VillagerNeeds#MAX}. */
    private static final float HUNGER_EAT_START_THRESHOLD = 50f;

    /** Which urgent need break should preempt work (lowest satisfiable meter wins). */
    public enum UrgentNeedKind {
        HUNGER,
        ENERGY,
        FUN
    }

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

    public static boolean matchesWorkPoiForBindingKind(@Nonnull PoiEntry e, @Nonnull String bindingKind) {
        // Inn-pool / temporary visitors never claim workplace desks; only assigned residents work there.
        if (TownVillagerBinding.isVisitorKind(bindingKind)) {
            return false;
        }
        String role = e.getWorkResidentKind();
        if (role != null && !role.isBlank()) {
            String want = bindingKind.trim();
            if (role.equals(want)) {
                return isWorkPoi(e);
            }
            return false;
        }
        if (TownVillagerBinding.KIND_BARD.equals(bindingKind)) {
            return isBardWorkPoi(e);
        }
        if (TownVillagerBinding.KIND_GUILD_MASTER.equals(bindingKind)) {
            return isNonBardWorkPoi(e);
        }
        return isWorkPoi(e);
    }

    /** True when hunger is low enough to leave the current schedule for food (below 50%), daytime only. */
    public static boolean needsHungerBreak(@Nonnull VillagerNeeds needs) {
        return needsHungerBreak(needs, false, true);
    }

    /**
     * Hunger break: start when hunger is below 50%, or keep eating while a fill session is active until the meter
     * is full. Always false at night so villagers sleep instead of seeking meals.
     */
    public static boolean needsHungerBreak(@Nonnull VillagerNeeds needs, boolean fillingHungerSession) {
        return needsHungerBreak(needs, fillingHungerSession, true);
    }

    public static boolean needsHungerBreak(
        @Nonnull VillagerNeeds needs,
        boolean fillingHungerSession,
        boolean daytime
    ) {
        if (!daytime || !isHungerNotFull(needs)) {
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

    /** Bed / rest spots used for energy breaks. */
    public static boolean isRestPoi(@Nonnull PoiEntry e) {
        PoiInteractionKind k = e.getInteractionKind();
        return k == PoiInteractionKind.SLEEP || e.getTags().contains("SLEEP") || e.getTags().contains("ENERGY");
    }

    /** Leisure spots used for fun breaks. */
    public static boolean isFunPoi(@Nonnull PoiEntry e) {
        return e.getTags().contains("FUN");
    }

    /**
     * Festival standing spots. Each spot belongs to one villager kind and is assigned directly by
     * {@link com.hexvane.aetherhaven.festival.FestivalSpotService}, so normal scoring must never hand one out.
     */
    public static boolean isFestivalPoi(@Nonnull PoiEntry e) {
        return e.getTags().contains(AetherhavenConstants.POI_TAG_FESTIVAL)
            || e.getTags().contains(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL);
    }

    public static boolean isEnergyNotFull(@Nonnull VillagerNeeds needs) {
        return needs.getEnergy() < VillagerNeeds.MAX - 0.25f;
    }

    public static boolean isFunNotFull(@Nonnull VillagerNeeds needs) {
        return needs.getFun() < VillagerNeeds.MAX - 0.25f;
    }

    /**
     * True for shared inn eat/rest POIs (not restaurant-tagged spots, not a villager's assigned house plot).
     */
    public static boolean isInnUtilityNeedCapPoi(
        @Nonnull PoiEntry poi,
        @Nullable TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nullable UUID villagerUuid
    ) {
        if (town == null || poi.getPlotId() == null) {
            return false;
        }
        if (poi.getTags().contains(AetherhavenConstants.POI_TAG_RESTAURANT)) {
            return false;
        }
        if (!resolveInnPlotIds(town, constructionCatalog).contains(poi.getPlotId())) {
            return false;
        }
        if (villagerUuid != null) {
            UUID homePlotId = resolveHomePlotId(town, villagerUuid, constructionCatalog);
            if (homePlotId != null && homePlotId.equals(poi.getPlotId())) {
                return false;
            }
        }
        return true;
    }

    public static float needFillCapForPoi(
        @Nonnull PoiEntry poi,
        @Nullable TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nullable UUID villagerUuid
    ) {
        return isInnUtilityNeedCapPoi(poi, town, constructionCatalog, villagerUuid)
            ? INN_UTILITY_NEED_CAP
            : VillagerNeeds.MAX;
    }

    /** Whether this POI type's meter is topped off for the villager (80% at inn, full elsewhere). */
    public static boolean isNeedMeterFilledForPoi(
        @Nonnull PoiEntry poi,
        @Nonnull VillagerNeeds needs,
        @Nullable TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nullable UUID villagerUuid
    ) {
        float cap = needFillCapForPoi(poi, town, constructionCatalog, villagerUuid);
        if (isEatPoi(poi)) {
            return needs.getHunger() >= cap - 0.25f;
        }
        if (isRestPoi(poi)) {
            return needs.getEnergy() >= cap - 0.25f;
        }
        if (isFunPoi(poi)) {
            return !isFunNotFull(needs);
        }
        return false;
    }

    public static void applyPoiUseComplete(
        @Nonnull VillagerNeeds needs,
        @Nonnull PoiEntry poi,
        @Nullable PlotRestaurantState restaurantState,
        @Nullable TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nullable UUID villagerUuid
    ) {
        applyPoiUseComplete(needs, poi, restaurantState, town, constructionCatalog, villagerUuid, false);
    }

    public static void applyPoiUseComplete(
        @Nonnull VillagerNeeds needs,
        @Nonnull PoiEntry poi,
        @Nullable PlotRestaurantState restaurantState,
        @Nullable TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nullable UUID villagerUuid,
        boolean shopping
    ) {
        PoiEffectTable.applyUseComplete(needs, poi, restaurantState);
        if (shopping && isShopPoi(poi)) {
            PoiEffectTable.applyShopFunRestore(needs);
        }
        if (!isInnUtilityNeedCapPoi(poi, town, constructionCatalog, villagerUuid)) {
            return;
        }
        if (isEatPoi(poi)) {
            needs.setHunger(Math.min(needs.getHunger(), INN_UTILITY_NEED_CAP));
        }
        if (isRestPoi(poi)) {
            needs.setEnergy(Math.min(needs.getEnergy(), INN_UTILITY_NEED_CAP));
        }
    }

    public static boolean needsEnergyBreak(@Nonnull VillagerNeeds needs, boolean fillingEnergySession) {
        if (!isEnergyNotFull(needs)) {
            return false;
        }
        return fillingEnergySession || needs.getEnergy() < ENERGY_REST_START_THRESHOLD;
    }

    public static boolean needsFunBreak(@Nonnull VillagerNeeds needs, boolean fillingFunSession, boolean daytime) {
        if (!daytime || !isFunNotFull(needs)) {
            return false;
        }
        return fillingFunSession || needs.getFun() < NEEDS_BREAK_THRESHOLD;
    }

    @Nullable
    public static UUID resolveHomePlotId(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        for (PlotInstance p :
            town.listCompletePlotsWithGameplayConstruction(
                constructionCatalog,
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE
            )) {
            if (p.hasHomeResident(entityUuid)) {
                return p.getPlotId();
            }
        }
        return null;
    }

    @Nonnull
    public static List<UUID> resolveInnPlotIds(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        return town.listCompletePlotIdsWithGameplayConstruction(
            constructionCatalog,
            AetherhavenConstants.CONSTRUCTION_PLOT_INN
        );
    }

    /** Completed park plots in the town (shared leisure utility; no villager assignment required). */
    @Nonnull
    public static List<UUID> resolveParkPlotIds(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        return town.listCompletePlotIdsWithGameplayConstruction(
            constructionCatalog,
            AetherhavenConstants.CONSTRUCTION_PLOT_PARK
        );
    }

    /**
     * Plot ids allowed during an urgent need break in {@link #pickBest}: assigned house (housing only), inn plots
     * (shared rest), or park plots (shared fun). Workplace assignment is not required for inn or park.
     */
    @Nullable
    public static Set<UUID> resolveUrgentBreakPlotAllowlist(
        @Nonnull VillagerNeeds needs,
        boolean fillingHungerSession,
        boolean fillingEnergySession,
        boolean fillingFunSession,
        boolean daytime,
        @Nullable TownRecord town,
        @Nullable UUID entityUuid,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (town == null) {
            return null;
        }
        UrgentNeedKind breakMode =
            pickBestBreakMode(needs, fillingHungerSession, fillingEnergySession, fillingFunSession, daytime);
        if (breakMode == UrgentNeedKind.FUN) {
            List<UUID> parkPlotIds = resolveParkPlotIds(town, constructionCatalog);
            return parkPlotIds.isEmpty() ? null : new HashSet<>(parkPlotIds);
        }
        if (breakMode == UrgentNeedKind.ENERGY) {
            Set<UUID> allowed = new HashSet<>(resolveInnPlotIds(town, constructionCatalog));
            if (entityUuid != null) {
                UUID homePlotId = resolveHomePlotId(town, entityUuid, constructionCatalog);
                if (homePlotId != null) {
                    allowed.add(homePlotId);
                }
            }
            return allowed.isEmpty() ? null : allowed;
        }
        return null;
    }

    /**
     * Rest break POI: assigned house bed when the villager has housing, otherwise the nearest inn bed. Inn use never
     * requires assignment.
     */
    @Nullable
    public static PoiEntry pickEnergyRestPoi(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ,
        @Nullable UUID homePlotId,
        @Nonnull List<UUID> innPlotIds
    ) {
        if (homePlotId != null) {
            PoiEntry homePick = pickNearestRestPoiOnPlots(candidates, cellOccupancy, npcX, npcZ, List.of(homePlotId));
            if (homePick != null) {
                return homePick;
            }
        }
        if (!innPlotIds.isEmpty()) {
            return pickNearestRestPoiOnPlots(candidates, cellOccupancy, npcX, npcZ, innPlotIds);
        }
        return null;
    }

    /**
     * Fun break POI: nearest completed park plot with an available fun POI. No villager assignment required.
     */
    @Nullable
    public static PoiEntry pickFunBreakPoi(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        List<UUID> parkPlotIds = resolveParkPlotIds(town, constructionCatalog);
        if (parkPlotIds.isEmpty()) {
            return null;
        }
        Set<UUID> parkPlots = new HashSet<>(parkPlotIds);
        Map<UUID, List<PoiEntry>> byPlot = new HashMap<>();
        for (PoiEntry e : candidates) {
            if (!isFunPoi(e) || e.getPlotId() == null || !parkPlots.contains(e.getPlotId())) {
                continue;
            }
            if (!hasAvailableCapacity(e, cellOccupancy)) {
                continue;
            }
            byPlot.computeIfAbsent(e.getPlotId(), k -> new ArrayList<>()).add(e);
        }
        if (byPlot.isEmpty()) {
            return null;
        }
        UUID nearestPlot = null;
        double nearestPlotDistSq = Double.POSITIVE_INFINITY;
        for (UUID plotId : byPlot.keySet()) {
            PlotInstance plot = town.findPlotById(plotId);
            double distSq;
            if (plot != null) {
                var fp = plot.toFootprint();
                double cx = (fp.getMinX() + fp.getMaxX()) * 0.5 + 0.5;
                double cz = (fp.getMinZ() + fp.getMaxZ()) * 0.5 + 0.5;
                distSq = distSqWorld(npcX, npcZ, cx, cz);
            } else {
                PoiEntry proxy = pickNearestClaimablePoi(byPlot.get(plotId), cellOccupancy, npcX, npcZ);
                if (proxy == null) {
                    continue;
                }
                distSq = distSqToPoi(proxy, npcX, npcZ);
            }
            if (nearestPlot == null || distSq < nearestPlotDistSq) {
                nearestPlot = plotId;
                nearestPlotDistSq = distSq;
            }
        }
        if (nearestPlot == null) {
            return null;
        }
        return pickNearestClaimablePoi(byPlot.get(nearestPlot), cellOccupancy, npcX, npcZ);
    }

    /**
     * Among needs below threshold with an available POI, pick the lowest meter. Active fill sessions keep priority
     * for their need type.
     */
    @Nullable
    public static UrgentNeedKind resolveMostUrgentSatisfiableNeed(
        @Nonnull VillagerNeeds needs,
        boolean fillingHunger,
        boolean fillingEnergy,
        boolean fillingFun,
        boolean daytime,
        @Nonnull List<PoiEntry> candidates,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ,
        @Nonnull UUID entityUuid,
        @Nullable TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (fillingHunger && needsHungerBreak(needs, true, daytime)) {
            if (hasSatisfiableHungerPoi(candidates, cellOccupancy)) {
                return UrgentNeedKind.HUNGER;
            }
        }
        if (fillingEnergy && needsEnergyBreak(needs, true)) {
            if (town != null && hasSatisfiableEnergyPoi(candidates, cellOccupancy, entityUuid, town, constructionCatalog)) {
                return UrgentNeedKind.ENERGY;
            }
        }
        if (fillingFun && needsFunBreak(needs, true, daytime)) {
            if (town != null
                && pickFunBreakPoi(candidates, cellOccupancy, npcX, npcZ, town, constructionCatalog) != null) {
                return UrgentNeedKind.FUN;
            }
        }
        UrgentNeedKind best = null;
        float bestMeter = Float.MAX_VALUE;
        if (daytime
            && needsHungerBreak(needs, false, daytime)
            && hasSatisfiableHungerPoi(candidates, cellOccupancy)
            && needs.getHunger() < bestMeter) {
            best = UrgentNeedKind.HUNGER;
            bestMeter = needs.getHunger();
        }
        if (needsEnergyBreak(needs, false)
            && town != null
            && hasSatisfiableEnergyPoi(candidates, cellOccupancy, entityUuid, town, constructionCatalog)
            && needs.getEnergy() < bestMeter) {
            best = UrgentNeedKind.ENERGY;
            bestMeter = needs.getEnergy();
        }
        if (daytime
            && needsFunBreak(needs, false, daytime)
            && town != null
            && pickFunBreakPoi(candidates, cellOccupancy, npcX, npcZ, town, constructionCatalog) != null
            && needs.getFun() < bestMeter) {
            best = UrgentNeedKind.FUN;
        }
        return best;
    }

    public static boolean hasSatisfiableHungerPoi(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull Map<String, Integer> cellOccupancy
    ) {
        for (PoiEntry e : candidates) {
            if (isEatPoi(e) && hasAvailableCapacity(e, cellOccupancy)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasSatisfiableEnergyPoi(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull Map<String, Integer> cellOccupancy,
        @Nonnull UUID entityUuid,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        UUID homePlotId = resolveHomePlotId(town, entityUuid, constructionCatalog);
        List<UUID> innPlotIds = resolveInnPlotIds(town, constructionCatalog);
        return pickEnergyRestPoi(candidates, cellOccupancy, Double.NaN, Double.NaN, homePlotId, innPlotIds) != null;
    }

    /** True when the villager should temporarily override a work shift to satisfy a low meter (eat / rest / fun). */
    public static boolean needsBreakForSchedule(@Nonnull VillagerNeeds needs) {
        return needsBreakForSchedule(needs, true);
    }

    /** Night: ignore hunger for work breaks so they do not leave for food; energy/fun still apply. */
    public static boolean needsBreakForSchedule(@Nonnull VillagerNeeds needs, boolean daytime) {
        return (daytime && needs.getHunger() < HUNGER_EAT_START_THRESHOLD)
            || needs.getEnergy() < ENERGY_REST_START_THRESHOLD
            || (daytime && needs.getFun() < NEEDS_BREAK_THRESHOLD);
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

    /** Shop POIs refill fun only while the villager is on a scheduled shopping segment. */
    public static boolean isShopFunFillPoi(@Nonnull PoiEntry poi, boolean shopping) {
        return shopping && isShopPoi(poi);
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
            // Mild preference only: a nearby inn hearth must still beat a distant restaurant on distance.
            s *= 1.35f;
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
     *     low, break POIs are allowed town-wide. Outside {@code work}, work stations are never claimed (homeless
     *     roosting at a workplace can still use sleep/fun/sit on that plot).
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
        return pickBest(
            candidates,
            needs,
            binding,
            cellOccupancy,
            npcX,
            npcZ,
            scheduleLocation,
            townHasRestaurant,
            fillingHungerSession,
            true,
            null
        );
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
        boolean fillingHungerSession,
        boolean daytime
    ) {
        return pickBest(
            candidates,
            needs,
            binding,
            cellOccupancy,
            npcX,
            npcZ,
            scheduleLocation,
            townHasRestaurant,
            fillingHungerSession,
            daytime,
            null
        );
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
        boolean fillingHungerSession,
        boolean daytime,
        @Nullable UUID lastUsedPoiId
    ) {
        return pickBest(
            candidates,
            needs,
            binding,
            cellOccupancy,
            npcX,
            npcZ,
            scheduleLocation,
            townHasRestaurant,
            fillingHungerSession,
            false,
            false,
            daytime,
            lastUsedPoiId,
            null
        );
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
        boolean fillingHungerSession,
        boolean fillingEnergySession,
        boolean fillingFunSession,
        boolean daytime,
        @Nullable UUID lastUsedPoiId
    ) {
        return pickBest(
            candidates,
            needs,
            binding,
            cellOccupancy,
            npcX,
            npcZ,
            scheduleLocation,
            townHasRestaurant,
            fillingHungerSession,
            fillingEnergySession,
            fillingFunSession,
            daytime,
            lastUsedPoiId,
            null
        );
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
        boolean fillingHungerSession,
        boolean fillingEnergySession,
        boolean fillingFunSession,
        boolean daytime,
        @Nullable UUID lastUsedPoiId,
        @Nullable Set<UUID> breakPlotAllowlist
    ) {
        UUID preferredPlot = binding.getPreferredPlotId();
        boolean atWork = isWorkScheduleSegment(scheduleLocation);
        boolean atShop = isShopScheduleSegment(scheduleLocation);
        UrgentNeedKind breakMode =
            pickBestBreakMode(needs, fillingHungerSession, fillingEnergySession, fillingFunSession, daytime);
        boolean breakOverride = atWork && needsBreakForSchedule(needs, daytime);
        boolean workOnlyShift = preferredPlot != null && atWork && !breakOverride && breakMode == null;
        boolean shopBrowseShift = atShop && breakMode == null;
        boolean allowTownWide = breakMode != null || (atWork && breakOverride);
        PoiEntry best = null;
        float bestScore = 0f;
        int bestUsed = Integer.MAX_VALUE;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (PoiEntry e : candidates) {
            if (e.getTags().contains(AetherhavenConstants.POI_TAG_QUEST_BOARD)) {
                continue;
            }
            if (isFestivalPoi(e)) {
                continue;
            }
            // Visitors may live at the inn plot but must not use workplace desks.
            if (TownVillagerBinding.isVisitorKind(binding.getKind()) && isWorkPoi(e)) {
                continue;
            }
            // Job stations only during the scheduled work segment (homeless roosting at the workplace must idle).
            if (!atWork && breakMode == null && isWorkPoi(e)) {
                continue;
            }
            // Night is for sleep: do not leave for non-feast eat spots after dark.
            if (!daytime
                && isEatPoi(e)
                && !e.getTags().contains(AetherhavenConstants.POI_TAG_FEAST)) {
                continue;
            }
            if (breakMode == UrgentNeedKind.HUNGER) {
                if (!isEatPoi(e)) {
                    continue;
                }
            } else if (breakMode == UrgentNeedKind.ENERGY) {
                if (!isRestPoi(e)) {
                    continue;
                }
                if (breakPlotAllowlist != null
                    && (e.getPlotId() == null || !breakPlotAllowlist.contains(e.getPlotId()))) {
                    continue;
                }
            } else if (breakMode == UrgentNeedKind.FUN) {
                if (!isFunPoi(e)) {
                    continue;
                }
                if (breakPlotAllowlist != null
                    && (e.getPlotId() == null || !breakPlotAllowlist.contains(e.getPlotId()))) {
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
            if (lastUsedPoiId != null && lastUsedPoiId.equals(e.getId())) {
                sc -= LAST_USED_POI_PENALTY;
            }
            if (workOnlyShift && isWorkPoi(e)) {
                sc += ThreadLocalRandom.current().nextFloat() * WORK_POI_JITTER;
            }
            double distSq = distSqToPoi(e, npcX, npcZ);
            // Hunger trips: prefer a nearby inn over a far restaurant so meals actually complete.
            if (breakMode == UrgentNeedKind.HUNGER && !Double.isNaN(distSq)) {
                sc -= (float) (Math.sqrt(distSq) * 0.25);
            }
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
            // Equal score: multi-capacity POIs prefer packing (join a half-full bench) so capacity > 1 is usable;
            // single-slot POIs prefer spreading to empty cells.
            boolean packMulti =
                cap > 1 || Math.max(1, best.getCapacity()) > 1;
            if (packMulti) {
                if (used > bestUsed) {
                    best = e;
                    bestScore = sc;
                    bestUsed = used;
                    bestDistSq = distSq;
                    continue;
                }
                if (used < bestUsed) {
                    continue;
                }
            } else {
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
        // Urgent need breaks always allow a scored POI even without preferredPlot.
        if (preferredPlot == null && !shopBrowseShift && breakMode == null && bestScore < 8f) {
            return null;
        }
        return best;
    }

    /**
     * Which need break {@link #pickBest} should pursue: active fill sessions win, otherwise the lowest meter among
     * satisfiable break thresholds (same priority as {@link #resolveMostUrgentSatisfiableNeed}).
     */
    @Nullable
    private static UrgentNeedKind pickBestBreakMode(
        @Nonnull VillagerNeeds needs,
        boolean fillingHungerSession,
        boolean fillingEnergySession,
        boolean fillingFunSession,
        boolean daytime
    ) {
        if (fillingHungerSession && needsHungerBreak(needs, true, daytime)) {
            return UrgentNeedKind.HUNGER;
        }
        if (fillingEnergySession && needsEnergyBreak(needs, true)) {
            return UrgentNeedKind.ENERGY;
        }
        if (fillingFunSession && needsFunBreak(needs, true, daytime)) {
            return UrgentNeedKind.FUN;
        }
        UrgentNeedKind best = null;
        float bestMeter = Float.MAX_VALUE;
        if (daytime && needsHungerBreak(needs, false, daytime) && needs.getHunger() < bestMeter) {
            best = UrgentNeedKind.HUNGER;
            bestMeter = needs.getHunger();
        }
        if (needsEnergyBreak(needs, false) && needs.getEnergy() < bestMeter) {
            best = UrgentNeedKind.ENERGY;
            bestMeter = needs.getEnergy();
        }
        if (daytime && needsFunBreak(needs, false, daytime) && needs.getFun() < bestMeter) {
            best = UrgentNeedKind.FUN;
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

    private static boolean hasAvailableCapacity(@Nonnull PoiEntry e, @Nonnull Map<String, Integer> cellOccupancy) {
        return PoiOccupancy.hasAvailableCapacity(cellOccupancy, e);
    }

    @Nullable
    private static PoiEntry pickNearestRestPoiOnPlots(
        @Nonnull List<PoiEntry> candidates,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ,
        @Nonnull List<UUID> plotIds
    ) {
        Set<UUID> allowed = new HashSet<>(plotIds);
        List<PoiEntry> filtered = new ArrayList<>();
        for (PoiEntry e : candidates) {
            if (!isRestPoi(e) || e.getPlotId() == null || !allowed.contains(e.getPlotId())) {
                continue;
            }
            if (!hasAvailableCapacity(e, cellOccupancy)) {
                continue;
            }
            filtered.add(e);
        }
        return pickNearestClaimablePoi(filtered, cellOccupancy, npcX, npcZ);
    }

    @Nullable
    private static PoiEntry pickNearestClaimablePoi(
        @Nonnull List<PoiEntry> pois,
        @Nonnull Map<String, Integer> cellOccupancy,
        double npcX,
        double npcZ
    ) {
        PoiEntry best = null;
        int bestUsed = Integer.MAX_VALUE;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (PoiEntry e : pois) {
            if (!hasAvailableCapacity(e, cellOccupancy)) {
                continue;
            }
            int used = cellOccupancy.getOrDefault(PoiOccupancy.standCellKey(e), 0);
            double distSq = distSqToPoi(e, npcX, npcZ);
            if (best == null || used < bestUsed || (!Double.isNaN(distSq) && distSq < bestDistSq - 1e-9)) {
                best = e;
                bestUsed = used;
                bestDistSq = distSq;
            } else if (used == bestUsed && !Double.isNaN(distSq) && distSq < bestDistSq - 1e-9) {
                best = e;
                bestDistSq = distSq;
            }
        }
        return best;
    }

    private static double distSqWorld(double npcX, double npcZ, double px, double pz) {
        if (Double.isNaN(npcX) || Double.isNaN(npcZ)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = px - npcX;
        double dz = pz - npcZ;
        return dx * dx + dz * dz;
    }
}
