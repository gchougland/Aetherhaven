package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Applies non-reputation rewards from quest JSON (items, etc.). */
public final class QuestRewardService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private QuestRewardService() {}

    public static void grantNonReputationRewards(
        @Nonnull QuestDefinition def,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        for (QuestReward r : def.rewardsOrEmpty()) {
            if (r.kind() == null) {
                continue;
            }
            String kind = r.kind().trim();
            if ("reputation".equalsIgnoreCase(kind)) {
                continue;
            }
            if ("item".equalsIgnoreCase(kind)) {
                String itemId = r.itemId();
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                int count = Math.max(1, Math.min(r.count(), 9999));
                String grantTo = r.grantTo();
                if (grantTo != null && !grantTo.isBlank() && !"player".equalsIgnoreCase(grantTo.trim())) {
                    LOGGER.atInfo().log("Skipping item reward grantTo=%s for quest %s", grantTo, def.idOrEmpty());
                    continue;
                }
                player.giveItem(new ItemStack(itemId.trim(), count), playerRef, store);
            } else if ("currency".equalsIgnoreCase(kind) || "unlock".equalsIgnoreCase(kind)) {
                LOGGER.atInfo().log(
                    "[Quest stub] reward kind %s for quest %s (not implemented)",
                    kind,
                    def.idOrEmpty()
                );
            }
        }
    }
}
