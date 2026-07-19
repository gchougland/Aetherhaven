package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.shopspot.ShopSpotRecord;
import com.hexvane.aetherhaven.shopspot.ShopSpotRegistry;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Tourist browsing: stand on the customer side of shop spot blocks and face the listing. */
public final class TouristShopSpotBrowse {
    /** Prefer authored tourist visit stands within this horizontal distance of the shop spot. */
    private static final double TOURIST_VISIT_STAND_MAX_DIST_SQ = 8.0 * 8.0;

    private TouristShopSpotBrowse() {}

    @Nonnull
    public static List<ShopSpotRecord> listOnPlot(@Nonnull ShopSpotRegistry registry, @Nonnull UUID plotId) {
        List<ShopSpotRecord> out = new ArrayList<>();
        for (ShopSpotRecord record : registry.allRecords()) {
            if (plotId.equals(record.getPlotId())) {
                out.add(record);
            }
        }
        return out;
    }

    public static boolean plotHasShopSpots(@Nonnull ShopSpotRegistry registry, @Nonnull UUID plotId) {
        return !listOnPlot(registry, plotId).isEmpty();
    }

    @Nullable
    public static ShopSpotRecord pickNext(
        @Nonnull List<ShopSpotRecord> spots,
        @Nullable UUID excludeSpotId,
        @Nonnull Random random
    ) {
        if (spots.isEmpty()) {
            return null;
        }
        List<ShopSpotRecord> pool = new ArrayList<>();
        for (ShopSpotRecord spot : spots) {
            if (excludeSpotId == null || !excludeSpotId.equals(spot.getSpotId())) {
                pool.add(spot);
            }
        }
        if (pool.isEmpty()) {
            pool = spots;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * World feet position for browsing a shop spot. Keeps the current customer stand when already nearby, otherwise
     * prefers authored {@code TOURIST_VISIT} stands, then the cell in front of the listing — never behind it.
     *
     * @return {@code {x, y, z}} or null when no stand cell is found
     */
    @Nullable
    public static double[] customerStandWorld(@Nonnull World world, @Nonnull ShopSpotRecord spot) {
        return customerStandWorld(world, spot, null, null);
    }

    @Nullable
    public static double[] customerStandWorld(
        @Nonnull World world,
        @Nonnull ShopSpotRecord spot,
        @Nullable PoiRegistry poiRegistry
    ) {
        return customerStandWorld(world, spot, poiRegistry, null);
    }

    @Nullable
    public static double[] customerStandWorld(
        @Nonnull World world,
        @Nonnull ShopSpotRecord spot,
        @Nullable PoiRegistry poiRegistry,
        @Nullable Vector3d preferNear
    ) {
        return customerStandWorld(world, spot, poiRegistry, preferNear, null);
    }

    @Nullable
    public static double[] customerStandWorld(
        @Nonnull World world,
        @Nonnull ShopSpotRecord spot,
        @Nullable PoiRegistry poiRegistry,
        @Nullable Vector3d preferNear,
        @Nullable Map<String, Integer> occupancy
    ) {
        Vector3i block = spot.getBlockPosition();
        int[] forward = horizontalForwardWorld(world, block);
        double shopCx = block.x + 0.5;
        double shopCz = block.z + 0.5;

        // Stay put when already close enough to browse this listing — avoids teleports across the counter.
        if (preferNear != null) {
            double alreadyDx = preferNear.x - shopCx;
            double alreadyDz = preferNear.z - shopCz;
            if (alreadyDx * alreadyDx + alreadyDz * alreadyDz <= TOURIST_VISIT_STAND_MAX_DIST_SQ) {
                int colX = (int) Math.floor(preferNear.x);
                int colZ = (int) Math.floor(preferNear.z);
                int standY = VillagerBlockUtil.findStandY(world, colX, colZ, (int) Math.floor(preferNear.y) + 3);
                if (standY != Integer.MIN_VALUE && VillagerBlockUtil.isNpcStandColumn(world, colX, standY, colZ)) {
                    if (occupancy != null) {
                        PoiOccupancy.tryClaimCell(
                            occupancy,
                            PoiOccupancy.standCellKey(preferNear.x, preferNear.y, preferNear.z),
                            1
                        );
                    }
                    return new double[] {preferNear.x, standY + 0.02, preferNear.z};
                }
            }
        }

        // Authored tourist stands (customer side by design). Prefer the one nearest the NPC.
        if (poiRegistry != null && spot.getPlotId() != null) {
            double[] touristStand =
                nearestTouristVisitStand(
                    world,
                    poiRegistry,
                    spot.getPlotId(),
                    shopCx,
                    shopCz,
                    preferNear,
                    occupancy
                );
            if (touristStand != null) {
                return touristStand;
            }
        }

        // One block in front of the shop spot facing. Never use the reverse/merchant cell.
        double[] forwardStand = standAtOffset(world, block, forward[0], forward[2], occupancy);
        if (forwardStand != null) {
            return forwardStand;
        }

        // Side cells only (still not behind the listing).
        int[][] sideOffsets = {
            {-forward[2], forward[0]},
            {forward[2], -forward[0]}
        };
        for (int[] offset : sideOffsets) {
            double[] side = standAtOffset(world, block, offset[0], offset[1], occupancy);
            if (side != null) {
                return side;
            }
        }
        return null;
    }

    @Nullable
    private static double[] nearestTouristVisitStand(
        @Nonnull World world,
        @Nonnull PoiRegistry poiRegistry,
        @Nonnull UUID plotId,
        double shopCx,
        double shopCz,
        @Nullable Vector3d preferNear,
        @Nullable Map<String, Integer> occupancy
    ) {
        double bestScore = Double.POSITIVE_INFINITY;
        double[] best = null;
        PoiEntry bestPoi = null;
        for (PoiEntry poi : poiRegistry.allEntries()) {
            if (!plotId.equals(poi.getPlotId())) {
                continue;
            }
            if (!poi.getTags().contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT)) {
                continue;
            }
            // Never treat merchant desks as customer stands.
            if (poi.getTags().contains("WORK") || poi.getTags().contains("SHOP")) {
                continue;
            }
            if (occupancy != null
                && !PoiOccupancy.isCellAvailable(occupancy, PoiOccupancy.standCellKey(poi), poi.getCapacity())) {
                continue;
            }
            double[] stand = standFromVisitPoi(world, poi);
            if (stand == null) {
                continue;
            }
            double dx = stand[0] - shopCx;
            double dz = stand[2] - shopCz;
            double distSq = dx * dx + dz * dz;
            if (distSq > TOURIST_VISIT_STAND_MAX_DIST_SQ) {
                continue;
            }
            double score = distSq;
            if (preferNear != null) {
                double px = stand[0] - preferNear.x;
                double pz = stand[2] - preferNear.z;
                score = px * px + pz * pz;
            }
            if (score >= bestScore) {
                continue;
            }
            bestScore = score;
            best = stand;
            bestPoi = poi;
        }
        if (best != null && occupancy != null && bestPoi != null) {
            PoiOccupancy.tryClaimStand(occupancy, bestPoi);
        }
        return best;
    }

    @Nullable
    static double[] standFromVisitPoi(@Nonnull World world, @Nonnull PoiEntry poi) {
        if (poi.hasInteractionTarget()) {
            Double tx = poi.getInteractionTargetX();
            Double ty = poi.getInteractionTargetY();
            Double tz = poi.getInteractionTargetZ();
            if (tx == null || ty == null || tz == null) {
                return null;
            }
            int columnX = (int) Math.floor(tx);
            int columnZ = (int) Math.floor(tz);
            int standY = (int) Math.floor(ty);
            if (!VillagerBlockUtil.isNpcStandColumn(world, columnX, standY, columnZ)) {
                int found = VillagerBlockUtil.findStandY(world, columnX, columnZ, standY + 3);
                if (found == Integer.MIN_VALUE || !VillagerBlockUtil.isNpcStandColumn(world, columnX, found, columnZ)) {
                    return null;
                }
                return new double[] {columnX + 0.5, found + 0.02, columnZ + 0.5};
            }
            return new double[] {tx, ty, tz};
        }
        int columnX = poi.getX();
        int columnZ = poi.getZ();
        int standY = VillagerBlockUtil.findStandY(world, columnX, columnZ, poi.getY() + 3);
        if (standY == Integer.MIN_VALUE || !VillagerBlockUtil.isNpcStandColumn(world, columnX, standY, columnZ)) {
            return null;
        }
        return new double[] {columnX + 0.5, standY + 0.02, columnZ + 0.5};
    }

    @Nullable
    private static double[] standAtOffset(
        @Nonnull World world,
        @Nonnull Vector3i block,
        int ox,
        int oz,
        @Nullable Map<String, Integer> occupancy
    ) {
        int cx = block.x + ox;
        int cz = block.z + oz;
        int standY = VillagerBlockUtil.findStandY(world, cx, cz, block.y + 3);
        if (standY == Integer.MIN_VALUE) {
            return null;
        }
        if (!VillagerBlockUtil.isNpcStandColumn(world, cx, standY, cz)) {
            return null;
        }
        String cell = PoiOccupancy.cellKey(cx, standY, cz);
        if (occupancy != null && !PoiOccupancy.tryClaimCell(occupancy, cell, 1)) {
            return null;
        }
        return new double[] {cx + 0.5, standY + 0.02, cz + 0.5};
    }

    public static void faceTowardShopSpot(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ShopSpotRecord spot
    ) {
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3i block = spot.getBlockPosition();
        double dx = (block.x + 0.5) - tc.getPosition().x;
        double dz = (block.z + 0.5) - tc.getPosition().z;
        if (dx * dx + dz * dz < 1.0e-6) {
            return;
        }
        tc.getRotation().setYaw((float) (Math.atan2(dx, dz) + Math.PI));
        commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);
    }

    @Nonnull
    private static int[] horizontalForwardWorld(@Nonnull World world, @Nonnull Vector3i blockWorldPos) {
        Rotation yaw = PlotBlockRotationUtil.readBlockYaw(world, blockWorldPos);
        return switch (yaw) {
            case None -> new int[] {0, 0, -1};
            case Ninety -> new int[] {1, 0, 0};
            case OneEighty -> new int[] {0, 0, 1};
            case TwoSeventy -> new int[] {-1, 0, 0};
            default -> new int[] {0, 0, -1};
        };
    }
}
