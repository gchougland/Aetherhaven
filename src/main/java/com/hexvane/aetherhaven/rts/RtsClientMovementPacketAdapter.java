package com.hexvane.aetherhaven.rts;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Records {@link ClientMovement} modifier keys and wish vectors before {@code GamePacketHandler}
 * queues them. Custom RTS camera clients often omit {@code movementStates} from the input queue
 * but still send them on the raw packet.
 */
public final class RtsClientMovementPacketAdapter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final class Snapshot {
        public boolean hasMovementStates;
        public boolean crouching;
        public boolean sprinting;
        public boolean hasWishMovement;
        public double wishX;
        public double wishZ;
        public long sequence;
    }

    private static final ConcurrentHashMap<UUID, Snapshot> LATEST = new ConcurrentHashMap<>();

    public void register() {
        LOGGER.atInfo().log("RTS ClientMovement packet adapter registered");
    }

    public void deregister() {
        LATEST.clear();
    }

    @Nullable
    public static Snapshot poll(@Nonnull UUID playerId) {
        return LATEST.remove(playerId);
    }

    /** Observe only — the server still processes the packet. */
    public static void observe(@Nonnull PlayerRef playerRef, @Nonnull ClientMovement movement) {
        Snapshot snap = new Snapshot();
        if (movement.movementStates != null) {
            MovementStates states = movement.movementStates;
            snap.hasMovementStates = true;
            snap.crouching = states.crouching || states.forcedCrouching;
            snap.sprinting = states.sprinting;
        }
        if (movement.wishMovement != null) {
            snap.hasWishMovement = true;
            snap.wishX = movement.wishMovement.x;
            snap.wishZ = movement.wishMovement.z;
        }
        if (snap.hasMovementStates || snap.hasWishMovement) {
            snap.sequence = System.nanoTime();
            LATEST.put(playerRef.getUuid(), snap);
        }
    }
}
