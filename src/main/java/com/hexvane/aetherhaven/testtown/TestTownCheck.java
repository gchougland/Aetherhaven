package com.hexvane.aetherhaven.testtown;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.plot.PlotBlockStamper;
import com.hexvane.aetherhaven.questboard.TownQuestBoardRank;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownAdmission;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPopulation;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownSaveCoordinator;
import com.hexvane.aetherhaven.town.TownWorldFile;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.warehouse.WarehouseDeskBlock;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.FoodSiteService;
import com.hexvane.aetherhaven.workplace.GrowerPlan;
import com.hexvane.aetherhaven.workplace.GrowerSiteService;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import com.hexvane.aetherhaven.workplace.WorkplaceEquipment;
import com.hexvane.aetherhaven.workplace.WorkplaceFood;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reads a built test town back: first from the town's records, then from the world itself (each plot's linked blocks,
 * benches standing in their workplaces, villagers alive and bound) and from the file the town was saved to. It changes
 * nothing in the town.
 */
public final class TestTownCheck {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** How long villagers are given to come back into the world after their chunks load. */
    private static final int ENTITY_WAIT_ATTEMPTS = 40;

    private TestTownCheck() {}

    /**
     * What the town's records say it holds.
     *
     * @param buildings complete plots
     * @param sites     plots still being built, which the Building yard works on
     * @param townsfolk townsfolk made citizens of the town, guards among them
     * @param assigned  townsfolk with a workplace job switched on
     */
    public record Census(int buildings, int sites, int villagers, List<String> missingVillagers, int workplaces,
                         Set<TownsfolkWorkType> workplaceTypes, int townsfolk, int assigned, int couriers, int guards,
                         int clerks, int production, int orders, int stock, int population, int townLevel, String rank) {
        /** Replaces the report's counts with what the town really holds. */
        public void copyInto(TestTownReport report) {
            report.set(TestTownReport.BUILDINGS, buildings);
            report.set(TestTownReport.SITES, sites);
            report.set(TestTownReport.VILLAGERS, villagers);
            report.set(TestTownReport.WORKPLACES, workplaces);
            report.set(TestTownReport.TOWNSFOLK, townsfolk);
            report.set(TestTownReport.ASSIGNED, assigned);
            report.set(TestTownReport.COURIERS, couriers);
            report.set(TestTownReport.GUARDS, guards);
            report.set(TestTownReport.CLERKS, clerks);
            report.set(TestTownReport.PRODUCTION, production);
            report.set(TestTownReport.ORDERS, orders);
            report.set(TestTownReport.STOCK, stock);
        }
    }

    /** Core villagers that have a building in this town, by role id. */
    static List<VillagerDefinition> expectedVillagers(AetherhavenPlugin plugin, TownRecord town) {
        List<VillagerDefinition> expected = new ArrayList<>();
        for (VillagerDefinition definition : plugin.getVillagerDefinitionCatalog().allByNpcRoleId().values()) {
            String work = definition.getWorkConstructionId();
            if (work == null || work.isBlank()) continue;
            if (town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), work.trim()) != null) expected.add(definition);
        }
        return expected;
    }

    static ResidentNpcRecord resident(TownRecord town, String roleId) {
        for (ResidentNpcRecord row : town.getResidentNpcRecords())
            if (roleId.equals(row.getNpcRoleId()) && !TownVillagerBinding.isVisitorKind(row.getKind())) return row;
        return null;
    }

    public static Census census(AetherhavenPlugin plugin, TownRecord town) {
        int buildings = 0, sites = 0, clerks = 0, production = 0;
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE) { sites++; continue; }
            buildings++;
            if (plot.getShopClerkId() != null && !plot.getShopClerkId().isBlank()) clerks++;
            // Peek only: asking a plot for its herd or garden would create one.
            var state = town.peekPlotProduction(plot.getPlotId());
            if (state != null && (state.usesPhysicalField() || state.usesPhysicalTimber() || state.usesPhysicalHerd()
                || state.usesSpiritGarden()) || mine(plot)) production++;
        }
        List<String> missing = new ArrayList<>();
        int villagers = 0;
        for (VillagerDefinition definition : expectedVillagers(plugin, town)) {
            if (resident(town, definition.getNpcRoleId()) != null) villagers++;
            else missing.add(definition.getNpcRoleId());
        }
        Set<TownsfolkWorkType> types = EnumSet.noneOf(TownsfolkWorkType.class);
        int workplaces = 0, orders = 0;
        for (ApprenticeWorkplace site : town.getApprenticeWorkplaces().sites()) {
            if (site.mineBaseId() != null || site.type() == null) continue;
            workplaces++;
            types.add(site.type());
            orders += site.orders().list().size();
            if (site.type() == TownsfolkWorkType.MINING && site.existingExcavation() != null
                && site.existingExcavation().id() != null) orders++;
            if (site.existingBuilding() != null) orders += site.existingBuilding().list().size();
        }
        int townsfolk = 0, assigned = 0, couriers = 0;
        for (var record : town.getTouristRecords()) {
            if (!record.isCitizen()) continue;
            townsfolk++;
            var progress = town.getTownsfolkProgress().get(record.getCharacterId().toLowerCase(Locale.ROOT));
            if (progress == null || !progress.isEmployed()) continue;
            if (progress.priority(TownsfolkWorkType.LOGISTICS) > 0) couriers++;
            for (TownsfolkWorkType type : TownsfolkWorkType.values())
                if (type.requiresWorkplace() && progress.priority(type) > 0) { assigned++; break; }
        }
        int population = TownPopulation.count(town);
        int stock = (int) town.getWarehouse().craftingStock().slots().stream()
            .filter(slot -> !ItemStack.isEmpty(slot.stack())).map(slot -> slot.stack().getItemId()).distinct().count();
        return new Census(buildings, sites, villagers, List.copyOf(missing), workplaces, types, townsfolk, assigned,
            couriers, town.getHiredGuardRecords().size(), clerks, production, orders, stock, population,
            TownPopulation.levelFor(population),
            TownQuestBoardRank.tierIdForXp(town.getQuestBoardRankXp(), plugin.getQuestBoardCatalog()));
    }

    private static boolean mine(PlotInstance plot) {
        var layout = plot.getProductionLayout();
        return layout != null && !layout.legacy() && layout.nodes() != null && !layout.nodes().isEmpty();
    }

    /** Streets and lanes the town's path records hold. World thread only. */
    static int paths(World world, AetherhavenPlugin plugin, TownRecord town) {
        String id = town.getTownId().toString();
        return (int) AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin).all().stream()
            .filter(record -> id.equals(record.townId)).count();
    }

    /** The result of the full check: one line to print, and whether it passed. */
    public record Result(boolean passed, String line, List<String> problems) {}

    /**
     * Loads the town's chunks, waits for its villagers to come back into the world, and checks every building,
     * villager and workplace. Completes with a FAIL result rather than an exception when something is wrong.
     */
    public static CompletableFuture<Result> run(AetherhavenPlugin plugin, TestTownSpec spec) {
        World world = TestTownWorlds.loaded(spec.world());
        if (world == null) {
            return CompletableFuture.completedFuture(new Result(false, "Test town check: FAIL. World " + spec.world().name()
                + " is not loaded. Build the town first.", List.of("no world")));
        }
        return CompletableFuture.supplyAsync(() -> {
            TownManager manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            List<TownRecord> towns = manager.allTowns();
            if (towns.isEmpty()) return CompletableFuture.completedFuture((TownRecord) null);
            TownRecord town = towns.getFirst();
            world.getWorldConfig().setCanUnloadChunks(false);
            // The saved file is compared with the live town below, so ask for it to be written now.
            TownSaveCoordinator.requestImmediateSave(manager);
            int[] bounds = bounds(town);
            int ground = town.getCharterY();
            return TestTownWorlds.loadBox(world, bounds[0] - 16, ground - TestTownBuilder.DEPTH_BELOW, bounds[1] - 16,
                bounds[2] + 16, ground + TestTownBuilder.HEIGHT_ABOVE, bounds[3] + 16).thenApply(ignored -> town);
        }, world).thenCompose(loading -> loading).thenCompose(town -> {
            if (town == null) return CompletableFuture.completedFuture(new Result(false,
                "Test town check: FAIL. World " + spec.world().name() + " has no town.", List.of("no town")));
            return awaitVillagers(world, plugin, town, 0)
                .thenCompose(ignored -> CompletableFuture.supplyAsync(() -> saved(plugin, world, town.getTownId()),
                    CompletableFuture.delayedExecutor(750, TimeUnit.MILLISECONDS)))
                .thenApplyAsync(saved -> inspect(world, plugin, spec, town, saved), world);
        }).orTimeout(240, TimeUnit.SECONDS).handle((result, failure) -> {
            world.getWorldConfig().setCanUnloadChunks(true);
            if (failure == null) return result;
            LOG.atWarning().withCause(failure).log("Test town check stopped early");
            return new Result(false, "Test town check: FAIL. The check itself stopped: " + failure, List.of(String.valueOf(failure)));
        });
    }

    /** The block bounds of everything the town owns: plots, workplaces and the charter. */
    private static int[] bounds(TownRecord town) {
        int[] b = {town.getCharterX(), town.getCharterZ(), town.getCharterX(), town.getCharterZ()};
        for (PlotInstance plot : town.getPlotInstances()) {
            var f = plot.toFootprint();
            b[0] = Math.min(b[0], f.getMinX()); b[1] = Math.min(b[1], f.getMinZ());
            b[2] = Math.max(b[2], f.getMaxX()); b[3] = Math.max(b[3], f.getMaxZ());
        }
        for (ApprenticeWorkplace site : town.getApprenticeWorkplaces().sites()) {
            var a = site.area();
            if (a == null) continue;
            b[0] = Math.min(b[0], a.minX()); b[1] = Math.min(b[1], a.minZ());
            b[2] = Math.max(b[2], a.maxX()); b[3] = Math.max(b[3], a.maxZ());
        }
        return b;
    }

    /** Villagers are saved with their chunks and return a moment after the chunks do. */
    private static CompletableFuture<Void> awaitVillagers(World world, AetherhavenPlugin plugin, TownRecord town, int attempt) {
        return CompletableFuture.supplyAsync(() -> absentEntities(world, plugin, town).isEmpty(), world).thenCompose(all -> {
            if (all || attempt >= ENTITY_WAIT_ATTEMPTS) return CompletableFuture.completedFuture(null);
            return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS))
                .thenCompose(ignored -> awaitVillagers(world, plugin, town, attempt + 1));
        });
    }

    /** Residents whose entity is not in the world, is dead, or is there without a binding to this town and building. */
    private static List<String> absentEntities(World world, AetherhavenPlugin plugin, TownRecord town) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<String> absent = new ArrayList<>();
        for (VillagerDefinition definition : expectedVillagers(plugin, town)) {
            ResidentNpcRecord row = resident(town, definition.getNpcRoleId());
            if (row == null) continue;
            // The Elder is the town's own villager and is not tied to one building.
            boolean anyPlot = AetherhavenConstants.ELDER_NPC_ROLE_ID.equals(definition.getNpcRoleId());
            String problem = bindingProblem(store, town, row.getLastEntityUuid(), row.getJobPlotId(), anyPlot);
            if (problem != null) absent.add("villager " + definition.getNpcRoleId() + " " + problem);
        }
        for (var record : town.getTouristRecords()) {
            if (!record.isCitizen()) continue;
            String problem = bindingProblem(store, town, record.getEntityUuid(), null, true);
            if (problem != null) absent.add("townsfolk " + record.getCharacterId() + " " + problem);
        }
        for (var guard : town.getHiredGuardRecords()) {
            String problem = bindingProblem(store, town, guard.getEntityUuid(), null, true);
            if (problem != null) absent.add("guard " + guard.getCharacterId() + " " + problem);
        }
        return absent;
    }

    private static String bindingProblem(Store<EntityStore> store, TownRecord town, UUID entity, UUID jobPlot, boolean anyPlot) {
        if (entity == null) return "has no entity on record";
        var ref = store.getExternalData().getRefFromUUID(entity);
        if (ref == null || !ref.isValid()) return "is not in the world";
        var binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (binding == null || !town.getTownId().equals(binding.getTownId())) return "is not bound to this town";
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) return "is dead";
        if (!anyPlot && (jobPlot == null || !jobPlot.equals(binding.getJobPlotId()))) return "is not bound to their building";
        return null;
    }

    private static Result inspect(World world, AetherhavenPlugin plugin, TestTownSpec spec, TownRecord town, Saved disk) {
        List<String> problems = new ArrayList<>();
        Census census = census(plugin, town);

        // Buildings: every building the layout names and this game has must be a complete plot.
        int wanted = 0;
        for (TestTownSpec.BuildingSpec building : spec.buildings()) {
            var definition = plugin.getConstructionCatalog().get(building.id());
            if (definition == null || definition.isWallSegment() || definition.isDecorationPlot()) continue;
            wanted += building.copies();
            long have = town.getPlotInstances().stream().filter(p -> p.getState() == PlotInstanceState.COMPLETE
                && building.id().equals(p.getConstructionId())).count();
            if (have < building.copies()) problems.add("building " + building.id() + ": " + have + " of " + building.copies() + " complete");
        }
        int allowedSites = spec.buildingSites().stream().mapToInt(TestTownSpec.BuildingSpec::copies).sum();
        if (census.sites() > allowedSites) problems.add(census.sites() + " plots are unfinished, " + allowedSites + " were meant to be");

        // The charter stands where the town was founded and is linked to it.
        String charter = charterProblem(world, town);
        if (charter != null) problems.add(charter);

        // Linked blocks: the mod's own inspection of each plot's management block, treasury, shop safe, Gaia statue
        // and production storage. It reports what a repair would have to relink, and repairs nothing here.
        int linked = 0, linkable = 0;
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE) continue;
            ConstructionDefinition definition = plugin.getConstructionCatalog().get(plot.getConstructionId());
            if (definition == null || !plot.hasStoredPrefabWorldAnchor()) { problems.add("plot " + plot.getConstructionId() + " has no placement on record"); continue; }
            linkable++;
            var links = PlotBlockStamper.inspectPlotLinks(world, town, plot, definition, plot.getStoredPrefabWorldAnchor(), plot.resolvePrefabYaw());
            if (links.getRelinked() == 0 && links.getFailed() == 0 && links.getSkippedChunkUnloaded() == 0) linked++;
            else problems.add("plot " + plot.getConstructionId() + ": linked blocks need repair (" + links.getRelinked() + " unlinked "
                + links.getDetails() + ", " + links.getFailed() + " missing, " + links.getSkippedChunkUnloaded() + " not loaded)");
        }

        // Villagers: a resident record, a living entity, bound to this town and to their building.
        for (String missing : census.missingVillagers()) problems.add("villager " + missing + " has no resident record");
        List<String> absent = absentEntities(world, plugin, town);
        problems.addAll(absent);
        long absentVillagers = absent.stream().filter(line -> line.startsWith("villager ")).count();
        long absentTownsfolk = absent.stream().filter(line -> line.startsWith("townsfolk ")).count();

        // Production: fields adopted, animals owned, Rootstones and Spirit Planters bound.
        production(plugin, town, problems);

        // Workplaces: one of every type that takes new work, its bench standing inside it, and someone to work it.
        int ready = 0;
        for (TownsfolkWorkType type : TownsfolkWorkType.values()) {
            if (!type.requiresWorkplace() || !type.availableForNewWork()) continue;
            ApprenticeWorkplace site = town.getApprenticeWorkplaces().sites().stream()
                .filter(s -> s.type() == type && s.mineBaseId() == null).findFirst().orElse(null);
            if (site == null) { problems.add("workplace " + type.id + " is missing"); continue; }
            String bench = benchProblem(world, site);
            String worker = workerProblem(town, type);
            if (bench != null) problems.add("workplace " + type.id + ": " + bench);
            if (worker != null) problems.add("workplace " + type.id + ": " + worker);
            if (bench == null && worker == null) ready++;
        }
        if (census.couriers() < 1) problems.add("no townsfolk has Logistics as work");
        if (spec.staff().guards() > 0 && census.guards() < 1) problems.add("no guard is on duty");
        if (spec.staff().clerks() > 0 && census.clerks() < 1) problems.add("the shop has no clerk");
        int desks = WarehouseDeskBlock.loaded(world, AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin), town).size();
        if (desks < 1) problems.add("no warehouse ledger desk stands in the town");
        if (census.stock() < 1) problems.add("the warehouse is empty");

        // Standing orders: a mineshaft plan or a building project only runs while its author may manage the town.
        if (town.hasOwner()) {
            for (ApprenticeWorkplace site : town.getApprenticeWorkplaces().sites()) {
                var plan = site.existingExcavation();
                if (plan != null && plan.id() != null && !mayManage(town, plan.author()))
                    problems.add("mineshaft plan at " + site.name() + " belongs to someone who cannot manage the town");
                if (site.existingBuilding() != null)
                    for (var order : site.existingBuilding().list())
                        if (!mayManage(town, order.author())) problems.add("building project at " + site.name() + " belongs to someone who cannot manage the town");
            }
        }

        // Saved records: the file on disk must hold the same town.
        String saved = disk.problem();
        if (saved == null && (disk.census().buildings() != census.buildings() || disk.census().villagers() != census.villagers()
            || disk.census().workplaces() != census.workplaces() || disk.census().townsfolk() != census.townsfolk()
            || disk.census().orders() != census.orders()))
            saved = "saved records differ: " + disk.census().buildings() + " buildings, " + disk.census().villagers() + " villagers, "
                + disk.census().workplaces() + " workplaces, " + disk.census().townsfolk() + " townsfolk, " + disk.census().orders()
                + " orders on disk";
        if (saved != null) problems.add(saved);

        boolean passed = problems.isEmpty();
        String line = "Test town check: " + (passed ? "PASS" : "FAIL") + ". Buildings complete " + census.buildings() + " of " + wanted
            + ", charter " + (charter == null ? "linked" : "NOT linked") + ", plots with their linked blocks intact " + linked + " of "
            + linkable + ", core villagers alive and bound to their building " + (census.villagers() - absentVillagers) + " of "
            + (census.villagers() + census.missingVillagers().size()) + ", townsfolk alive and bound "
            + (census.townsfolk() - absentTownsfolk) + " of " + census.townsfolk() + ", workplace types with bench and worker "
            + ready + " of " + workplaceTypes() + ", production buildings set up " + census.production() + ", couriers "
            + census.couriers() + ", guards " + census.guards() + ", shop clerks " + census.clerks() + ", ledger desks " + desks
            + ", kinds of stock " + census.stock() + ", orders " + census.orders() + ", building sites " + census.sites()
            + ", Town Level " + census.townLevel() + ", owner "
            + (town.hasOwner() ? (town.getOwnerUsername() == null ? town.getOwnerUuid().toString() : town.getOwnerUsername()) : "none yet")
            + ", saved records " + (saved == null ? "match" : "differ") + ", problems " + problems.size() + ".";
        return new Result(passed, line, List.copyOf(problems));
    }

    private static String charterProblem(World world, TownRecord town) {
        int x = town.getCharterX(), y = town.getCharterY(), z = town.getCharterZ();
        var block = ChunkSectionBlockUtil.blockType(world, x, y, z);
        if (block == null || !AetherhavenConstants.CHARTER_BLOCK_TYPE_ID.equals(block.getId()))
            return "no charter stands at " + x + ", " + y + ", " + z;
        var ref = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        var link = ref == null || !ref.isValid() ? null : ref.getStore().getComponent(ref, CharterBlock.getComponentType());
        return link != null && town.getTownId().toString().equals(link.getTownId()) ? null : "the charter is not linked to the town";
    }

    private static boolean mayManage(TownRecord town, UUID player) {
        return player != null && town.hasMemberOrOwner(player) && town.playerCanManageConstructions(player);
    }

    static int workplaceTypes() {
        int count = 0;
        for (TownsfolkWorkType type : TownsfolkWorkType.values()) if (type.requiresWorkplace() && type.availableForNewWork()) count++;
        return count;
    }

    private static void production(AetherhavenPlugin plugin, TownRecord town, List<String> problems) {
        var catalog = plugin.getConstructionCatalog();
        record Need(String construction, String what, java.util.function.Predicate<com.hexvane.aetherhaven.production.PlotProductionState> met) {}
        for (Need need : List.of(
            new Need(AetherhavenConstants.CONSTRUCTION_PLOT_FARM, "farm fields are not adopted", s -> s.usesFarmFields() && s.farm().plannedCount() > 0),
            new Need(AetherhavenConstants.CONSTRUCTION_PLOT_BARN, "the barn owns no animals", s -> s.usesPhysicalHerd() && !s.herd().animals().isEmpty()),
            new Need(AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL, "no Rootstone is bound", s -> s.usesPhysicalTimber()),
            new Need("plot_druid", "no Spirit Planter is bound", s -> s.usesSpiritGarden() && !s.garden().planters().isEmpty()))) {
            PlotInstance plot = town.findCompletePlotWithConstruction(catalog, need.construction());
            if (plot == null) continue;
            var state = town.peekPlotProduction(plot.getPlotId());
            if (state == null || !need.met().test(state)) problems.add(need.what());
        }
        PlotInstance mine = town.findCompletePlotWithConstruction(catalog, AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT);
        if (mine != null && !mine(mine)) problems.add("the mine has no nodes");
    }

    /** Why the workplace cannot be worked as it stands in the world, or null when its bench (or beds, or shaft) is there. */
    static String benchProblem(World world, ApprenticeWorkplace site) {
        TownsfolkWorkType type = site.type();
        if (type == TownsfolkWorkType.MINING)
            return site.existingExcavation() != null && site.existingExcavation().id() != null ? null : "no shaft is planned";
        if (WorkplaceFood.supports(type)) {
            var habitat = FoodSiteService.inspect(world, site);
            return habitat.simulated() && habitat.issue() == null ? null : String.valueOf(habitat.issue());
        }
        if (GrowerPlan.supports(type)) {
            var habitat = GrowerSiteService.inspect(world, site);
            return habitat.ready() ? null : "grower site reports '" + habitat.issue() + "'";
        }
        var equipment = WorkplaceEquipment.scan(world, site.area());
        if (!equipment.fullySimulated()) return "its chunks are not loaded";
        for (var bench : equipment.benches()) {
            var block = ChunkSectionBlockUtil.blockType(world, bench.x(), bench.y(), bench.z());
            if (WorkplaceBenchAccess.matches(type, bench.id(), block == null ? null : block.getId())) return null;
        }
        return "its bench was not found inside the area";
    }

    /** Why nobody would work this type, or null when a housed, employed townsfolk has it as their first priority. */
    static String workerProblem(TownRecord town, TownsfolkWorkType type) {
        for (var record : town.getTouristRecords()) {
            if (!record.isCitizen() || !TownAdmission.isMember(town, record.getEntityUuid())) continue;
            var progress = town.getTownsfolkProgress().get(record.getCharacterId().toLowerCase(Locale.ROOT));
            if (progress != null && progress.isEmployed() && progress.priority(type) == 1 && progress.skill(type.skill) >= 1
                && town.getPlotInstances().stream().anyMatch(p -> p.hasHomeResident(record.getEntityUuid()))) return null;
        }
        return "no housed, employed townsfolk has it as first priority";
    }

    /** What the town's saved file holds, or why it could not be read. */
    private record Saved(Census census, String problem) {}

    /** Reads the saved file. Runs off the world thread, after any save already on its way has been written. */
    private static Saved saved(AetherhavenPlugin plugin, World world, UUID townId) {
        Path file = TownManager.pluginData(plugin).resolve("worlds").resolve(world.getName()).resolve("towns.json");
        try {
            TownSaveCoordinator.awaitPendingWrites();
            TownRecord saved = TownWorldFile.readOrEmpty(file).getTowns().stream()
                .filter(t -> townId.equals(t.getTownId())).findFirst().orElse(null);
            if (saved == null) return new Saved(null, "the saved file " + file.getFileName() + " does not hold this town");
            return new Saved(census(plugin, saved), null);
        } catch (java.io.IOException | RuntimeException failure) {
            return new Saved(null, "the saved file could not be read: " + failure.getMessage());
        }
    }
}
