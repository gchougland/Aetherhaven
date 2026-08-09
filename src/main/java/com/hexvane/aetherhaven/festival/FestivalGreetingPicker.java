package com.hexvane.aetherhaven.festival;

import com.hypixel.hytale.server.core.Message;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Picks what a villager says while their town is celebrating. Same idea as the birthday greetings: the line is stable
 * for a given player, villager, and day, so walking away and coming back does not reshuffle it.
 */
public final class FestivalGreetingPicker {
    /** Keeps festival lines from landing on the same pick as the birthday and everyday greetings. */
    private static final long SEED_SALT = 811_527_449L;

    private FestivalGreetingPicker() {}

    @Nullable
    public static Message pickMessage(
        @Nonnull FestivalDefinition festival,
        @Nullable String residentKind,
        @Nonnull UUID playerUuid,
        @Nonnull UUID npcEntityUuid,
        long gameEpochDay
    ) {
        List<String> keys = festival.getGreetingLangKeys(residentKind);
        if (keys.isEmpty()) {
            return null;
        }
        long seed =
            playerUuid.getMostSignificantBits()
                ^ playerUuid.getLeastSignificantBits()
                ^ npcEntityUuid.getMostSignificantBits()
                ^ npcEntityUuid.getLeastSignificantBits()
                ^ gameEpochDay
                ^ festival.getId().hashCode()
                ^ SEED_SALT;
        Random rnd = new Random(seed);
        return Message.translation(keys.get(rnd.nextInt(keys.size())));
    }
}
