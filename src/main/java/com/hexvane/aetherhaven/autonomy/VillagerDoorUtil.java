package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.math.util.TrigMathUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
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
    private static Vector3i doorPrimaryBlock(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return new Vector3i(x, y, z);
        }
        int filler = ChunkSectionBlockUtil.filler(world, x, y, z);
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
    private static final double THROUGH_ALONG_MIN = 0.72;
    /**
     * Max XZ distance (m) from the door→leash line — wide enough for diagonal approaches and double-door jambs;
     * still rejects “walking past” far beside the building.
     */
    private static final double THROUGH_PERP_MAX = 1.85;
    /** Only open doors once the NPC is this close (XZ) — avoids opening from path nodes far up the leash ray. */
    private static final double DOOR_OPEN_MAX_HORIZONTAL = 3.0;
    /** Do not auto-close while the NPC is still this close unless they are clearly through the frame. */
    private static final double DOOR_APPROACH_KEEP_OPEN_HORIZONTAL = 3.25;
    /** Block auto-close when another NPC is within this XZ range of the door (both sides of the frame). */
    private static final double DOOR_OTHER_NPC_BLOCK_HORIZONTAL = 3.25;

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
     * When pathing stalls near a doorway, close an open door and reopen with the alternate swing so the leaf does not
     * block the corridor. Runs on the world task queue (see class Javadoc).
     */
    public static void tryUnjamDoorsAlongPath(
        @Nonnull World world,
        @Nonnull Vector3d npcPos,
        @Nonnull Vector3d leashPos
    ) {
        Vector3d npcCopy = new Vector3d(npcPos);
        Vector3d leashCopy = new Vector3d(leashPos);
        world.execute(() -> tryUnjamDoorsAlongPathSync(world, npcCopy, leashCopy));
    }

    private static void tryUnjamDoorsAlongPathSync(
        @Nonnull World world,
        @Nonnull Vector3d npcPos,
        @Nonnull Vector3d leashPos
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
                        if (!isNpcNearDoor(npcPos, bx, by, bz)) {
                            continue;
                        }
                        tryUnjamDoorAt(world, npcPos, leashPos, new Vector3i(bx, by, bz));
                    }
                }
            }
        }
    }

    private static boolean isNpcNearDoor(@Nonnull Vector3d npcPos, int doorX, int doorY, int doorZ) {
        return horizontalDistanceToDoorBlock(npcPos, doorX, doorY, doorZ) <= 2.75;
    }

    /**
     * Close an open door and reopen with the opposite swing when the NPC is wedged in the frame (not while still
     * walking up from a few blocks away).
     */
    private static boolean tryUnjamDoorAt(
        @Nonnull World world,
        @Nonnull Vector3d entityPos,
        @Nonnull Vector3d leashPos,
        @Nonnull Vector3i blockPos
    ) {
        Vector3i primary = doorPrimaryBlock(world, blockPos.x, blockPos.y, blockPos.z);
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(primary.x, primary.z));
        if (chunk == null) {
            return false;
        }
        RotationTuple rotationTuple = RotationTuple.get(
            VillagerBlockUtil.rotationIndexForLoadedChunk(world, primary.x, primary.y, primary.z)
        );
        DoorInteraction.DoorInfo doorInfo = DoorInteraction.getDoorAtPosition(
            world.getChunkStore(),
            primary.x,
            primary.y,
            primary.z,
            rotationTuple.yaw()
        );
        if (doorInfo == null) {
            return false;
        }
        BlockType blockType = doorInfo.getBlockType();
        DoorState doorState = DoorState.fromBlockState(blockType.getStateForBlock(blockType));
        if (doorState == DoorState.CLOSED) {
            return tryOpenDoorAt(world, entityPos, primary);
        }
        if (isNpcApproachingDoor(entityPos, primary.x, primary.y, primary.z, leashPos)) {
            return false;
        }
        if (horizontalDistanceToDoorBlock(entityPos, primary.x, primary.y, primary.z) > 1.35) {
            return false;
        }
        if (!tryCloseDoorAt(world, primary.x, primary.y, primary.z)) {
            return false;
        }
        return tryOpenDoorAt(world, entityPos, primary);
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
        int y0 = (int) Math.floor(npcPos.y);
        int r = (int) Math.ceil(DOOR_OPEN_MAX_HORIZONTAL);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    int bx = x0 + dx;
                    int by = y0 + dy;
                    int bz = z0 + dz;
                    if (!isNpcWithinDoorOpenRange(npcPos, bx, by, bz)) {
                        continue;
                    }
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
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(primary.x, primary.z));
        if (chunk == null) {
            return false;
        }
        RotationTuple rotationTuple = RotationTuple.get(
            VillagerBlockUtil.rotationIndexForLoadedChunk(world, primary.x, primary.y, primary.z)
        );
        DoorInteraction.DoorInfo doorInfo = DoorInteraction.getDoorAtPosition(
            world.getChunkStore(),
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
        chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(primary.x, primary.z));
        if (chunk == null) {
            return false;
        }
        rotationTuple = RotationTuple.get(
            VillagerBlockUtil.rotationIndexForLoadedChunk(world, primary.x, primary.y, primary.z)
        );
        doorInfo = DoorInteraction.getDoorAtPosition(world.getChunkStore(), primary.x, primary.y, primary.z, rotationTuple.yaw());
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
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z));
        if (chunk == null) {
            return false;
        }
        RotationTuple rotationTuple = RotationTuple.get(
            VillagerBlockUtil.rotationIndexForLoadedChunk(world, blockPos.x, blockPos.y, blockPos.z)
        );
        DoorInteraction.DoorInfo doorInfo = DoorInteraction.getDoorAtPosition(
            world.getChunkStore(),
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
        if (!canOpenDoor(world, blockPos, interactionState)) {
            return false;
        }
        return activateDoor(world, blockType, blockPos, DoorState.CLOSED, targetOpen, interactionState);
    }

    /**
     * Closes a door if it is currently open (OPENED_IN / OPENED_OUT). Retries with the alternate close interaction if
     * the first attempt does not reach {@link DoorState#CLOSED} (some layouts / states are finicky).
     */
    public static boolean tryCloseDoorAt(@Nonnull World world, int x, int y, int z) {
        Vector3i pos = doorPrimaryBlock(world, x, y, z);
        for (int attempt = 0; attempt < 2; attempt++) {
            WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            if (chunk == null) {
                return false;
            }
            RotationTuple rotationTuple = RotationTuple.get(
                VillagerBlockUtil.rotationIndexForLoadedChunk(world, pos.x, pos.y, pos.z)
            );
            DoorInteraction.DoorInfo doorInfo = DoorInteraction.getDoorAtPosition(
                world.getChunkStore(), pos.x, pos.y, pos.z, rotationTuple.yaw()
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
            if (!canOpenDoor(world, pos, interactionState)) {
                continue;
            }
            activateDoor(world, blockType, pos, doorState, DoorState.CLOSED, interactionState);
            BlockType afterType = ChunkSectionBlockUtil.blockType(world, pos.x, pos.y, pos.z);
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

    private static boolean canOpenDoor(@Nonnull World world, @Nonnull Vector3i blockPosition, @Nonnull String state) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z));
        if (chunk == null) {
            return false;
        }
        BlockType originalBlockType = ChunkSectionBlockUtil.blockType(world, blockPosition.x, blockPosition.y, blockPosition.z);
        if (originalBlockType == null) {
            return false;
        }
        BlockType variantBlockType = originalBlockType.getBlockForState(state);
        if (variantBlockType == null) {
            return false;
        }
        int rotation = VillagerBlockUtil.rotationIndexForLoadedChunk(world, blockPosition.x, blockPosition.y, blockPosition.z);
        BlockSection section =
            ChunkSectionBlockUtil.blockSectionAt(world, blockPosition.x, blockPosition.y, blockPosition.z);
        if (section == null) {
            return false;
        }
        return BlockOperations.testPlaceBlock(
            world.getChunkStore().getStore(),
            section,
            blockPosition.x,
            blockPosition.y,
            blockPosition.z,
            variantBlockType,
            rotation,
            (blockX, blockY, blockZ, _blockType, _rotation, filler) -> {
            if (filler != 0) {
                blockX -= FillerBlockUtil.unpackX(filler);
                blockY -= FillerBlockUtil.unpackY(filler);
                blockZ -= FillerBlockUtil.unpackZ(filler);
            }
            return blockX == blockPosition.x && blockY == blockPosition.y && blockZ == blockPosition.z;
        });
    }

    /**
     * Closes doors this NPC opened once it has reached its travel goal and no other NPC is using the doorway. Pending
     * entries that cannot close yet (another NPC nearby) stay in the list for a later arrival. Runs on the world task
     * queue (see class Javadoc).
     */
    public static void closePendingDoorsAfterGoalReached(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID openerEntityUuid,
        @Nonnull Vector3d openerPos,
        @Nonnull Vector3d goalPos,
        double goalArriveHorizontalSq,
        @Nonnull ArrayList<int[]> pendingOpenDoors
    ) {
        if (pendingOpenDoors.isEmpty()) {
            return;
        }
        double dx = openerPos.x - goalPos.x;
        double dz = openerPos.z - goalPos.z;
        if (dx * dx + dz * dz > goalArriveHorizontalSq) {
            return;
        }
        world.execute(() ->
            closePendingDoorsAfterGoalReachedSync(world, store, openerEntityUuid, pendingOpenDoors)
        );
    }

    private static void closePendingDoorsAfterGoalReachedSync(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID openerEntityUuid,
        @Nonnull ArrayList<int[]> pendingOpenDoors
    ) {
        Iterator<int[]> it = pendingOpenDoors.iterator();
        while (it.hasNext()) {
            int[] d = it.next();
            if (isOtherNpcNearDoor(store, openerEntityUuid, d[0], d[1], d[2])) {
                continue;
            }
            if (tryCloseDoorAt(world, d[0], d[1], d[2])) {
                it.remove();
            }
        }
    }

    /**
     * While traveling through a doorway, ignore separation steering from other NPCs also using that frame so push-apart
     * forces do not wedge them in a 1-block opening. Must run after {@code ignoredEntitiesForAvoidance} is cleared and
     * before {@link com.hypixel.hytale.server.npc.systems.AvoidanceSystem} blends separation.
     */
    public static void applyDoorwaySeparationBypass(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> selfRef,
        @Nonnull Role role,
        @Nonnull Vector3d selfPos
    ) {
        if (!role.isApplySeparation()) {
            return;
        }
        ArrayList<int[]> doors = collectDoorPrimariesNearNpc(world, selfPos, DOOR_OTHER_NPC_BLOCK_HORIZONTAL);
        if (doors.isEmpty()) {
            return;
        }
        Set<Ref<EntityStore>> ignored = role.getIgnoredEntitiesForAvoidance();
        store.forEachChunk(
            Query.and(TransformComponent.getComponentType(), NPCEntity.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> otherRef = archetypeChunk.getReferenceTo(i);
                    if (otherRef == null || otherRef.equals(selfRef) || !otherRef.isValid()) {
                        continue;
                    }
                    TransformComponent tc = archetypeChunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d otherPos = tc.getPosition();
                    for (int[] door : doors) {
                        if (horizontalDistanceToDoorBlock(otherPos, door[0], door[1], door[2]) <= DOOR_OTHER_NPC_BLOCK_HORIZONTAL) {
                            ignored.add(otherRef);
                            break;
                        }
                    }
                }
                return false;
            }
        );
    }

    @Nonnull
    private static ArrayList<int[]> collectDoorPrimariesNearNpc(
        @Nonnull World world,
        @Nonnull Vector3d npcPos,
        double maxHorizontal
    ) {
        ArrayList<int[]> out = new ArrayList<>();
        int x0 = (int) Math.floor(npcPos.x);
        int z0 = (int) Math.floor(npcPos.z);
        int y0 = (int) Math.floor(npcPos.y);
        int r = (int) Math.ceil(maxHorizontal);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    int bx = x0 + dx;
                    int by = y0 + dy;
                    int bz = z0 + dz;
                    if (horizontalDistanceToDoorBlock(npcPos, bx, by, bz) > maxHorizontal) {
                        continue;
                    }
                    Vector3i primary = doorPrimaryBlock(world, bx, by, bz);
                    if (!isDoorPrimaryBlock(world, primary)) {
                        continue;
                    }
                    if (containsDoorPrimary(out, primary.x, primary.y, primary.z)) {
                        continue;
                    }
                    out.add(new int[] { primary.x, primary.y, primary.z });
                }
            }
        }
        return out;
    }

    private static boolean isDoorPrimaryBlock(@Nonnull World world, @Nonnull Vector3i primary) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(primary.x, primary.z));
        if (chunk == null) {
            return false;
        }
        RotationTuple rotationTuple = RotationTuple.get(
            VillagerBlockUtil.rotationIndexForLoadedChunk(world, primary.x, primary.y, primary.z)
        );
        return DoorInteraction.getDoorAtPosition(
            world.getChunkStore(),
            primary.x,
            primary.y,
            primary.z,
            rotationTuple.yaw()
        ) != null;
    }

    private static boolean containsDoorPrimary(@Nonnull ArrayList<int[]> doors, int x, int y, int z) {
        for (int[] door : doors) {
            if (door[0] == x && door[1] == y && door[2] == z) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when a different {@link NPCEntity} is close enough to this door that closing it would cut off their path.
     */
    private static boolean isOtherNpcNearDoor(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID excludeEntityUuid,
        int doorX,
        int doorY,
        int doorZ
    ) {
        boolean[] occupied = { false };
        store.forEachChunk(
            Query.and(
                TransformComponent.getComponentType(),
                NPCEntity.getComponentType(),
                UUIDComponent.getComponentType()
            ),
            (archetypeChunk, commandBuffer) -> {
                if (occupied[0]) {
                    return true;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || excludeEntityUuid.equals(uc.getUuid())) {
                        continue;
                    }
                    TransformComponent tc = archetypeChunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    if (horizontalDistanceToDoorBlock(tc.getPosition(), doorX, doorY, doorZ) <= DOOR_OTHER_NPC_BLOCK_HORIZONTAL) {
                        occupied[0] = true;
                        return true;
                    }
                }
                return false;
            }
        );
        return occupied[0];
    }

    /** True while the NPC is still near the doorway and has not crossed to the leash side — keep the door open. */
    private static boolean isNpcApproachingDoor(
        @Nonnull Vector3d npcPos,
        int doorX,
        int doorY,
        int doorZ,
        @Nonnull Vector3d leashPos
    ) {
        if (isNpcThroughDoorTowardLeash(npcPos, doorX, doorY, doorZ, leashPos)) {
            return false;
        }
        return horizontalDistanceToDoorBlock(npcPos, doorX, doorY, doorZ) <= DOOR_APPROACH_KEEP_OPEN_HORIZONTAL;
    }

    private static boolean isNpcWithinDoorOpenRange(@Nonnull Vector3d npcPos, int doorX, int doorY, int doorZ) {
        if (Math.abs(npcPos.y - doorY) > 2.5) {
            return false;
        }
        return horizontalDistanceToDoorBlock(npcPos, doorX, doorY, doorZ) <= DOOR_OPEN_MAX_HORIZONTAL;
    }

    private static double horizontalDistanceToDoorBlock(@Nonnull Vector3d npcPos, int doorX, int doorY, int doorZ) {
        if (Math.abs(npcPos.y - doorY) > 3.5) {
            return Double.POSITIVE_INFINITY;
        }
        double cx = doorX + 0.5;
        double cz = doorZ + 0.5;
        double dx = npcPos.x - cx;
        double dz = npcPos.z - cz;
        return Math.hypot(dx, dz);
    }

    /** Strict corridor test — used by door-unjam only (auto-close waits for travel goal arrival). */
    private static boolean shouldCloseDoorBehindNpc(
        @Nonnull Vector3d npcPos,
        int doorX,
        int doorY,
        int doorZ,
        @Nonnull Vector3d leashPos
    ) {
        return isNpcThroughDoorTowardLeash(npcPos, doorX, doorY, doorZ, leashPos);
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
        Vector3d blockCenter = new Vector3d(blockPosition.x() + 0.5, blockPosition.y() + 0.5, blockPosition.z() + 0.5);
        Vector3d direction = Vector3dUtil.directionTo(blockCenter, playerPosition);
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
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z));
        if (chunk == null) {
            return false;
        }
        int rotationIndex = VillagerBlockUtil.rotationIndexForLoadedChunk(world, blockPosition.x, blockPosition.y, blockPosition.z);
        BlockBoundingBoxes oldHitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        world.setBlockInteractionState(blockPosition, blockType, interactionStateToSend);
        BlockType currentBlockType = ChunkSectionBlockUtil.blockType(world, blockPosition.x, blockPosition.y, blockPosition.z);
        if (currentBlockType == null) {
            return false;
        }
        BlockType newBlockType = currentBlockType.getBlockForState(interactionStateToSend);
        if (oldHitbox != null) {
            FillerBlockUtil.forEachFillerBlock(
                oldHitbox.get(rotationIndex),
                (x, y, z) -> ChunkSectionBlockUtil.performBlockUpdate(world, blockPosition.x + x, blockPosition.y + y, blockPosition.z + z)
            );
        }

        if (newBlockType != null) {
            BlockBoundingBoxes newHitbox = BlockBoundingBoxes.getAssetMap().getAsset(newBlockType.getHitboxTypeIndex());
            if (newHitbox != null && newHitbox != oldHitbox) {
                FillerBlockUtil.forEachFillerBlock(
                    newHitbox.get(rotationIndex),
                    (x, y, z) -> ChunkSectionBlockUtil.performBlockUpdate(world, blockPosition.x + x, blockPosition.y + y, blockPosition.z + z)
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
