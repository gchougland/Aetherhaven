package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime helper: pick a random jewelry item id, roll instance stats, keep {@link JewelryMetadata} unappraised
 * (the rolled bundle is still hidden until appraisal in UI).
 */
public final class UnidentifiedJewelry {
    @Nullable
    private static volatile String[] cachedIds;

    private UnidentifiedJewelry() {}

    @Nonnull
    public static ItemStack rollStack(@Nonnull ThreadLocalRandom random) {
        String[] ids = allJewelryItemIds();
        if (ids.length == 0) {
            return ItemStack.EMPTY;
        }
        String id = ids[random.nextInt(ids.length)];
        ItemStack stack = new ItemStack(id, 1);
        return JewelryMetadata.ensureRolled(stack);
    }

    @Nonnull
    private static String[] allJewelryItemIds() {
        String[] c = cachedIds;
        if (c != null) {
            return c;
        }
        synchronized (UnidentifiedJewelry.class) {
            c = cachedIds;
            if (c != null) {
                return c;
            }
            List<String> list = new ObjectArrayList<>();
            for (String id : Item.getAssetMap().getAssetMap().keySet()) {
                if (JewelryItemIds.isJewelry(id)) {
                    list.add(id);
                }
            }
            c = list.toArray(new String[0]);
            cachedIds = c;
            return c;
        }
    }
}
