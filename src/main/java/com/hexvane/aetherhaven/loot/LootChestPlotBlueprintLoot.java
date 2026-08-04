package com.hexvane.aetherhaven.loot;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockPageMetadata;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockService;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class LootChestPlotBlueprintLoot {
    private LootChestPlotBlueprintLoot() {}

    @Nonnull
    public static List<String> listEligibleConstructionIds(@Nonnull ConstructionCatalog catalog) {
        List<String> ids = new ObjectArrayList<>();
        for (String id : catalog.ids()) {
            ConstructionDefinition def = catalog.get(id);
            if (def == null || id == null || id.isBlank()) {
                continue;
            }
            if (PlotTokenUnlockService.requiresUnlock(def)) {
                ids.add(id.trim());
            }
        }
        ids.sort(Comparator.naturalOrder());
        return List.copyOf(ids);
    }

    @Nullable
    public static ItemStack roll(@Nonnull ConstructionCatalog catalog, @Nonnull ThreadLocalRandom rnd) {
        if (listEligibleConstructionIds(catalog).isEmpty()) {
            return null;
        }
        return PlotTokenUnlockPageMetadata.createGenericStack();
    }
}
