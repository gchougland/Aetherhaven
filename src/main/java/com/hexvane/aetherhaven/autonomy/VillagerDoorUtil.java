package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.TrigMathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.ArrayList;
import java.util.Iterator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Opens/closes doors for villager autonomy without running the full {@link
 * com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction} pipeline (no
 * InteractionContext). Logic mirrors that class so pathfinding is not swapped to a different motion mode.
 *
 * <p>Block updates are scheduled with {@link World#execute(Runnable)} so they run after the entity store tick — {@code
 * setBlockInteractionState} can load chunks and touch the entity store, which must not happen during {@link
 * com.hypixel.hytale.component.system.tick.EntityTickingSystem} execution.
 */
public final class VillagerDoorUtil {
    private VillagerDoorUtil() {}

    /**
     * Multi-block doors store secondary segments with non-zero filler pointing at the primary (hinge) cell.
     * {@link com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction#activateDoor}
     * applies hitbox offsets from {@code blockPosition}; using a filler segment as origin shifts or misplaces the door.
     */
    @Nonnull
    @SuppressWarnings({ "deprecation", "removal" })
    private static Vector3i doorPrimaryBlock(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return new Vector3i(x, y, z);
        }
        int filler = chunk.getFiller(x, y, z);
        if (filler == 0) {
            return new Vector3i(x, y, z);
        }
        return new Vector3i(
            x - FillerBlockUtil.unpackX(filler),
            y - FillerBlockUtil.unpackY(filler),
            z - FillerBlockUtil.unpackZ(filler)
        );
    }

    /**
     * Min XZ distance (m) past the door block center along the door→leash axis — NPC is on the leash side of the
     * doorway plane.
     */
    private static final double THROUGH_ALONG_MIN = 0.22;
    /**
     * Max XZ distance (m) from the door→leash line — wide enough for diagonal approaches and double-door jambs;
     * still rejects “walking past” far beside the building.
     */
    private static final double THROUGH_PERP_MAX = 2.35;

    private enum DoorState {
        CLOSED,
        OPENED_IN,
        OPENED_OUT;

        /**
         * Uses substring match — {@link BlockType#getStateForBlock} for doors like {@code Furniture_Village_Door} may
         * not equal the short ids {@code OpenDoorOut}/{@code OpenDoorIn} exactly.
         */
        @Nonnull
        static DoorState fromBlockState(@Nullable String state) {
            if (state == null || state.isEmpty()) {
                return CLOSED;
            }
            if (state.contains("OpenDoorOut")) {
                return OPENED_IN;
            }
            if (state.contains("OpenDoorIn")) {
                return OPENED_OUT;
            }
            return CLOSED;
        }
    }

    /**
     * Scans a short segment from NPC toward the leash plus a small neighborhood for closed doors and opens them.
     * Runs the scan on the world task queue (see class Javadoc).
     *
     * @param onOpened optional callback with door block position (for closing behind later)
     */
    public static void tryOpenDoorsTowardLeash(
        @Nonnull World world,
        @Nonnull Vector3d npcPos,
        @Nonnull Vector3d leashPos,
        @Nullable DoorOpenedCallback onOpened
    ) {
        Vector3d npcCopy = new Vector3d(npcPos);
        Vector3d leashCopy = new Vector3d(leashPos);
        world.execute(() -> tryOpenDoorsTowardLeashSync(world, npcCopy, leashCopy, onOpened));
    }

    private static void tryOpenDoorsTowardLeashSync(
        @Nonnull World world,
        @Nonnull Vector3d npcPos,
        @Nonnull Vector3d leashPos,
        @Nullable DoorOpenedCallback onOpened
    ) {
        int x0 = (int) Math.floor(npcPos.x);
        int z0 = (int) Math.floor(npcPos.z);
        int x1 = (int) Math.floor(leashPos.x);
        int z1 = (int) Math.floor(leashPos.z);
        int y0 = (int) Math.floor(npcPos.y);
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)) + 3;
        for (int s = 0; s <= steps; s++) {
            double t = steps == 0 ? 0.0 : (double) s / (double) steps;
            int cx = (int) Math.floor(x0 + (x1 - x0) * t);
            int cz = (int) Math.floor(z0 + (z1 - z0) * t);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        int bx = cx + dx;
                        int by = y0 + dy;
                        int bz = cz + dz;
                        if (shouldCloseDoorBehindNpc(npcPos, bx, by, bz, leashPos)) {
                            continue;
                        }
                        if (tryOpenDoorAt(world, npcPos, new Vector3i(bx, by, bz))) {
                            if (onOpened != null) {
                                Vector3i primary = doorPrimaryBlock(world, bx, by, bz);
                                onOpened.onOpened(primary.x, primary.y, primary.z);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Callback for the last door block opened (world cell). */
    @FunctionalInterface
    public interface DoorOpenedCallback {
        void onOpened(int x, int y, int z);
    }

    /**
     * If the block is a closed door, opens it. Tries the swing direction from {@code DoorInteraction} (in-front test),
     * then the opposite swing if the first attempt leaves the door closed (covers gates and odd layouts).
     */
    public static boolean tryOpenDoorAt(@Nonnull World world, @Nonnull Vector3d entityPos, @Nonnull Vector3i blockPos) {
        Vector3i primary = doorPrimaryBlock(world, blockPos.x, blockPos.y, blockPos.z);
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(primary.x, primary.z));
        if (chunk == null) {
            return false;
        }
        RotationTuple rotationTuple = RotationTuple.get(
            VillagerBlockUtil.rotationIndexForLoadedChunk(chunk, primary.x, primary.y, primary.z)
        );
        DoorInteraction.DoorInfo doorInfo = DoorInteraction.getDoorAtPosition(
            world,
            primary.x,
            primary.y,
            primary.z,
            rotationTuple.yaw()
        );
        if (doorInfo == null) {
            return false;
        }
        BlockType blockType = doorInfo.getBlockType();
        if (DoorState.fromBlockState(blockType.getStateForBlock(blockType)) != DoorState.CLOSED) {
            return false;
        }
        DoorState primarySwing = isInFrontOfDoor(primary, rotationTuple.yaw(), entityPos) ? DoorState.OPENED_OUT : DoorState.OPENED_IN;
        DoorState alternate = primarySwing == DoorState.OPENED_OUT ? DoorState.OPENED_IN : DoorState.OPENED_OUT;
        if (tryOpenClosedDoor(world, primary, primarySwing)) {
            return true;
        }
        chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(primary.x, primary.z));
        if (chunk == null) {
            return false;
        }
        rotationTuple = RotationTuple.get(
            VillagerBlockUtil.rotationIndexForLoadedChunk(chunk, primary.x, primary.y, primary.z)
        );
        doorInfo = DoorInteraction.getDoorAtPosition(world, primary.x, primary.y, primary.z, rotationTuple.yaw());
        if (doorInfo == null) {
            return false;
        }
        blockType = doorInfo.getBlockType();
        if (DoorState.fromBlockState(blockType.getStateForBlock(blockType)) != DoorState.CLOSED) {
            return false;
        }
        return tryOpenClosedDoor(world, primary, alternate);
    }

    private static boolean tryOpenClosedDoor(@Nonnull World world, @Nonnull Vector3i blockPos, @Nonnull DoorState targetOpen) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z));
        if (chunk == null) {
            return false;
        }
        RotationTuple rotationTuple = RotationTuple.get(
            VillagerBlockUtil.rotationIndexForLoadedChunk(chunk, blockPos.x, blockPos.y, blockPos.z)
        );
        DoorInteraction.DoorInfo doorInfo = DoorInteraction.getDoorAtPosition(
            world,
            blockPos.x,
            blockPos.y,
            blockPos.z,
            rotationTuple.yaw()
        );
        if (doorInfo == null) {
            return false;
        }
        BlockType blockType = doorInfo.getBlockType();
        if (DoorState.fromBlockState(blockType.getStateForBlock(blockType)) != DoorState.CLOSED) {
            return false;
        }
        String interactionState = interactionStateForTransition(DoorState.CLOSED, targetOpen);
        return activateDoor(world, blockType, blockPos, DoorState.CLOSED, targetOpen, interactionState);
    }

    /**
     * Closes a door if it is currently open (OPENED_IN / OPENED_OUT). Retries with the alternate close interaction if
     * the first attempt does not reach {@link DoorState#CLOSED} (some layouts / states are finicky).
     */
    public static boolean tryCloseDoorAt(@Nonnull World world, int x, int y, int z) {
        Vector3i pos = doorPrimaryBlock(world, x, y, z);
        for (int attempt = 0; attempt < 2; attempt++) {
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            if (chunk == null) {
                return false;
            }
            RotationTuple rotationTuple = RotationTuple.get(
                VillagerBlockUtil.rotationIndexForLoadedChunk(chunk, pos.x, pos.y, pos.z)
            );
            DoorInteraction.DoorInfo doorInfo = DoorInteraction.getDoorAtPosition(
                world, pos.x, pos.y, pos.z, rotationTuple.yaw()
            );
            if (doorInfo == null) {
                return false;
            }
            BlockType blockType = doorInfo.getBlockType();
            String blockState = blockType.getStateForBlock(blockType);
            DoorState doorState = DoorState.fromBlockState(blockState);
            if (doorState == DoorState.CLOSED) {
                return true;
            }
            String primary = interactionStateForTransition(doorState, DoorState.CLOSED);
            String interactionState = attempt == 0 ? primary : alternateCloseInteraction(primary);
            activateDoor(world, blockType, pos, doorState, DoorState.CLOSED, interactionState);
            BlockType afterType = world.getBlockType(pos);
            if (afterType != null && DoorState.fromBlockState(afterType.getStateForBlock(afterType)) == DoorState.CLOSED) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String alternateCloseInteraction(@Nonnull String primary) {
        return "CloseDoorOut".equals(primary) ? "CloseDoorIn" : "CloseDoorOut";
    }

    /**
     * For each door in {@code pendingOpenDoors} that the NPC has passed through (toward the leash), closes it and
     * removes it from the list. Supports multiple doors on one route; does not wait for the POI. Runs on the world task
     * queue (see class Javadoc).
     */
    public static void closePendingDoorsWhenPassed(
        @Nonnull World world,
        @Nonnull Vector3d npcPos,
        @Nonnull Vector3d leashPos,
        @Nonnull ArrayList<int[]> pendingOpenDoors
    ) {
        Vector3d npcCopy = new Vector3d(npcPos);
        Vector3d leashCopy = new Vector3d(leashPos);
        world.execute(() -> closePendingDoorsWhenPassedSync(world, npcCopy, leashCopy, pendingOpenDoors));
    }

    private static void closePendingDoorsWhenPassedSync(
        @Nonnull World world,
        @Nonnull Vector3d npcPos,
        @Nonnull Vector3d leashPos,
        @Nonnull ArrayList<int[]> pendingOpenDoors
    ) {
        Iterator<int[]> it = pendingOpenDoors.iterator();
        while (it.hasNext()) {
            int[] d = it.next();
            if (shouldCloseDoorBehindNpc(npcPos, d[0], d[1], d[2], leashPos)) {
                if (tryCloseDoorAt(world, d[0], d[1], d[2])) {
                    it.remove();
                }
            }
        }
    }

    /** Strict corridor test or looser “past door toward leash” (single doors, offset paths). */
    private static boolean shouldCloseDoorBehindNpc(
        @Nonnull Vector3d npcPos,
        int doorX,
        int doorY,
        int doorZ,
        @Nonnull Vector3d leashPos
    ) {
        return isNpcThroughDoorTowardLeash(npcPos, doorX, doorY, doorZ, leashPos)
            || isNpcPastDoorTowardLeashLoose(npcPos, doorX, doorY, doorZ, leashPos);
    }

    /**
     * No perpendicular cap: once the NPC is clearly past the door block along the door→leash axis and not standing
     * inside the door cell, close behind them (handles diagonal paths where {@link #isNpcThroughDoorTowardLeash} fails).
     */
    private static boolean isNpcPastDoorTowardLeashLoose(
        @Nonnull Vector3d npcPos,
        int doorX,
        int doorY,
        int doorZ,
        @Nonnull Vector3d leashPos
    ) {
        double cx = doorX + 0.5;
        double cz = doorZ + 0.5;
        double ldx = leashPos.x - cx;
        double ldz = leashPos.z - cz;
        double len = Math.hypot(ldx, ldz);
        if (len < 0.08) {
            return false;
        }
        if (Math.abs(npcPos.y - doorY) > 3.5) {
            return false;
        }
        double ux = ldx / len;
        double uz = ldz / len;
        double px = npcPos.x - cx;
        double pz = npcPos.z - cz;
        double along = px * ux + pz * uz;
        double dist = Math.hypot(px, pz);
        return along > 0.18 && dist > 0.42;
    }

    /**
     * True when the NPC is clearly on the leash side of this door’s opening plane (XZ), still within a narrow
     * corridor along door→leash so we do not close for someone walking past the building sideways.
     */
    static boolean isNpcThroughDoorTowardLeash(
        @Nonnull Vector3d npcPos,
        int doorX,
        int doorY,
        int doorZ,
        @Nonnull Vector3d leashPos
    ) {
        double cx = doorX + 0.5;
        double cz = doorZ + 0.5;
        double ldx = leashPos.x - cx;
        double ldz = leashPos.z - cz;
        double len = Math.hypot(ldx, ldz);
        if (len < 1e-4) {
            return false;
        }
        if (Math.abs(npcPos.y - doorY) > 3.5) {
            return false;
        }
        double ux = ldx / len;
        double uz = ldz / len;
        double px = npcPos.x - cx;
        double pz = npcPos.z - cz;
        double along = px * ux + pz * uz;
        double perp = Math.abs(px * uz - pz * ux);
        return along > THROUGH_ALONG_MIN && perp < THROUGH_PERP_MAX;
    }

    private static boolean isInFrontOfDoor(
        @Nonnull Vector3i blockPosition,
        @Nullable Rotation doorRotationYaw,
        @Nonnull Vector3d playerPosition
    ) {
        double doorRotationRad = Math.toRadians(doorRotationYaw != null ? doorRotationYaw.getDegrees() : 0.0);
        Vector3d doorRotationVector = new Vector3d(TrigMathUtil.sin(doorRotationRad), 0.0, TrigMathUtil.cos(doorRotationRad));
        Vector3d direction = Vector3d.directionTo(blockPosition, playerPosition);
        return direction.dot(doorRotationVector) < 0.0;
    }

    @Nonnull
    private static String interactionStateForTransition(@Nonnull DoorState fromState, @Nonnull DoorState doorState) {
        if (doorState == DoorState.CLOSED && fromState == DoorState.OPENED_IN) {
            return "CloseDoorOut";
        }
        if (doorState == DoorState.CLOSED && fromState == DoorState.OPENED_OUT) {
            return "CloseDoorIn";
        }
        if (doorState == DoorState.OPENED_IN) {
            return "OpenDoorOut";
        }
        return "OpenDoorIn";
    }

    /**
     * Mirrors {@code DoorInteraction.activateDoor} — applies interaction state and hitbox/filler updates.
     */
    private static boolean activateDoor(
        @Nonnull World world,
        @Nonnull BlockType blockType,
        @Nonnull Vector3i blockPosition,
        @Nonnull DoorState fromState,
        @Nonnull DoorState doorState,
        @Nonnull String interactionStateToSend
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z));
        if (chunk == null) {
            return false;
        }
        int rotationIndex = VillagerBlockUtil.rotationIndexForLoadedChunk(chunk, blockPosition.x, blockPosition.y, blockPosition.z);
        BlockBoundingBoxes oldHitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        world.setBlockInteractionState(blockPosition, blockType, interactionStateToSend);
        BlockType currentBlockType = world.getBlockType(blockPosition);
        if (currentBlockType == null) {
            return false;
        }
        BlockType newBlockType = currentBlockType.getBlockForState(interactionStateToSend);
        if (oldHitbox != null) {
            FillerBlockUtil.forEachFillerBlock(
                oldHitbox.get(rotationIndex),
                (x, y, z) -> world.performBlockUpdate(blockPosition.x + x, blockPosition.y + y, blockPosition.z + z)
            );
        }

        if (newBlockType != null) {
            BlockBoundingBoxes newHitbox = BlockBoundingBoxes.getAssetMap().getAsset(newBlockType.getHitboxTypeIndex());
            if (newHitbox != null && newHitbox != oldHitbox) {
                FillerBlockUtil.forEachFillerBlock(
                    newHitbox.get(rotationIndex),
                    (x, y, z) -> world.performBlockUpdate(blockPosition.x + x, blockPosition.y + y, blockPosition.z + z)
                );
            }
        }

        DoorState after = DoorState.fromBlockState(currentBlockType.getStateForBlock(currentBlockType));
        if (fromState == DoorState.CLOSED && doorState != DoorState.CLOSED) {
            return after != DoorState.CLOSED;
        }
        return fromState != DoorState.CLOSED && doorState == DoorState.CLOSED ? after == DoorState.CLOSED : true;
    }
}
