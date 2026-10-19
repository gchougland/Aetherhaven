package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.plot.PlotBuildingStyles;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Picks which festival JSON to paste on the square. The calendar and minigames always use the base holiday; the town
 * can choose a look with its own prefab and spots.
 */
public final class FestivalLookSelection {
    private FestivalLookSelection() {}

    /** Base holiday for {@code def}: itself, or the festival it counts as. */
    @Nullable
    public static FestivalDefinition gameplayBase(
        @Nonnull FestivalCatalog catalog,
        @Nullable FestivalDefinition def
    ) {
        if (def == null) {
            return null;
        }
        if (!def.isLook()) {
            return def;
        }
        FestivalDefinition base = catalog.get(def.getGameplayFestivalId());
        return base != null && !base.isLook() ? base : def;
    }

    /**
     * Prefab, spots, and NPCs for this town when {@code base} is running. Falls back to the base when no look is
     * selected or the look is gone.
     */
    @Nonnull
    public static FestivalDefinition layoutFor(
        @Nonnull FestivalCatalog catalog,
        @Nonnull TownRecord town,
        @Nonnull FestivalDefinition base
    ) {
        FestivalDefinition holiday = gameplayBase(catalog, base);
        if (holiday == null) {
            return base;
        }
        String lookId = town.getSelectedFestivalLookId(holiday.getId());
        if (lookId == null) {
            return holiday;
        }
        FestivalDefinition look = catalog.get(lookId);
        if (look == null || !look.isLook()) {
            return holiday;
        }
        if (!holiday.getId().equals(look.getGameplayFestivalId())) {
            return holiday;
        }
        return look;
    }

    /** The square's actual construction style, independent of the town's preference for future builds. */
    public static String squareStyle(ConstructionCatalog constructions, PlotInstance square) {
        var def = square == null ? null : constructions.get(square.getConstructionId());
        String style = def == null ? null : PlotBuildingStyles.styleIdOf(def);
        return style == null ? "core" : style;
    }

    /** A stable choice within the square's style; the base festival is always the core fallback. */
    public static FestivalDefinition layoutFor(FestivalCatalog catalog, ConstructionCatalog constructions,
                                               TownRecord town, PlotInstance square, FestivalDefinition base) {
        FestivalDefinition holiday = gameplayBase(catalog, base);
        if (holiday == null) return base;
        String style = squareStyle(constructions, square);
        var candidates = looksOf(catalog, holiday.getId()).stream()
            .filter(look -> style.equals(PlotBuildingStyles.normalize(look.getStyleId())))
            .filter(look -> !look.getPrefabPath().isBlank())
            .sorted(Comparator.comparing(FestivalDefinition::getId)).toList();
        String selected = town.getSelectedFestivalLookId(holiday.getId());
        for (var look : candidates) if (look.getId().equals(selected)) return look;
        return style.equals("core") || candidates.isEmpty() ? holiday : candidates.getFirst();
    }

    public static FestivalDefinition layoutFor(AetherhavenPlugin plugin, TownRecord town,
                                               PlotInstance square, FestivalDefinition base) {
        return layoutFor(plugin.getFestivalCatalog(), plugin.getConstructionCatalog(), town, square, base);
    }

    /** Exact layout placed for the current run; old saves retain their existing manual layout until it ends. */
    public static FestivalDefinition runningLayout(FestivalCatalog catalog, TownRecord town, FestivalDefinition base) {
        String saved = town.getActiveFestivalLayoutId();
        FestivalDefinition placed = saved == null ? null : catalog.get(saved);
        if (placed != null && placed.getGameplayFestivalId().equals(base.getGameplayFestivalId())) return placed;
        return layoutFor(catalog, town, base);
    }

    /** Layout currently pasted for a running festival, or null when none is on. */
    @Nullable
    public static FestivalDefinition activeLayout(
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord town
    ) {
        if (town == null) {
            return null;
        }
        String runningId = town.getActiveFestivalId();
        if (runningId == null) {
            return null;
        }
        FestivalCatalog catalog = plugin.getFestivalCatalog();
        FestivalDefinition running = catalog.get(runningId);
        if (running == null) {
            return null;
        }
        FestivalDefinition base = gameplayBase(catalog, running);
        return base != null ? runningLayout(catalog, town, base) : running;
    }

    /** Looks that count as {@code baseId}, in catalog order. */
    @Nonnull
    public static List<FestivalDefinition> looksOf(@Nonnull FestivalCatalog catalog, @Nonnull String baseId) {
        String id = baseId.trim();
        List<FestivalDefinition> out = new ArrayList<>();
        if (id.isEmpty()) {
            return out;
        }
        for (FestivalDefinition def : catalog.list()) {
            if (def.isLook() && id.equals(def.getGameplayFestivalId())) {
                out.add(def);
            }
        }
        return out;
    }
}
