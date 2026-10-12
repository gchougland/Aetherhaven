package com.hexvane.aetherhaven.testtown;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * What the test town contains, read from {@code Server/Aetherhaven/TestTown/test_town.json}. A file with the same
 * name in a later asset pack replaces the bundled one, so the town can be changed without rebuilding the mod.
 *
 * <p>{@code buildings} are built complete. {@code buildingSites} are placed and started but not built, so the
 * Building yard has a project to work on.
 *
 * <p>Every list is optional. Entries the running game does not know (a building, a block, an item, a recipe) are
 * skipped when the town is built and named in the report; they never stop the build.
 */
public record TestTownSpec(WorldSpec world, TownSpec town, GridSpec grid, List<BuildingSpec> buildings,
                           List<BuildingSpec> buildingSites, ProductionSpec production, List<WorkplaceSpec> workplaces, StaffSpec staff,
                           List<StackSpec> stock, KitSpec kit) {
    public static final String PATH = "Server/Aetherhaven/TestTown";
    public static final String FILE = "test_town.json";
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** One band of ground, bottom first. */
    public record Layer(String block, int thickness) {}

    /**
     * @param name          folder and command name of the world; lower case letters, digits and underscores only
     * @param environment   the environment every block of the world belongs to, which sets its weather and sky
     * @param naturalSpawns whether the game's own animals and monsters appear, as in a normal world
     * @param layers        the ground, bottom first; the top of the last layer is where the town stands
     */
    public record WorldSpec(String name, String displayName, String environment, boolean naturalSpawns, List<Layer> layers) {
        /** Y of the top ground block. */
        public int surfaceY() {
            int top = 0;
            for (Layer layer : layers) top += layer.thickness();
            return top - 1;
        }
    }

    /**
     * Town Level is not set here: the game works it out from the number of residents, and a full town reaches the top
     * level by itself.
     *
     * @param rank           quest board rank the town starts at, which decides how many guards it may keep
     * @param treasuryGold   gold coins placed in the treasury
     * @param completeQuests quests marked complete besides the building quests, such as villager rescues
     * @param coreMastery    Mastery level given to the farmer, rancher, miner, logger, druid and alchemist
     */
    public record TownSpec(String displayName, String rank, long treasuryGold, List<String> completeQuests, int coreMastery) {}

    /**
     * @param rowWidth widest a row of buildings may be, in blocks
     * @param street   clear blocks between neighbours and between rows
     * @param plaza    clear blocks on each side of the charter
     */
    public record GridSpec(int rowWidth, int street, int plaza) {}

    /** A building of the mod by construction id; {@code count} copies are placed (default 1). */
    public record BuildingSpec(String id, int count) {
        public int copies() { return Math.max(1, count); }
    }

    public record StackSpec(String item, int count) {}

    /**
     * A block placed inside a workplace area. {@code x} and {@code z} count from the area's north west corner. The
     * block stands on the ground; {@code below}, when given, replaces the ground block under it (tilled soil for a crop).
     *
     * @param turn quarter turns, 0 to 3
     */
    public record BlockSpec(String block, int x, int z, int turn, String below) {}

    /** An order given to a workplace: a recipe id, or "auto" for the first recipe its bench can make. */
    public record OrderSpec(String recipe, int amount) {}

    /**
     * One Apprentice workplace.
     *
     * @param type    a {@code TownsfolkWorkType} id
     * @param width   blocks along X; a mineshaft is always 5 wide
     * @param depth   blocks along Z; a mineshaft's tunnel is this minus 3 long
     * @param floor   block the ground inside the area is replaced with, or null to leave the grass
     * @param blocks  benches and other blocks the workplace needs inside its area
     * @param workers townsfolk hired for it, each with this work as their only priority
     * @param skill   skill level those townsfolk are given in this work
     * @param choice  what a grower site grows, or the tunnel layout of a mineshaft
     */
    public record WorkplaceSpec(String type, String name, int width, int depth, String floor, List<BlockSpec> blocks,
                                int workers, int skill, List<OrderSpec> orders, String choice) {}

    /**
     * @param couriers   townsfolk whose only work is Logistics
     * @param guards     townsfolk put on guard duty at the guild hall
     * @param guardClass guard_knight, guard_archer, guard_rogue or guard_mage
     * @param clerks     townsfolk assigned to the player shop (0 or 1)
     * @param skill      skill level couriers and clerks are given in their work
     */
    public record StaffSpec(int couriers, int guards, String guardClass, int clerks, int skill) {}

    /**
     * @param farmOutputs  crop items the farm's output slots are pointed at, first slot first
     * @param mineOutputs  products the mine's output slots are pointed at, first slot first
     * @param animals      animal ids bought for the barn
     * @param saplings     wood items (such as Wood_Beech_Trunk) bound to Rootstones at the lumbermill
     * @param gardenPlants plant items (such as Plant_Crop_Health1) bound to Spirit Planters at the Conservatory
     */
    public record ProductionSpec(List<String> farmOutputs, List<String> mineOutputs, List<String> animals,
                                 List<String> saplings, List<String> gardenPlants) {}

    /**
     * What the player who runs the command is given.
     *
     * @param creative   whether they are switched to creative mode, which also lets them fly
     * @param items      plain items
     * @param plotTokens construction ids to give one plot token each for
     * @param props      prop ids to give one of each
     */
    public record KitSpec(boolean creative, List<StackSpec> items, List<String> plotTokens, List<String> props) {}

    /** The bundled file, or a later pack's replacement of it. Null when neither can be read. */
    public static TestTownSpec load(ClassLoader loader) {
        TestTownSpec spec = null;
        try {
            spec = loadFromClasspath(loader);
        } catch (Exception failure) {
            LOG.atWarning().withCause(failure).log("Unable to read the bundled test town layout");
        }
        try {
            for (var file : AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(PATH)) {
                if (!FILE.equals(file.absolutePath().getFileName().toString())) continue;
                try (InputStream in = Files.newInputStream(file.absolutePath())) {
                    TestTownSpec replacement = parse(in);
                    if (replacement != null && replacement.problem() == null) spec = replacement;
                    else LOG.atWarning().log("Ignoring test town layout %s: %s", file.absolutePath(),
                        replacement == null ? "it is empty" : replacement.problem());
                } catch (Exception failure) {
                    LOG.atWarning().withCause(failure).log("Unable to read test town layout %s", file.absolutePath());
                }
            }
        } catch (RuntimeException failure) {
            LOG.atWarning().withCause(failure).log("Unable to scan asset packs for a test town layout");
        }
        return spec;
    }

    /** Bundled file only, for tests that run without the asset packs. */
    public static TestTownSpec loadFromClasspath(ClassLoader loader) throws IOException {
        try (InputStream in = loader.getResourceAsStream(PATH + "/" + FILE)) {
            return in == null ? null : parse(in);
        }
    }

    static TestTownSpec parse(InputStream in) {
        TestTownSpec raw = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), TestTownSpec.class);
        return raw == null ? null : raw.normalized();
    }

    /** Fills every missing list so the builder never has to test for null. */
    TestTownSpec normalized() {
        WorldSpec w = world == null ? null : new WorldSpec(world.name(), world.displayName(), world.environment(),
            world.naturalSpawns(), list(world.layers()));
        TownSpec t = town == null ? null : new TownSpec(town.displayName(), town.rank(), town.treasuryGold(),
            list(town.completeQuests()), Math.clamp(town.coreMastery(), 1, 10));
        ProductionSpec p = production == null ? new ProductionSpec(List.of(), List.of(), List.of(), List.of(), List.of())
            : new ProductionSpec(list(production.farmOutputs()), list(production.mineOutputs()), list(production.animals()),
                list(production.saplings()), list(production.gardenPlants()));
        List<WorkplaceSpec> sites = list(workplaces).stream().map(s -> new WorkplaceSpec(s.type(), s.name(), s.width(), s.depth(),
            s.floor(), list(s.blocks()), s.workers(), s.skill(), list(s.orders()), s.choice())).toList();
        KitSpec k = kit == null ? new KitSpec(false, List.of(), List.of(), List.of())
            : new KitSpec(kit.creative(), list(kit.items()), list(kit.plotTokens()), list(kit.props()));
        return new TestTownSpec(w, t, grid, list(buildings), list(buildingSites), p, sites,
            staff == null ? new StaffSpec(0, 0, null, 0, 1) : staff, list(stock), k);
    }

    private static <T> List<T> list(List<T> values) {
        if (values == null) return List.of();
        List<T> out = new ArrayList<>();
        for (T value : values) if (value != null) out.add(value);
        return List.copyOf(out);
    }

    /** Why this layout cannot be used at all, or null when it can. */
    public String problem() {
        if (world == null || world.name() == null || !world.name().matches("[a-z0-9_]{3,48}"))
            return "world.name must be 3 to 48 lower case letters, digits or underscores";
        if (world.layers().isEmpty()) return "world.layers is empty";
        for (Layer layer : world.layers())
            if (layer.block() == null || layer.block().isBlank() || layer.thickness() < 1)
                return "world.layers has an entry without a block or thickness";
        if (world.surfaceY() < 16 || world.surfaceY() > 200) return "the ground must end between Y 16 and Y 200";
        if (world.environment() == null || world.environment().isBlank()) return "world.environment is missing";
        if (town == null) return "town is missing";
        if (grid == null || grid.rowWidth() < 32 || grid.street() < 4 || grid.plaza() < 6)
            return "grid needs rowWidth of 32 or more, street of 4 or more and plaza of 6 or more";
        if (buildings.isEmpty()) return "buildings is empty";
        for (BuildingSpec building : buildings)
            if (building.id() == null || building.id().isBlank()) return "a building has no id";
        for (BuildingSpec building : buildingSites)
            if (building.id() == null || building.id().isBlank()) return "a building site has no id";
        List<String> types = new ArrayList<>();
        for (WorkplaceSpec workplace : workplaces) {
            if (workplace.type() == null || workplace.type().isBlank()) return "a workplace has no type";
            if (types.contains(workplace.type())) return "workplace " + workplace.type() + " is listed twice";
            types.add(workplace.type());
            if (workplace.width() < 3 || workplace.depth() < 3 || workplace.width() > 64 || workplace.depth() > 64)
                return "workplace " + workplace.type() + " must be 3 to 64 blocks on each side";
            for (BlockSpec block : workplace.blocks())
                if (block.block() == null || block.block().isBlank() || block.x() < 0 || block.z() < 0
                    || block.x() >= workplace.width() || block.z() >= workplace.depth() || block.turn() < 0 || block.turn() > 3)
                    return "workplace " + workplace.type() + " has a block outside its area";
            for (OrderSpec order : workplace.orders())
                if (order.recipe() == null || order.recipe().isBlank() || order.amount() < 1)
                    return "workplace " + workplace.type() + " has an order without a recipe or amount";
        }
        for (StackSpec stack : stock) if (stack.item() == null || stack.item().isBlank() || stack.count() < 1) return "stock has an entry without an item or count";
        for (StackSpec stack : kit.items()) if (stack.item() == null || stack.item().isBlank() || stack.count() < 1) return "kit.items has an entry without an item or count";
        return null;
    }
}
