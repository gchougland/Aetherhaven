package com.hexvane.aetherhaven.schedule;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
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
    @Nonnull
    public static VillagerScheduleResolveOutcome resolvePlot(
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nonnull UUID entityUuid,
        @Nonnull String locationSymbol
    ) {
        String loc = normalizeLocation(locationSymbol);
        if (loc == null) {
            return VillagerScheduleResolveOutcome.skip();
        }
        return switch (loc) {
            case LOC_HOME -> resolveHome(town, entityUuid);
            case LOC_WORK -> resolveWork(town, binding);
            case LOC_INN -> resolveSharedBuilding(town, AetherhavenConstants.CONSTRUCTION_INN_V1);
            case LOC_PARK -> resolveSharedBuilding(town, AetherhavenConstants.CONSTRUCTION_PLOT_PARK);
            default -> VillagerScheduleResolveOutcome.skip();
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
    private static VillagerScheduleResolveOutcome resolveWork(@Nonnull TownRecord town, @Nonnull TownVillagerBinding binding) {
        UUID job = binding.getJobPlotId();
        if (job != null) {
            PlotInstance pi = town.findPlotById(job);
            if (pi != null && pi.getState() == PlotInstanceState.COMPLETE) {
                return new VillagerScheduleResolveOutcome(job, null);
            }
            return VillagerScheduleResolveOutcome.skip();
        }
        UUID inferred = inferJobPlotFromTown(town, binding.getKind());
        if (inferred == null) {
            return VillagerScheduleResolveOutcome.skip();
        }
        return new VillagerScheduleResolveOutcome(inferred, inferred);
    }

    @Nullable
    private static UUID inferJobPlotFromTown(@Nonnull TownRecord town, @Nonnull String kind) {
        return switch (kind) {
            case TownVillagerBinding.KIND_FARMER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_FARM);
            case TownVillagerBinding.KIND_MERCHANT -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL);
            case TownVillagerBinding.KIND_INNKEEPER -> plotIdIfComplete(town, AetherhavenConstants.CONSTRUCTION_INN_V1);
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
