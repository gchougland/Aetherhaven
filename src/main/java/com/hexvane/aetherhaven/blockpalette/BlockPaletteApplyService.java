package com.hexvane.aetherhaven.blockpalette;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.construction.ConstructionPrefabSequence;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Immediately re-stamps remappable prefab cells for a plot using its palette selections. */
public final class BlockPaletteApplyService {
    private BlockPaletteApplyService() {}

    public static void applyToPlot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def
    ) {
        String prefabPath = def.getPrefabPath();
        if (prefabPath == null || prefabPath.isBlank()) {
            return;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(prefabPath.trim());
        if (buffer == null) {
            return;
        }
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();
        Map<String, String> selections = plot.getBlockPaletteSelections();
        PlotFootprintRecord footprint = plot.toFootprint();
        world.execute(() -> applyOnWorldThread(world, buffer, anchor, yaw, selections, footprint));
    }

    private static void applyOnWorldThread(
        @Nonnull World world,
        @Nonnull IPrefabBuffer buffer,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull Map<String, String> selections,
        @Nonnull PlotFootprintRecord footprint
    ) {
        ConstructionPrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw, null);
        List<PendingBlock> cells = ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks());
        LocalCachedChunkAccessor accessor = ConstructionPasteOps.createAccessor(world, origin, buffer);
        BlockTypeAssetMap<String, BlockType> typeMap = BlockType.getAssetMap();
        Map<Long, String> prefabBlockIdByCell = new HashMap<>();
        for (PendingBlock pb : cells) {
            if (pb.filler() != FillerBlockUtil.NO_FILLER || pb.blockId() == 0) {
                continue;
            }
            BlockType prefabType = typeMap.getAsset(pb.blockId());
            if (prefabType == null || prefabType.getId() == null || prefabType.getId().isBlank()) {
                continue;
            }
            int wx = origin.x + pb.x();
            int wy = origin.y + pb.y();
            int wz = origin.z + pb.z();
            prefabBlockIdByCell.put(cellKey(wx, wy, wz), prefabType.getId());
        }
        int settings =
            SetBlockSettings.NO_SEND_PARTICLES
                | SetBlockSettings.NO_BREAK_FILLER
                | SetBlockSettings.NO_SET_FILLER;
        for (int bx = footprint.getMinX(); bx <= footprint.getMaxX(); bx++) {
            for (int by = footprint.getMinY(); by <= footprint.getMaxY(); by++) {
                for (int bz = footprint.getMinZ(); bz <= footprint.getMaxZ(); bz++) {
                    String prefabBlockTypeId = prefabBlockIdByCell.get(cellKey(bx, by, bz));
                    WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(bx, bz));
                    if (chunk == null) {
                        chunk = ChunkSectionBlockUtil.worldChunkIfNonTicking(world, ChunkUtil.indexChunkFromBlock(bx, bz));
                    }
                    if (chunk == null) {
                        continue;
                    }
                    BlockType current = chunk.getBlockType(bx, by, bz);
                    if (current == null || current.getId() == null || current.getId().isBlank()) {
                        continue;
                    }
                    String targetId = resolveTargetBlockTypeId(prefabBlockTypeId, current, selections);
                    if (targetId == null || targetId.isBlank()) {
                        continue;
                    }
                    if (targetId.equals(current.getId())) {
                        continue;
                    }
                    int newBlockId = typeMap.getIndex(targetId);
                    if (newBlockId < 0) {
                        String alt =
                            targetId.startsWith("*") ? targetId.substring(1) : ("*" + targetId);
                        newBlockId = typeMap.getIndex(alt);
                        if (newBlockId < 0) {
                            continue;
                        }
                        targetId = alt;
                    }
                    BlockType target = typeMap.getAsset(newBlockId);
                    if (target == null) {
                        continue;
                    }
                    BlockSection section = blockSectionAt(world, bx, by, bz);
                    if (section == null) {
                        continue;
                    }
                    int rotationIndex = section.getRotationIndex(bx, by, bz);
                    int filler = section.getFiller(bx, by, bz);
                    chunk.setBlock(bx, by, bz, newBlockId, target, rotationIndex, filler, settings);
                }
            }
        }
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) x << 42) ^ ((long) y << 21) ^ (long) z;
    }

    /**
     * Rotation and filler come off the chunk section entity's {@link BlockSection}, matching the section-based reads
     * elsewhere in the mod rather than the {@code BlockAccessor} helpers on {@link WorldChunk}.
     */
    @Nullable
    private static BlockSection blockSectionAt(@Nonnull World world, int bx, int by, int bz) {
        if (by < ChunkUtil.MIN_Y || by > ChunkUtil.HEIGHT_MINUS_1) {
            return null;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(bx, by, bz);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        return chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
    }

    /**
     * Default (no palette for the category) restores the prefab block. Otherwise remaps the current world block when
     * it is a remappable variant so connected shapes are preserved, falling back to the prefab id.
     */
    @Nullable
    private static String resolveTargetBlockTypeId(
        @Nullable String prefabBlockTypeId,
        @Nonnull BlockType current,
        @Nonnull Map<String, String> selections
    ) {
        boolean hasPrefab = prefabBlockTypeId != null && !prefabBlockTypeId.isBlank();
        BlockPaletteRemapper.ParsedBlock prefabParsed =
            hasPrefab ? BlockPaletteRemapper.parse(prefabBlockTypeId) : null;
        BlockPaletteRemapper.ParsedBlock currentParsed =
            current.getId() != null ? BlockPaletteRemapper.parse(current.getId()) : null;
        if (prefabParsed == null && currentParsed == null) {
            return null;
        }
        String category = currentParsed != null ? currentParsed.category() : prefabParsed.category();
        String selected = selections.get(category);
        boolean isDefault = selected == null || selected.isBlank();
        if (isDefault) {
            if (hasPrefab && isRemappableCategoryBlock(prefabBlockTypeId)) {
                return prefabBlockTypeId;
            }
            return null;
        }
        String sourceId;
        if (currentParsed != null && currentParsed.category().equals(category)) {
            sourceId = current.getId();
        } else if (hasPrefab && prefabParsed != null) {
            sourceId = prefabBlockTypeId;
        } else {
            return null;
        }
        return BlockPaletteRemapper.remapBlockTypeId(sourceId, selections);
    }

    private static boolean isRemappableCategoryBlock(@Nonnull String blockTypeId) {
        String id = blockTypeId.startsWith("*") ? blockTypeId.substring(1) : blockTypeId;
        if (id.startsWith("Wood_Village_Wall_")) {
            return true;
        }
        if (id.startsWith("Cloth_Block_Wool_")) {
            return true;
        }
        if (id.startsWith("Rock_") && id.contains("_Cobble") && !id.contains("_Cobble_Roof")) {
            return true;
        }
        if (id.startsWith("Rock_") && id.contains("_Brick") && !id.contains("_Brick_Roof")) {
            return true;
        }
        if (id.startsWith("Wood_") && (id.contains("_Trunk") || id.contains("_Branch") || id.endsWith("_Roots"))) {
            return true;
        }
        if (id.startsWith("Wood_")
            && !id.contains("_Roof")
            && !id.contains("_Village_")
            && (id.contains("_Planks")
                || id.contains("_Stairs")
                || id.contains("_Fence")
                || id.contains("_Beam")
                || id.contains("_Ornate")
                || id.contains("_Decorative"))) {
            return true;
        }
        if (id.contains("_Roof") || id.startsWith("Cloth_Roof_")) {
            return true;
        }
        if (id.startsWith("Furniture_") && id.contains("_Window") && !id.contains("_Windows")) {
            return true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        return plugin != null && plugin.getBlockPaletteCatalog().isRemapGroupBlock(id);
    }
}
