package com.hexvane.aetherhaven.prop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stops players from breaking placed props by hand. Same pattern as
 * {@link com.hexvane.aetherhaven.festival.FestivalPlotProtection}: players use the packaging wand instead, or an
 * admin can toggle break-through for their own session with {@code /ah prop break}.
 */
public final class PropBreakProtection {
    private static final String PROTECTED_MESSAGE_KEY = "aetherhaven_props.aetherhaven.prop.protected";
    /** Players hold down mine, so only remind them every few seconds. */
    private static final long MESSAGE_COOLDOWN_MS = 6_000L;

    private static final Map<UUID, Long> LAST_WARNED_AT = new ConcurrentHashMap<>();
    /** Players who turned on prop breaking for themselves (session only). */
    private static final Set<UUID> BREAK_ALLOWED = ConcurrentHashMap.newKeySet();

    private PropBreakProtection() {}

    public static boolean isBreakAllowed(@Nullable UUID playerUuid) {
        return playerUuid != null && BREAK_ALLOWED.contains(playerUuid);
    }

    /** Flips prop breaking for this player. Returns the new state ({@code true} = breaking allowed). */
    public static boolean toggleBreakAllowed(@Nonnull UUID playerUuid) {
        if (BREAK_ALLOWED.remove(playerUuid)) {
            return false;
        }
        BREAK_ALLOWED.add(playerUuid);
        return true;
    }

    /** Tells the player why nothing happened, at most once every few seconds. */
    public static void warn(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nullable UUID playerUuid
    ) {
        if (playerUuid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_WARNED_AT.get(playerUuid);
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) {
            return;
        }
        if (LAST_WARNED_AT.size() > 256) {
            LAST_WARNED_AT.values().removeIf(at -> now - at >= MESSAGE_COOLDOWN_MS);
        }
        LAST_WARNED_AT.put(playerUuid, now);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation(PROTECTED_MESSAGE_KEY));
        }
    }
}
