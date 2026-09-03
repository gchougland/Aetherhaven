package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime helper: pick a random jewelry item id. Trait rolling is left to {@link JewelryMetadata} so loot chests can
 * apply zone-aware rarity.
 */
public final class UnidentifiedJewelry {
    @Nullable
    private static volatile String[] cachedIds;

    private UnidentifiedJewelry() {}

    @Nonnull
    public static ItemStack rollStack(@Nonnull ThreadLocalRandom random) {
        return rollEnchantedStack(random);
    }

    /**
     * Random gem jewelry base stack (no traits yet). Callers that need rolled traits must run
     * {@link JewelryMetadata#ensureRolled} or {@link JewelryMetadata#ensureRolledForLootChest}.
     */
    @Nonnull
    public static ItemStack rollEnchantedStack(@Nonnull ThreadLocalRandom random) {
        String[] ids = allEnchantedJewelryItemIds();
        if (ids.length == 0) {
            return ItemStack.EMPTY;
        }
        String id = ids[random.nextInt(ids.length)];
        return new ItemStack(id, 1);
    }

    @Nonnull
    private static String[] allEnchantedJewelryItemIds() {
        String[] c = cachedIds;
        if (c != null && c.length > 0) {
            return c;
        }
        synchronized (UnidentifiedJewelry.class) {
            c = cachedIds;
            if (c != null && c.length > 0) {
                return c;
            }
            List<String> list = new ObjectArrayList<>();
            for (String id : Item.getAssetMap().getAssetMap().keySet()) {
                String baseId = JewelryVirtualItemRegistry.getBaseItemId(id);
                String resolveId = baseId != null ? baseId : id;
                if (JewelryPieceKind.isEnchanted(resolveId)) {
                    list.add(resolveId);
                }
            }
            c = list.toArray(new String[0]);
            // Never permanently cache an empty scan: jewelry assets may not be loaded yet at first chest inject.
            if (c.length > 0) {
                cachedIds = c;
            }
            return c;
        }
    }
}
