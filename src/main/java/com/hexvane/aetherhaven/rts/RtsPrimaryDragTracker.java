package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Box drag via repeated Primary interaction pulses (custom camera routes clicks through item Primary,
 * not {@code PlayerMouseButtonEvent} / {@code CameraManager} press state).
 */
final class RtsPrimaryDragTracker {
    private static final long RELEASE_IDLE_MS = 180;

    private static final Map<UUID, Long> lastPulseMs = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> pulseCount = new ConcurrentHashMap<>();
    private static final Map<UUID, float[]> lastRawScreen = new ConcurrentHashMap<>();

    private RtsPrimaryDragTracker() {}

    /** @return true when the pulse was consumed for drag tracking (caller should not single-click yet). */
    static boolean onPrimaryPulse(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull RtsCommandPlayerComponent session,
        @Nullable Vector3i targetBlock,
        @Nonnull ItemStack hand
    ) {
        if (AetherhavenConstants.RTS_EXIT_ITEM_ID.equals(hand.getItemId())) {
            return false;
        }
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        long now = System.currentTimeMillis();
        boolean starting = !session.isBoxSelectActive();
        int count = pulseCount.merge(pr != null ? pr.getUuid() : UUID.randomUUID(), 1, Integer::sum);
        org.joml.Vector2fc screen = RtsScreenPickUtil.latestCameraScreenPoint(playerRef, store);
        if (pr != null) {
            lastPulseMs.put(pr.getUuid(), now);
            if (screen != null) {
                noteScreenMotion(pr.getUuid(), screen.x(), screen.y());
            }
        }

        if (starting) {
            RtsMouseInputListener.beginBoxSelect(playerRef, store, session, targetBlock, screen);
        } else {
            RtsMouseInputListener.updateBoxDrag(playerRef, store, session, targetBlock, screen);
        }
        if (pr != null) {
            RtsDiagnostics.boxDragPulse(pr, starting ? "begin" : "continue", count, targetBlock, session, screen);
        }
        commandBuffer.putComponent(playerRef, RtsCommandPlayerComponent.getComponentType(), session);
        RtsMouseInputListener.refreshBoxHud(playerRef, store, session);
        return true;
    }

    static void tickPendingReleases(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull RtsCommandPlayerComponent session,
        @Nonnull PlayerRef pr
    ) {
        if (!session.isBoxSelectActive()) {
            lastPulseMs.remove(pr.getUuid());
            pulseCount.remove(pr.getUuid());
            lastRawScreen.remove(pr.getUuid());
            return;
        }
        PendingRelease release = pollPendingRelease(pr.getUuid(), System.currentTimeMillis());
        if (release == null) {
            return;
        }
        RtsDiagnostics.boxDragRelease(pr, "primary-idle", release.idleMs(), release.pulses(), session);

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            RtsMouseInputListener.cancelBoxSelect(playerRef, store, commandBuffer, session);
            return;
        }
        TownRecord town = RtsSelectionService.townForSession(store, store.getExternalData().getWorld(), plugin, session);
        if (town == null) {
            RtsMouseInputListener.cancelBoxSelect(playerRef, store, commandBuffer, session);
            return;
        }
        RtsMouseInputListener.finishBoxSelect(playerRef, store, commandBuffer, session, town, null, null);
    }

    record PendingRelease(long idleMs, int pulses) {}

    /** Consumes expired tracking state, including drags tracked only through camera motion. */
    @Nullable
    static PendingRelease pollPendingRelease(@Nonnull UUID playerId, long nowMs) {
        Long last = lastPulseMs.get(playerId);
        if (last == null) {
            return null;
        }
        long idle = nowMs - last;
        if (idle < RELEASE_IDLE_MS) {
            return null;
        }
        lastPulseMs.remove(playerId);
        // noteScreenMotion can start the timer without onPrimaryPulse ever recording a count.
        Integer pulses = pulseCount.remove(playerId);
        lastRawScreen.remove(playerId);
        return new PendingRelease(idle, pulses != null ? pulses : 0);
    }

    /** Extends drag lifetime while the cursor keeps moving between Primary interaction pulses. */
    static void noteScreenMotion(@Nonnull UUID playerId, float rawX, float rawY) {
        float[] last = lastRawScreen.get(playerId);
        if (last != null && !RtsScreenPickUtil.rawScreenMoved(rawX, rawY, last[0], last[1])) {
            return;
        }
        lastRawScreen.put(playerId, new float[] { rawX, rawY });
        lastPulseMs.put(playerId, System.currentTimeMillis());
    }
}
