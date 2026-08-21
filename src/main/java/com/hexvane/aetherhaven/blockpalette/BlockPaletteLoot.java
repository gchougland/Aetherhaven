package com.hexvane.aetherhaven.blockpalette;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Random block palette stacks for loot chests and floating gifts. */
public final class BlockPaletteLoot {
    private BlockPaletteLoot() {}

    @Nullable
    public static ItemStack roll(@Nonnull BlockPaletteCatalog catalog, @Nonnull ThreadLocalRandom rnd) {
        List<String> ids = catalog.ids();
        if (ids.isEmpty()) {
            return null;
        }
        String id = ids.get(rnd.nextInt(ids.size()));
        BlockPaletteDefinition def = catalog.get(id);
        if (def == null) {
            return null;
        }
        return BlockPaletteItemMetadata.createStack(def);
    }

    @Nonnull
    public static List<ItemStack> rollUnique(
        @Nonnull BlockPaletteCatalog catalog,
        int count,
        @Nonnull ThreadLocalRandom rnd
    ) {
        if (count <= 0) {
            return List.of();
        }
        List<String> pool = new ArrayList<>(catalog.ids());
        if (pool.isEmpty()) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            int idx = rnd.nextInt(pool.size());
            String id = pool.remove(idx);
            BlockPaletteDefinition def = catalog.get(id);
            if (def != null) {
                out.add(BlockPaletteItemMetadata.createStack(def));
            }
        }
        return List.copyOf(out);
    }
}
