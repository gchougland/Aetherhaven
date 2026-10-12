package com.hexvane.aetherhaven.shopspot;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * A price change is live on the server the moment the catalog is replaced, but a client keeps the price it was last
 * sent on each item's tooltip. This asks the engine to send every online player their inventory again, through its
 * ordinary path, so the tooltips show the new price at once and not only after the next time a slot changes.
 */
public final class ShopPriceLiveRefresh {
    private ShopPriceLiveRefresh() {}

    /** Safe from any thread: the work is handed to each world. Does nothing when nobody is online. */
    public static void afterPriceChange() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }
        for (World world : universe.getWorlds().values()) {
            if (world.getPlayerRefs().isEmpty()) {
                continue;
            }
            world.execute(() -> resendInventories(world));
        }
    }

    private static void resendInventories(@Nonnull World world) {
        if (!world.isAlive() || world.getEntityStore() == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            for (var types : List.of(InventoryComponent.HOTBAR_STORAGE_BACKPACK, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE)) {
                for (ComponentType<EntityStore, ? extends InventoryComponent> type : types) {
                    InventoryComponent section = store.getComponent(ref, type);
                    if (section != null) {
                        // Only raises the flag the engine's own inventory sender looks at; nothing in the store changes.
                        section.markDirty();
                    }
                }
            }
        }
    }
}
