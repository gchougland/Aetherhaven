package com.hexvane.aetherhaven.festival;

import com.hypixel.hytale.server.core.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        @Nonnull FestivalGreetingLangIndex greetingIndex,
        @Nullable String residentKind,
        @Nonnull UUID playerUuid,
        @Nonnull UUID npcEntityUuid,
        long gameEpochDay
    ) {
        List<String> keys = mergedGreetingLangKeys(festival, greetingIndex, residentKind);
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

    @Nonnull
    public static List<String> mergedGreetingLangKeys(
        @Nonnull FestivalDefinition festival,
        @Nonnull FestivalGreetingLangIndex greetingIndex,
        @Nullable String residentKind
    ) {
        String festivalId = festival.getId();
        String kind = normalizeKind(residentKind);
        List<String> keys = new ArrayList<>();
        appendUnique(keys, explicitKindKeys(festival, kind));
        appendUnique(keys, greetingIndex.keysForKindOnly(festivalId, kind));
        if (keys.isEmpty()) {
            appendUnique(keys, explicitKindKeys(festival, FestivalDefinition.GREETING_DEFAULT_KIND));
            appendUnique(keys, greetingIndex.keysForDefault(festivalId));
        }
        return List.copyOf(keys);
    }

    @Nonnull
    private static List<String> explicitKindKeys(@Nonnull FestivalDefinition festival, @Nonnull String kind) {
        List<String> keys = festival.getGreetings().get(kind);
        return keys != null ? keys : List.of();
    }

    private static void appendUnique(@Nonnull List<String> target, @Nonnull List<String> additions) {
        for (String key : additions) {
            if (key != null && !key.isBlank() && !target.contains(key)) {
                target.add(key);
            }
        }
    }

    @Nonnull
    private static String normalizeKind(@Nullable String kind) {
        return kind != null ? kind.trim().toLowerCase(Locale.ROOT) : "";
    }
}
