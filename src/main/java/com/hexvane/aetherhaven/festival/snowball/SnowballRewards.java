package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.festival.FestivalRewardNotify;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fight-start snowball wipe and fight-end ticket grants. Inventory writes run on {@code world.execute}, not during the
 * tick system itself.
 */
final class SnowballRewards {
    private SnowballRewards() {}

    static void clearPlayerSnowballs(@Nonnull World world, @Nonnull SnowballSession session) {
        Set<UUID> playerUuids = playerUuidsToClear(session);
        if (playerUuids.isEmpty()) {
            return;
        }
        world.execute(() -> {
            Store<EntityStore> live = liveStore(world);
            if (live == null) {
                return;
            }
            for (UUID playerUuid : playerUuids) {
                Ref<EntityStore> playerRef = live.getExternalData().getRefFromUUID(playerUuid);
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }
                CombinedItemContainer inv =
                    InventoryComponent.getCombined(live, playerRef, InventoryComponent.EVERYTHING);
                if (inv == null) {
                    continue;
                }
                int count = inv.countItemStacks(
                    stack -> stack != null
                        && !ItemStack.isEmpty(stack)
                        && SnowballIds.SNOWBALL_ITEM_ID.equals(stack.getItemId())
                );
                if (count > 0) {
                    inv.removeItemStack(new ItemStack(SnowballIds.SNOWBALL_ITEM_ID, count));
                }
            }
        });
    }

    static void grantPendingTickets(@Nonnull World world, @Nonnull SnowballSession session) {
        Set<UUID> winners = session.pendingTicketPlayerUuids();
        if (winners.isEmpty()) {
            return;
        }
        world.execute(() -> {
            Store<EntityStore> live = liveStore(world);
            if (live == null) {
                return;
            }
            for (UUID playerUuid : winners) {
                if (!session.hasPendingTickets(playerUuid)) {
                    continue;
                }
                Ref<EntityStore> playerRef = live.getExternalData().getRefFromUUID(playerUuid);
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }
                Player player = live.getComponent(playerRef, Player.getComponentType());
                if (player == null) {
                    continue;
                }
                int tickets = session.collectTickets(playerUuid);
                if (tickets <= 0) {
                    continue;
                }
                FestivalRewardNotify.giveAndNotify(
                    player,
                    playerRef,
                    live,
                    new ItemStack(SnowballIds.WINTER_TICKET_ITEM_ID, tickets)
                );
            }
        });
    }

    @Nonnull
    private static Set<UUID> playerUuidsToClear(@Nonnull SnowballSession session) {
        Set<UUID> out = new LinkedHashSet<>(session.joinedPlayersView());
        for (SnowballSession.Fighter fighter : session.fightersView()) {
            if (fighter.isPlayer()) {
                out.add(fighter.uuid());
            }
        }
        return out;
    }

    @Nullable
    private static Store<EntityStore> liveStore(@Nonnull World world) {
        return world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
    }
}
