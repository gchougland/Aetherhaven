package com.hexvane.aetherhaven.schedule;

import com.hexvane.aetherhaven.AetherhavenConstants;
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
     * Resolves symbolic location to a plot UUID. Skipped segments return {@link VillagerScheduleResolveOutcome#skip()}.
     */
    /**
     * Explains why {@link #resolvePlot} returned no plot (for server logs). Not for in-game chat.
     */
    @Nonnull
    public static String describeSchedulePlotUnresolvedReason(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nonnull String locationSymbol
    ) {
        return describeSchedulePlotUnresolvedReason(town, binding, entityUuid, locationSymbol, null);
    }

    @Nonnull
    public static String describeSchedulePlotUnresolvedReason(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nonnull String locationSymbol,
        @Nullable VillagerDefinition villagerDef
    ) {
        String loc = normalizeLocation(locationSymbol);
        if (loc == null) {
            return "empty or invalid location symbol";
        }
        return switch (loc) {
            case LOC_HOME -> describeHomeUnresolved(town, entityUuid);
            case LOC_WORK -> describeWorkUnresolved(town, binding, villagerDef);
            case LOC_INN -> describeSharedUnresolved(town, sharedConstructionId(loc, villagerDef));
            case LOC_PARK -> describeSharedUnresolved(town, sharedConstructionId(loc, villagerDef));
            case LOC_GAIA_ALTAR -> describeSharedUnresolved(town, sharedConstructionId(loc, villagerDef));
            default -> "unsupported location '" + loc + "' (not home/work/inn/park/gaia_altar)";
        };
    }

    @Nonnull
    public static VillagerScheduleResolveOutcome resolvePlot(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nonnull String locationSymbol
    ) {
        return resolvePlot(town, binding, entityUuid, locationSymbol, null);
    }

    @Nonnull
    public static VillagerScheduleResolveOutcome resolvePlot(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nonnull String locationSymbol,
        @Nullable VillagerDefinition villagerDef
    ) {
        String loc = normalizeLocation(locationSymbol);
        if (loc == null) {
            return VillagerScheduleResolveOutcome.skip();
        }
        return switch (loc) {
            case LOC_HOME -> resolveHome(town, entityUuid);
            case LOC_WORK -> resolveWork(town, binding, villagerDef);
            case LOC_INN -> resolveSharedBuilding(town, sharedConstructionId(loc, villagerDef));
            case LOC_PARK -> resolveSharedBuilding(town, sharedConstructionId(loc, villagerDef));
            case LOC_GAIA_ALTAR -> resolveSharedBuilding(town, sharedConstructionId(loc, villagerDef));
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
    private static String describeHomeUnresolved(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (PlotInstance p : town.getPlotInstances()) {
            if (p.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(p.getConstructionId())) {
                continue;
            }
            UUID resident = p.getHomeResidentEntityUuid();
            if (entityUuid.equals(resident)) {
                return "home: unexpected (plot exists)";
            }
        }
        return "home: no COMPLETE house plot with homeResidentEntityUuid=" + entityUuid;
    }

    @Nonnull
    private static String describeWorkUnresolved(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nullable VillagerDefinition def
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
            return "work: JobPlotId resolves but resolvePlot failed elsewhere (report as bug)";
        }
        UUID inferred = inferJobPlotFromTown(town, binding.getKind(), def);
        if (inferred == null) {
            String c = workConstructionId(binding, def);
            if (c == null) {
                return "work: cannot infer job plot for binding kind=\"" + binding.getKind() + "\"";
            }
            if (!townHasAnyPlotWithConstruction(town, c)) {
                return "work: no plot with construction " + c + " in town";
            }
            return "work: no COMPLETE plot for construction " + c + " (building not finished?)";
        }
        return "work: unexpected (infer returned " + inferred + ")";
    }

    private static boolean townHasAnyPlotWithConstruction(@Nonnull TownRecord town, @Nonnull String constructionId) {
        for (PlotInstance p : town.getPlotInstances()) {
            if (constructionId.equals(p.getConstructionId())) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String describeSharedUnresolved(@Nonnull TownRecord town, @Nonnull String constructionId) {
        if (town.findCompletePlotWithConstruction(constructionId) == null) {
            if (!townHasAnyPlotWithConstruction(town, constructionId)) {
                return "shared: no plot for construction " + constructionId;
            }
            return "shared: plot exists for " + constructionId + " but not COMPLETE";
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
            case TownVillagerBinding.KIND_BLACKSMITH -> AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP;
            case TownVillagerBinding.KIND_PRIESTESS -> AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR;
            case TownVillagerBinding.KIND_MINER -> AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT;
            case TownVillagerBinding.KIND_LOGGER -> AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL;
            case TownVillagerBinding.KIND_RANCHER -> AetherhavenConstants.CONSTRUCTION_PLOT_BARN;
            case TownVillagerBinding.KIND_INNKEEPER -> AetherhavenConstants.CONSTRUCTION_PLOT_INN;
            case TownVillagerBinding.KIND_ELDER -> AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL;
            default -> null;
        };
    }

    @Nonnull
    private static VillagerScheduleResolveOutcome resolveHome(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (PlotInstance p : town.getPlotInstances()) {
            if (p.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(p.getConstructionId())) {
                continue;
            }
            UUID resident = p.getHomeResidentEntityUuid();
            if (entityUuid.equals(resident)) {
                return new VillagerScheduleResolveOutcome(p.getPlotId(), null);
            }
        }
        return VillagerScheduleResolveOutcome.skip();
    }

    @Nonnull
    private static VillagerScheduleResolveOutcome resolveWork(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nullable VillagerDefinition def
    ) {
        UUID job = binding.getJobPlotId();
        if (job != null) {
            PlotInstance pi = town.findPlotById(job);
            if (pi != null && pi.getState() == PlotInstanceState.COMPLETE) {
                return new VillagerScheduleResolveOutcome(job, null);
            }
        }
        UUID inferred = inferJobPlotFromTown(town, binding.getKind(), def);
        if (inferred == null) {
            return VillagerScheduleResolveOutcome.skip();
        }
        return new VillagerScheduleResolveOutcome(inferred, inferred);
    }

    @Nullable
    private static UUID inferJobPlotFromTown(
        @Nonnull TownRecord town,
        @Nonnull String kind,
        @Nullable VillagerDefinition def
    ) {
        if (def != null) {
            String w = def.getWorkConstructionId();
            if (w != null) {
                return plotIdIfComplete(town, w);
            }
        }
        return switch (kind) {
            case TownVillagerBinding.KIND_FARMER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_FARM);
            case TownVillagerBinding.KIND_MERCHANT -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL);
            case TownVillagerBinding.KIND_BLACKSMITH -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP);
            case TownVillagerBinding.KIND_PRIESTESS -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR);
            case TownVillagerBinding.KIND_MINER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT);
            case TownVillagerBinding.KIND_LOGGER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL);
            case TownVillagerBinding.KIND_RANCHER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_BARN);
            case TownVillagerBinding.KIND_INNKEEPER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_INN);
            case TownVillagerBinding.KIND_ELDER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL);
            default -> null;
        };
    }

    @Nullable
    private static UUID plotIdIfComplete(@Nonnull TownRecord town, @Nonnull String constructionId) {
        PlotInstance p = town.findCompletePlotWithConstruction(constructionId);
        return p != null ? p.getPlotId() : null;
    }

    @Nonnull
    private static VillagerScheduleResolveOutcome resolveSharedBuilding(@Nonnull TownRecord town, @Nonnull String constructionId) {
        PlotInstance p = town.findCompletePlotWithConstruction(constructionId);
        if (p == null) {
            return VillagerScheduleResolveOutcome.skip();
        }
        return new VillagerScheduleResolveOutcome(p.getPlotId(), null);
    }

    private record Segment(int weekMinute, @Nonnull String location) {}
}
