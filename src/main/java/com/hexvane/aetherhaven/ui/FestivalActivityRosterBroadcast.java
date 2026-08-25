package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pushes one activity's roster to exactly the players taking part in it. Remembers who was shown last tick so a
 * player who leaves the activity gets the overlay taken away instead of keeping a stale list on screen.
 */
public final class FestivalActivityRosterBroadcast {
    private static final Message EMPTY_TEXT =
        Message.translation("aetherhaven_festivals.aetherhaven.festival.roster.empty");

    private static final Map<String, Set<UUID>> SHOWN_BY_ACTIVITY = new ConcurrentHashMap<>();

    private FestivalActivityRosterBroadcast() {}

    /** An online player the roster can be sent to. */
    public record Target(@Nonnull Player player, @Nonnull PlayerRef playerRef) {}

    /**
     * Key for one activity running in one town. Festival ids keep the key apart so a session that outlives its
     * festival by a tick cannot hide the roster the next festival just published.
     */
    @Nonnull
    public static String activityKey(@Nonnull UUID townId, @Nonnull String festivalId) {
        return townId + ":" + festivalId;
    }

    /**
     * @param activityKey from {@link #activityKey}
     * @param audience players who should see the roster right now
     * @param lookup resolves a player uuid to an online target, or null when that player is offline
     */
    public static void publish(
        @Nonnull String activityKey,
        @Nonnull Message title,
        @Nonnull List<FestivalActivityRosterHud.Row> rows,
        @Nonnull Collection<UUID> audience,
        @Nonnull Function<UUID, Target> lookup
    ) {
        publish(activityKey, title, rows, audience, lookup, EMPTY_TEXT);
    }

    /** @param emptyText shown in place of the rows when the activity has nobody in it yet */
    public static void publish(
        @Nonnull String activityKey,
        @Nonnull Message title,
        @Nonnull List<FestivalActivityRosterHud.Row> rows,
        @Nonnull Collection<UUID> audience,
        @Nonnull Function<UUID, Target> lookup,
        @Nonnull Message emptyText
    ) {
        hideOutsiders(activityKey, audience, lookup);
        Set<UUID> next = new LinkedHashSet<>();
        for (UUID playerUuid : audience) {
            Target target = lookup.apply(playerUuid);
            if (target == null) {
                continue;
            }
            FestivalActivityRosterHudSupport.show(target.player(), target.playerRef(), title, rows, emptyText);
            next.add(playerUuid);
        }
        if (next.isEmpty()) {
            SHOWN_BY_ACTIVITY.remove(activityKey);
        } else {
            SHOWN_BY_ACTIVITY.put(activityKey, next);
        }
    }

    /** Takes the roster away from everybody it was shown to, for example when the activity ends. */
    public static void clear(@Nonnull String activityKey, @Nonnull Function<UUID, Target> lookup) {
        Set<UUID> previous = SHOWN_BY_ACTIVITY.remove(activityKey);
        if (previous == null) {
            return;
        }
        for (UUID playerUuid : previous) {
            hide(lookup.apply(playerUuid));
        }
    }

    private static void hideOutsiders(
        @Nonnull String activityKey,
        @Nonnull Collection<UUID> audience,
        @Nonnull Function<UUID, Target> lookup
    ) {
        Set<UUID> previous = SHOWN_BY_ACTIVITY.get(activityKey);
        if (previous == null) {
            return;
        }
        for (UUID playerUuid : previous) {
            if (!audience.contains(playerUuid)) {
                hide(lookup.apply(playerUuid));
            }
        }
    }

    private static void hide(@Nullable Target target) {
        if (target != null) {
            FestivalActivityRosterHudSupport.hide(target.player(), target.playerRef());
        }
    }
}
