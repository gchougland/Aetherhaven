package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.ui.FestivalRewardWindowPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Opens the festival reward window once a prize batch has settled and the player is not looking at another page.
 * Waiting for a free screen matters because a prize can land mid conversation, and the window would otherwise take
 * the dialogue's place. A conversation that is closing hands the window over itself, in
 * {@link FestivalRewardNotify#openWindowNow}, so this only picks up prizes won away from an NPC.
 */
public final class FestivalRewardWindowSystem extends TickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        List<UUID> waiting = FestivalRewardQueue.waitingPlayers();
        if (waiting.isEmpty()) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }
        long now = System.currentTimeMillis();
        FestivalRewardQueue.dropExpired(now);
        for (UUID playerUuid : waiting) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(playerUuid);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null || player.getPageManager().getCustomPage() != null) {
                continue;
            }
            FestivalRewardQueue.Payload payload = FestivalRewardQueue.takeIfSettled(playerUuid, now);
            if (payload == null) {
                continue;
            }
            world.execute(() -> {
                if (!ref.isValid()) {
                    return;
                }
                Player live = store.getComponent(ref, Player.getComponentType());
                if (live == null || live.getPageManager().getCustomPage() != null) {
                    return;
                }
                live.getPageManager()
                    .openCustomPage(
                        ref,
                        store,
                        new FestivalRewardWindowPage(playerRef, payload.entries(), payload.outcome())
                    );
            });
        }
    }
}
