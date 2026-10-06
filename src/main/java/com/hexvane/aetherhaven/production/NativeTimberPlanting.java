package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import java.util.function.Predicate;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** World-thread planting adapter. Preparation and commit never load world chunks. */
public final class NativeTimberPlanting {
    private NativeTimberPlanting() {}

    public static TimberProductionState.Prepared prepare(World world, PlotProductionState production, int slot,
            TimberPlantingReceipt tree, Predicate<Position> withinGrove) {
        requireWorldThread(world);
        return production.timber().prepare(slot, WorkplaceProductionUpgrades.slotCount(production), tree,
            withinGrove, p -> observe(world, p));
    }

    /**
     * Caller validates the Logger, town permissions, selected material and physical
     * attendance, then saves the town if this returns null. No sapling item is required.
     * This operation alone never awards timber or marks a harvest complete.
     */
    public static String plant(World world, PlotProductionState production, int slot,
            TimberProductionState.Prepared prepared, Predicate<Position> withinGrove) {
        requireWorldThread(world);
        if (prepared == null || !prepared.ready()) return "Prepare this tree before planting it.";
        var tree = prepared.planting().tree(); var root = tree.root();
        // A changed integration or missing sapling must fail before terrain work.
        var species = NativeTimberGrowth.species(tree.output());
        if (species == null || !species.sapling().equals(tree.sapling()) || BlockType.getAssetMap().getAsset(tree.sapling()) == null)
            return "This sapling is no longer available.";
        var current = NativeTimberGrowth.capture(tree.output(), tree.root(), tree.initialGeneration(), withinGrove);
        if (!current.ready() || !current.receipt().stages().equals(tree.stages()) || !current.receipt().clearance().equals(tree.clearance()))
            return "This tree's growth has changed. Prepare its growing space again.";
        return production.timber().plant(slot, WorkplaceProductionUpgrades.slotCount(production), prepared,
            withinGrove, p -> observe(world, p), () -> ChunkSectionBlockUtil.setBlockByKey(
                world, root.x(), root.y(), root.z(), tree.sapling(), 0, 0));
    }

    public static TimberProductionState.Ground observe(World world, Position p) {
        requireWorldThread(world);
        if (p.y() < ChunkUtil.MIN_Y || p.y() > ChunkUtil.HEIGHT_MINUS_1
                || ChunkSectionBlockUtil.worldChunkIfTicking(world, p.x(), p.z()) == null)
            return new TimberProductionState.Ground(false, null, 0, 0, false, false, false, true);
        var section = ChunkSectionBlockUtil.sectionRefAt(world, p.x(), p.y(), p.z());
        var blocks = ChunkSectionBlockUtil.blockSectionAt(world, p.x(), p.y(), p.z());
        if (section == null || blocks == null) return new TimberProductionState.Ground(false, null, 0, 0, false, false, false, true);
        var type = ChunkSectionBlockUtil.blockType(world, p.x(), p.y(), p.z());
        if (type == null) return new TimberProductionState.Ground(false, null, 0, 0, false, false, false, true);
        var components = ChunkSectionBlockUtil.blockComponentSectionAt(world, p.x(), p.y(), p.z());
        int index = ChunkUtil.indexBlock(p.x(), p.y(), p.z());
        boolean entity = components != null && (components.getBlockReference(index) != null || components.getBlockHolder(index) != null);
        var fluids = world.getChunkStore().getStore().getComponent(section, FluidSection.getComponentType());
        boolean wet = fluids != null && fluids.getFluidId(p.x(), p.y(), p.z()) != 0;
        int typeIndex = BlockType.getAssetMap().getIndex(type.getId());
        var soils = BlockType.getAssetMap().getIndexesForTag(AssetRegistry.getOrCreateTagIndex("Type=Soil"));
        boolean soil = soils != null && soils.contains(typeIndex);
        boolean empty = BlockType.EMPTY.getId().equals(type.getId());
        String draw = String.valueOf(type.getDrawType());
        boolean natural = (soil || type.getId().startsWith("Rock_") || type.getId().startsWith("Ore_"))
            && ("Cube".equals(draw) || "CubeWithModel".equals(draw))
            && (type.getInteractions() == null || type.getInteractions().isEmpty());
        return new TimberProductionState.Ground(true, type.getId(), blocks.getRotationIndex(p.x(), p.y(), p.z()),
            blocks.getFiller(p.x(), p.y(), p.z()), empty, soil, natural, entity || wet);
    }
    private static void requireWorldThread(World world) {
        if (!world.isInThread()) throw new IllegalStateException("Timber planting must run on the world thread");
    }
}
