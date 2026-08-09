package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Keeps the festival square exactly as it was built. The whole square is replaced every time a festival starts and
 * ends, so anything a player digs or builds there would vanish on the next swap.
 */
public final class FestivalPlotProtection {
    private static final String PROTECTED_MESSAGE_KEY = "aetherhaven_festivals.aetherhaven.festival.square.protected";
    /** Players hold down mine, so only remind them every few seconds. */
    private static final long MESSAGE_COOLDOWN_MS = 6_000L;

    private static final Map<UUID, Long> LAST_WARNED_AT = new ConcurrentHashMap<>();

    private FestivalPlotProtection() {}

    /** True when the block belongs to a finished festival square. */
    public static boolean isInsideFestivalSquare(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Vector3i pos
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownContainingBlock(world.getName(), pos.x, pos.z);
        if (town == null) {
            return false;
        }
        PlotInstance plot = town.findCompletePlotContaining(pos.x, pos.y, pos.z);
        if (plot == null) {
            return false;
        }
        return plugin
            .getConstructionCatalog()
            .matchesGameplayConstruction(plot.getConstructionId(), AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE);
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
