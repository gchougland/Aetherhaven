package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Swaps tourist portal blocks to color variant types so idle particles match town portal color. */
public final class TouristPortalVisualService {
    private TouristPortalVisualService() {}

    public static void applyTownColorToAllPortals(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        if (!world.getName().equals(town.getWorldName())) {
            return;
        }
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        List<TouristPortalRecord> portals = registry.recordsForTown(town.getTownId());
        if (portals.isEmpty()) {
            return;
        }
        world.execute(() -> {
            for (TouristPortalRecord portal : portals) {
                applyColorVariantAtBlock(world, portal.getBlockPosition(), town);
            }
        });
    }

    @SuppressWarnings({ "deprecation", "removal" })
    public static void applyColorVariantAtBlock(
        @Nonnull World world,
        @Nonnull Vector3i basePos,
        @Nonnull TownRecord town
    ) {
        String targetId = TownPortalTravelColor.blockTypeIdForTown(town);
        BlockTypeAssetMap<String, BlockType> typeMap = BlockType.getAssetMap();
        int targetIndex = typeMap.getIndex(targetId);
        BlockType targetType = typeMap.getAsset(targetIndex);
        if (targetType == null || targetIndex < 0) {
            return;
        }

        Vector3i base = TouristPortalBlockUtil.resolvePortalBaseBlock(world, basePos);
        if (!TouristPortalBlockUtil.isPortalBaseBlock(world, base.x, base.y, base.z)) {
            return;
        }

        TouristPortalBlock saved = TouristPortalBlockUtil.getBlockComponent(world, base);
        int settings =
            SetBlockSettings.NO_UPDATE_STATE
                | SetBlockSettings.NO_SEND_PARTICLES
                | SetBlockSettings.NO_BREAK_FILLER
                | SetBlockSettings.NO_SET_FILLER;

        TouristPortalBlockUtil.forEachPlatformCell(world, base, cell -> {
            WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(cell.x, cell.z));
            if (chunk == null) {
                return;
            }
            BlockType current = chunk.getBlockType(cell.x, cell.y, cell.z);
            if (current != null && targetId.equals(current.getId())) {
                return;
            }
            if (current == null || !TownPortalTravelColor.isTouristPortalBlockTypeId(current.getId())) {
                return;
            }
            int rotationIndex = chunk.getRotationIndex(cell.x, cell.y, cell.z);
            int filler = chunk.getFiller(cell.x, cell.y, cell.z);
            chunk.setBlock(
                cell.x,
                cell.y,
                cell.z,
                targetIndex,
                targetType,
                rotationIndex,
                filler,
                settings
            );
        });

        if (saved != null) {
            TouristPortalBlockUtil.writeBlockComponent(world, base, saved);
        }
    }
}
