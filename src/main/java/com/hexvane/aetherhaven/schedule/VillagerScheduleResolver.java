package com.hexvane.aetherhaven.schedule;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.autonomy.BuildingTagScoring;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityCatalog;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Picks active segment from weekly transitions and maps symbolic locations to plot UUIDs. */
public final class VillagerScheduleResolver {
    public static final String LOC_HOME = "home";
    public static final String LOC_WORK = "work";
    public static final String LOC_INN = "inn";
    public static final String LOC_PARK = "park";
    /** Visits a completed {@link AetherhavenConstants#CONSTRUCTION_PLOT_GAIA_ALTAR} (skipped if not built). */
    public static final String LOC_GAIA_ALTAR = "gaia_altar";
    /** Browses SHOP POIs on any complete plot tagged {@code shop}; purchases only at player shop spots. */
    public static final String LOC_SHOP = "shop";

    private VillagerScheduleResolver() {}

    /**
     * Minute offset from Monday 00:00 within a single week (0 .. 7*24*60-1).
     */
    public static int weekMinuteFromGameTime(@Nonnull LocalDateTime gameTime) {
        DayOfWeek dow = gameTime.getDayOfWeek();
        int dayFromMonday = (dow.getValue() + 6) % 7;
        int mod = gameTime.getHour() * 60 + gameTime.getMinute();
        return dayFromMonday * 24 * 60 + mod;
    }

    /**
     * Symbolic location for the current game time, or null if there are no transitions.
     */
    @Nullable
    public static String activeLocationSymbol(@Nonnull VillagerScheduleDefinition def, @Nonnull LocalDateTime gameTime) {
        List<VillagerScheduleTransition> raw = def.getTransitions();
        if (raw.isEmpty()) {
            return null;
        }
        List<Segment> segments = new ArrayList<>();
        for (VillagerScheduleTransition t : raw) {
            int wm = weekMinuteFromTransition(t);
            String loc = normalizeLocation(t.getLocation());
            if (loc == null || loc.isEmpty()) {
                continue;
            }
            segments.add(new Segment(wm, loc));
        }
        if (segments.isEmpty()) {
            return null;
        }
        segments.sort(Comparator.comparingInt(s -> s.weekMinute));
        int now = weekMinuteFromGameTime(gameTime);
        Segment chosen = null;
        for (Segment s : segments) {
            if (s.weekMinute <= now) {
                chosen = s;
            }
        }
        if (chosen == null) {
            chosen = segments.get(segments.size() - 1);
        }
        return chosen.location;
    }

    private static int weekMinuteFromTransition(@Nonnull VillagerScheduleTransition t) {
        DayOfWeek dow = parseDayOfWeek(t.getDayOfWeek());
        int dayFromMonday = (dow.getValue() + 6) % 7;
        int h = Math.max(0, Math.min(23, t.getHour()));
        int m = Math.max(0, Math.min(59, t.getMinute()));
        return dayFromMonday * 24 * 60 + h * 60 + m;
    }

    @Nonnull
    private static DayOfWeek parseDayOfWeek(@Nullable Object raw) {
        if (raw == null) {
            return DayOfWeek.MONDAY;
        }
        if (raw instanceof Number n) {
            int v = n.intValue();
            if (v >= 1 && v <= 7) {
                return DayOfWeek.of(v);
            }
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return DayOfWeek.MONDAY;
        }
        try {
            return DayOfWeek.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            try {
                int v = Integer.parseInt(s);
                if (v >= 1 && v <= 7) {
                    return DayOfWeek.of(v);
                }
            } catch (NumberFormatException ignored) {
            }
            return DayOfWeek.MONDAY;
        }
    }

    @Nullable
    private static String normalizeLocation(@Nullable String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        return location.trim().toLowerCase();
    }

    /**
     * Explains why {@link #resolvePlot} returned no plot (for server logs). Not for in-game chat.
     */
    @Nonnull
    public static String describeSchedulePlotUnresolvedReason(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nonnull String locationSymbol,
        @Nullable VillagerDefinition villagerDef,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        String loc = normalizeLocation(locationSymbol);
        if (loc == null) {
            return "empty or invalid location symbol";
        }
        return switch (loc) {
            case LOC_HOME -> describeHomeUnresolved(town, binding, entityUuid, villagerDef, constructionCatalog);
            case LOC_WORK -> describeWorkUnresolved(town, binding, villagerDef, constructionCatalog);
            case LOC_INN ->
                describeSharedUnresolved(town, sharedConstructionId(loc, villagerDef), constructionCatalog);
            case LOC_PARK ->
                describeSharedUnresolved(town, sharedConstructionId(loc, villagerDef), constructionCatalog);
            case LOC_GAIA_ALTAR ->
                describeSharedUnresolved(town, sharedConstructionId(loc, villagerDef), constructionCatalog);
            case LOC_SHOP -> "no complete shop tagged building in town";
            default -> "unsupported location '" + loc + "' (not home/work/inn/park/gaia_altar/shop)";
        };
    }

    @Nonnull
    public static VillagerScheduleResolveOutcome resolvePlot(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nonnull String locationSymbol,
        @Nullable VillagerDefinition villagerDef,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nullable VillagerScheduleTickState tickState,
        boolean timeJump
    ) {
        return resolvePlot(
            town,
            binding,
            entityUuid,
            locationSymbol,
            villagerDef,
            constructionCatalog,
            tickState,
            timeJump,
            null,
            null
        );
    }

    @Nonnull
    public static VillagerScheduleResolveOutcome resolvePlot(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nonnull String locationSymbol,
        @Nullable VillagerDefinition villagerDef,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nullable VillagerScheduleTickState tickState,
        boolean timeJump,
        @Nullable TownsfolkPersonalityCatalog personalityCatalog,
        @Nullable List<String> personalityIds
    ) {
        String loc = normalizeLocation(locationSymbol);
        if (loc == null) {
            return VillagerScheduleResolveOutcome.skip();
        }
        return switch (loc) {
            case LOC_HOME -> resolveHome(town, binding, entityUuid, villagerDef, constructionCatalog);
            case LOC_WORK -> resolveWork(town, binding, villagerDef, constructionCatalog);
            case LOC_INN ->
                resolveSharedBuilding(
                    town,
                    constructionCatalog,
                    sharedConstructionId(loc, villagerDef),
                    loc,
                    tickState,
                    timeJump,
                    personalityCatalog,
                    personalityIds
                );
            case LOC_PARK ->
                resolveSharedBuilding(
                    town,
                    constructionCatalog,
                    sharedConstructionId(loc, villagerDef),
                    loc,
                    tickState,
                    timeJump,
                    personalityCatalog,
                    personalityIds
                );
            case LOC_GAIA_ALTAR ->
                resolveSharedBuilding(
                    town,
                    constructionCatalog,
                    sharedConstructionId(loc, villagerDef),
                    loc,
                    tickState,
                    timeJump,
                    personalityCatalog,
                    personalityIds
                );
            case LOC_SHOP -> resolveShopBrowsing(town, constructionCatalog);
            default -> VillagerScheduleResolveOutcome.skip();
        };
    }

    @Nonnull
    private static String sharedConstructionId(@Nonnull String normalizedLoc, @Nullable VillagerDefinition def) {
        if (def != null) {
            String fromDef = def.sharedConstructionIdForLocationSymbol(normalizedLoc);
            if (fromDef != null) {
                return fromDef;
            }
        }
        return switch (normalizedLoc) {
            case LOC_INN -> AetherhavenConstants.CONSTRUCTION_PLOT_INN;
            case LOC_PARK -> AetherhavenConstants.CONSTRUCTION_PLOT_PARK;
            case LOC_GAIA_ALTAR -> AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR;
            default -> AetherhavenConstants.CONSTRUCTION_PLOT_INN;
        };
    }

    @Nonnull
    private static String describeHomeUnresolved(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nullable VillagerDefinition def,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        for (PlotInstance p : town.getPlotInstances()) {
            if (p.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!constructionCatalog.matchesGameplayConstruction(
                p.getConstructionId(),
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE
            )) {
                continue;
            }
            if (p.hasHomeResident(entityUuid)) {
                return "home: unexpected (house plot exists)";
            }
        }
        UUID job = binding.getJobPlotId();
        if (job != null) {
            PlotInstance pi = town.findPlotById(job);
            if (pi != null && pi.getState() == PlotInstanceState.COMPLETE) {
                return "home: unexpected (no house but job plot " + job + " could roost)";
            }
        }
        UUID inferred = inferJobPlotFromTown(town, binding.getKind(), def, constructionCatalog);
        if (inferred != null) {
            return "home: unexpected (no house but workplace " + inferred + " could roost)";
        }
        return "home: no COMPLETE house and no COMPLETE workplace to roost at for uuid=" + entityUuid;
    }

    @Nonnull
    private static String describeWorkUnresolved(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nullable VillagerDefinition def,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        UUID job = binding.getJobPlotId();
        if (job != null) {
            PlotInstance pi = town.findPlotById(job);
            if (pi == null) {
                return "work: JobPlotId " + job + " not found in town plot list (stale save?)";
            }
            if (pi.getState() != PlotInstanceState.COMPLETE) {
                return "work: job plot " + job + " state is " + pi.getState() + " (need COMPLETE)";
            }
            String expected = workConstructionId(binding, def);
            if (expected != null && !isValidWorkPlot(pi, expected, constructionCatalog)) {
                String actual = constructionCatalog.resolveGameplayConstructionId(pi.getConstructionId());
                return "work: job plot " + job + " construction resolves to " + actual + " (expected " + expected + ")";
            }
            return "work: JobPlotId resolves but resolvePlot failed elsewhere (report as bug)";
        }
        UUID inferred = inferJobPlotFromTown(town, binding.getKind(), def, constructionCatalog);
        if (inferred == null) {
            String c = workConstructionId(binding, def);
            if (c == null) {
                return "work: cannot infer job plot for binding kind=\"" + binding.getKind() + "\"";
            }
            if (!townHasAnyPlotWithGameplayConstruction(town, constructionCatalog, c)) {
                return "work: no plot with construction " + c + " in town";
            }
            return "work: no COMPLETE plot for construction " + c + " (building not finished?)";
        }
        return "work: unexpected (infer returned " + inferred + ")";
    }

    private static boolean townHasAnyPlotWithGameplayConstruction(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nonnull String gameplayConstructionId
    ) {
        String g = gameplayConstructionId.trim();
        if (g.isEmpty()) {
            return false;
        }
        for (PlotInstance p : town.getPlotInstances()) {
            if (constructionCatalog.matchesGameplayConstruction(p.getConstructionId(), g)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String describeSharedUnresolved(
        @Nonnull TownRecord town,
        @Nonnull String gameplayConstructionId,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (town.findCompletePlotWithConstruction(constructionCatalog, gameplayConstructionId) == null) {
            if (!townHasAnyPlotWithGameplayConstruction(town, constructionCatalog, gameplayConstructionId)) {
                return "shared: no plot for construction " + gameplayConstructionId;
            }
            return "shared: plot exists for " + gameplayConstructionId + " but not COMPLETE";
        }
        return "shared: unexpected (complete plot exists)";
    }

    @Nullable
    private static String workConstructionId(@Nonnull TownVillagerBinding binding, @Nullable VillagerDefinition def) {
        if (def != null) {
            String w = def.getWorkConstructionId();
            if (w != null) {
                return w;
            }
        }
        return constructionIdForKind(binding.getKind());
    }

    @Nullable
    private static String constructionIdForKind(@Nonnull String kind) {
        return switch (kind) {
            case TownVillagerBinding.KIND_FARMER -> AetherhavenConstants.CONSTRUCTION_PLOT_FARM;
            case TownVillagerBinding.KIND_MERCHANT -> AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL;
            case TownVillagerBinding.KIND_CHEF -> AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT;
            case TownVillagerBinding.KIND_BLACKSMITH -> AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP;
            case TownVillagerBinding.KIND_PRIESTESS -> AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR;
            case TownVillagerBinding.KIND_MINER -> AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT;
            case TownVillagerBinding.KIND_LOGGER -> AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL;
            case TownVillagerBinding.KIND_RANCHER -> AetherhavenConstants.CONSTRUCTION_PLOT_BARN;
            case TownVillagerBinding.KIND_INNKEEPER -> AetherhavenConstants.CONSTRUCTION_PLOT_INN;
            case TownVillagerBinding.KIND_ELDER -> AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL;
            case TownVillagerBinding.KIND_GUILD_MASTER, TownVillagerBinding.KIND_BARD ->
                AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL;
            case TownVillagerBinding.KIND_BUILDER -> AetherhavenConstants.CONSTRUCTION_PLOT_BUILDERS_HUT;
            case TownVillagerBinding.KIND_FLORIST -> AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP;
            case TownVillagerBinding.KIND_PYROTECHNIC -> AetherhavenConstants.CONSTRUCTION_PLOT_BOMB_SHOP;
            case TownVillagerBinding.KIND_CRYSTAL_KEEPER -> AetherhavenConstants.CONSTRUCTION_PLOT_CRYSTAL_KEEPERS_SHOP;
            default -> null;
        };
    }

    /** True when the plot is COMPLETE and resolves to the villager's expected gameplay workplace id. */
    public static boolean isValidWorkPlot(
        @Nonnull PlotInstance plot,
        @Nonnull String expectedGameplayConstructionId,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (plot.getState() != PlotInstanceState.COMPLETE) {
            return false;
        }
        String expected = expectedGameplayConstructionId.trim();
        if (expected.isEmpty()) {
            return false;
        }
        return constructionCatalog.matchesGameplayConstruction(plot.getConstructionId(), expected);
    }

    /**
     * Prefer the assigned house. If none exists yet, roost at the workplace plot so the schedule segment still
     * advances to {@code home} (they must not keep claiming WORK spots overnight — see {@code PoiScoring}).
     */
    @Nonnull
    private static VillagerScheduleResolveOutcome resolveHome(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nullable VillagerDefinition def,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        for (PlotInstance p : town.getPlotInstances()) {
            if (p.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!constructionCatalog.matchesGameplayConstruction(
                p.getConstructionId(),
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE
            )) {
                continue;
            }
            if (p.hasHomeResident(entityUuid)) {
                return new VillagerScheduleResolveOutcome(p.getPlotId(), null);
            }
        }
        UUID job = binding.getJobPlotId();
        if (job != null) {
            PlotInstance pi = town.findPlotById(job);
            if (pi != null && pi.getState() == PlotInstanceState.COMPLETE) {
                return new VillagerScheduleResolveOutcome(job, null);
            }
        }
        UUID inferred = inferJobPlotFromTown(town, binding.getKind(), def, constructionCatalog);
        if (inferred != null) {
            return new VillagerScheduleResolveOutcome(inferred, inferred);
        }
        return VillagerScheduleResolveOutcome.skip();
    }

    @Nonnull
    private static VillagerScheduleResolveOutcome resolveWork(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nullable VillagerDefinition def,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        String expectedWork = workConstructionId(binding, def);
        if (expectedWork == null) {
            return VillagerScheduleResolveOutcome.skip();
        }
        UUID job = binding.getJobPlotId();
        if (job != null) {
            PlotInstance pi = town.findPlotById(job);
            if (pi != null && isValidWorkPlot(pi, expectedWork, constructionCatalog)) {
                return new VillagerScheduleResolveOutcome(job, null);
            }
        }
        UUID inferred = inferJobPlotFromTown(town, binding.getKind(), def, constructionCatalog);
        if (inferred == null) {
            return VillagerScheduleResolveOutcome.skip();
        }
        return new VillagerScheduleResolveOutcome(inferred, inferred);
    }

    @Nullable
    private static UUID inferJobPlotFromTown(
        @Nonnull TownRecord town,
        @Nonnull String kind,
        @Nullable VillagerDefinition def,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (def != null) {
            String w = def.getWorkConstructionId();
            if (w != null) {
                return plotIdIfComplete(town, w, constructionCatalog);
            }
        }
        return switch (kind) {
            case TownVillagerBinding.KIND_FARMER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_FARM, constructionCatalog);
            case TownVillagerBinding.KIND_MERCHANT ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL, constructionCatalog);
            case TownVillagerBinding.KIND_CHEF ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT, constructionCatalog);
            case TownVillagerBinding.KIND_BLACKSMITH ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP, constructionCatalog);
            case TownVillagerBinding.KIND_PRIESTESS ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR, constructionCatalog);
            case TownVillagerBinding.KIND_MINER ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT, constructionCatalog);
            case TownVillagerBinding.KIND_LOGGER ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL, constructionCatalog);
            case TownVillagerBinding.KIND_RANCHER ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_BARN, constructionCatalog);
            case TownVillagerBinding.KIND_INNKEEPER ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_INN, constructionCatalog);
            case TownVillagerBinding.KIND_ELDER ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL, constructionCatalog);
            case TownVillagerBinding.KIND_GUILD_MASTER, TownVillagerBinding.KIND_BARD ->
                plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL, constructionCatalog);
            default -> null;
        };
    }

    @Nullable
    private static UUID plotIdIfComplete(
        @Nonnull TownRecord town,
        @Nonnull String gameplayConstructionId,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        PlotInstance p = town.findCompletePlotWithConstruction(constructionCatalog, gameplayConstructionId);
        return p != null ? p.getPlotId() : null;
    }

    @Nonnull
    private static VillagerScheduleResolveOutcome resolveSharedBuilding(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nonnull String gameplayConstructionId,
        @Nonnull String normalizedScheduleSegment,
        @Nullable VillagerScheduleTickState tickState,
        boolean timeJump,
        @Nullable TownsfolkPersonalityCatalog personalityCatalog,
        @Nullable List<String> personalityIds
    ) {
        String g = gameplayConstructionId.trim();
        if (g.isEmpty()) {
            return VillagerScheduleResolveOutcome.skip();
        }
        List<PlotInstance> complete =
            town.listCompletePlotsWithGameplayConstruction(constructionCatalog, g);
        if (complete.isEmpty()) {
            return VillagerScheduleResolveOutcome.skip();
        }
        if (!constructionCatalog.isScheduleSharedUtilityGameplay(g)) {
            return new VillagerScheduleResolveOutcome(complete.get(0).getPlotId(), null);
        }
        UUID picked = null;
        if (!timeJump && tickState != null) {
            if (normalizedScheduleSegment.equals(tickState.getScheduleUtilityPickSegment())
                && g.equals(tickState.getScheduleUtilityPickGameplayConstructionId())) {
                String pid = tickState.getScheduleUtilityPickPlotId();
                if (pid != null && !pid.isBlank()) {
                    try {
                        UUID prev = UUID.fromString(pid.trim());
                        for (PlotInstance pi : complete) {
                            if (prev.equals(pi.getPlotId())) {
                                picked = prev;
                                break;
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        if (picked == null) {
            picked = pickSharedPlot(complete, constructionCatalog, personalityCatalog, personalityIds);
        }
        return new VillagerScheduleResolveOutcome(picked, null, g, normalizedScheduleSegment, picked, false);
    }

    @Nonnull
    private static VillagerScheduleResolveOutcome resolveShopBrowsing(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (!townHasCompleteShopBuilding(town, constructionCatalog)) {
            return VillagerScheduleResolveOutcome.skip();
        }
        return VillagerScheduleResolveOutcome.browseTownWide();
    }

    private static boolean townHasCompleteShopBuilding(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        for (PlotInstance p : town.getPlotInstances()) {
            if (p.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            ConstructionDefinition def = constructionCatalog.get(p.getConstructionId());
            if (def != null && def.getBuildingTags().contains("shop")) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static UUID pickSharedPlot(
        @Nonnull List<PlotInstance> complete,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nullable TownsfolkPersonalityCatalog personalityCatalog,
        @Nullable List<String> personalityIds
    ) {
        if (complete.size() == 1) {
            return complete.get(0).getPlotId();
        }
        float total = 0f;
        float[] weights = new float[complete.size()];
        for (int i = 0; i < complete.size(); i++) {
            PlotInstance pi = complete.get(i);
            ConstructionDefinition def = constructionCatalog.get(pi.getConstructionId());
            float w = 1f;
            if (def != null) {
                w = BuildingTagScoring.multiplier(def, personalityCatalog, personalityIds);
            }
            weights[i] = w;
            total += w;
        }
        if (total <= 0f) {
            return complete.get(ThreadLocalRandom.current().nextInt(complete.size())).getPlotId();
        }
        float roll = ThreadLocalRandom.current().nextFloat() * total;
        float acc = 0f;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (roll <= acc) {
                return complete.get(i).getPlotId();
            }
        }
        return complete.get(complete.size() - 1).getPlotId();
    }

    private record Segment(int weekMinute, @Nonnull String location) {}
}
