package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Merges catalog-flagged plot blueprints into the regular balloon loot table. */
public final class FloatingGiftBlueprintLoot {
    private FloatingGiftBlueprintLoot() {}

    @Nonnull
    public static List<String> listBlueprintConstructionIds(@Nonnull ConstructionCatalog catalog) {
        List<String> ids = new ArrayList<>();
        for (ConstructionDefinition def : catalog.list()) {
            if (def.isFloatingGiftBlueprint() && def.getId() != null && !def.getId().isBlank()) {
                ids.add(def.getId().trim());
            }
        }
        ids.sort(Comparator.naturalOrder());
        return List.copyOf(ids);
    }

    @Nonnull
    public static FloatingGiftLootTable mergeIntoRegularTable(
        @Nonnull FloatingGiftLootTable manual,
        @Nonnull ConstructionCatalog catalog,
        int blueprintWeight
    ) {
        return mergeIntoRegularTable(manual, listBlueprintConstructionIds(catalog), blueprintWeight);
    }

    @Nonnull
    public static FloatingGiftLootTable mergeIntoRegularTable(
        @Nonnull FloatingGiftLootTable manual,
        @Nonnull List<String> blueprintConstructionIds,
        int blueprintWeight
    ) {
        int weight = Math.max(0, blueprintWeight);
        List<FloatingGiftLootTable.Entry> merged = new ArrayList<>();
        for (FloatingGiftLootTable.Entry e : manual.entries()) {
            if (!isPlotBlueprintEntry(e)) {
                merged.add(e);
            }
        }
        for (String constructionId : blueprintConstructionIds) {
            if (weight > 0 && constructionId != null && !constructionId.isBlank()) {
                merged.add(
                    new FloatingGiftLootTable.Entry(
                        AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE,
                        constructionId.trim(),
                        weight
                    )
                );
            }
        }
        return FloatingGiftLootTable.of(merged);
    }

    /**
     * Rolls one stack from the regular balloon table, excluding plot blueprints the owner already unlocked. When every
     * blueprint is owned, falls back to a random owned blueprint.
     */
    @Nullable
    public static ItemStack rollRegularLoot(
        @Nonnull FloatingGiftLootTable table,
        @Nullable Ref<EntityStore> ownerRef,
        @Nullable Store<EntityStore> ownerStore,
        @Nonnull ThreadLocalRandom rnd
    ) {
        if (ownerRef == null || ownerStore == null || !ownerRef.isValid()) {
            return table.rollStack(rnd);
        }
        return rollRegularLootExcludingUnlocked(
            table,
            constructionId -> PlotTokenUnlockService.isUnlocked(ownerRef, ownerStore, constructionId),
            rnd
        );
    }

    @Nullable
    static ItemStack rollRegularLootExcludingUnlocked(
        @Nonnull FloatingGiftLootTable table,
        @Nonnull Set<String> unlockedConstructionIds,
        @Nonnull ThreadLocalRandom rnd
    ) {
        return rollRegularLootExcludingUnlocked(
            table,
            constructionId -> unlockedConstructionIds.contains(normalizeConstructionId(constructionId)),
            rnd
        );
    }

    @Nullable
    static ItemStack rollRegularLootExcludingUnlocked(
        @Nonnull FloatingGiftLootTable table,
        @Nonnull Predicate<String> isConstructionUnlocked,
        @Nonnull ThreadLocalRandom rnd
    ) {
        List<FloatingGiftLootTable.Entry> pool = buildFilteredRollPool(table, isConstructionUnlocked);
        if (pool.isEmpty()) {
            return null;
        }
        return FloatingGiftLootTable.of(pool).rollStack(rnd);
    }

    @Nonnull
    static List<FloatingGiftLootTable.Entry> buildFilteredRollPool(
        @Nonnull FloatingGiftLootTable table,
        @Nonnull Predicate<String> isConstructionUnlocked
    ) {
        List<FloatingGiftLootTable.Entry> blueprints = new ArrayList<>();
        List<FloatingGiftLootTable.Entry> other = new ArrayList<>();
        for (FloatingGiftLootTable.Entry e : table.entries()) {
            if (isPlotBlueprintEntry(e)) {
                blueprints.add(e);
            } else {
                other.add(e);
            }
        }
        List<FloatingGiftLootTable.Entry> candidates = new ArrayList<>();
        for (FloatingGiftLootTable.Entry e : blueprints) {
            String constructionId = e.constructionId();
            if (constructionId != null && !isConstructionUnlocked.test(constructionId)) {
                candidates.add(e);
            }
        }
        if (candidates.isEmpty() && !blueprints.isEmpty()) {
            candidates = blueprints;
        }
        List<FloatingGiftLootTable.Entry> pool = new ArrayList<>(other);
        pool.addAll(candidates);
        return pool;
    }

    static boolean isPlotBlueprintEntry(@Nonnull FloatingGiftLootTable.Entry e) {
        return AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE.equals(e.itemId()) && e.constructionId() != null;
    }

    @Nonnull
    static String normalizeConstructionId(@Nonnull String constructionId) {
        return constructionId.trim().toLowerCase(Locale.ROOT);
    }
}
