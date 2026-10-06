package com.hexvane.aetherhaven.production;

import com.hypixel.hytale.builtin.adventure.farming.config.stages.PrefabFarmingStageData;
import com.hypixel.hytale.builtin.adventure.farming.config.stages.BlockTypeFarmingStageData;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.HashUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** Native prefab geometry only. Does not grow plants, load chunks or remove terrain. */
public final class NativeTimberGrowth {
    private NativeTimberGrowth() {}

    public record Species(String output, String sapling, Set<String> timber, Set<String> other) {
        public Species {
            if (output == null || output.isBlank() || sapling == null || sapling.isBlank()
                    || timber == null || timber.isEmpty() || other == null)
                throw new IllegalArgumentException("A timber species needs its output, sapling and exact permitted blocks");
            timber = Set.copyOf(timber); other = Set.copyOf(other);
            if (!Collections.disjoint(timber, other)) throw new IllegalArgumentException("Timber and canopy blocks overlap");
        }
        Kind kind(String block) { return timber.contains(block) ? Kind.TIMBER : other.contains(block) ? Kind.FOLIAGE : null; }
    }
    public record Capture(TimberPlantingReceipt receipt, String issue) {
        public boolean ready() { return receipt != null && issue == null; }
    }
    private static final Map<String, Species> SPECIES = new ConcurrentHashMap<>();
    static {
        defaults("Beech", "Beech", "Deco_Nest", "Wood_Oak_Branch_Long");
        defaults("Birch", "Birch", "Deco_Nest");
        defaults("Fir", "Spruce", "Plant_Leaves_Fir_Tip");
        defaults("Apple", "Apple", "Plant_Crop_Apple_Block_Wall");
        defaults("Ash", "Ash");
        defaults("Azure", "Azure", "Plant_Fruit_Azure", "Plant_Moss_Rug_Blue", "Plant_Moss_Wall_Blue");
        defaults("Amber", "Amber", "Plant_Leaves_Goldentree");
        defaults("Aspen", "Aspen", "Deco_Nest");
        defaults("Bamboo", "Bamboo");
        defaults("Banyan", "Banyan");
        defaults("Bottletree", "Bottletree", "Plant_Leaves_Bottle");
        defaults("Camphor", "Camphor");
        defaults("Cedar", "Cedar");
        defaults("Crystal", "Crystal", "*Plant_Crop_Berry_Winter_Block_State_Definitions_StageFinal",
                "Rock_Crystal_Pink_Block", "Rock_Crystal_Pink_Medium", "Rock_Crystal_Pink_Small",
                "Rock_Shale", "Rubble_Shale", "Rubble_Shale_Medium");
        defaults("Dry", "Dry");
        defaults("Fig_Blue", "Fig_Blue");
        defaults("Fire", "Fire", "Deco_Fire");
        defaults("Gumboab", "Gumboab", "Plant_Leaves_Bottle");
        defaults("Ice", "Ice", "Plant_Leaves_Frostwood");
        defaults("Jungle", "Jungle");
        defaults("Maple", "Maple", "Wood_Ash_Branch_Long", "Wood_Oak_Branch_Long", "Wood_Oak_Roots");
        defaults("Oak", "Oak");
        // Native palms build their stems entirely from branch anchors. Count each
        // real anchor once; hitbox fillers and coconuts never award extra logs.
        register(new Species("Wood_Palm_Trunk", "Plant_Sapling_Palm",
                Set.of("Wood_Palm_Branch_Corner", "Wood_Palm_Branch_Long"),
                Set.of("Plant_Fruit_Coconut", "Plant_Leaves_Palm_Arid")));
        defaults("Palo", "Palo");
        defaults("Petrified", "Petrified");
        defaults("Poisoned", "Poisoned");
        defaults("Redwood", "Redwood");
        defaults("Sallow", "Sallow");
        defaults("Spiral", "Spiral");
        defaults("Stormbark", "Stormbark", "Plant_Moss_Rug_Blue");
        defaults("Windwillow", "Windwillow", "Plant_Fruit_Windwillow");
        defaults("Wisteria_Wild", "Wisteria_Wild", "Plant_Vine_Hanging", "Rock_Crystal_Pink_Block");
    }
    /** Stable snapshot for diagnostics and integrations, without exposing mutable registration. */
    public static List<Species> species() {
        return SPECIES.values().stream().sorted(Comparator.comparing(Species::output)).toList();
    }
    private static void defaults(String wood, String sapling, String... extras) {
        String prefix = "Wood_" + wood;
        Set<String> other = new HashSet<>(List.of(prefix + "_Branch_Short", prefix + "_Branch_Long",
                prefix + "_Branch_Corner", prefix + "_Roots", "Plant_Leaves_" + wood));
        Collections.addAll(other, extras);
        register(new Species(prefix + "_Trunk", "Plant_Sapling_" + sapling,
                Set.of(prefix + "_Trunk", prefix + "_Trunk_Full"), other));
    }
    /** Add-ons explicitly opt in their saplings and block families. No prefix guesses about modded blocks. */
    public static void register(Species species) {
        Objects.requireNonNull(species);
        Species previous = SPECIES.putIfAbsent(species.output(), species);
        if (previous != null && !previous.equals(species)) throw new IllegalArgumentException("Timber output already registered: " + species.output());
    }
    public static Species species(String output) { return output == null ? null : SPECIES.get(output); }

    /**
     * For a newly planted sapling beginning at Default stage zero only. Predict
     * every chosen native prefab before planting; save the returned record rather
     * than recomputing it after an asset update or after the root component ends.
     */
    public static Capture capture(String output, Position root, int initialGeneration, Predicate<Position> withinGrove) {
        Species species = species(output);
        if (species == null || root == null || initialGeneration < 0)
            return rejected("This timber does not have a supported sapling.");
        BlockType sapling = BlockType.getAssetMap().getAsset(species.sapling());
        var farming = sapling == null ? null : sapling.getFarming();
        var stages = farming == null || farming.getStages() == null ? null : farming.getStages().get("Default");
        if (stages == null || stages.length < 2 || stages.length > 32 || !"Default".equals(farming.getStartingStageSet())
                || initialGeneration > Integer.MAX_VALUE - stages.length)
            return rejected("This sapling has unsupported growth stages.");
        if (!(stages[0] instanceof BlockTypeFarmingStageData seed) || !species.sapling().equals(seed.getBlock())
                || stages[0].getDuration() == null || stages[stages.length - 1].getDuration() != null)
            return rejected("This sapling needs a supported seed and mature tree stage.");
        PrefabRotation rotation = growthRotation(root);
        List<TimberPlantingReceipt.Stage> planned = new ArrayList<>();
        planned.add(new TimberPlantingReceipt.Stage(0, species.sapling(),
                List.of(new Cell(root, species.sapling(), 0, 0, Kind.FOLIAGE, true))));
        try {
            for (int index = 1; index < stages.length; index++) {
                if (!(stages[index] instanceof PrefabFarmingStageData stage) || stage.getPrefabStages() == null)
                    return rejected("This sapling does not grow through supported tree prefabs.");
                if (index < stages.length - 1 && stage.getDuration() == null)
                    return rejected("This sapling stops growing before its final tree stage.");
                var path = stage.getPrefabStages().get(HashUtil.random(root.x(), root.y(), root.z(), initialGeneration + index)).getResolvedPath();
                IPrefabBuffer prefab = PrefabBufferUtil.getCached(path);
                List<Cell> cells = new ArrayList<>();
                if (prefab.getChildPrefabs() != null && prefab.getChildPrefabs().length > 0)
                    return rejected("Nested tree prefabs need a dedicated timber integration.");
                boolean accepted = prefab.forEachRaw(IPrefabBuffer.iterateAllColumns(),
                    (x, y, z, block, chance, holder, support, facing, filler, ignored) -> {
                        if (block == BlockType.EMPTY_ID) return true;
                        if (chance != 1f || cells.size() >= MAX_TREE_CELLS) return false;
                        BlockType type = BlockType.getAssetMap().getAsset(block);
                        Kind kind = type == null ? null : species.kind(type.getId());
                        if (kind == null) return false;
                        // Native Apple prefabs carry crop growth on their fruit.
                        // Record that fact without accepting chests or arbitrary
                        // saved components as part of a harvestable tree.
                        boolean crop = holder != null && holder.getArchetype().count() == 1
                                && holder.getComponent(FarmingBlock.getComponentType()) != null && type.getFarming() != null;
                        if (holder != null && !crop) return false;
                        Position point = new Position(Math.addExact(root.x(), rotation.getX(x, z)),
                                Math.addExact(root.y(), y), Math.addExact(root.z(), rotation.getZ(x, z)));
                        cells.add(new Cell(point, type.getId(), rotation.getRotation(facing), rotation.getFiller(filler), kind, crop));
                        return true;
                    }, (x, y, z, fluid, level, ignored) -> fluid == 0,
                    (x, z, entities, ignored) -> entities == null || entities.length == 0, null);
                if (!accepted) return rejected("This tree contains unsupported blocks or decorations.");
                planned.add(new TimberPlantingReceipt.Stage(index, path.getFileName().toString(), cells));
            }
        } catch (RuntimeException unavailable) {
            return rejected("The tree's growth assets could not be prepared.");
        }
        Set<Position> clearance = new HashSet<>();
        try {
            for (var stage : planned) for (var cell : stage.cells()) {
                if (cell.filler() != 0) continue;
                var type = BlockType.getAssetMap().getAsset(cell.blockId());
                var boxes = type == null ? null : BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxType());
                if (boxes == null) return rejected("A tree part has no supported growing bounds.");
                FillerBlockUtil.forEachFillerBlock(boxes.get(cell.rotation()), (dx, dy, dz) -> {
                    clearance.add(new Position(Math.addExact(cell.position().x(), dx),
                        Math.addExact(cell.position().y(), dy), Math.addExact(cell.position().z(), dz)));
                    if (clearance.size() > MAX_TREE_CELLS) throw new IllegalArgumentException("Tree clearance limit");
                });
            }
        } catch (RuntimeException unsupported) { return rejected("This tree needs unsupported growing space."); }
        var receipt = new TimberPlantingReceipt(UUID.randomUUID(), species.sapling(), output, root, initialGeneration, planned, clearance);
        String issue = receipt.issue(withinGrove);
        return issue == null ? new Capture(receipt, null) : rejected(issue);
    }

    /** Matches native PrefabFarmingStageData: yaw is hashed from section-local root coordinates. */
    static PrefabRotation growthRotation(Position root) {
        int choice = HashUtil.randomInt(ChunkUtil.localCoordinate(root.x()), ChunkUtil.localCoordinate(root.y()),
                ChunkUtil.localCoordinate(root.z()), Rotation.VALUES.length);
        return PrefabRotation.fromRotation(Rotation.VALUES[choice]);
    }
    private static Capture rejected(String issue) { return new Capture(null, issue); }
}
