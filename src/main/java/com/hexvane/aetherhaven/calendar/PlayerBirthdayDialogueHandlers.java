package com.hexvane.aetherhaven.calendar;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.plugin.DialogueActionRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Dialogue actions for player birthday gifts. */
public final class PlayerBirthdayDialogueHandlers {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PlayerBirthdayDialogueHandlers() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        DialogueActionRegistry actions = plugin.getDialogueActionRegistry();
        actions.register("player_birthday_receive_gift", PlayerBirthdayDialogueHandlers::receiveGift);
    }

    private static void receiveGift(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        try {
            PlayerBirthdayGiftService.giveIncomingGift(playerRef, store, npcRef);
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Could not give birthday gift");
        } finally {
            out.setCloseDialogue(true);
        }
    }
}
