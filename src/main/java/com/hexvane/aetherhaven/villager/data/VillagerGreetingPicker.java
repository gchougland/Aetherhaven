package com.hexvane.aetherhaven.villager.data;

import com.hypixel.hytale.server.core.Message;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Picks a stable-per-day greeting translation for hub dialogue. */
public final class VillagerGreetingPicker {
    private VillagerGreetingPicker() {}

    @Nullable
    public static Message pickMessage(
        @Nonnull VillagerDefinition def,
        @Nonnull UUID playerUuid,
        @Nonnull UUID npcEntityUuid,
        long gameEpochDay
    ) {
        List<String> generic = def.getDialogueGreetingLangKeys();
        List<String> hints = def.getDialogueGiftHintLangKeys();
        if (generic.isEmpty() && hints.isEmpty()) {
            return null;
        }
        long seed =
            playerUuid.getMostSignificantBits()
                ^ playerUuid.getLeastSignificantBits()
                ^ npcEntityUuid.getMostSignificantBits()
                ^ npcEntityUuid.getLeastSignificantBits()
                ^ gameEpochDay;
        Random rnd = new Random(seed);
        if (!hints.isEmpty() && rnd.nextFloat() < 0.35f) {
            return Message.translation(hints.get(rnd.nextInt(hints.size())));
        }
        if (!generic.isEmpty()) {
            return Message.translation(generic.get(rnd.nextInt(generic.size())));
        }
        if (!hints.isEmpty()) {
            return Message.translation(hints.get(rnd.nextInt(hints.size())));
        }
        return null;
    }
}
