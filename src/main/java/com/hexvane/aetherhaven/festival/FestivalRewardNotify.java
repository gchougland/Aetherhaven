package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.ui.FestivalRewardWindowPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Entry point for festival prizes. Grants go into {@link FestivalRewardQueue} so a single centred window lists
 * everything the player won, instead of one corner toast per item stack.
 */
public final class FestivalRewardNotify {
    private FestivalRewardNotify() {}

    public static void giveAndNotify(
        @Nullable Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ItemStack stack
    ) {
        if (player == null || ItemStack.isEmpty(stack)) {
            return;
        }
        player.giveItem(stack, playerRef, store);
        notify(store, playerRef, stack);
    }

    public static void notify(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ItemStack stack
    ) {
        UUID playerUuid = playerUuid(store, playerRef);
        if (playerUuid != null) {
            FestivalRewardQueue.queueItem(playerUuid, stack);
        }
    }

    /** Opens the reward window with a "no prize this time" line when an activity settles with nothing to hand over. */
    public static void notifyLoss(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        UUID playerUuid = playerUuid(store, playerRef);
        if (playerUuid != null) {
            FestivalRewardQueue.queueOutcome(playerUuid, FestivalRewardQueue.Outcome.LOST);
        }
    }

    /** Frames the batch as a present rather than a prize, for gifts a villager hands over in person. */
    public static void giveGift(
        @Nullable Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ItemStack stack
    ) {
        UUID playerUuid = playerUuid(store, playerRef);
        if (playerUuid != null) {
            FestivalRewardQueue.queueOutcome(playerUuid, FestivalRewardQueue.Outcome.GIFTED);
        }
        giveAndNotify(player, playerRef, store, stack);
    }

    /**
     * Replaces the page in front of the player with their prizes. The dialogue that queued them has to hand the screen
     * straight over: closing it first and letting {@link FestivalRewardWindowSystem} open the window later leaves the
     * page manager waiting on two client acknowledgements, and it drops every button press until both arrive.
     *
     * @return whether a window was opened
     */
    public static boolean openWindowNow(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUID playerUuid = playerUuid(store, playerRef);
        if (playerUuid == null) {
            return false;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return false;
        }
        FestivalRewardQueue.Payload payload = FestivalRewardQueue.take(playerUuid);
        if (payload == null) {
            return false;
        }
        player.getPageManager()
            .openCustomPage(
                playerRef,
                store,
                new FestivalRewardWindowPage(pr, payload.entries(), payload.outcome())
            );
        return true;
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        if (!playerRef.isValid()) {
            return null;
        }
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }
}
