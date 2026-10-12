package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Hands a player's unsold items back when the stall they were listed on stops existing, so rebuilding or moving a
 * building can never delete them. An offline seller's items wait in the saved registry until that player is next in
 * the world.
 */
public final class ShopSpotListingReturns {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop";

    private ShopSpotListingReturns() {}

    /** A stall holding real items that belong to a player (NPC stock is rolled, not owned). */
    public static boolean isReturnable(@Nullable ShopSpotRecord record) {
        return record != null && record.isPlayerControlled() && record.hasStock() && record.getSellerUuid() != null;
    }

    /**
     * Removes the stall row. Its listing goes straight to the seller when they are in this world, otherwise it is held
     * in the registry. Caller saves the registry.
     */
    public static void retire(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record
    ) {
        if (!registry.removeKeepingListing(record)) {
            return;
        }
        UUID seller = record.getSellerUuid();
        Ref<EntityStore> sellerRef = seller != null ? onlineRef(world, seller) : null;
        if (sellerRef != null) {
            deliver(store, registry, sellerRef, record);
        } else {
            LOGGER.atInfo().log(
                "Holding %s x%s from a removed shop stall for offline seller %s",
                record.getItemId(),
                record.getStock(),
                seller
            );
        }
    }

    /**
     * Gives the player every listing held for them. Run on the world thread, outside a store tick.
     *
     * @return how many held listings were handed over
     */
    public static int deliverHeld(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull UUID playerUuid
    ) {
        List<ShopSpotRecord> held = registry.heldReturnsFor(playerUuid);
        int delivered = 0;
        for (ShopSpotRecord record : held) {
            if (deliver(store, registry, playerRef, record)) {
                delivered++;
            }
        }
        if (delivered > 0) {
            ShopSpotPersistence.save(world, plugin, registry);
        }
        return delivered;
    }

    /**
     * Hands held listings to any seller who is in this world now. Cheap when nothing is held; safe to call from the
     * game-minute tick because the hand-over itself runs as a world task.
     */
    public static void deliverToOnlineSellers(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        if (!registry.hasHeldReturns()) {
            return;
        }
        world.execute(
            () -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null) {
                    return;
                }
                for (PlayerRef pr : world.getPlayerRefs()) {
                    Ref<EntityStore> ref = pr.getReference();
                    if (ref != null && ref.isValid() && !registry.heldReturnsFor(pr.getUuid()).isEmpty()) {
                        deliverHeld(world, plugin, store, registry, ref, pr.getUuid());
                    }
                }
            }
        );
    }

    private static boolean deliver(
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ShopSpotRecord record
    ) {
        String itemId = record.getItemId();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (itemId == null || player == null || transform == null) {
            return false;
        }
        ItemStack stack = ShopSpotJewelrySupport.buildListingStack(itemId, record.getStock(), record);
        Vector3d feet = transform.getPosition();
        Vector3i dropCell = new Vector3i((int) Math.floor(feet.x), (int) Math.floor(feet.y), (int) Math.floor(feet.z));
        ShopSpotItemDelivery.Result result =
            ShopSpotItemDelivery.grantAtShop(player, playerRef, store, null, stack, dropCell);
        if (!result.succeeded()) {
            return false;
        }
        registry.releaseHeldReturn(record);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + (result.droppedOnGround() ? ".listingReturnedDropped" : ".listingReturned")),
                NotificationStyle.Success
            );
        }
        return true;
    }

    @Nullable
    private static Ref<EntityStore> onlineRef(@Nonnull World world, @Nonnull UUID playerUuid) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (playerUuid.equals(pr.getUuid())) {
                Ref<EntityStore> ref = pr.getReference();
                if (ref != null && ref.isValid()) {
                    return ref;
                }
            }
        }
        return null;
    }
}
