package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.BlacksmithShopCompletion;
import com.hexvane.aetherhaven.inn.FarmerPlotCompletion;
import com.hexvane.aetherhaven.inn.MerchantStallCompletion;
import com.hexvane.aetherhaven.poi.PoiExtractor;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hexvane.aetherhaven.plot.TreasuryBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class ConstructionCompleter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MANAGEMENT_PLACE_SETTINGS = 10;

    private ConstructionCompleter() {}

    /**
     * Run on the world thread after {@link com.hexvane.aetherhaven.prefab.ConstructionAnimator} finishes
     * (plot sign already removed).
     */
    public static void finishBuild(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID ownerUuid,
        @Nonnull UUID plotId,
        @Nonnull Vector3i prefabAnchorWorld,
        @Nonnull Rotation prefabYaw
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForOwnerInWorld(ownerUuid);
        if (town == null) {
            LOGGER.atWarning().log("Construction complete but no town for owner %s", ownerUuid);
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null) {
            LOGGER.atWarning().log("Construction complete but plot %s missing in town %s", plotId, town.getTownId());
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            LOGGER.atWarning().log("Unknown construction id %s for plot %s", plot.getConstructionId(), plotId);
        }

        long now = System.currentTimeMillis();
        plot.setState(PlotInstanceState.COMPLETE);
        plot.setLastStateChangeEpochMs(now);
        plot.setPrefabWorldPlacement(prefabAnchorWorld.x, prefabAnchorWorld.y, prefabAnchorWorld.z, prefabYaw);
        tm.updateTown(town);

        if (def != null) {
            PoiExtractor.registerForCompletedBuild(plugin, world, town, plotId, def.getId(), prefabAnchorWorld, prefabYaw);
            stampManagementBlock(world, town, plotId, def, prefabAnchorWorld, prefabYaw);
            stampTreasuryBlock(world, town, plotId, def, prefabAnchorWorld, prefabYaw);
            if (AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL.equals(def.getId())) {
                MerchantStallCompletion.onStallBuilt(world, plugin, town, plotId, tm);
            }
            if (AetherhavenConstants.CONSTRUCTION_PLOT_FARM.equals(def.getId())) {
                FarmerPlotCompletion.onFarmBuilt(world, plugin, town, plotId, tm);
            }
            if (AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP.equals(def.getId())) {
                BlacksmithShopCompletion.onShopBuilt(world, plugin, town, plotId, tm);
            }
        }
    }

    private static void stampManagementBlock(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        int[] local = def.getManagementBlockLocalPos();
        if (local == null) {
            return;
        }
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Management block chunk not loaded at %s,%s,%s", wx, wy, wz);
            return;
        }

        /*
         * Prefab paste uses placeBlock + optional setState(holder); that often does not match a player-placed mod
         * block, so OpenCustomUI from the block type never runs. Re-place like {@link
         * com.hexvane.aetherhaven.placement.PlotPlacementCommit#placePlotSign} to get a proper block entity +
         * interactions, then attach {@link ManagementBlock} data.
         */
        Integer managementY = null;
        for (int dy = -4; dy <= 4; dy++) {
            int y = wy + dy;
            if (y < 0 || y >= 320) {
                continue;
            }
            BlockType bt = world.getBlockType(wx, y, wz);
            if (bt != null && AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID.equals(bt.getId())) {
                managementY = y;
                break;
            }
        }
        if (managementY == null) {
            LOGGER.atWarning().log(
                "No %s in column %s,*,%s near y=%s (prefab must place that block at managementBlockLocalPos)",
                AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID,
                wx,
                wz,
                wy
            );
            return;
        }

        Vector3i cell = new Vector3i(wx, managementY, wz);
        Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
        RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
        int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);

        boolean placed =
            chunk.placeBlock(
                wx,
                managementY,
                wz,
                AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID,
                rt.yaw(),
                rt.pitch(),
                rt.roll(),
                MANAGEMENT_PLACE_SETTINGS
            );
        if (!placed) {
            world.breakBlock(wx, managementY, wz, MANAGEMENT_PLACE_SETTINGS);
            placed =
                chunk.placeBlock(
                    wx,
                    managementY,
                    wz,
                    AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID,
                    rt.yaw(),
                    rt.pitch(),
                    rt.roll(),
                    MANAGEMENT_PLACE_SETTINGS
                );
        }
        if (!placed) {
            BlockTypeAssetMap<String, BlockType> typeMap = BlockType.getAssetMap();
            String blockId = AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID;
            int indexKey = typeMap.getIndex(blockId);
            BlockType blockType = typeMap.getAsset(indexKey);
            chunk.setBlock(wx, managementY, wz, indexKey, blockType, rotationIndex, 0, MANAGEMENT_PLACE_SETTINGS);
        }

        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(wx, managementY, wz);
        if (blockRef == null) {
            for (int dy : new int[] {-1, 1, -2, 2}) {
                int y = managementY + dy;
                if (y < 0 || y >= 320) {
                    continue;
                }
                Ref<ChunkStore> r = chunk.getBlockComponentEntity(wx, y, wz);
                if (r != null) {
                    blockRef = r;
                    break;
                }
            }
        }
        if (blockRef == null) {
            LOGGER.atWarning().log("No block entity after re-placing management block at %s,%s,%s", wx, managementY, wz);
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(
            blockRef,
            ManagementBlock.getComponentType(),
            new ManagementBlock(plotId.toString(), town.getTownId().toString())
        );
    }

    private static void stampTreasuryBlock(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        int[] local = def.getTreasuryLocalPos();
        if (local == null) {
            return;
        }
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
        if (chunk == null) {
            LOGGER.atWarning().log("Treasury block chunk not loaded at %s,%s,%s", wx, wy, wz);
            return;
        }

        Integer treasuryY = null;
        for (int dy = -4; dy <= 4; dy++) {
            int y = wy + dy;
            if (y < 0 || y >= 320) {
                continue;
            }
            BlockType bt = world.getBlockType(wx, y, wz);
            if (bt != null && AetherhavenConstants.TREASURY_BLOCK_TYPE_ID.equals(bt.getId())) {
                treasuryY = y;
                break;
            }
        }
        if (treasuryY == null) {
            LOGGER.atWarning().log(
                "No %s in column %s,*,%s near y=%s (prefab must place treasury at treasuryLocalPos)",
                AetherhavenConstants.TREASURY_BLOCK_TYPE_ID,
                wx,
                wz,
                wy
            );
            return;
        }

        Vector3i cell = new Vector3i(wx, treasuryY, wz);
        Rotation blockYaw = PlotBlockRotationUtil.readBlockYaw(world, cell);
        RotationTuple rt = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
        int rotationIndex = PlotBlockRotationUtil.readBlockRotationIndex(world, cell);

        boolean placed =
            chunk.placeBlock(
                wx,
                treasuryY,
                wz,
                AetherhavenConstants.TREASURY_BLOCK_TYPE_ID,
                rt.yaw(),
                rt.pitch(),
                rt.roll(),
                MANAGEMENT_PLACE_SETTINGS
            );
        if (!placed) {
            world.breakBlock(wx, treasuryY, wz, MANAGEMENT_PLACE_SETTINGS);
            placed =
                chunk.placeBlock(
                    wx,
                    treasuryY,
                    wz,
                    AetherhavenConstants.TREASURY_BLOCK_TYPE_ID,
                    rt.yaw(),
                    rt.pitch(),
                    rt.roll(),
                    MANAGEMENT_PLACE_SETTINGS
                );
        }
        if (!placed) {
            BlockTypeAssetMap<String, BlockType> typeMap = BlockType.getAssetMap();
            String blockId = AetherhavenConstants.TREASURY_BLOCK_TYPE_ID;
            int indexKey = typeMap.getIndex(blockId);
            BlockType blockType = typeMap.getAsset(indexKey);
            chunk.setBlock(wx, treasuryY, wz, indexKey, blockType, rotationIndex, 0, MANAGEMENT_PLACE_SETTINGS);
        }

        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(wx, treasuryY, wz);
        if (blockRef == null) {
            for (int dy : new int[] {-1, 1, -2, 2}) {
                int y = treasuryY + dy;
                if (y < 0 || y >= 320) {
                    continue;
                }
                Ref<ChunkStore> r = chunk.getBlockComponentEntity(wx, y, wz);
                if (r != null) {
                    blockRef = r;
                    break;
                }
            }
        }
        if (blockRef == null) {
            LOGGER.atWarning().log("No block entity after re-placing treasury at %s,%s,%s", wx, treasuryY, wz);
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(
            blockRef,
            TreasuryBlock.getComponentType(),
            new TreasuryBlock(plotId.toString(), town.getTownId().toString())
        );
    }
}
