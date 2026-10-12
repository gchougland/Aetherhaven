package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.alchemy.AlchemyIngredients;
import com.hexvane.aetherhaven.alchemy.AlchemyTownStock;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class GrowerSiteTest {
    private final Gson gson = new Gson();
    private final GrowerCatalog catalog = GrowerCatalog.loadFromClasspath(getClass().getClassLoader());
    private final GrowerPlan mushrooms = catalog.find(TownsfolkWorkType.MYCOLOGY);
    private final GrowerPlan berries = catalog.find(TownsfolkWorkType.BERRIES);
    private final com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer inventory =
        new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1));

    private static WarehouseBatch.Point at(int x) { return new WarehouseBatch.Point(x, 100, 0); }
    private static List<WarehouseBatch.Point> points(int count) {
        var result = new ArrayList<WarehouseBatch.Point>();
        for (int i = 0; i < count; i++) result.add(at(i));
        return result;
    }
    /** A townsfolk standing at the bed for as long as the job takes. */
    private static boolean labour(GrowerPlan plan, GrowerSite site, GrowerSite.Bed bed, GrowerSite.Job job) {
        for (int tick = 0; tick < 200; tick++) if (site.work(plan, bed, job, .25, true, 1)) return true;
        return false;
    }
    private static boolean doJob(GrowerPlan plan, GrowerSite site, GrowerSite.Task task, boolean ripe) {
        return labour(plan, site, task.bed(), task.job()) && site.complete(plan, site.chosen(plan), task.bed(), task.job(), ripe, () -> true);
    }
    private GrowerSite reload(GrowerSite site) { return gson.fromJson(gson.toJson(site), GrowerSite.class); }
    private TownRecord townWith(String... kinds) {
        var town = new TownRecord(); town.setTownId(UUID.randomUUID()); town.setOwner(UUID.randomUUID(), "Owner");
        town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(0, 0));
        for (String kind : kinds) town.getResidentNpcRecords().add(new ResidentNpcRecord(kind, kind, null, UUID.randomUUID()));
        return town;
    }

    @Test void bothGrowerSitesAreOnePlanEachAndTheMycologistGrowsEveryMushroomTheTownNeeds() {
        assertNotNull(mushrooms, "Bundled mycology plan must load");
        assertNotNull(berries, "Bundled berry plan must load");
        assertEquals(2, catalog.all().size());
        assertTrue(mushrooms.logBeds()); assertFalse(berries.logBeds());
        assertNull(mushrooms.bench(), "Mushroom logs are their own handle");
        assertEquals("Farmingbench", berries.bench());
        assertNull(mushrooms.onlyCrop(), "The Mycologist asks which mushroom to grow");
        assertEquals("Plant_Fruit_Berries_Red", berries.onlyCrop().item());
        var grown = new HashSet<String>();
        for (var crop : mushrooms.crops()) grown.add(crop.item());
        // Every mushroom the cauldron brews with must be growable. Puffy Green is obtainable no other way.
        for (var ingredient : AlchemyIngredients.all())
            if (ingredient.item().startsWith("Plant_Crop_Mushroom_")) assertTrue(grown.contains(ingredient.item()), ingredient.item());
        for (String wanted : List.of("Common_Lime", "Boomshroom_Small", "Boomshroom_Large", "Cap_Red", "Glowing_Red", "Common_Blue",
            "Glowing_Blue", "Flatcap_Green", "Cap_Green", "Glowing_Green", "Common_Brown")) assertTrue(grown.contains("Plant_Crop_Mushroom_" + wanted), wanted);
        // The stronger the effect, the more Farming skill it takes.
        var skill = new HashMap<String, Integer>();
        for (var crop : mushrooms.crops()) skill.put(crop.item().substring("Plant_Crop_Mushroom_".length()), crop.skill());
        assertEquals(1, skill.get("Common_Brown"));
        assertTrue(skill.get("Cap_Red") < skill.get("Glowing_Red"));
        assertTrue(skill.get("Common_Blue") < skill.get("Glowing_Blue"));
        assertTrue(skill.get("Flatcap_Green") < skill.get("Cap_Green") && skill.get("Cap_Green") < skill.get("Glowing_Green"));
        assertTrue(skill.get("Boomshroom_Small") >= 5 && skill.get("Glowing_Blue") >= 6, "Boomshrooms and glowing mushrooms come late");
        for (var plan : catalog.all()) for (var crop : plan.crops()) assertTrue(crop.skill() >= 1 && crop.skill() <= 10);
    }

    @Test void everyGrowableMushroomHasItsOwnGrownLogAndEveryTextNamedExists() throws Exception {
        var item = JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Item/Items/Aetherhaven/Aetherhaven_Mushroom_Log.json"))).getAsJsonObject();
        assertEquals(List.of("Aetherhaven_Mushroom_Log"), mushrooms.bedBlocks());
        var block = item.getAsJsonObject("BlockType");
        var states = block.getAsJsonObject("State").getAsJsonObject("Definitions");
        assertTrue(states.has(GrowerPlan.SEEDED_STATE)); assertTrue(states.has(GrowerPlan.SPROUTING_STATE));
        var common = Path.of("src/main/resources/Common");
        for (var crop : mushrooms.crops()) {
            assertTrue(states.has(crop.grownState()), "No grown log for " + crop.id());
            var state = states.getAsJsonObject(crop.grownState());
            assertTrue(Files.isRegularFile(common.resolve(state.get("CustomModel").getAsString())));
            assertTrue(Files.isRegularFile(common.resolve(state.getAsJsonArray("CustomModelTexture").get(0).getAsJsonObject().get("Texture").getAsString())));
        }
        // The log is only a bed. It has no growth of its own, so nothing can spread from it or explode.
        assertFalse(block.has("Farming")); assertFalse(block.has("BlockEntity"));
        assertFalse(block.getAsJsonObject("Interactions").has("CollisionEnter"));
        assertEquals(GrowerSitePage.BED_PAGE, block.getAsJsonObject("Interactions").getAsJsonObject("Use").getAsJsonArray("Interactions")
            .get(0).getAsJsonObject().getAsJsonObject("Page").get("Id").getAsString());
        var lang = Files.readString(Path.of("src/main/resources/Server/Languages/en-US/aetherhaven_growers.lang"));
        assertTrue(lang.lines().filter(l -> l.startsWith("aetherhaven.growers.logHint")).findFirst().orElseThrow().contains("{key}"));
        for (var plan : catalog.all()) for (var crop : plan.crops())
            assertTrue(lang.contains("aetherhaven.growers.use." + crop.use() + " ="), "Missing use text " + crop.use());
        for (String type : List.of("mycology", "berries")) for (String key : List.of("title", "bedsTitle", "bedCount", "extraBeds", "noBeds", "about", "growing"))
            assertTrue(lang.contains("aetherhaven.growers." + type + "." + key + " ="), type + "." + key);
        assertFalse(lang.contains(" - ") || lang.contains("—") || lang.contains("–"), "No dashes used as pauses");
    }

    @Test void everyMushroomBerryAndBushIsARealItemWithAnIconInTheInstalledGame() throws Exception {
        String appData = System.getenv("APPDATA");
        Assumptions.assumeTrue(appData != null);
        var assets = Path.of(appData, "Hytale/install/pre-release/package/game/latest/Assets.zip");
        Assumptions.assumeTrue(Files.isRegularFile(assets));
        try (var zip = new java.util.zip.ZipFile(assets.toFile())) {
            var items = new HashMap<String, java.util.zip.ZipEntry>();
            zip.stream().filter(e -> e.getName().startsWith("Server/Item/Items/") && e.getName().endsWith(".json"))
                .forEach(e -> items.put(Path.of(e.getName()).getFileName().toString().replace(".json", ""), e));
            var ids = new LinkedHashSet<String>(berries.bedBlocks());
            for (var plan : catalog.all()) for (var crop : plan.crops()) ids.add(crop.item());
            ids.add("Bench_Farming"); ids.add("Bench_Builders");
            for (String id : ids) {
                assertNotNull(items.get(id), "Missing native item " + id);
                var data = JsonParser.parseString(new String(zip.getInputStream(items.get(id)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(data.has("Icon") || data.has("Parent"), "Missing icon " + id);
            }
            // Bushes must be the kind that regrow after a harvest, or picking one would destroy it.
            var template = JsonParser.parseString(new String(zip.getInputStream(items.get("Plant_Crop_Berry_Block")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("BlockType").getAsJsonObject("Farming");
            assertEquals("Harvested", template.get("StageSetAfterHarvest").getAsString());
        }
    }

    @Test void druidUnlocksBothGrowerSitesAndBuilderUnlocksTheBlockWorkshopForOneHundredGold() {
        var town = townWith(); var owner = town.getOwnerUuid(); var sites = town.getApprenticeWorkplaces();
        town.setTreasuryGoldCoinCount(1000);
        for (var type : List.of(TownsfolkWorkType.MYCOLOGY, TownsfolkWorkType.BERRIES)) {
            assertEquals("druid", WorkplaceRules.supervisor(type));
            assertEquals(TownsfolkSkill.FARMING, type.skill);
            assertTrue(type.requiresWorkplace() && type.availableForNewWork());
        }
        assertEquals("builder", WorkplaceRules.supervisor(TownsfolkWorkType.BLOCK_WORKSHOP));
        assertEquals(TownsfolkSkill.BUILDING, TownsfolkWorkType.BLOCK_WORKSHOP.skill);
        assertNotNull(sites.buyUnlock(town, owner, TownsfolkWorkType.MYCOLOGY, inventory), "No Druid, no Mycologist");
        assertNotNull(sites.buyUnlock(town, owner, TownsfolkWorkType.BLOCK_WORKSHOP, inventory), "No Builder, no Block Workshop");
        assertEquals(1000, town.getTreasuryGoldCoinCount());
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Rowan", "druid", null, UUID.randomUUID()));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Builder", "builder", null, UUID.randomUUID()));
        assertNotNull(sites.buyUnlock(town, UUID.randomUUID(), TownsfolkWorkType.MYCOLOGY, inventory), "Strangers cannot unlock");
        assertNull(sites.buyUnlock(town, owner, TownsfolkWorkType.MYCOLOGY, inventory));
        assertNull(sites.buyUnlock(town, owner, TownsfolkWorkType.BERRIES, inventory));
        assertNull(sites.buyUnlock(town, owner, TownsfolkWorkType.BLOCK_WORKSHOP, inventory));
        assertEquals(700, town.getTreasuryGoldCoinCount());
        assertNotNull(sites.buyUnlock(town, owner, TownsfolkWorkType.BERRIES, inventory), "Never charged twice");
        assertEquals(700, town.getTreasuryGoldCoinCount());
        assertFalse(sites.unlocked(TownsfolkWorkType.BUILDING), "The yard and the workshop are unlocked separately");
        // One Druid slot to start with, shared by both kinds of site, and more can be bought like anyone else's.
        assertNull(sites.save(town, null, TownsfolkWorkType.MYCOLOGY, new WorkplaceArea(0, 100, 0, 4, 4), "Log yard"));
        assertNotNull(sites.save(town, null, TownsfolkWorkType.BERRIES, new WorkplaceArea(8, 100, 0, 12, 4), "Berry patch"));
        for (int i = 0; i < 2; i++) town.getResidentNpcRecords().add(new ResidentNpcRecord("Core" + i, "core" + i, null, UUID.randomUUID()));
        assertNull(sites.buySlot(town, owner, "druid", 0, inventory));
        assertEquals(2, sites.slots("druid"));
        assertNull(sites.save(town, null, TownsfolkWorkType.BERRIES, new WorkplaceArea(8, 100, 0, 12, 4), "Berry patch"));
        // A new town's bought Druid slots survive the one-time garden cleanup when it is next loaded.
        var loaded = gson.fromJson(gson.toJson(town), TownRecord.class);
        loaded.migrateInnFieldsIfNeeded();
        assertEquals(2, loaded.getApprenticeWorkplaces().slots("druid"));
        assertEquals(2, loaded.getApprenticeWorkplaces().sites().size());
        assertTrue(loaded.getApprenticeWorkplaces().unlocked(TownsfolkWorkType.MYCOLOGY));
        var tree = BuildingUpgradeTree.workplaces("druid");
        assertEquals(Set.of("work:mycology", "work:berries"), new HashSet<>(tree.stream()
            .filter(n -> n.kind() == BuildingUpgradeTree.Kind.WORKPLACE).map(BuildingUpgradeTree.Node::id).toList()));
        assertEquals(Set.of("work:building", "work:blockWorkshop"), new HashSet<>(BuildingUpgradeTree.workplaces("builder").stream()
            .filter(n -> n.kind() == BuildingUpgradeTree.Kind.WORKPLACE).map(BuildingUpgradeTree.Node::id).toList()));
    }

    @Test void growerBenchesOpenTheGrowerScreen() {
        assertEquals(WorkplaceBenchAccess.Screen.GROWER, WorkplaceBenchAccess.screen(TownsfolkWorkType.MYCOLOGY));
        assertEquals(WorkplaceBenchAccess.Screen.GROWER, WorkplaceBenchAccess.screen(TownsfolkWorkType.BERRIES));
        assertTrue(WorkplaceBenchAccess.matches(TownsfolkWorkType.BERRIES, "Farmingbench", "Bench_Farming"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.BERRIES, "Farmingbench", "Bench_Trough"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.MYCOLOGY, "Farmingbench", "Bench_Farming"));
        assertTrue(GrowerPlan.supports(TownsfolkWorkType.MYCOLOGY) && GrowerPlan.supports(TownsfolkWorkType.BERRIES));
        assertFalse(GrowerPlan.supports(TownsfolkWorkType.FARMING) || GrowerPlan.supports(TownsfolkWorkType.CULTIVATION) || GrowerPlan.supports(null));
        assertFalse(WorkplaceFood.supports(TownsfolkWorkType.BERRIES), "A berry patch makes items, not food points");
    }

    @Test void aLogIsSeededTendedGrownAndPickedOnlyByLabourAndItsHarvestArrivesExactlyOnceAcrossSaves() {
        var site = new GrowerSite();
        assertTrue(site.sync(mushrooms, points(1), null));
        assertNull(site.next(mushrooms, site.chosen(mushrooms), bed -> true, null), "Nothing grows before a mushroom is chosen");
        assertEquals("skill", site.choose(mushrooms, "glowing_red", 3, site.revision()));
        assertNull(site.choose(mushrooms, "cap_red", 3, site.revision()));
        var crop = site.chosen(mushrooms);
        assertEquals("Plant_Crop_Mushroom_Cap_Red", crop.item());

        var seed = site.next(mushrooms, crop, bed -> true, null);
        assertEquals(GrowerSite.Job.SEED, seed.job());
        assertEquals("default", mushrooms.logState(crop, seed.bed().stage()));
        // Nobody at the bed: no labour, however long it is left.
        for (int i = 0; i < 1000; i++) assertFalse(site.work(mushrooms, seed.bed(), seed.job(), .25, false, 1));
        assertFalse(site.complete(mushrooms, crop, seed.bed(), seed.job(), false, () -> fail("Unworked jobs never reach the world")));
        assertFalse(site.work(mushrooms, seed.bed(), seed.job(), 3600, true, 1), "One long tick counts as a quarter second");
        assertTrue(labour(mushrooms, site, seed.bed(), seed.job()));
        assertFalse(site.complete(mushrooms, crop, seed.bed(), seed.job(), false, () -> false), "A failed block change changes nothing");
        assertEquals(0, seed.bed().stage());
        assertTrue(site.complete(mushrooms, crop, seed.bed(), seed.job(), false, () -> true));
        assertEquals(1, seed.bed().stage());
        assertEquals(GrowerPlan.SEEDED_STATE, mushrooms.logState(crop, 1));

        // Time alone never tends, ripens or picks. It only decides when the next visit is worth making.
        assertNull(site.next(mushrooms, crop, bed -> true, null), "A freshly seeded log rests first");
        site.advance(mushrooms, mushrooms.stageMinutes() - 1, true, true);
        assertNull(site.next(mushrooms, crop, bed -> true, null));
        site.advance(mushrooms, 100_000, false, true); site.advance(mushrooms, 100_000, true, false);
        assertNull(site.next(mushrooms, crop, bed -> true, null), "Resting pauses while nobody is online or the site is unloaded");
        site.advance(mushrooms, 1_000_000, true, true);
        assertEquals(1, site.beds().getFirst().stage(), "A year of waiting grows nothing by itself");
        assertEquals(0, site.stored());

        for (int tend = 1; tend <= mushrooms.tends(); tend++) {
            site = reload(site);
            var task = site.next(mushrooms, site.chosen(mushrooms), bed -> true, null);
            assertEquals(GrowerSite.Job.TEND, task.job());
            assertTrue(doJob(mushrooms, site, task, false));
            assertEquals(1 + tend, task.bed().stage());
            if (tend < mushrooms.tends()) {
                assertEquals(GrowerPlan.SPROUTING_STATE, mushrooms.logState(crop, task.bed().stage()));
                assertNull(site.next(mushrooms, site.chosen(mushrooms), bed -> true, null));
                site.advance(mushrooms, mushrooms.stageMinutes(), true, true);
            }
        }
        assertEquals("Grown_Cap_Red", mushrooms.logState(crop, site.beds().getFirst().stage()));
        assertEquals(0, site.stored(), "Ripe on the log is not yet in the store");

        site = reload(site);
        var pick = site.next(mushrooms, site.chosen(mushrooms), bed -> false, null);
        assertEquals(GrowerSite.Job.PICK, pick.job());
        assertTrue(doJob(mushrooms, site, pick, false));
        assertEquals(crop.yield(), site.amount(crop.item()));
        assertEquals(0, pick.bed().stage(), "A picked log is bare again and must be seeded");
        assertFalse(site.complete(mushrooms, site.chosen(mushrooms), pick.bed(), GrowerSite.Job.PICK, false, () -> fail("Picked once")));
        site = reload(site);
        assertEquals(crop.yield(), site.amount(crop.item()));
        assertEquals(1, site.picks());
        assertEquals(crop.yield(), site.remove(crop.item(), 99));
        assertEquals(0, site.remove(crop.item(), 99));
        assertEquals(0, reload(site).stored());
    }

    @Test void aFullStorePausesTheWholeSiteAndNothingIsLost() {
        var site = new GrowerSite();
        site.sync(mushrooms, points(mushrooms.maxBeds()), null);
        assertNull(site.choose(mushrooms, "common_brown", 10, site.revision()));
        var crop = site.chosen(mushrooms);
        int guard = 0;
        while (site.stored() + crop.yield() <= mushrooms.storeCapacity() && guard++ < 10_000) {
            var task = site.next(mushrooms, crop, bed -> true, null);
            if (task == null) { site.advance(mushrooms, mushrooms.stageMinutes(), true, true); continue; }
            assertTrue(doJob(mushrooms, site, task, false));
        }
        assertEquals(mushrooms.storeCapacity(), site.stored());
        assertTrue(site.full(mushrooms, crop));
        site.advance(mushrooms, 10_000, true, true);
        assertNull(site.next(mushrooms, crop, bed -> true, null), "No seeding, tending or picking while the store is full");
        var ripe = site.beds().stream().filter(bed -> bed.stage() > mushrooms.tends()).findFirst().orElse(null);
        if (ripe != null) {
            labour(mushrooms, site, ripe, GrowerSite.Job.PICK);
            assertFalse(site.complete(mushrooms, crop, ripe, GrowerSite.Job.PICK, false, () -> fail("A full store never touches the bed")));
        }
        assertEquals(mushrooms.storeCapacity(), site.stored(), "Nothing spills");
        assertEquals(crop.yield(), site.remove(crop.item(), crop.yield()));
        assertNotNull(site.next(mushrooms, crop, bed -> true, null), "Collecting lets work resume");
    }

    @Test void changingTheMushroomClearsWhatIsGrowingButKeepsWhatWasPicked() {
        var site = new GrowerSite();
        site.sync(mushrooms, points(3), null);
        assertNull(site.choose(mushrooms, "common_brown", 10, site.revision()));
        for (int i = 0; i < 40; i++) {
            var task = site.next(mushrooms, site.chosen(mushrooms), bed -> true, null);
            if (task == null) { site.advance(mushrooms, mushrooms.stageMinutes(), true, true); continue; }
            doJob(mushrooms, site, task, false);
            if (site.stored() > 0 && site.beds().stream().anyMatch(bed -> bed.stage() > 0)) break;
        }
        int stored = site.stored();
        assertTrue(stored > 0);
        assertTrue(site.beds().stream().anyMatch(bed -> bed.stage() > 0));
        assertEquals("same", site.choose(mushrooms, "common_brown", 10, site.revision()));
        assertEquals("changed", site.choose(mushrooms, "cap_white", 10, site.revision() - 1), "A stale screen cannot change the crop");
        assertEquals("unknown", site.choose(mushrooms, "tomato", 10, site.revision()));
        assertNull(site.choose(mushrooms, "cap_white", 10, site.revision()));
        site = reload(site);
        assertEquals("Plant_Crop_Mushroom_Cap_White", site.chosen(mushrooms).item());
        for (var bed : site.beds()) { assertEquals(0, bed.stage()); assertEquals(0, bed.minutes()); assertEquals(0, bed.work()); }
        assertEquals(stored, site.stored());
        assertEquals(stored, site.amount("Plant_Crop_Mushroom_Common_Brown"));
        assertEquals(GrowerSite.Job.SEED, site.next(mushrooms, site.chosen(mushrooms), bed -> true, null).job());
    }

    @Test void aSiteWorksALimitedNumberOfBedsAndKeepsTheOnesAlreadyGrowing() {
        var site = new GrowerSite();
        site.sync(mushrooms, points(mushrooms.maxBeds() + 3), null);
        assertEquals(mushrooms.maxBeds(), site.beds().size());
        assertNull(site.choose(mushrooms, "common_brown", 10, site.revision()));
        var last = site.bed(at(mushrooms.maxBeds() - 1));
        assertTrue(doJob(mushrooms, site, new GrowerSite.Task(last, GrowerSite.Job.SEED), false));
        // The first log is broken. A spare one takes its place and the growing log is not disturbed.
        var found = points(mushrooms.maxBeds() + 3); found.removeFirst();
        assertTrue(site.sync(mushrooms, found, null));
        assertEquals(mushrooms.maxBeds(), site.beds().size());
        assertNull(site.bed(at(0)));
        assertSame(last, site.bed(at(mushrooms.maxBeds() - 1)));
        assertEquals(1, last.stage());
        assertNotNull(site.bed(at(mushrooms.maxBeds())));
        assertFalse(site.sync(mushrooms, found, null), "Nothing changes when the world has not");
        assertEquals(at(1), site.pickup(), "Couriers collect beside the first bed");
        assertTrue(site.sync(mushrooms, List.of(), null));
        assertTrue(site.beds().isEmpty());
        assertEquals(at(1), site.pickup(), "The collection point outlives the beds while stock may remain");
    }

    @Test void berryBushesAreWateredOnceThenPickedWhenTheBushItselfIsRipeAndTheyRegrow() {
        var site = new GrowerSite();
        var bench = new WarehouseBatch.Point(5, 100, 5);
        site.sync(berries, points(2), bench);
        assertEquals(bench, site.pickup(), "Berries are collected at the Farmer's Workbench");
        var crop = site.chosen(berries);
        assertEquals("Plant_Fruit_Berries_Red", crop.item());
        assertEquals("unknown", site.choose(berries, "blue", 10, site.revision()));
        // A bush is planted by the player and is never seeded. It rests, then wants water.
        assertNull(site.next(berries, crop, bed -> true, null));
        site.advance(berries, berries.stageMinutes(), true, true);
        var water = site.next(berries, crop, bed -> true, bed -> bed.point().x());
        assertEquals(GrowerSite.Job.TEND, water.job());
        assertEquals(at(0), water.bed().point(), "Nearest first");
        assertTrue(doJob(berries, site, water, false));
        // Watered, but the bush has not ripened yet in the world: nothing to pick.
        assertNull(site.job(berries, water.bed(), false));
        assertEquals(0, site.stored());
        assertTrue(site.growth(berries, water.bed(), false) < 1);
        assertEquals(1, site.growth(berries, water.bed(), true));
        site = reload(site);
        var watered = site.bed(at(0));
        var pick = site.next(berries, site.chosen(berries), bed -> bed == watered, null);
        assertEquals(GrowerSite.Job.PICK, pick.job());
        assertSame(watered, pick.bed());
        assertFalse(site.complete(berries, crop, pick.bed(), pick.job(), true, () -> fail("No labour yet")));
        assertTrue(labour(berries, site, pick.bed(), pick.job()));
        assertFalse(site.complete(berries, site.chosen(berries), pick.bed(), pick.job(), true, () -> false), "The bush's own harvest must succeed first");
        assertTrue(site.complete(berries, site.chosen(berries), pick.bed(), pick.job(), true, () -> true));
        assertEquals(crop.yield(), site.amount("Plant_Fruit_Berries_Red"));
        assertEquals(1, pick.bed().stage(), "The bush regrows: it is never bare");
        assertNull(site.job(berries, pick.bed(), true), "A bush just picked rests before its next watering, ripe or not");
    }

    @Test void theStoreIsTownStockForTheCauldronAndBlocksRemovalUntilCollected() {
        var town = townWith("druid");
        var sites = town.getApprenticeWorkplaces();
        assertNull(sites.unlock(town, TownsfolkWorkType.MYCOLOGY)); assertNull(sites.unlock(town, TownsfolkWorkType.BERRIES));
        assertNull(sites.save(town, null, TownsfolkWorkType.MYCOLOGY, new WorkplaceArea(0, 100, 0, 4, 4), "Log yard"));
        var site = sites.sites().getFirst();
        assertNull(GrowerOutput.find(town, site.id()), "An unworked site has no store yet");
        assertNull(GrowerOutput.find(town, UUID.randomUUID()));
        var grower = site.grower();
        grower.sync(mushrooms, points(1), null);
        assertNull(grower.choose(mushrooms, "cap_red", 10, grower.revision()));
        for (int i = 0; i < 20 && grower.stored() == 0; i++) {
            var task = grower.next(mushrooms, grower.chosen(mushrooms), bed -> true, null);
            if (task == null) grower.advance(mushrooms, mushrooms.stageMinutes(), true, true); else doJob(mushrooms, grower, task, false);
        }
        var saved = gson.fromJson(gson.toJson(town), TownRecord.class);
        var store = GrowerOutput.find(saved, site.id());
        assertNotNull(store);
        assertEquals(Map.of("Plant_Crop_Mushroom_Cap_Red", 2L), store.getAmounts());
        assertEquals(at(0), store.pickup());
        var stock = AlchemyTownStock.snapshot(saved.getWarehouse(), List.of(new AlchemyTownStock.Source(site.id(), store)));
        assertEquals(2, stock.count("Plant_Crop_Mushroom_Cap_Red"), "The cauldron can brew with what the Mycologist holds");
        var owner = saved.getOwnerUuid(); var live = saved.getApprenticeWorkplaces();
        assertNotNull(live.remove(saved, owner, site.id()), "Stock is never thrown away with the workplace");
        assertNotNull(live.save(saved, site.id(), TownsfolkWorkType.BERRIES, site.area(), "Log yard"), "Nor by turning it into another kind of site");
        assertTrue(store.withdrawAll(Map.of("Plant_Crop_Mushroom_Cap_Red", 2L)));
        assertFalse(store.withdrawAll(Map.of("Plant_Crop_Mushroom_Cap_Red", 1L)));
        assertNull(live.save(saved, site.id(), TownsfolkWorkType.BERRIES, site.area(), "Berry patch"));
        assertNull(live.find(site.id()).existingGrower(), "A new kind of site starts with nothing growing");
        assertNull(live.remove(saved, owner, site.id()));
    }
}
