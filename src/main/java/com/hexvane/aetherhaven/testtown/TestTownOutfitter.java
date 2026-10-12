package com.hexvane.aetherhaven.testtown;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyBuildStartResult;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.guild.GuardDutyService;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.pathtool.PathCementService;
import com.hexvane.aetherhaven.pathtool.PathCommitRecord;
import com.hexvane.aetherhaven.pathtool.PathNavPolylineUtil;
import com.hexvane.aetherhaven.pathtool.PathPlannedCell;
import com.hexvane.aetherhaven.pathtool.PathSplineUtil;
import com.hexvane.aetherhaven.pathtool.PathToolNode;
import com.hexvane.aetherhaven.pathtool.PathToolPersistence;
import com.hexvane.aetherhaven.pathtool.PathToolRegistry;
import com.hexvane.aetherhaven.production.BarnAnimalCatalog;
import com.hexvane.aetherhaven.production.GardenUpgrades;
import com.hexvane.aetherhaven.production.NativeFarmCrops;
import com.hexvane.aetherhaven.production.NativeHerdService;
import com.hexvane.aetherhaven.production.NativeTimberGrowth;
import com.hexvane.aetherhaven.production.NativeTimberPlanting;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.production.Rootstone;
import com.hexvane.aetherhaven.production.SpiritGarden;
import com.hexvane.aetherhaven.production.SpiritGardenRules;
import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades;
import com.hexvane.aetherhaven.shopspot.ShopStaffing;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.HouseResidentAssignment;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPopulation;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgressService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkRoster;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.BuildingProjectAutoQueue;
import com.hexvane.aetherhaven.workplace.ExcavationFittings;
import com.hexvane.aetherhaven.workplace.ExcavationPlan;
import com.hexvane.aetherhaven.workplace.GrovePage;
import com.hexvane.aetherhaven.workplace.GrowerPlan;
import com.hexvane.aetherhaven.workplace.GrowerSiteService;
import com.hexvane.aetherhaven.workplace.MineshaftSelection;
import com.hexvane.aetherhaven.workplace.NativeExcavation;
import com.hexvane.aetherhaven.workplace.TownWorkplaces;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import com.hexvane.aetherhaven.workplace.WorkplaceEquipment;
import com.hexvane.aetherhaven.workplace.WorkplaceOrders;
import com.hexvane.aetherhaven.workplace.WorkplaceRecipe;
import com.hexvane.aetherhaven.workplace.WorkplaceRules;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Everything the test town holds beyond its buildings and core villagers: production set up at the farm, mine, barn,
 * lumbermill and Conservatory, one Apprentice workplace of every type with its bench, hired and housed townsfolk,
 * couriers, guards, a shop clerk, warehouse stock, orders and streets.
 *
 * <p>Almost all of it goes through the calls the game's own screens make. These are the places where no such call can
 * be driven without a player and the state is written directly, in the same way the mod's own test fixtures do:
 * <ul>
 *   <li>A hired townsfolk is made a citizen by adding a citizen visitor record. In play that record comes from
 *       inviting a guild hall adventurer in conversation and giving a move in gift.</li>
 *   <li>Their needs start full, their work skill is set to the level the layout asks for, and their work priorities
 *       are set. In play those come from training and from the journal's Work page.</li>
 *   <li>Benches, crops, Mushroom Logs, Rootstones and Spirit Planters are placed as blocks. In play a player places
 *       them by hand.</li>
 *   <li>Extra workplace slots, warehouse room and barn animals are paid for from the treasury, with no player
 *       inventory in the picture.</li>
 *   <li>The farmer, rancher, miner, logger, druid and alchemist are trained to the Mastery level the layout asks for
 *       without the cost a player pays for each level.</li>
 *   <li>A building site is recorded as a plot without its sign ever standing in the world: starting construction
 *       removes the sign in the same moment.</li>
 * </ul>
 * The builder itself adds three more: the town's rank and treasury are set, the quests the layout lists are marked
 * complete, and a core villager the town has too little food to admit is bound to their building anyway.
 */
final class TestTownOutfitter {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** Open ground in the grid that production blocks stand on. */
    enum Yard {
        GROVE("yard:grove", 40, 22),
        GARDEN("yard:garden", 13, 7);

        final String key;
        final int width, depth;

        Yard(String key, int width, int depth) { this.key = key; this.width = width; this.depth = depth; }
    }

    static String workplaceKey(TestTownSpec.WorkplaceSpec workplace) { return "work:" + workplace.type(); }

    /** A hired townsfolk. */
    private record Hired(String characterId, UUID entity) {}

    /** One bed in one house. */
    private record Home(UUID plot, int slot) {}

    private final TestTownBuilder b;
    private final AetherhavenPlugin plugin;
    private final World world;
    private final TestTownReport report;
    private final Random random = new Random(4040L);
    /** Free home slots, taken in order as townsfolk are hired. */
    private final Deque<Home> homes = new ArrayDeque<>();
    private final Map<TownsfolkWorkType, Integer> workerSkill = new LinkedHashMap<>();
    private final Map<TownsfolkWorkType, ApprenticeWorkplace> sites = new LinkedHashMap<>();

    TestTownOutfitter(TestTownBuilder builder) {
        this.b = builder;
        this.plugin = builder.plugin;
        this.world = builder.world;
        this.report = builder.report;
    }

    private TownRecord town() { return b.town(); }
    private TownManager manager() { return b.manager; }
    private Store<EntityStore> store() { return world.getEntityStore().getStore(); }
    private int groundY() { return b.groundY(); }

    /** Runs one part of a stage. A failure is written to the report under the part's name and nothing else stops. */
    private void part(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.atWarning().withCause(failure).log("Test town could not set up %s", what);
            report.skip(what, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private boolean place(int x, int y, int z, String block, int turn) {
        if (BlockType.getAssetMap().getAsset(block) == null) return false;
        int rotation = RotationTuple.of(Rotation.VALUES[Math.floorMod(turn, 4)], Rotation.None).index();
        return ChunkSectionBlockUtil.setBlockByKey(world, x, y, z, block, rotation, 0);
    }

    // ---------------------------------------------------------------------------------------------- production

    void production() {
        TownRecord town = town();
        part("core villager mastery", () -> {
            for (String kind : List.of("farmer", "rancher", "miner", "logger", "druid", "alchemist")) {
                if (!TownWorkplaces.hasSupervisor(town, kind)) continue;
                var mastery = CoreVillagerMasteryService.progress(town, kind);
                while (mastery.level() < b.spec.town().coreMastery() && mastery.train()) { /* one level at a time */ }
            }
        });
        part("farm fields", this::farm);
        part("mine nodes", this::mine);
        part("barn herd", this::barn);
        part("lumbermill grove", this::grove);
        part("Conservatory garden", this::garden);
        manager().updateTown(town);
    }

    private void farm() {
        TownRecord town = town();
        PlotInstance plot = b.completePlot(AetherhavenConstants.CONSTRUCTION_PLOT_FARM);
        if (plot == null) { report.skip("farm fields", "there is no complete farm"); return; }
        var review = NativeFarmCrops.review(world, town, plot);
        if (!review.ready()) { report.skip("farm fields", review.issue() == null ? "the farm has no crops to adopt" : review.issue()); return; }
        PlotProductionState state = com.hexvane.aetherhaven.production.FarmFields.production(town, plot);
        int planned = 0;
        for (var bed : review.beds())
            if (state.farm().paint(com.hexvane.aetherhaven.production.FarmFieldsState.HOME, bed.point().x(), bed.point().y(), bed.point().z(), bed.output())) planned++;
        if (planned == 0 && state.farm().plannedCount() == 0) { report.skip("farm fields", "no crop bed could be planned"); return; }
        selectOutputs(state, AetherhavenConstants.CONSTRUCTION_PLOT_FARM, b.spec.production().farmOutputs(), "farm");
        report.add(TestTownReport.PRODUCTION);
        report.note("Farm: " + review.beds().size() + " crop beds adopted.");
    }

    private void mine() {
        TownRecord town = town();
        PlotInstance plot = b.completePlot(AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT);
        if (plot == null) { report.skip("mine nodes", "there is no complete Miner's Hut"); return; }
        var layout = plot.getProductionLayout();
        if (layout == null || layout.legacy() || layout.nodes() == null || layout.nodes().isEmpty()) {
            report.skip("mine nodes", "this Miner's Hut has no mining nodes in its layout");
            return;
        }
        PlotProductionState state = town.getOrCreatePlotProduction(plot.getPlotId());
        selectOutputs(state, AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT, b.spec.production().mineOutputs(), "mine");
        report.add(TestTownReport.PRODUCTION);
        report.note("Mine: " + layout.nodes().size() + " nodes, first product " + b.spec.production().mineOutputs().stream().findFirst().orElse("the default") + ".");
    }

    /** Points the building's output slots at the wanted products, as far as it has slots and the products are unlocked. */
    private void selectOutputs(PlotProductionState state, String construction, List<String> wanted, String label) {
        var entry = ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(), plugin.getWorkplaceUnlockCatalog(), construction, state);
        if (entry == null) { report.skip(label + " products", "the production catalog has no entry for " + construction); return; }
        int slots = WorkplaceProductionUpgrades.slotCount(state);
        for (int slot = 0; slot < Math.min(slots, wanted.size()); slot++)
            if (!state.setSlotCursorToItem(slot, entry, wanted.get(slot)))
                report.skip(label + " product " + wanted.get(slot), "it is not unlocked at this building yet");
    }

    private void barn() {
        TownRecord town = town();
        PlotInstance plot = town.getPlotInstances().stream().filter(p -> p.getState() == PlotInstanceState.COMPLETE
            && NativeHerdService.barn(plugin, p)).findFirst().orElse(null);
        if (plot == null) { report.skip("barn herd", "there is no complete barn"); return; }
        PlotProductionState state = town.getOrCreatePlotProduction(plot.getPlotId());
        var herd = state.herd();
        int mastery = CoreVillagerMasteryService.level(town, "rancher"), owned = 0;
        for (String species : b.spec.production().animals()) {
            var kind = BarnAnimalCatalog.get(species);
            if (kind == null) { report.skip("barn animal " + species, "the barn has no such animal"); continue; }
            String refused = herd.buy(species, mastery, () -> payFromTreasury(kind.rarity().gold));
            if (refused != null) { report.skip("barn animal " + species, refused); continue; }
            owned++;
            for (int meal = 0; meal < 8; meal++) herd.supply(kind.food(), () -> true);
        }
        int pens = WorkplaceProductionUpgrades.slotCount(state);
        for (int pen = 0; pen < Math.min(pens, herd.animals().size()); pen++)
            herd.assign(pen, herd.animals().get(pen).id(), pens);
        NativeHerdService.update(world, plugin, town, plot);
        if (owned > 0) report.add(TestTownReport.PRODUCTION);
        report.note("Barn: " + owned + " animals owned, " + Math.min(pens, owned) + " in a pen.");
    }

    private boolean payFromTreasury(long gold) {
        TownRecord town = town();
        if (town.getTreasuryGoldCoinCount() < gold) return false;
        town.addTreasuryGoldCoins(-gold);
        return true;
    }

    private void grove() {
        TownRecord town = town();
        PlotInstance plot = b.completePlot(AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL);
        TestTownGrid.Cell yard = b.grid.find(Yard.GROVE.key);
        if (plot == null || yard == null) { report.skip("lumbermill grove", "there is no complete lumbermill"); return; }
        PlotProductionState state = town.getOrCreatePlotProduction(plot.getPlotId());
        var timber = state.timber();
        int allowance = WorkplaceProductionUpgrades.stoneCount(state), bound = 0;
        List<String> woods = b.spec.production().saplings();
        int z = Math.floorDiv(yard.minZ() + yard.maxZ(), 2);
        for (int stone = 0; stone < Math.min(allowance, woods.size()); stone++) {
            String wood = woods.get(stone);
            // Stones are spread along the yard so each tree has room for its crown.
            int x = yard.minX() + (int) Math.round((stone + 0.5) * Yard.GROVE.width / (double) Math.min(allowance, woods.size()));
            Position root = new Position(x, groundY(), z);
            if (NativeTimberGrowth.species(wood) == null) { report.skip("Rootstone for " + wood, "no tree grows that wood"); continue; }
            if (!place(root.x(), root.y() - 1, root.z(), Rootstone.BLOCK, 0)) { report.skip("Rootstone for " + wood, "the Rootstone block could not be placed"); continue; }
            var captured = NativeTimberGrowth.capture(wood, root, 0, GrovePage.bounds(town));
            if (!captured.ready()) { report.skip("Rootstone for " + wood, captured.issue()); continue; }
            int slot = timber.freeStone(allowance);
            String refused = slot < 0 ? "this lumbermill cannot tend another Rootstone yet"
                : timber.plan(slot, allowance, timber.layoutRevision(), captured.receipt(), GrovePage.bounds(town),
                    position -> NativeTimberPlanting.observe(world, position));
            if (refused != null) { report.skip("Rootstone for " + wood, refused); continue; }
            bound++;
        }
        if (bound > 0) report.add(TestTownReport.PRODUCTION);
        report.note("Lumbermill: " + bound + " Rootstones bound to saplings. The Logger plants them on her next visit.");
    }

    private void garden() {
        TownRecord town = town();
        PlotInstance plot = b.completePlot(SpiritGarden.CONSTRUCTION);
        TestTownGrid.Cell yard = b.grid.find(Yard.GARDEN.key);
        if (plot == null || yard == null) { report.skip("Conservatory garden", "there is no complete Conservatory"); return; }
        PlotProductionState state = SpiritGarden.production(town, plot);
        var garden = state.garden();
        int allowance = GardenUpgrades.planters(state), bound = 0;
        List<String> plants = b.spec.production().gardenPlants();
        int z = Math.floorDiv(yard.minZ() + yard.maxZ(), 2);
        for (int planter = 0; planter < Math.min(allowance, plants.size()); planter++) {
            String plant = plants.get(planter);
            int x = yard.minX() + 2 + planter * 3;
            if (SpiritGardenRules.get().plant(plant) == null) { report.skip("Spirit Planter for " + plant, "the garden does not grow that plant"); continue; }
            if (!place(x, groundY(), z, SpiritGarden.BLOCK, 0)) { report.skip("Spirit Planter for " + plant, "the planter block could not be placed"); continue; }
            int slot = garden.freeSlot(allowance);
            String refused = slot < 0 ? "full" : garden.bind(slot, allowance, garden.revision(), new WarehouseBatch.Point(x, groundY(), z), plant);
            if (refused != null) { report.skip("Spirit Planter for " + plant, "the garden answered '" + refused + "'"); continue; }
            bound++;
        }
        if (bound > 0) report.add(TestTownReport.PRODUCTION);
        report.note("Conservatory: " + bound + " Spirit Planters bound to seeds. The Druid plants them on his round.");
    }

    // ---------------------------------------------------------------------------------------------- workplaces

    void workplaces() {
        TownRecord town = town();
        TownWorkplaces registry = town.getApprenticeWorkplaces();
        for (TestTownSpec.WorkplaceSpec spec : b.spec.workplaces()) {
            String label = "workplace " + spec.type();
            part(label, () -> {
                TownsfolkWorkType type = TownsfolkWorkType.byId(spec.type());
                if (type == null) { report.skip(label, "there is no work type with that id"); return; }
                if (!type.requiresWorkplace() || !type.availableForNewWork()) { report.skip(label, "this work does not take a new workplace"); return; }
                String supervisor = WorkplaceRules.supervisor(type);
                String refused = registry.unlock(town, type);
                if (refused != null) { report.skip(label, refused + " (supervisor: " + supervisor + ")"); return; }
                // One more slot when this supervisor's workplaces are all in use, paid from the treasury.
                while (registry.used(supervisor) >= registry.slots(supervisor)) {
                    String slot = registry.buySlot(town, b.owner.uuid(), supervisor, registry.extraSlots(supervisor),
                        new CombinedItemContainer(new SimpleItemContainer((short) 1)));
                    if (slot != null) { report.skip(label, "no free slot for the " + supervisor + ": " + slot); return; }
                }
                TestTownGrid.Cell cell = b.grid.find(workplaceKey(spec));
                WorkplaceArea area = type == TownsfolkWorkType.MINING ? shaftArea(spec, cell)
                    : new WorkplaceArea(cell.minX(), groundY(), cell.minZ(), cell.maxX() + 1, cell.maxZ() + 1);
                String name = spec.name() == null || spec.name().isBlank() ? "Test " + type.id : spec.name();
                refused = registry.validate(town, null, type, area, name);
                if (refused != null) { report.skip(label, refused); return; }
                furnish(spec, cell, label);
                refused = registry.save(town, null, type, area, name);
                if (refused != null) { report.skip(label, refused); return; }
                ApprenticeWorkplace site = registry.sites().getLast();
                sites.put(type, site);
                workerSkill.put(type, Math.clamp(spec.skill(), 1, 10));
                report.add(TestTownReport.WORKPLACES);
                if (type == TownsfolkWorkType.MINING) planShaft(spec, cell, site, label);
                if (GrowerPlan.supports(type)) chooseCrop(spec, site, label);
                String missing = TestTownCheck.benchProblem(world, site);
                if (missing != null) report.skip(label + " bench", missing);
            });
        }
        manager().updateTown(town);
    }

    /** Floor, benches and beds, placed before the area is saved so the site is complete from its first moment. */
    private void furnish(TestTownSpec.WorkplaceSpec spec, TestTownGrid.Cell cell, String label) {
        if (spec.floor() != null && !spec.floor().isBlank()) {
            if (BlockType.getAssetMap().getAsset(spec.floor()) == null) report.skip(label + " floor", "unknown block " + spec.floor());
            else for (int x = cell.minX(); x <= cell.maxX(); x++) for (int z = cell.minZ(); z <= cell.maxZ(); z++)
                place(x, groundY() - 1, z, spec.floor(), 0);
        }
        for (TestTownSpec.BlockSpec block : spec.blocks()) {
            int x = cell.minX() + block.x(), z = cell.minZ() + block.z();
            if (block.below() != null && !block.below().isBlank() && !place(x, groundY() - 1, z, block.below(), 0))
                report.skip(label + " block " + block.below(), "it could not be placed under " + block.block());
            if (!place(x, groundY(), z, block.block(), block.turn()))
                report.skip(label + " block " + block.block(), "unknown block or no room at " + x + ", " + groundY() + ", " + z);
        }
    }

    private MineshaftSelection.Direction travel(TestTownGrid.Cell cell) {
        // The shaft opens onto the street and runs away from it.
        return cell.south() ? MineshaftSelection.Direction.SOUTH : MineshaftSelection.Direction.NORTH;
    }

    private ExcavationPlan.Layout shaftLayout(TestTownSpec.WorkplaceSpec spec) {
        try {
            return spec.choice() == null || spec.choice().isBlank() ? ExcavationPlan.Layout.STAIRS_DOWN
                : ExcavationPlan.Layout.valueOf(spec.choice().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return ExcavationPlan.Layout.STAIRS_DOWN;
        }
    }

    private WorkplaceArea shaftArea(TestTownSpec.WorkplaceSpec spec, TestTownGrid.Cell cell) {
        int x = cell.centerX(), z = cell.south() ? cell.minZ() + 2 : cell.maxZ() - 2;
        return MineshaftSelection.area(x, groundY() - 1, z, travel(cell), spec.depth() - 3, shaftLayout(spec));
    }

    private void planShaft(TestTownSpec.WorkplaceSpec spec, TestTownGrid.Cell cell, ApprenticeWorkplace site, String label) {
        TownRecord town = town();
        var entrance = MineshaftSelection.entrance(travel(cell));
        var layout = shaftLayout(spec);
        site.setMineshaftConfiguration(entrance, layout);
        var review = NativeExcavation.review(world, town, site, entrance, layout);
        if (!review.ready()) { report.skip(label + " shaft", review.issue()); return; }
        String refused = site.excavation().start(0, b.owner.uuid(), town.getApprenticeWorkplaces().mineBase(site).id(), site.area(),
            entrance, layout, review.cells(), ExcavationFittings.Mode.NONE);
        if (refused != null) { report.skip(label + " shaft", refused); return; }
        report.add(TestTownReport.ORDERS);
        report.note("Mineshaft: " + layout.label + " planned, " + review.cells().size() + " blocks to dig.");
    }

    private void chooseCrop(TestTownSpec.WorkplaceSpec spec, ApprenticeWorkplace site, String label) {
        GrowerPlan plan = GrowerSiteService.plan(site.type());
        if (plan == null) { report.skip(label + " crop", "there is no grower plan for this work"); return; }
        GrowerSiteService.sync(world, site);
        if (plan.onlyCrop() != null) return;
        if (spec.choice() == null || spec.choice().isBlank()) { report.skip(label + " crop", "the layout names no crop to grow"); return; }
        String refused = site.grower().choose(plan, spec.choice(), Math.clamp(spec.skill(), 1, 10), site.grower().revision());
        if (refused != null) { report.skip(label + " crop " + spec.choice(), "the site answered '" + refused + "'"); return; }
        GrowerSiteService.refreshBeds(world, site);
    }

    // ---------------------------------------------------------------------------------------------- townsfolk

    void townsfolk() {
        TownRecord town = town();
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE || !plugin.getConstructionCatalog()
                .matchesGameplayConstruction(plot.getConstructionId(), AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE)) continue;
            var definition = plugin.getConstructionCatalog().get(plot.getConstructionId());
            int beds = definition == null ? 1 : definition.getMaxHomeResidents();
            for (int slot = 0; slot < beds; slot++) homes.add(new Home(plot.getPlotId(), slot));
        }
        for (TestTownSpec.WorkplaceSpec spec : b.spec.workplaces()) {
            TownsfolkWorkType type = TownsfolkWorkType.byId(spec.type());
            ApprenticeWorkplace site = type == null ? null : sites.get(type);
            if (site == null) continue;
            TestTownGrid.Cell cell = b.grid.find(workplaceKey(spec));
            for (int worker = 0; worker < Math.max(1, spec.workers()); worker++) {
                int index = worker;
                part("townsfolk for " + type.id, () -> {
                    Hired hired = hire(streetSpot(cell, index), "townsfolk for " + type.id, null);
                    if (hired == null) return;
                    setWork(hired, type, workerSkill.getOrDefault(type, 1));
                    report.add(TestTownReport.ASSIGNED);
                });
            }
        }
        PlotInstance warehouse = b.completePlot("plot_warehouse");
        for (int courier = 0; courier < b.spec.staff().couriers(); courier++) {
            int index = courier;
            part("courier", () -> {
                Hired hired = hire(spotNear(warehouse, index), "courier", null);
                if (hired == null) return;
                setWork(hired, TownsfolkWorkType.LOGISTICS, b.spec.staff().skill());
                report.add(TestTownReport.COURIERS);
            });
        }
        part("shop clerk", this::clerks);
        part("guards", this::guards);
        // The journal's Work page lists the town's townsfolk the same way before any of them is put to work.
        TownsfolkWorkRoster.list(town, manager(), store(), plugin);
        manager().updateTown(town);
    }

    private Vector3d streetSpot(TestTownGrid.Cell cell, int index) {
        return new Vector3d(cell.centerX() + 0.5 + index * 2, groundY(), cell.streetZ(2) + 0.5);
    }

    private Vector3d spotNear(PlotInstance plot, int index) {
        if (plot == null) return new Vector3d(3.5 + index * 2, groundY(), 3.5);
        return new Vector3d(plot.getSignX() + 1.5 + index * 2, groundY(), plot.getSignZ() + 0.5);
    }

    /**
     * Spawns a townsfolk from the town's pool, makes them a citizen and gives them a home. Returns null, with the
     * reason in the report, when any step fails.
     */
    private Hired hire(Vector3d position, String label, String characterId) {
        TownRecord town = town();
        if (homes.isEmpty()) { report.skip(label, "every house already has its residents; add houses to the layout"); return null; }
        var spawned = TownsfolkSpawnService.trySpawn(world, plugin, town, store(), position, TownsfolkAssignmentKinds.IDLE, characterId, random);
        if (spawned.isEmpty()) { report.skip(label, "no townsfolk could be spawned at " + position); return null; }
        var person = spawned.get();
        TouristRecord record = new TouristRecord(person.characterId(), person.entityUuid(), UUID.randomUUID(), true, true);
        record.setPortalId(null);
        town.getTouristRecords().add(record);
        var ref = store().getExternalData().getRefFromUUID(person.entityUuid());
        if (ref != null && ref.isValid()) store().putComponent(ref, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        // They are a resident from here, as after a move in: they start with priorities for what they are good at.
        TownsfolkWorkRoster.startResidentWork(town, manager(), plugin, person.characterId());
        Home home = homes.poll();
        var outcome = HouseResidentAssignment.assignResident(town, home.plot(), home.slot(), person.entityUuid(), manager(),
            world, store(), plugin.getConstructionCatalog());
        if (outcome != HouseResidentAssignment.Outcome.ASSIGNED) {
            report.skip(label + " housing", "the house answered " + outcome);
            homes.addFirst(home);
        }
        report.add(TestTownReport.TOWNSFOLK);
        return new Hired(person.characterId(), person.entityUuid());
    }

    /** One job at first priority and nothing else, so each workplace keeps the worker it was given. */
    private void setWork(Hired hired, TownsfolkWorkType work, int skill) {
        TownRecord town = town();
        TownsfolkProgress progress = TownsfolkProgressService.getOrCreate(town, manager(), plugin, hired.characterId());
        progress.setSkill(work.skill, Math.max(Math.clamp(skill, 1, 10), progress.skill(work.skill)));
        if (work == TownsfolkWorkType.CRAFTING) progress.setSkill(TownsfolkSkill.FARMING, Math.max(Math.clamp(skill, 1, 10), progress.skill(TownsfolkSkill.FARMING)));
        for (TownsfolkWorkType type : TownsfolkWorkType.values()) progress.setPriority(type, type == work ? 1 : 0);
        progress.setEmployed(true);
        town.getPayroll().ensureCurrentBill(hired.characterId());
    }

    private void clerks() {
        TownRecord town = town();
        if (b.spec.staff().clerks() <= 0) return;
        PlotInstance shop = town.getPlotInstances().stream().filter(p -> ShopStaffing.isShop(p, plugin.getConstructionCatalog())).findFirst().orElse(null);
        if (shop == null) { report.skip("shop clerk", "there is no complete player shop"); return; }
        Hired hired = hire(spotNear(shop, 0), "shop clerk", null);
        if (hired == null) return;
        setWork(hired, TownsfolkWorkType.COMMERCE, b.spec.staff().skill());
        ShopStaffing.assign(town, shop, hired.characterId());
        report.add(TestTownReport.CLERKS);
    }

    private void guards() {
        TownRecord town = town();
        PlotInstance hall = b.completePlot(AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL);
        if (b.spec.staff().guards() <= 0) return;
        if (hall == null) { report.skip("guards", "there is no complete guild hall"); return; }
        List<String> guardable = TownsfolkSpawnService.availableCharacterIds(world, plugin, town.getTownId(), TownsfolkAssignmentKinds.GUARD);
        List<String> idle = TownsfolkSpawnService.availableCharacterIds(world, plugin, town.getTownId(), TownsfolkAssignmentKinds.IDLE);
        List<String> candidates = new ArrayList<>(guardable);
        candidates.retainAll(idle);
        candidates.sort(Comparator.naturalOrder());
        String profile = b.spec.staff().guardClass() == null || b.spec.staff().guardClass().isBlank() ? "guard_knight" : b.spec.staff().guardClass();
        for (int guard = 0; guard < b.spec.staff().guards(); guard++) {
            if (candidates.isEmpty()) { report.skip("guard " + (guard + 1), "no townsfolk who can stand guard is left in the pool"); break; }
            Hired hired = hire(spotNear(hall, guard), "guard " + (guard + 1), candidates.removeFirst());
            if (hired == null) continue;
            TownsfolkProgress progress = TownsfolkProgressService.getOrCreate(town, manager(), plugin, hired.characterId());
            progress.setEmployed(true);
            town.getPayroll().ensureCurrentBill(hired.characterId());
            String refused = GuardDutyService.change(store(), town, manager(), plugin, b.owner.uuid(), hired.entity(), hired.characterId(), profile);
            if (refused != null) { report.skip("guard " + (guard + 1) + " duty", refused); continue; }
            report.add(TestTownReport.GUARDS);
        }
    }

    // ---------------------------------------------------------------------------------------------- building sites

    /**
     * Records a plot and starts its construction through the call a creative player's Build click makes, then leaves
     * it unbuilt. Starting a project puts it on a Building yard by itself, so the builders have something to raise.
     */
    void buildingSites() {
        TownRecord town = town();
        for (TestTownBuilder.Placement site : b.sitePlacements.values()) {
            String label = "building site " + site.constructionId();
            part(label, () -> {
                if (!manager().isInsideTerritory(town, site.footprint().getMinX(), site.footprint().getMinZ())
                    || !manager().isInsideTerritory(town, site.footprint().getMaxX(), site.footprint().getMaxZ())) {
                    report.skip(label, "its place in the grid is outside town borders");
                    return;
                }
                Vector3i sign = site.definition().resolvePreviewSignAnchorWorld(site.anchor(), site.yaw());
                PlotInstance plot = new PlotInstance(UUID.randomUUID(), site.constructionId(), PlotInstanceState.BLUEPRINTING,
                    site.footprint(), sign.x, sign.y, sign.z, System.currentTimeMillis());
                plot.captureProductionLayout(site.definition());
                plot.setPrefabWorldPlacement(site.anchor().x, site.anchor().y, site.anchor().z, site.yaw());
                town.addPlotInstance(plot);
                manager().updateTown(town);
                var started = PlotAssemblyService.startFromBuildClick(plugin, world, town, plot, sign, b.owner.uuid(), site.anchor(),
                    site.yaw(), site.definition(), site.buffer(), 0L, true);
                if (started != PlotAssemblyBuildStartResult.OK) {
                    town.removePlotInstance(plot.getPlotId());
                    manager().updateTown(town);
                    report.skip(label, "construction did not start: " + started);
                    return;
                }
                ApprenticeWorkplace yard = town.getApprenticeWorkplaces().sites().stream().filter(s -> s.existingBuilding() != null
                    && s.existingBuilding().list().stream().anyMatch(order -> order.plot().equals(plot.getPlotId()))).findFirst().orElse(null);
                report.add(TestTownReport.SITES);
                report.note("Building site: " + site.constructionId() + " is started and "
                    + (yard == null ? "waits for a Building yard with room." : "queued at " + yard.name() + "."));
            });
        }
        manager().updateTown(town);
    }

    // ---------------------------------------------------------------------------------------------- stock and orders

    void stockAndOrders() {
        TownRecord town = town();
        int kinds = 0;
        for (TestTownSpec.StackSpec stack : b.spec.stock()) {
            if (deposit(stack.item(), stack.count(), "stock " + stack.item())) kinds++;
        }
        report.add(TestTownReport.STOCK, kinds);
        for (TestTownSpec.WorkplaceSpec spec : b.spec.workplaces()) {
            TownsfolkWorkType type = TownsfolkWorkType.byId(spec.type());
            ApprenticeWorkplace site = type == null ? null : sites.get(type);
            if (site == null || WorkplaceBenchAccess.screen(type) != WorkplaceBenchAccess.Screen.ORDERS) continue;
            part("orders for " + type.id, () -> orders(spec, site));
        }
        manager().updateTown(town);
    }

    private void orders(TestTownSpec.WorkplaceSpec spec, ApprenticeWorkplace site) {
        TownRecord town = town();
        var equipment = WorkplaceEquipment.scan(world, site.area());
        int skill = workerSkill.getOrDefault(site.type(), 1);
        List<WorkplaceRecipe> usable = plugin.getApprenticeRecipes().all().stream()
            .filter(r -> r.type() == site.type() && r.skillLevel() <= skill && r.unlocked(town)
                && r.nativeUnlockIssue(town, world) == null && r.equipmentIssue(site, equipment) == null && hasInputs(r))
            .sorted(Comparator.comparing((WorkplaceRecipe r) -> r.id().startsWith("native/")).thenComparingInt(WorkplaceRecipe::skillLevel)
                .thenComparing(WorkplaceRecipe::id)).toList();
        List<String> taken = new ArrayList<>();
        for (TestTownSpec.OrderSpec order : spec.orders()) {
            String label = "order " + order.recipe() + " at " + site.type().id;
            WorkplaceRecipe recipe = "auto".equalsIgnoreCase(order.recipe())
                ? usable.stream().filter(r -> !taken.contains(r.id())).findFirst().orElse(null)
                : usable.stream().filter(r -> r.id().equals(order.recipe())).findFirst().orElse(null);
            if (recipe == null && !"auto".equalsIgnoreCase(order.recipe())) {
                WorkplaceRecipe named = plugin.getApprenticeRecipes().find(order.recipe());
                String why = named == null ? "no recipe has that id" : named.type() != site.type() ? "it belongs to another workplace type"
                    : named.skillLevel() > skill ? "it needs skill " + named.skillLevel() : !named.unlocked(town) ? named.unlockIssue(town)
                    : named.nativeUnlockIssue(town, world) != null ? named.nativeUnlockIssue(town, world)
                    : named.equipmentIssue(site, equipment) != null ? named.equipmentIssue(site, equipment) : "it has no source recipe in this game";
                // Fall back to a recipe this bench can make, so the workplace still has work.
                recipe = usable.stream().filter(r -> !taken.contains(r.id())).findFirst().orElse(null);
                report.note("Order " + order.recipe() + " at " + site.type().id + " was replaced by "
                    + (recipe == null ? "nothing" : recipe.id()) + ": " + why + ".");
            }
            if (recipe == null) { report.skip(label, "no recipe of this type can be made at the bench with skill " + skill); continue; }
            int amount = Math.clamp(order.amount(), 1, 100);
            String refused = site.orders().add(recipe.id(), WorkplaceOrders.Mode.COUNT, amount);
            if (refused != null) { report.skip(label, refused); continue; }
            taken.add(recipe.id());
            report.add(TestTownReport.ORDERS);
            // Named items are stocked for the whole order. "Any wood" and the like are covered by the general stock.
            for (var input : recipe.source().getInput())
                if (input != null && input.getItemId() != null)
                    deposit(input.getItemId(), input.getQuantity() * amount, "ingredient " + input.getItemId() + " for " + recipe.id());
        }
    }

    private static boolean hasInputs(WorkplaceRecipe recipe) {
        var source = recipe.source();
        return source != null && source.getInput() != null && source.getInput().length > 0;
    }

    /** Puts items in the town's general stock through the same deposit the warehouse screen makes. */
    private boolean deposit(String item, int count, String label) {
        TownRecord town = town();
        Item asset = item == null ? null : Item.getAssetMap().getAsset(item);
        if (asset == null) { report.skip(label, "this game has no item with that id"); return false; }
        TownWarehouse warehouse = town.getWarehouse();
        int left = Math.max(1, count), stackSize = Math.max(1, asset.getMaxStack());
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(left, stackSize));
            SimpleItemContainer hand = new SimpleItemContainer((short) 1);
            hand.setItemStackForSlot((short) 0, stack, false);
            int moved = warehouse.deposit(TownWarehouse.Page.GENERAL, hand, (short) 0, stack, TownWarehouse.Amount.STACK).moved();
            if (moved <= 0) {
                // Full: buy one more row of shelves from the treasury, as the warehouse screen does, and try again.
                if (TownPopulation.levelFor(TownPopulation.count(town)) < warehouse.upgradeTownLevel()
                    || !payFromTreasury(warehouse.upgradeCost()) || !warehouse.upgrade(warehouse.upgrades())) {
                    report.skip(label, "the warehouse is full (" + (count - left) + " of " + count + " stored)");
                    return left < count;
                }
                continue;
            }
            left -= moved;
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------- paths

    /** A street along the front of every row and a lane down each side of the grid, laid with the Path Designer's own service. */
    void paths() {
        TownRecord town = town();
        AetherhavenPluginConfig config = plugin.getConfig().get();
        PathToolRegistry registry = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
        int width = Math.max(2, Math.min(4, b.spec.grid().street() - 2)), built = 0;
        int laneWest = b.grid.minX() - b.spec.grid().street() / 2 - 1, laneEast = b.grid.maxX() + b.spec.grid().street() / 2 + 1;
        List<int[]> segments = new ArrayList<>();
        for (TestTownGrid.Street street : b.grid.streets()) segments.add(new int[] {laneWest, street.centerZ(), laneEast, street.centerZ()});
        int north = b.grid.streets().stream().mapToInt(TestTownGrid.Street::centerZ).min().orElse(0);
        int south = b.grid.streets().stream().mapToInt(TestTownGrid.Street::centerZ).max().orElse(0);
        if (south > north) {
            segments.add(new int[] {laneWest, north, laneWest, south});
            segments.add(new int[] {laneEast, north, laneEast, south});
        }
        for (int[] s : segments) {
            Vector3d direction = new Vector3d(s[2] - s[0], 0.0, s[3] - s[1]);
            double yaw = PathSplineUtil.yawDegFromLookDirection(direction);
            List<PathToolNode> nodes = List.of(
                new PathToolNode(UUID.randomUUID(), new Vector3d(s[0] + 0.5, groundY(), s[1] + 0.5), yaw),
                new PathToolNode(UUID.randomUUID(), new Vector3d(s[2] + 0.5, groundY(), s[3] + 0.5), yaw));
            List<PathSplineUtil.PathSample> samples = PathSplineUtil.sample(nodes, 2);
            List<PathPlannedCell.Planned> cells = PathPlannedCell.build(world, samples, width, config.getPathToolRayStartAboveY(),
                config.getPathToolMaxRayDown());
            cells = cells.stream().filter(cell -> town.getCharterX() != cell.pos.x || town.getCharterZ() != cell.pos.z).toList();
            PathCommitRecord record = PathCementService.tryCement(world, config, cells, 0, width, random);
            if (record == null) { report.skip("street at " + s[0] + ", " + s[1], "the path service laid nothing there"); continue; }
            record.townId = town.getTownId().toString();
            record.navNodes = PathNavPolylineUtil.resampleCenterline(samples, config.getPathNavNodeSpacing());
            if (record.pathWidthBlocks <= 0) record.pathWidthBlocks = width;
            registry.addRecord(record);
            built++;
        }
        if (built > 0) {
            AetherhavenWorldRegistries.getOrCreatePathNavGraphService(world).rebuildAll(registry, config);
            PathToolPersistence.save(world, plugin, registry);
        }
        report.add(TestTownReport.PATHS, built);
    }

    /**
     * A mineshaft plan only works while its author can manage the town. A town that was built without an owner has a
     * stand-in as the author, so the plan is started again in the new owner's name while nothing has been dug yet.
     */
    static void adoptOwner(World world, TownRecord town, UUID owner) {
        for (ApprenticeWorkplace site : town.getApprenticeWorkplaces().sites()) {
            var plan = site.existingExcavation();
            if (site.type() != TownsfolkWorkType.MINING || plan == null || plan.id() == null || owner.equals(plan.author())
                || plan.completed() > 0 || plan.held()) continue;
            var review = NativeExcavation.review(world, town, site, plan.entrance(), plan.layout());
            if (!review.ready()) continue;
            String refused = plan.start(plan.revision(), owner, plan.mine(), site.area(), plan.entrance(), plan.layout(), review.cells(),
                plan.fittingsMode());
            if (refused != null) LOG.atWarning().log("Test town mineshaft could not be handed to its new owner: %s", refused);
        }
        // The same goes for building projects: an order only runs while its author may manage buildings.
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() == PlotInstanceState.COMPLETE) continue;
            plot.setAssemblyOwnerUuid(owner);
            for (ApprenticeWorkplace site : town.getApprenticeWorkplaces().sites()) {
                var orders = site.existingBuilding();
                if (orders == null) continue;
                for (var order : List.copyOf(orders.list()))
                    if (order.plot().equals(plot.getPlotId()) && !owner.equals(order.author())) orders.remove(order.id());
            }
            plot.setBuilderYardOffered(false);
            BuildingProjectAutoQueue.offer(town, plot, owner);
        }
    }
}
