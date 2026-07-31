package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.construction.PrefabYaw;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Resolves world positions and facing for plot creator stand spawns and POI anchors from a block click. */
public final class PlotCreatorSpotPlacement {
    public enum SpotRole {
        STAND_SPAWN,
        POI_ANCHOR
    }

    public record ResolvedSpot(
        @Nonnull Vector3i worldBlock,
        @Nullable Float worldYawRadians,
        @Nonnull SpotRole role,
        @Nonnull Vector3i supportBlock
    ) {}

    private PlotCreatorSpotPlacement() {}

    /** Stand cell for visitor, innkeeper, guild master, or adventurer spawn substeps. */
    @Nonnull
    public static ResolvedSpot resolveStandSpawn(@Nonnull World world, @Nonnull Vector3i clickedBlock) {
        Vector3i support = VillagerBlockUtil.resolveSupportBlockFromClick(world, clickedBlock);
        Vector3i stand = VillagerBlockUtil.resolveStandBlockFromSupport(world, support);
        return new ResolvedSpot(stand, null, SpotRole.STAND_SPAWN, support);
    }

    /**
     * Stand cell plus optional seat-forward yaw (prefab-local radians) for adventurer spawns.
     */
    @Nonnull
    public static ResolvedSpot resolveAdventurerSpawn(
        @Nonnull World world,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i clickedBlock,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        ResolvedSpot stand = resolveStandSpawn(world, clickedBlock);
        Float worldYaw = resolveAdventurerSpawnYaw(world, draft, stand.worldBlock(), playerRef, store);
        return new ResolvedSpot(stand.worldBlock(), worldYaw, SpotRole.STAND_SPAWN, stand.supportBlock());
    }

    /** POI anchor block and optional seat-forward yaw for mount blocks. */
    @Nonnull
    public static ResolvedSpot resolvePoiAnchor(@Nonnull World world, @Nonnull Vector3i clickedBlock) {
        Vector3i support = VillagerBlockUtil.resolveSupportBlockFromClick(world, clickedBlock);
        Vector3i anchor = support;
        if (VillagerBlockUtil.isBlockMountSeat(world, support.x, support.y, support.z)) {
            anchor = VillagerBlockUtil.resolveMountBaseBlock(world, support.x, support.y, support.z);
        }
        Float seatYaw = VillagerBlockUtil.seatForwardYawRadians(world, anchor);
        return new ResolvedSpot(anchor, seatYaw, SpotRole.POI_ANCHOR, support);
    }

    @Nullable
    private static Float resolveAdventurerSpawnYaw(
        @Nonnull World world,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i standBlock,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        Vector3d standCenter = new Vector3d(standBlock.x + 0.5, standBlock.y, standBlock.z + 0.5);
        Vector3i seatBlock = VillagerBlockUtil.findGuildHallSeatBelowSpawn(world, standCenter);
        if (seatBlock != null) {
            Float seatYaw = VillagerBlockUtil.seatForwardYawRadians(world, seatBlock);
            if (seatYaw != null) {
                return seatYaw;
            }
        }
        int[] prefabLocal = PlotCreatorPrefabCoords.standPrefabLocal(draft, standBlock);
        return PlotCreatorPrefabCoords.standPrefabYawFacingPlayer(
            draft,
            playerRef,
            store,
            prefabLocal[0],
            prefabLocal[1],
            prefabLocal[2]
        );
    }

    /** Converts a world yaw to prefab-local radians for adventurer spawn JSON. */
    public static float prefabYawFromWorld(
        @Nonnull PlotCreatorDraft draft,
        float worldYawRadians
    ) {
        Rotation placement = PlotCreatorPrefabCoords.placementYaw(draft);
        return PrefabYaw.prefabFromWorld(placement, worldYawRadians);
    }

    /**
     * True when {@code clickedBlock} matches a saved stand spawn or its supporting surface block.
     */
    public static boolean matchesStandSpawnClick(
        @Nonnull World world,
        @Nonnull Vector3i clickedBlock,
        @Nonnull Vector3i savedStandBlock
    ) {
        if (sameBlock(savedStandBlock, clickedBlock)) {
            return true;
        }
        Vector3i support = VillagerBlockUtil.resolveSupportBlockFromClick(world, clickedBlock);
        Vector3i standFromClick = VillagerBlockUtil.resolveStandBlockFromSupport(world, support);
        if (sameBlock(savedStandBlock, standFromClick)) {
            return true;
        }
        Vector3i supportBelowStand =
            findSupportBelowStand(world, savedStandBlock.x, savedStandBlock.y, savedStandBlock.z);
        return supportBelowStand != null && sameBlock(supportBelowStand, clickedBlock);
    }

    @Nullable
    private static Vector3i findSupportBelowStand(@Nonnull World world, int standX, int standY, int standZ) {
        for (int dy = 1; dy <= 6; dy++) {
            int sy = standY - dy;
            if (sy < 0) {
                break;
            }
            Vector3i candidate = new Vector3i(standX, sy, standZ);
            Vector3i standFromSupport = VillagerBlockUtil.resolveStandBlockFromSupport(world, candidate);
            if (standFromSupport.y == standY && standFromSupport.x == standX && standFromSupport.z == standZ) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean sameBlock(@Nonnull Vector3i a, @Nonnull Vector3i b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }
}
