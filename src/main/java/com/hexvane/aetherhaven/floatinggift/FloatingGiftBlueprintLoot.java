package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;

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
            if (!isLegacyManualBlueprintEntry(e)) {
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

    private static boolean isLegacyManualBlueprintEntry(@Nonnull FloatingGiftLootTable.Entry e) {
        return AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE.equals(e.itemId()) && e.constructionId() != null;
    }
}
