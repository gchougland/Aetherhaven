package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * World-thread adapter between saved planters and the blocks that show them. It only reads and writes chunks that
 * are already simulated, so a planter in an unloaded part of town simply waits.
 */
public final class SpiritGardenWorld {
    private SpiritGardenWorld() {}
    /** The cell cannot be read right now. */
    public static final int UNLOADED = -2;
    /** The cell holds no plant of this planter. */
    public static final int NONE = -1;
    private static final String[] STATES = {null, "Stage1", "Stage2", "Ripe"};

    private static void requireWorldThread(World world) {
        if (!world.isInThread()) throw new IllegalStateException("The Spirit Garden must be read on the world thread");
    }
    private static boolean loaded(World world, Point p) {
        return p.y() >= ChunkUtil.MIN_Y && ChunkSectionBlockUtil.worldChunkIfTicking(world, p.x(), p.z()) != null
            && ChunkSectionBlockUtil.blockSectionAt(world, p.x(), p.y(), p.z()) != null;
    }
    /** Whole game seconds on the world's clock. Growth is measured against this and nothing else. */
    public static long now(World world) {
        return world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType()).getGameTime().getEpochSecond();
    }

    /** True while this block is a Spirit Planter in a simulated chunk. */
    public static boolean planterStands(World world, Point block) {
        requireWorldThread(world);
        if (!loaded(world, block)) return false;
        var type = ChunkSectionBlockUtil.blockType(world, block.x(), block.y(), block.z());
        return type != null && SpiritGarden.is(type.getId());
    }
    /** True when the planter is simulated and its block is gone, so the binding has nothing left to stand on. */
    public static boolean planterMissing(World world, Point block) {
        requireWorldThread(world);
        return loaded(world, block) && !planterStands(world, block);
    }

    /** True when a plant can be set above this planter: plain air, with no water in it. */
    public static boolean cellFree(World world, Point cell) {
        requireWorldThread(world);
        if (!loaded(world, cell)) return false;
        var type = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z());
        if (type == null || !BlockType.EMPTY.getId().equals(type.getId())) return false;
        var section = ChunkSectionBlockUtil.sectionRefAt(world, cell.x(), cell.y(), cell.z());
        var fluids = section == null ? null : world.getChunkStore().getStore().getComponent(section, FluidSection.getComponentType());
        return fluids == null || fluids.getFluidId(cell.x(), cell.y(), cell.z()) == 0;
    }

    private static BlockType stageBlock(SpiritGardenRules.Plant plant, int stage) {
        var base = BlockType.getAssetMap().getAsset(plant.block());
        if (base == null || stage < 0 || stage > SpiritGardenState.RIPE) return null;
        return STATES[stage] == null ? base : base.getBlockForState(STATES[stage]);
    }

    /** The growth stage standing above the planter, {@link #NONE} or {@link #UNLOADED}. */
    public static int stageAt(World world, SpiritGardenState.Planter planter, SpiritGardenRules.Plant plant) {
        requireWorldThread(world);
        var cell = planter.cell();
        if (!loaded(world, cell)) return UNLOADED;
        var type = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z());
        // Each stage is its own block id, so the cell is matched against the four shapes this plant can take.
        if (type != null) for (int stage = SpiritGardenState.RIPE; stage >= 0; stage--) {
            var shape = stageBlock(plant, stage);
            if (shape != null && shape.getId().equals(type.getId())) return stage;
        }
        return NONE;
    }

    /**
     * Sets the plant's shape for a stage above its planter. It replaces only air or this planter's own plant, and
     * never touches anything a player put there.
     */
    public static boolean show(World world, SpiritGardenState.Planter planter, SpiritGardenRules.Plant plant, int stage) {
        requireWorldThread(world);
        var cell = planter.cell();
        var shape = stageBlock(plant, stage);
        if (shape == null || !planterStands(world, planter.block())) return false;
        if (stageAt(world, planter, plant) == NONE && !cellFree(world, cell)) return false;
        return ChunkSectionBlockUtil.setBlock(world, cell.x(), cell.y(), cell.z(), shape, 0);
    }

    /** Takes this planter's plant out of the world. Anything else in the cell is left alone. */
    public static boolean clear(World world, SpiritGardenState.Planter planter, SpiritGardenRules.Plant plant) {
        requireWorldThread(world);
        var cell = planter.cell();
        return plant != null && stageAt(world, planter, plant) >= 0 && ChunkSectionBlockUtil.setBlockEmpty(world, cell.x(), cell.y(), cell.z(), 0);
    }

    /**
     * One harvest: the plant's own harvest list rolled once for each plant, and the list a broken ripe plant
     * drops rolled for its petals. Only the plant and its petal count, whatever else a list may hold.
     */
    public static Map<String, Integer> rollHarvest(SpiritGardenRules.Plant plant, int plants, int petalRolls) {
        var module = ItemModule.get();
        int grown = 0, petals = 0;
        for (int i = 0; i < plants; i++) grown += count(module, plant.plantDrops(), plant.plant());
        for (int i = 0; i < petalRolls; i++) petals += count(module, plant.petalDrops(), plant.petal());
        var result = new LinkedHashMap<String, Integer>();
        // A pack that renames a drop list still gets an honest harvest.
        result.put(plant.plant(), Math.clamp(grown > 0 ? grown : plants, 1, 4096));
        if (petalRolls > 0) result.put(plant.petal(), Math.clamp(petals > 0 ? petals : petalRolls, 1, 4096));
        return result;
    }
    private static int count(ItemModule module, String dropList, String item) {
        int total = 0;
        var rolled = module == null ? null : module.getRandomItemDrops(dropList);
        if (rolled != null) for (var stack : rolled)
            if (!ItemStack.isEmpty(stack) && item.equals(stack.getItemId())) total += stack.getQuantity();
        return total;
    }
}
