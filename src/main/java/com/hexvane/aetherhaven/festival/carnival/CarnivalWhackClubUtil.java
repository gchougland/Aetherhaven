package com.hexvane.aetherhaven.festival.carnival;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Inventory helpers for the carnival Goblin Whacker. */
public final class CarnivalWhackClubUtil {
    private CarnivalWhackClubUtil() {}

    public static boolean playerHasWhacker(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        return inv != null && countWhackers(inv) > 0;
    }

    public static boolean holdingWhacker(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        if (store.getComponent(playerRef, Player.getComponentType()) == null) {
            return false;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return false;
        }
        ItemStack held = hotbar.getActiveItem();
        return held != null && !ItemStack.isEmpty(held) && CarnivalIds.CLUB_ITEM_ID.equals(held.getItemId());
    }

    public static void removeAllWhackers(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        int count = countWhackers(inv);
        if (count > 0) {
            inv.removeItemStack(new ItemStack(CarnivalIds.CLUB_ITEM_ID, count));
        }
    }

    public static void removeAllWhackersForPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        if (store.getComponent(playerRef, Player.getComponentType()) == null) {
            return;
        }
        removeAllWhackers(store, playerRef);
    }

    private static int countWhackers(@Nonnull CombinedItemContainer inv) {
        return inv.countItemStacks(
            stack -> stack != null
                && !ItemStack.isEmpty(stack)
                && CarnivalIds.CLUB_ITEM_ID.equals(stack.getItemId())
        );
    }
}
