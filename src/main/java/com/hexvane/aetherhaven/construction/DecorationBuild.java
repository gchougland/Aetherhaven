package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerEntity;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerEntity;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * "Build as decoration" on a plot sign: the prefab goes up as ordinary blocks and the plot is dropped when it finishes,
 * so nothing is left that the town could link to. Vanilla blocks, benches included, stay as they do in any prefab.
 */
public final class DecorationBuild {
    private static final String MOD_BLOCK_PREFIX = "Aetherhaven_";
    /**
     * This mod's blocks that are only scenery. Every other block of this mod is taken out, so a block added later is
     * not left working inside a decoration because nobody listed it.
     */
    private static final Set<String> SCENERY_BLOCKS =
        Set.of(
            "Aetherhaven_Heartberry",
            "Aetherhaven_Mine_Beam_Corner",
            "Aetherhaven_Mine_Beam_Corner_Cross",
            "Aetherhaven_Mine_Ceiling_Beam",
            "Aetherhaven_Mine_Ceiling_Beam_Cross",
            "Aetherhaven_Smokestack",
            "Aetherhaven_Wood_Scaffold"
        );

    private DecorationBuild() {}

    /** Decoration plots and wall pieces never join the town, so the choice means nothing for them. */
    public static boolean canBuildAsDecoration(@Nonnull ConstructionDefinition def) {
        return !def.isDecorationPlot() && !def.isWallSegment();
    }

    public static boolean isFunctionalBlock(@Nullable String blockTypeId) {
        if (blockTypeId == null) {
            return false;
        }
        // State variants of a block are registered as "*Base_State_...".
        String id = blockTypeId.startsWith("*") ? blockTypeId.substring(1) : blockTypeId;
        return id.startsWith(MOD_BLOCK_PREFIX) && !SCENERY_BLOCKS.contains(id);
    }

    /** The cells and entities assembly works from: a plot's prefab, without the working parts when it is a decoration. */
    @Nonnull
    public static ConstructionPrefabSequence sequenceFor(
        @Nonnull IPrefabBuffer buffer,
        @Nonnull Rotation yaw,
        @Nonnull PlotInstance plot
    ) {
        ConstructionPrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw, plot.getBlockPaletteSelections());
        if (!plot.isBuildAsDecoration()) {
            return seq;
        }
        List<Holder<EntityStore>> entities = new ArrayList<>(seq.prefabEntitiesInOrder());
        entities.removeIf(DecorationBuild::isTownMarker);
        return new ConstructionPrefabSequence(
            withoutFunctionalBlocks(seq.pendingBlocks(), DecorationBuild::isFunctionalBlockId),
            entities,
            seq.prefabRotation()
        );
    }

    /**
     * Turns functional cells into air, keeping their place in the list and any fluid. Clearing still empties them, so a
     * tree standing where the records shelf would have gone does not stay inside the building.
     */
    @Nonnull
    static List<PendingBlock> withoutFunctionalBlocks(@Nonnull List<PendingBlock> cells, @Nonnull IntPredicate functional) {
        List<PendingBlock> out = new ArrayList<>(cells.size());
        for (PendingBlock cell : cells) {
            out.add(
                functional.test(cell.blockId())
                    ? new PendingBlock(cell.x(), cell.y(), cell.z(), 0, null, 0, 0, 0, cell.fluidId(), cell.fluidLevel())
                    : cell
            );
        }
        return out;
    }

    private static boolean isFunctionalBlockId(int blockId) {
        if (blockId == 0) {
            return false;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        return type != null && isFunctionalBlock(type.getId());
    }

    private static boolean isTownMarker(@Nonnull Holder<EntityStore> entity) {
        return has(entity, PoiMarkerEntity.getComponentType()) || has(entity, AdventurerSpawnMarkerEntity.getComponentType());
    }

    private static <T extends Component<EntityStore>> boolean has(
        @Nonnull Holder<EntityStore> entity,
        @Nullable ComponentType<EntityStore, T> type
    ) {
        return type != null && entity.getComponent(type) != null;
    }
}
