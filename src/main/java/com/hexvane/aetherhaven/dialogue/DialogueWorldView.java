package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.Message;
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

    /** True when the player may accept new town quests (member permissions), aligned with {@code start_quest}. */
    default boolean aetherhavenPlayerCanAcceptQuests(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store
    ) {
        return false;
    }

    /** True if an NPC with this role id is currently listed in the town's inn visitor pool (loaded entity only). */
    default boolean innPoolHasNpcRole(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String npcRoleId
    ) {
        return false;
    }

    /**
     * True when the inn exists, the build-inn quest is complete, and the town's inn visitor pool list has no entries
     * (every inn-eligible role is already a resident, so no travelers are assigned beds).
     */
    default boolean townInnVisitorPoolEmpty(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
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

    /** Active hotbar stack matches {@code itemId} with at least {@code minCount} (1 if not listed). */
    default boolean playerHoldsItemInActiveHotbar(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String itemId, int minCount
    ) {
        return false;
    }

    default boolean playerHoldsAnyItemInActiveHotbar(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store
    ) {
        return false;
    }

    default boolean villagerGiftAllowed(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return false;
    }

    /**
     * Shown after the choice label when the gift choice is disabled only due to daily/weekly gift limits
     * (see {@code giftDisableWhenNotAllowed} in dialogue JSON). {@code null} when N/A.
     */
    @Nullable
    default Message villagerGiftBlockMessage(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return null;
    }

    /** Gold coins in inventory plus optional treasury debit per town rules. */
    default boolean goldCoinPaymentCanAfford(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef, long cost
    ) {
        return false;
    }

    default boolean playerHealthBelowMax(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return false;
    }

    default boolean gaiaDraughtUnlocked(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        return false;
    }

    default boolean gaiaDraughtChargesBelowCapacity(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return false;
    }

    default boolean gaiaDraughtCapacityBelowMax(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return false;
    }

    default boolean gaiaDraughtHealTierBelowMax(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return false;
    }

    /** True when the next shard capacity upgrade is affordable at its current scaled gold tithe. */
    default boolean gaiaDraughtShardUpgradeGoldAffordable(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return false;
    }

    /** True when the next catalyst heal upgrade is affordable at its current scaled gold tithe. */
    default boolean gaiaDraughtCatalystUpgradeGoldAffordable(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return false;
    }

    /** Gold tithe for the player's next shard upgrade (0 when no town/state). */
    default long nextGaiaDraughtShardUpgradeGoldCost(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return 0L;
    }

    /** Gold tithe for the player's next catalyst upgrade (0 when no town/state). */
    default long nextGaiaDraughtCatalystUpgradeGoldCost(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
    ) {
        return 0L;
    }

    /** All {@code entity_kills} objectives for the active town quest meet their kill counts (or one objective if id given). */
    default boolean townQuestEntityKillsMet(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String questId,
        @Nullable String objectiveId
    ) {
        return false;
    }

    /** True when the player is hurt and can pay Serah's gold heal (inventory plus allowed treasury). */
    default boolean priestessHealGoldAffordable(
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

        @Override
        public boolean playerHoldsItemInActiveHotbar(
            @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String itemId, int minCount
        ) {
            return false;
        }

        @Override
        public boolean playerHoldsAnyItemInActiveHotbar(
            @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store
        ) {
            return false;
        }

        @Override
        public boolean villagerGiftAllowed(
            @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
        ) {
            return false;
        }

        @Override
        @Nullable
        public Message villagerGiftBlockMessage(
            @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef
        ) {
            return null;
        }
    }
}
