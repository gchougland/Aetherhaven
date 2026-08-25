package com.hexvane.aetherhaven.festival.treeclimb;

import com.hexvane.aetherhaven.festival.FestivalRewardNotify;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Hands summer tickets to racers the moment they cross the finish, with a toast showing the amount. Inventory
 * writes run on {@code world.execute}, not during the tick system itself.
 */
final class TreeClimbRewards {
    private TreeClimbRewards() {}

    static void grantPendingTickets(@Nonnull World world, @Nonnull TreeClimbSession session) {
        Set<UUID> owed = session.pendingTicketPlayerUuids();
        if (owed.isEmpty()) {
            return;
        }
        world.execute(() -> {
            Store<EntityStore> live = liveStore(world);
            if (live == null) {
                return;
            }
            for (UUID playerUuid : owed) {
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
                    new ItemStack(TreeClimbIds.SUMMER_TICKET_ITEM_ID, tickets)
                );
            }
        });
    }

    @Nullable
    private static Store<EntityStore> liveStore(@Nonnull World world) {
        return world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
    }
}
