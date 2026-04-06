package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Pluggable game state for dialogue conditions; extend when achievements / village sim exist. */
public interface DialogueWorldView {
    boolean hasAchievement(@Nonnull String id);

    boolean getFlag(@Nonnull String id);

    boolean hasItem(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String itemId, int minCount);

    boolean isVillagerInTown(@Nonnull String villagerId);

    /** Town-scoped quest active (Aetherhaven towns). Default: false. */
    default boolean townQuestActive(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        return false;
    }

    /** Town-scoped quest completed. Default: false. */
    default boolean townQuestCompleted(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        return false;
    }

    /** True if the player's town has a COMPLETE plot with this construction id. */
    default boolean townHasCompletePlot(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String constructionId
    ) {
        return false;
    }

    /** True if the player owns an Aetherhaven town in the current world. */
    default boolean aetherhavenHasTown(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return false;
    }

    /** True if an NPC with this role id is currently listed in the town's inn visitor pool (loaded entity only). */
    default boolean innPoolHasNpcRole(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String npcRoleId
    ) {
        return false;
    }

    /**
     * True if the speaking NPC is registered as home resident on a complete house plot in the player's town
     * (used to finish house quests after the management block assignment).
     */
    default boolean townNpcHomeResidentOnHousePlot(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return false;
    }

    final class DefaultDialogueWorldView implements DialogueWorldView {
        @Override
        public boolean hasAchievement(@Nonnull String id) {
            return false;
        }

        @Override
        public boolean getFlag(@Nonnull String id) {
            return false;
        }

        @Override
        public boolean hasItem(
            @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String itemId, int minCount
        ) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) {
                return false;
            }
            CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
            return InventoryMaterials.count(inv, itemId) >= minCount;
        }

        @Override
        public boolean isVillagerInTown(@Nonnull String villagerId) {
            return false;
        }
    }
}
