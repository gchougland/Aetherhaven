package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * World-thread adapter between a crop plan and the game's own farmland and crops. Everything here is the game's:
 * its tilled soil, its seedling blocks, its growth stages, its harvest lists and its watering. It only reads and
 * writes chunks that are already simulated, so a field in an unloaded part of town simply waits.
 */
public final class FarmFieldWorld {
    private FarmFieldWorld() {}

    /** Why a cell can or cannot be farmed. */
    public enum Ground {
        /** Farmland, or ground the game's hoe can till, with room for a crop above it. */
        OK,
        /** The chunk is not loaded right now. */
        FAR,
        OUTSIDE_TOWN,
        /** Inside another building's plot. */
        BUILDING,
        /** Inside the farm's own plot, which belongs to the farm's own field. */
        FARMYARD,
        /** Inside a townsfolk workplace. Those crops feed the town through the Apprentices. */
        WORKPLACE,
        /** Something stands on the soil. */
        BLOCKED,
        NOT_SOIL,
        /** The Scarecrow's own cell. */
        SCARECROW
    }
    /**
     * One column of a field.
     *
     * @param y the cell a crop stands in, directly above the soil. Meaningful only when the ground is OK.
     * @param tilled true when the soil is farmland already
     * @param crop the registered crop standing there, or null
     */
    public record Spot(Ground ground, int y, boolean tilled, NativeFarmCrops.Crop crop) {
        public boolean ok() { return ground == Ground.OK; }
        static Spot no(Ground ground) { return new Spot(ground, 0, false, null); }
    }

    private static void requireWorldThread(World world) {
        if (!world.isInThread()) throw new IllegalStateException("A field must be read on the world thread");
    }
    private static boolean loaded(World world, int x, int y, int z) {
        return y >= ChunkUtil.MIN_Y && ChunkSectionBlockUtil.worldChunkIfTicking(world, x, z) != null
            && ChunkSectionBlockUtil.blockSectionAt(world, x, y, z) != null;
    }
    /** A block's own id, whichever of its states it is in: watered farmland is still farmland. */
    static String baseId(BlockType type) {
        if (type == null) return null;
        return type.getDefaultStateKey() != null ? type.getDefaultStateKey() : type.getId();
    }
    public static Instant instant(World world) {
        return world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType()).getGameTime();
    }
    /** The town calendar's date and time. */
    public static LocalDateTime dateTime(World world) {
        return world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType()).getGameDateTime();
    }
    private static boolean empty(World world, int x, int y, int z) {
        var type = ChunkSectionBlockUtil.blockType(world, x, y, z);
        if (type == null || !BlockType.EMPTY.getId().equals(type.getId())) return false;
        var section = ChunkSectionBlockUtil.sectionRefAt(world, x, y, z);
        var fluids = section == null ? null : world.getChunkStore().getStore().getComponent(section, FluidSection.getComponentType());
        return fluids == null || fluids.getFluidId(x, y, z) == 0;
    }

    /**
     * Looks at one column of a field from the top down for soil the Farmer can use: farmland or ground the game's
     * hoe can till, with nothing above it but air or a crop.
     *
     * @param farm the farm this field belongs to
     * @param home true for the farm building's own field, which lies inside its plot
     * @param anchor the Scarecrow, or null
     */
    public static Spot inspect(World world, TownRecord town, PlotInstance farm, boolean home, Point anchor,
            FarmFields.Bounds bounds, int x, int z) {
        requireWorldThread(world);
        if (anchor != null && anchor.x() == x && anchor.z() == z) return Spot.no(Ground.SCARECROW);
        if (ChunkSectionBlockUtil.worldChunkIfTicking(world, x, z) == null) return Spot.no(Ground.FAR);
        if (!TownTerritoryClaims.contains(town, ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z))) return Spot.no(Ground.OUTSIDE_TOWN);
        if (!home) for (var plot : town.getPlotInstances()) {
            var f = plot.toFootprint();
            if (x >= f.getMinX() && x <= f.getMaxX() && z >= f.getMinZ() && z <= f.getMaxZ())
                return Spot.no(plot == farm ? Ground.FARMYARD : Ground.BUILDING);
        }
        var rules = FarmFieldRules.get();
        for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
            if (!loaded(world, x, y, z) || !loaded(world, x, y + 1, z)) continue;
            String soil = baseId(ChunkSectionBlockUtil.blockType(world, x, y, z));
            boolean tilled = rules.tilled(soil);
            if (!tilled && !rules.tillable(soil)) continue;
            if (ChunkSectionBlockUtil.filler(world, x, y, z) != 0) continue;
            if (excluded(town, x, y + 1, z)) return Spot.no(Ground.WORKPLACE);
            var crop = NativeFarmCrops.crop(ChunkSectionBlockUtil.blockType(world, x, y + 1, z));
            if (crop == null && !empty(world, x, y + 1, z)) return Spot.no(Ground.BLOCKED);
            return new Spot(Ground.OK, y + 1, tilled, crop);
        }
        return Spot.no(Ground.NOT_SOIL);
    }
    private static boolean excluded(TownRecord town, int x, int y, int z) {
        return town.getApprenticeWorkplaces().sites().stream().anyMatch(site -> site.area().contains(x, y, z));
    }
    /** True while a planned cell may still be worked: loaded, in town, and not handed to a townsfolk workplace. */
    public static boolean workable(World world, TownRecord town, Point cell) {
        requireWorldThread(world);
        return loaded(world, cell.x(), cell.y(), cell.z()) && loaded(world, cell.x(), cell.y() - 1, cell.z())
            && TownTerritoryClaims.contains(town, ChunkUtil.chunkCoordinate(cell.x()), ChunkUtil.chunkCoordinate(cell.z()))
            && !excluded(town, cell.x(), cell.y(), cell.z());
    }

    /** What one planned cell looks like to the Farmer right now. */
    public static FarmFieldWork.View view(World world, TownRecord town, FarmFieldsState.Cell cell) {
        var at = cell.point();
        if (!workable(world, town, at)) return FarmFieldWork.View.FAR;
        var standing = cropAt(world, at);
        var rules = FarmFieldRules.get();
        if (standing != null)
            return new FarmFieldWork.View(true, false, standing.output(), ripe(world, at, standing), needsWater(world, at, rules.work().rewaterHours()));
        String ground = baseId(ChunkSectionBlockUtil.blockType(world, at.x(), at.y() - 1, at.z()));
        boolean plantable = (rules.tilled(ground) || rules.tillable(ground)) && empty(world, at.x(), at.y(), at.z());
        return new FarmFieldWork.View(true, plantable, null, false, false);
    }

    /** The registered crop standing in a cell, or null. */
    public static NativeFarmCrops.Crop cropAt(World world, Point cell) {
        requireWorldThread(world);
        return loaded(world, cell.x(), cell.y(), cell.z()) ? NativeFarmCrops.crop(ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z())) : null;
    }
    public static boolean free(World world, Point cell) {
        requireWorldThread(world);
        return loaded(world, cell.x(), cell.y(), cell.z()) && empty(world, cell.x(), cell.y(), cell.z());
    }
    /** True when this crop stands in the cell at its last growth stage, the one the game lets a player harvest. */
    public static boolean ripe(World world, Point cell, NativeFarmCrops.Crop crop) {
        requireWorldThread(world);
        if (crop == null || !loaded(world, cell.x(), cell.y(), cell.z())) return false;
        var block = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z());
        return crop.equals(NativeFarmCrops.crop(block)) && block.getFarming() != null
            && block.getGathering() != null && block.getGathering().getHarvest() != null;
    }
    /** Share of the way from seed to ripe, by the game's own growth stages. */
    public static double growth(World world, Point cell, NativeFarmCrops.Crop crop) {
        requireWorldThread(world);
        if (crop == null || !loaded(world, cell.x(), cell.y(), cell.z())) return 0;
        var block = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z());
        if (!crop.equals(NativeFarmCrops.crop(block)) || block.getFarming() == null || block.getFarming().getStages() == null) return 0;
        if (block.getGathering() != null && block.getGathering().getHarvest() != null) return 1;
        var ref = ChunkSectionBlockUtil.blockEntityRefAt(world, cell.x(), cell.y(), cell.z());
        var growing = ref == null || !ref.isValid() ? null : world.getChunkStore().getStore().getComponent(ref, FarmingBlock.getComponentType());
        var stages = growing == null ? null : block.getFarming().getStages().get(growing.getCurrentStageSet());
        if (stages == null || stages.length < 2) return 0;
        return Math.clamp(growing.getGrowthProgress() / (stages.length - 1), 0, 0.99);
    }

    private static boolean soilIsTilled(World world, Point cell) {
        return loaded(world, cell.x(), cell.y() - 1, cell.z())
            && FarmFieldRules.get().tilled(baseId(ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y() - 1, cell.z())));
    }
    /**
     * Makes the ground under a cell into farmland, as the game's own hoe would. Only ground the hoe can till is
     * changed, so nothing a player built is ever replaced.
     */
    public static boolean till(World world, Point cell) {
        requireWorldThread(world);
        if (soilIsTilled(world, cell)) return true;
        int x = cell.x(), y = cell.y() - 1, z = cell.z();
        var rules = FarmFieldRules.get();
        if (!loaded(world, x, y, z) || !rules.tillable(baseId(ChunkSectionBlockUtil.blockType(world, x, y, z)))) return false;
        return ChunkSectionBlockUtil.setBlockByKey(world, x, y, z, rules.soil().tilled(), SetBlockSettings.NONE);
    }
    /** Sets the crop's own seedling block on farmland, exactly what the game's seed bag places. Only into free air. */
    public static boolean plant(World world, Point cell, NativeFarmCrops.Crop crop) {
        requireWorldThread(world);
        if (crop == null || !soilIsTilled(world, cell) || !free(world, cell)) return false;
        return ChunkSectionBlockUtil.setBlockByKey(world, cell.x(), cell.y(), cell.z(), crop.block(), SetBlockSettings.NONE);
    }
    /** Takes a registered crop out of a cell. Anything else in the cell is left alone. */
    public static boolean uproot(World world, Point cell) {
        requireWorldThread(world);
        return cropAt(world, cell) != null
            && ChunkSectionBlockUtil.setBlockEmpty(world, cell.x(), cell.y(), cell.z(), SetBlockSettings.PERFORM_BLOCK_UPDATE | SetBlockSettings.NO_DROP_ITEMS);
    }
    /**
     * A favoured crop is planted already sprouted: the seedling skips straight to the game's first growth stage,
     * through the same stage change the game itself makes when a seedling sprouts.
     */
    public static boolean sprout(World world, Point cell, NativeFarmCrops.Crop crop) {
        requireWorldThread(world);
        var block = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z());
        if (crop == null || block == null || !crop.block().equals(block.getId()) || block.getFarming() == null || block.getFarming().getStages() == null) return false;
        var farming = block.getFarming();
        var stages = farming.getStages().get(farming.getStartingStageSet());
        var ref = ChunkSectionBlockUtil.blockEntityRefAt(world, cell.x(), cell.y(), cell.z());
        var section = ChunkSectionBlockUtil.sectionRefAt(world, cell.x(), cell.y(), cell.z());
        if (stages == null || stages.length < 3 || ref == null || !ref.isValid() || section == null || !section.isValid()) return false;
        var store = world.getChunkStore().getStore();
        var growing = store.getComponent(ref, FarmingBlock.getComponentType());
        if (growing == null || growing.getGrowthProgress() >= 1) return false;
        int lx = ChunkUtil.localCoordinate(cell.x()), ly = ChunkUtil.localCoordinate(cell.y()), lz = ChunkUtil.localCoordinate(cell.z());
        if (!stages[1].canApply(store, section, ref, lx, ly, lz)) return false;
        growing.setCurrentStageSet(farming.getStartingStageSet()); growing.setGrowthProgress(1); growing.setExecutions(0);
        growing.setGeneration(growing.getGeneration() + 1); growing.setLastTickGameTime(instant(world));
        stages[1].apply(store, section, ref, lx, ly, lz, stages[0]);
        var components = ChunkSectionBlockUtil.blockComponentSectionAt(world, cell.x(), cell.y(), cell.z());
        if (components != null) components.markBlockNeedsSaving(ChunkUtil.indexBlock(cell.x(), cell.y(), cell.z()));
        return true;
    }

    /**
     * Rolls the ripe plant's own harvest list once, as a player's harvest would.
     *
     * @return the crop count and the Life Essence count, or null when the cell holds no ripe plant of this crop
     */
    public static int[] rollHarvest(World world, Point cell, NativeFarmCrops.Crop crop) {
        requireWorldThread(world);
        if (!ripe(world, cell, crop)) return null;
        var harvest = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z()).getGathering().getHarvest();
        var module = ItemModule.get();
        var rolled = harvest.getDropListId() != null && module != null ? module.getRandomItemDrops(harvest.getDropListId())
            : harvest.getItemId() == null ? java.util.List.<ItemStack>of() : java.util.List.of(new ItemStack(harvest.getItemId(), 1));
        int crops = 0, essence = 0;
        if (rolled != null) for (var stack : rolled) {
            if (ItemStack.isEmpty(stack)) continue;
            if (crop.output().equals(stack.getItemId())) crops += stack.getQuantity();
            else if (NativeFarmCrops.LIFE_ESSENCE.equals(stack.getItemId())) essence += stack.getQuantity();
        }
        // A pack that renames a drop list still gets an honest harvest, and every harvest carries some Life Essence.
        return new int[] {Math.clamp(crops, 1, 4096), Math.clamp(essence, 1, 4096)};
    }

    private static TilledSoilBlock soil(World world, Point cell, boolean create) {
        int x = cell.x(), y = cell.y() - 1, z = cell.z();
        if (!loaded(world, x, y, z)) return null;
        var store = world.getChunkStore().getStore();
        var ref = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        if ((ref == null || !ref.isValid()) && create) {
            var type = ChunkSectionBlockUtil.blockType(world, x, y, z);
            var section = ChunkSectionBlockUtil.sectionRefAt(world, x, y, z);
            var components = ChunkSectionBlockUtil.blockComponentSectionAt(world, x, y, z);
            if (type != null && section != null && section.isValid() && components != null
                && BlockEntity.declaresComponent(type, TilledSoilBlock.getComponentType()))
                ref = BlockEntity.ensureBlockEntity(store, section, components, x, y, z, type, ChunkSectionBlockUtil.filler(world, x, y, z));
        }
        return ref == null || !ref.isValid() ? null : store.getComponent(ref, TilledSoilBlock.getComponentType());
    }
    private static void soilChanged(World world, Point cell, Instant tickAt) {
        int x = cell.x(), y = cell.y() - 1, z = cell.z(), index = ChunkUtil.indexBlock(x, y, z);
        var section = ChunkSectionBlockUtil.blockSectionAt(world, x, y, z);
        var components = ChunkSectionBlockUtil.blockComponentSectionAt(world, x, y, z);
        if (components != null) components.markBlockNeedsSaving(index);
        // As the game's watering can does: tick now to show the change, and again when the water runs out.
        if (section != null) { section.setTicking(x, y, z, true); if (tickAt != null) section.scheduleTick(index, tickAt); }
        var above = ChunkSectionBlockUtil.blockSectionAt(world, x, y + 1, z);
        if (above != null) above.setTicking(x, y + 1, z, true);
    }
    /** True when the farmland under a cell will be dry within the given hours and nothing else keeps it wet. */
    public static boolean needsWater(World world, Point cell, int withinHours) {
        requireWorldThread(world);
        if (!soilIsTilled(world, cell)) return false;
        var soil = soil(world, cell, false);
        if (soil == null) return true;
        return !soil.hasExternalWater() && (soil.getWateredUntil() == null || soil.getWateredUntil().isBefore(instant(world).plusSeconds(withinHours * 3600L)));
    }
    /** Wets the farmland under a cell for some game hours, the way the game's watering can does. */
    public static boolean water(World world, Point cell, int hours) {
        requireWorldThread(world);
        if (!soilIsTilled(world, cell)) return false;
        var soil = soil(world, cell, true);
        if (soil == null) return false;
        var now = instant(world); var until = now.plusSeconds(hours * 3600L);
        if (soil.getWateredUntil() != null && soil.getWateredUntil().isAfter(until)) until = soil.getWateredUntil();
        soil.setWateredUntil(until);
        soilChanged(world, cell, until);
        return true;
    }
    /** Worn soil will not hold water: whatever the Farmer poured on it is gone. Rain and sprinklers still reach it. */
    public static boolean dry(World world, Point cell) {
        requireWorldThread(world);
        var soil = soilIsTilled(world, cell) ? soil(world, cell, false) : null;
        if (soil == null || soil.getWateredUntil() == null) return false;
        soil.setWateredUntil(null);
        soilChanged(world, cell, null);
        return true;
    }
    /** True while the farmland under a cell is wet from a watering can, a sprinkler, rain or water beside it. */
    public static boolean wet(World world, Point cell) {
        requireWorldThread(world);
        var soil = soilIsTilled(world, cell) ? soil(world, cell, false) : null;
        return soil != null && (soil.hasExternalWater() || soil.getWateredUntil() != null && soil.getWateredUntil().isAfter(instant(world)));
    }
}
