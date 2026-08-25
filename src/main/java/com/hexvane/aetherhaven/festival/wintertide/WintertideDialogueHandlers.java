package com.hexvane.aetherhaven.festival.wintertide;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.plugin.DialogueActionRegistry;
import com.hexvane.aetherhaven.plugin.DialogueConditionRegistry;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Dialogue conditions and actions for the Wintertide merchant and gift exchange. */
public final class WintertideDialogueHandlers {
    private WintertideDialogueHandlers() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        DialogueConditionRegistry conditions = plugin.getDialogueConditionRegistry();
        conditions.register("wintertide_already_given", (c, p, s, n) -> alreadyGiven(p, s, n));
        conditions.register("wintertide_has_assignment", (c, p, s, n) -> hasAssignment(p, s, n));
        conditions.register("wintertide_not_member", (c, p, s, n) -> !isMember(p, s, n));

        DialogueActionRegistry actions = plugin.getDialogueActionRegistry();
        actions.register("wintertide_gift_villager", WintertideDialogueHandlers::giftVillager);
        actions.register("wintertide_rate_love", (a, p, s, o, n) -> rate(p, s, o, GiftPreference.LOVE));
        actions.register("wintertide_rate_like", (a, p, s, o, n) -> rate(p, s, o, GiftPreference.LIKE));
        actions.register("wintertide_rate_neutral", (a, p, s, o, n) -> rate(p, s, o, GiftPreference.NEUTRAL));
        actions.register("wintertide_rate_dislike", (a, p, s, o, n) -> rate(p, s, o, GiftPreference.DISLIKE));
        actions.register("wintertide_receive_gift", WintertideDialogueHandlers::receiveGift);
    }

    private static boolean alreadyGiven(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = WintertideGiftService.resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null) {
            return false;
        }
        return WintertideGiftService.sessionFor(town, store).hasGiven(playerUuid);
    }

    /**
     * Visitors draw a gift partner just like residents do, so the only thing that decides whether the merchant will
     * name yours is whether the draw actually gave you one — asking for town membership on top of that hid the choice
     * from everybody who came in for the festival.
     */
    private static boolean hasAssignment(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = WintertideGiftService.resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        if (town == null || playerUuid == null) {
            return false;
        }
        return WintertideGiftService.sessionFor(town, store).hasOutgoing(playerUuid);
    }

    private static boolean isMember(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        TownRecord town = WintertideGiftService.resolveTown(playerRef, store, npcRef);
        UUID playerUuid = playerUuid(playerRef, store);
        return town != null && playerUuid != null && town.hasMemberOrOwner(playerUuid);
    }

    private static void giftVillager(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        WintertideGiftService.applyVillagerGift(playerRef, store, npcRef, out);
    }

    private static void rate(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nonnull GiftPreference preference
    ) {
        WintertideGiftService.applyPlayerRating(playerRef, store, preference, out);
    }

    private static void receiveGift(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        WintertideGiftService.giveIncomingGift(playerRef, store, npcRef);
        out.setCloseDialogue(true);
    }

    @Nullable
    private static UUID playerUuid(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }
}
