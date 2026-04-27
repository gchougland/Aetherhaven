package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.tool.PoiDebugLineHelper;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Re-sends path nav polylines to a player on an interval (client {@link
 * com.hypixel.hytale.protocol.packets.player.DisplayDebug} lines). Toggled with {@code /aetherhaven path navviz}.
 */
public final class PathNavViz {
    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Aetherhaven-PathNavViz");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<UUID, Holder> BY_PLAYER = new ConcurrentHashMap<>();
    private static final Vector3f SEG_COLOR = new Vector3f(0.15f, 0.9f, 0.2f);
    private static final Vector3f ENDPOINT_COLOR = new Vector3f(0.95f, 0.9f, 0.1f);
    private static final float NODE_LINE_SECONDS = 3.0f;
    private static final long INTERVAL_MS = 2500L;
    private static final double SEG_THICK = 0.07;
    private static final double NODE_THICK = 0.12;

    private PathNavViz() {}

    private static final class Holder {
        @Nonnull
        final World world;
        @Nonnull
        final PlayerRef playerRef;
        @Nonnull
        final ScheduledFuture<?> future;

        Holder(@Nonnull World world, @Nonnull PlayerRef playerRef, @Nonnull ScheduledFuture<?> future) {
            this.world = world;
            this.playerRef = playerRef;
            this.future = future;
        }
    }

    /** Clear any active client viz (safe on plugin reload; the scheduler stays usable). */
    public static void shutdown() {
        for (Holder h : BY_PLAYER.values()) {
            h.future.cancel(false);
        }
        BY_PLAYER.clear();
    }

    public static void toggle(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Player pl,
        @Nonnull PlayerRef playerRef
    ) {
        @Nullable
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        UUID id = uc.getUuid();
        Holder existing = BY_PLAYER.get(id);
        if (existing != null) {
            existing.future.cancel(false);
            BY_PLAYER.remove(id, existing);
            return;
        }
        PathToolRegistry reg = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
        ScheduledFuture<?> f =
            EXEC.scheduleAtFixedRate(
                () -> {
                    try {
                        world.execute(
                            () -> {
                                if (!ref.isValid()) {
                                    cancelFor(id);
                                    return;
                                }
                                if (store.getComponent(ref, Player.getComponentType()) == null) {
                                    cancelFor(id);
                                    return;
                                }
                                drawOnce(playerRef, reg);
                            }
                        );
                    } catch (Throwable ignored) {
                    }
                },
                0L,
                INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        BY_PLAYER.put(id, new Holder(world, playerRef, f));
    }

    private static void cancelFor(@Nonnull UUID id) {
        Holder h = BY_PLAYER.remove(id);
        if (h != null) {
            h.future.cancel(false);
        }
    }

    public static boolean isOn(@Nullable UUID playerUuid) {
        return playerUuid != null && BY_PLAYER.containsKey(playerUuid);
    }

    private static void drawOnce(@Nonnull PlayerRef playerRef, @Nonnull PathToolRegistry reg) {
        List<PathCommitRecord> recs = reg.all();
        for (PathCommitRecord rec : recs) {
            if (rec == null || rec.navNodes == null || rec.navNodes.size() < 2) {
                continue;
            }
            for (int i = 0; i + 1 < rec.navNodes.size(); i++) {
                PathNavPoint a = rec.navNodes.get(i);
                PathNavPoint b = rec.navNodes.get(i + 1);
                PoiDebugLineHelper.addLineToPlayer(
                    playerRef,
                    a.x,
                    a.y,
                    a.z,
                    b.x,
                    b.y,
                    b.z,
                    SEG_COLOR,
                    SEG_THICK,
                    NODE_LINE_SECONDS,
                    0
                );
            }
            for (int i = 0; i < rec.navNodes.size(); i++) {
                PathNavPoint p = rec.navNodes.get(i);
                boolean isEnd = i == 0 || i == rec.navNodes.size() - 1;
                PoiDebugLineHelper.addLineToPlayer(
                    playerRef,
                    p.x,
                    p.y,
                    p.z,
                    p.x,
                    p.y + 0.55,
                    p.z,
                    isEnd ? ENDPOINT_COLOR : DebugUtils.COLOR_CYAN,
                    NODE_THICK,
                    NODE_LINE_SECONDS,
                    0
                );
            }
        }
    }
}
