package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.market.MarketStallService;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftService;
import com.hexvane.aetherhaven.shopspot.ShopSpotItemDelivery;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Keeps what {@link FestivalHeldItems} holds moving towards its owner: a stall is put back after a restart, leftovers
 * are dropped once their place is loaded, an unanswered gift goes back to its giver, and anything owed to a player is
 * handed over when that player is in the world.
 */
public final class FestivalHeldItemsService {
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.";

    private FestivalHeldItemsService() {}

    /**
     * Once a game minute for every town. Costs one field read for a town that holds nothing; the work itself runs as
     * a world task, never inside the tick that called this.
     */
    public static void tick(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        if (!town.hasFestivalHeldItems()) {
            return;
        }
        UUID townId = town.getTownId();
        world.execute(() -> settle(world, plugin, townId));
    }

    /** World thread, outside a store tick. */
    public static void settle(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull UUID townId) {
        if (!world.isAlive() || world.getEntityStore() == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (store == null || town == null || !town.hasFestivalHeldItems()) {
            return;
        }
        boolean changed = WintertideGiftService.settleHeldGift(world, store, town);
        changed |= MarketStallService.settleHeld(world, town);
        FestivalHeldItems held = town.festivalHeldItems();
        for (PlayerRef online : world.getPlayerRefs()) {
            UUID player = online.getUuid();
            Ref<EntityStore> ref = online.getReference();
            if (player == null || ref == null || !ref.isValid() || !held.owedPlayers().contains(player)) {
                continue;
            }
            boolean any = false;
            for (ItemStack stack : held.takeOwed(player)) {
                if (handBack(store, ref, stack)) {
                    any = true;
                } else {
                    held.owe(player, stack);
                }
            }
            if (any) {
                changed = true;
                NotificationUtil.sendNotification(
                    online.getPacketHandler(),
                    Message.translation(LANG + "heldItemReturned"),
                    NotificationStyle.Success
                );
            }
        }
        if (changed) {
            saved(tm, town);
        }
    }

    /**
     * Gives a player a stack that is theirs: into the inventory, and whatever does not fit at their feet. Nothing is
     * dropped silently and nothing vanishes with a full inventory.
     *
     * @return false when the player is not there to take it; the caller still holds the stack then
     */
    public static boolean handBack(
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> playerRef,
        @Nonnull ItemStack stack
    ) {
        if (playerRef == null || !playerRef.isValid() || ItemStack.isEmpty(stack)) {
            return false;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (player == null || transform == null) {
            return false;
        }
        Vector3d feet = transform.getPosition();
        Vector3i cell = new Vector3i((int) Math.floor(feet.x), (int) Math.floor(feet.y), (int) Math.floor(feet.z));
        return ShopSpotItemDelivery.grantAtShop(player, playerRef, store, null, stack, cell).succeeded();
    }

    /**
     * A town that is being removed lets go of everything it holds: stall goods, an unanswered gift and whatever was
     * set aside. Each stack goes to its player when they are in this world. For the others it is put on the ground at
     * the town's charter, where the people removing the town are, because there will be no town left to keep it.
     */
    public static void releaseAll(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        MarketStallService.returnLeftovers(world, town);
        com.hexvane.aetherhaven.festival.market.MarketSessionIndex.remove(town.getTownId());
        WintertideGiftService.returnPendingGift(world, store, town, com.hexvane.aetherhaven.festival.wintertide.WintertideSessionIndex.get(town.getTownId()));
        com.hexvane.aetherhaven.festival.wintertide.WintertideSessionIndex.remove(town.getTownId());
        if (!town.hasFestivalHeldItems()) {
            return;
        }
        FestivalHeldItems held = town.festivalHeldItems();
        java.util.List<ItemStack> ground = new java.util.ArrayList<>();
        for (UUID player : held.owedPlayers()) {
            for (ItemStack stack : held.takeOwed(player)) {
                if (!handBack(store, onlineRef(world, player), stack)) {
                    ground.add(stack);
                }
            }
        }
        if (!ground.isEmpty()) {
            ShopSpotItemDelivery.dropOnGround(
                store, ground, new Vector3d(town.getCharterX() + 0.5, town.getCharterY() + 1.0, town.getCharterZ() + 0.5));
        }
        town.releaseFestivalHeldItemsIfEmpty();
    }

    /** Hands the stack to the player now when they are in this world, otherwise sets it aside for them. */
    public static void giveOrOwe(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull FestivalHeldItems held,
        @Nonnull UUID player,
        @Nonnull ItemStack stack
    ) {
        Ref<EntityStore> ref = onlineRef(world, player);
        if (!handBack(store, ref, stack)) {
            held.owe(player, stack);
        }
    }

    @Nullable
    public static Ref<EntityStore> onlineRef(@Nonnull World world, @Nonnull UUID player) {
        for (PlayerRef online : world.getPlayerRefs()) {
            if (player.equals(online.getUuid())) {
                Ref<EntityStore> ref = online.getReference();
                return ref != null && ref.isValid() ? ref : null;
            }
        }
        return null;
    }

    /** Call after changing a town's held items: an emptied record is dropped from the save, and the town is saved. */
    public static void saved(@Nonnull TownManager tm, @Nonnull TownRecord town) {
        town.releaseFestivalHeldItemsIfEmpty();
        tm.updateTown(town);
    }

    /** Same as {@link #saved(TownManager, TownRecord)} for callers that only have the world. */
    public static void saved(@Nonnull World world, @Nonnull TownRecord town) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            saved(AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin), town);
        }
    }
}
