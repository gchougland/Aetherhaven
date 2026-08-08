package com.hexvane.aetherhaven.calendar;

import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.server.core.Message;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Picks stable birthday greetings for hub dialogue. */
public final class VillagerBirthdayGreetingPicker {
    private VillagerBirthdayGreetingPicker() {}

    @Nullable
    public static Message pickMessage(
        @Nonnull VillagerDefinition def,
        @Nonnull UUID playerUuid,
        @Nonnull UUID npcEntityUuid,
        long gameEpochDay
    ) {
        List<String> keys = def.getDialogueBirthdayGreetingLangKeys();
        if (keys.isEmpty()) {
            return null;
        }
        long seed =
            playerUuid.getMostSignificantBits()
                ^ playerUuid.getLeastSignificantBits()
                ^ npcEntityUuid.getMostSignificantBits()
                ^ npcEntityUuid.getLeastSignificantBits()
                ^ gameEpochDay
                ^ 297_874_8123L;
        Random rnd = new Random(seed);
        return Message.translation(keys.get(rnd.nextInt(keys.size())));
    }
}
