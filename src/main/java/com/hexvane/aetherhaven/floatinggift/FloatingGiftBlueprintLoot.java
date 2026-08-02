package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Merges a generic plot blueprint entry into the regular balloon loot table. */
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
        if (weight > 0 && !blueprintConstructionIds.isEmpty()) {
            merged.add(new FloatingGiftLootTable.Entry(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, weight));
        }
        return FloatingGiftLootTable.of(merged);
    }

    @Nullable
    public static ItemStack rollRegularLoot(
        @Nonnull FloatingGiftLootTable table,
        @Nullable Ref<EntityStore> ownerRef,
        @Nullable Store<EntityStore> ownerStore,
        @Nonnull ThreadLocalRandom rnd
    ) {
        return table.rollStack(rnd);
    }

    static boolean isPlotBlueprintEntry(@Nonnull FloatingGiftLootTable.Entry e) {
        return AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE.equals(e.itemId());
    }
}
