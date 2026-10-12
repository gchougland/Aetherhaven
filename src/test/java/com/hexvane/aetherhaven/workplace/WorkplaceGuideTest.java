package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.WorkplaceQuestFixture;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceGuide.Word;
import com.hexvane.aetherhaven.workplace.WorkplaceStations.CraftedAt;
import com.hexvane.aetherhaven.workplace.WorkplaceStations.Placeable;
import com.hexvane.aetherhaven.workplace.WorkplaceStations.Station;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What the first workplace quest tells a player to do: the villager and building that unlock a type today, the types
 * on offer, and the bench the chosen type needs with the place its recipe is made, all taken from the town and from
 * the loaded items and recipes.
 */
@Tag("town")
class WorkplaceGuideTest {
    private static final ConstructionCatalog NO_BUILDINGS = ConstructionCatalog.empty();
    private static final GrowerCatalog GROWERS = GrowerCatalog.loadFromClasspath(WorkplaceGuideTest.class.getClassLoader());
    private static final Function<String, String> NAMES = role -> Map.of("Role_blacksmith", "Garren Vale", "Role_farmer", "Irienne").get(role);

    private static Placeable bench(String item, String benchId) { return new Placeable(item, benchId, item, item); }

    /** The benches and beds of the base game (0.7.0) and of this mod, with a few blocks that are neither. */
    private static final List<Placeable> PLACEABLES = List.of(
        bench("Bench_Furnace", "Furnace"), bench("Bench_Weapon", "Weapon_Bench"), bench("Bench_Armour", "Armor_Bench"),
        bench("Bench_Farming", "Farmingbench"), bench("Bench_Trough", "Farmingbench"), bench("Bench_Alchemy", "Alchemybench"),
        bench("Bench_WorkBench", "Workbench"), bench("Bench_Builders", "Builders"), bench("Bench_Tannery", "Tannery"),
        bench("Bench_Salvage", "Salvagebench"), bench("Bench_Cooking", "Cookingbench"),
        bench("Aetherhaven_Jewelry_Crafting_Bench", "Aetherhaven_Jewelry_Crafting_Bench"),
        new Placeable("Aetherhaven_Mushroom_Log", null, null, "Aetherhaven_Mushroom_Log"),
        bench("Bench_Campfire", "Campfire"), bench("Bench_Arcane", "Arcanebench"),
        new Placeable("Rock_Stone", null, null, "Rock_Stone"), new Placeable("Plant_Crop_Berry_Block", null, null, "Plant_Crop_Berry_Block"));

    private static CraftingRecipe recipe(String output, Object... benchThenTier) {
        var requirements = new BenchRequirement[benchThenTier.length / 2];
        for (int i = 0; i < requirements.length; i++) {
            requirements[i] = new BenchRequirement();
            requirements[i].id = (String) benchThenTier[i * 2];
            requirements[i].type = BenchType.Crafting;
            requirements[i].requiredTierLevel = (Integer) benchThenTier[i * 2 + 1];
        }
        var result = new MaterialQuantity(output, null, null, 1, null);
        return new CraftingRecipe(new MaterialQuantity[]{new MaterialQuantity("Ingredient_Fibre", null, null, 3, null)},
            result, new MaterialQuantity[]{result}, 1, requirements, 4, true, 1);
    }

    /** The recipes of the benches as 0.7.0 ships them. */
    private static final List<CraftingRecipe> RECIPES = List.of(
        recipe("Bench_Furnace", "Workbench", 0), recipe("Bench_Weapon", "Workbench", 0), recipe("Bench_Alchemy", "Workbench", 2),
        recipe("Bench_Salvage", "Workbench", 2), recipe("Bench_WorkBench", "Fieldcraft", 0),
        recipe("Bench_Builders", "Fieldcraft", 0, "Workbench", 0), recipe("Other_Item", "Weapon_Bench", 0));

    private static Station station(TownsfolkWorkType type) {
        return WorkplaceStations.station(type, GROWERS.find(type), PLACEABLES, RECIPES);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.hexvane.aetherhaven.quest.WorkplaceQuestFixture#everyType")
    void everyWorkplaceTypeNamesTheItemThatIsItsStation(TownsfolkWorkType type) {
        String expected = switch (type) {
            case SMELTING -> "Bench_Furnace";
            case BLACKSMITHING -> "Bench_Weapon";
            case ARMOR_CRAFTING -> "Bench_Armour";
            case FARMING, BERRIES -> "Bench_Farming";
            case RANCHING -> "Bench_Trough";
            case MYCOLOGY -> "Aetherhaven_Mushroom_Log";
            case MINING, CRAFTING -> "Bench_WorkBench";
            case BUILDING, BLOCK_WORKSHOP -> "Bench_Builders";
            case ALCHEMY -> "Bench_Alchemy";
            case LEATHERWORKING -> "Bench_Tannery";
            case COOKING -> "Bench_Cooking";
            case JEWELRY -> "Aetherhaven_Jewelry_Crafting_Bench";
            case SALVAGING -> "Bench_Salvage";
            default -> throw new AssertionError(type);
        };
        assertEquals(expected, WorkplaceStations.item(type, GROWERS.find(type), PLACEABLES));
        // The item named is one the workplace itself accepts: the quest and the workplace use one rule.
        var world = new WorkplaceQuestFixture.World();
        var area = new WorkplaceArea(0, 100, 0, 4, 4);
        WorkplaceQuestFixture.placeStation(world, type, area);
        assertEquals(com.hexvane.aetherhaven.quest.WorldProof.YES, WorkplaceQuestSteps.scan(world, area, type, GROWERS.find(type)));
    }

    @Test
    void aBenchAddedByAnotherPackIsFollowedAndTheSameItemIsNamedEveryTime() {
        var more = new ArrayList<>(PLACEABLES);
        more.add(bench("Zeta_Furnace", "Furnace"));
        more.add(bench("Alpha_Furnace", "Furnace"));
        assertEquals("Alpha_Furnace", WorkplaceStations.item(TownsfolkWorkType.SMELTING, null, more));
        java.util.Collections.reverse(more);
        assertEquals("Alpha_Furnace", WorkplaceStations.item(TownsfolkWorkType.SMELTING, null, more));
        // A pack may name its own bench for a type. Its item is then one of the type's stations.
        WorkplaceBenchAccess.registerPrimaryBench(TownsfolkWorkType.COOKING, "Pack_Oven");
        more.add(bench("A_Pack_Oven", "Pack_Oven"));
        assertEquals("A_Pack_Oven", WorkplaceStations.item(TownsfolkWorkType.COOKING, null, more));
        assertNull(WorkplaceStations.item(TownsfolkWorkType.SMELTING, null, List.of()), "No item, no name");
    }

    @Test
    void theRecipeSaysWhereTheBenchIsMade() {
        assertEquals(new Station("Bench_Furnace", new CraftedAt("Workbench", 1), "Bench_WorkBench"), station(TownsfolkWorkType.SMELTING));
        assertEquals(new CraftedAt("Workbench", 2), station(TownsfolkWorkType.ALCHEMY).craftedAt(), "The tier the recipe asks for is kept");
        // Made in the inventory: nothing has to be built first, so that is the place named.
        var workbench = station(TownsfolkWorkType.CRAFTING);
        assertTrue(workbench.craftedAt().pocket());
        assertNull(workbench.craftedAtItemId());
        assertTrue(station(TownsfolkWorkType.BUILDING).craftedAt().pocket(), "Pocket Crafting wins over a bench");
        // No recipe names a bench: the item is still named, the place is left out.
        assertEquals(new Station("Bench_Cooking", null, null), station(TownsfolkWorkType.COOKING));
        // Two recipes: the lower tier, then the first bench by id, whatever order they arrive in.
        var two = List.of(recipe("Bench_Tannery", "Workbench", 3), recipe("Bench_Tannery", "Weapon_Bench", 3), recipe("Bench_Tannery", "Workbench", 2));
        assertEquals(new CraftedAt("Workbench", 2), WorkplaceStations.craftedAt("Bench_Tannery", two));
        assertEquals(new CraftedAt("Weapon_Bench", 3), WorkplaceStations.craftedAt("Bench_Tannery", two.subList(0, 2)));
        assertEquals(new CraftedAt("Weapon_Bench", 3), WorkplaceStations.craftedAt("Bench_Tannery", two.subList(0, 2).reversed()));
        assertNull(WorkplaceStations.craftedAt("Bench_Tannery", List.of()));
        // A pack that moves the furnace's recipe to another bench is followed.
        var moved = WorkplaceStations.station(TownsfolkWorkType.SMELTING, null, PLACEABLES, List.of(recipe("Bench_Furnace", "Weapon_Bench", 0)));
        assertEquals("Bench_Weapon", moved.craftedAtItemId());
    }

    @Test
    void theUnlockStepNamesTheVillagerTheirBuildingAndWhatTheyOffer() {
        var town = WorkplaceQuestFixture.emptyTown();
        var waiting = WorkplaceGuide.unlockStep(town, NO_BUILDINGS, null, NAMES);
        assertEquals("waiting", waiting.variant());
        assertEquals("waiting", waiting.hudVariant());
        assertTrue(waiting.words().isEmpty());

        WorkplaceQuestFixture.moveIn(town, "blacksmith");
        var step = WorkplaceGuide.unlockStep(town, NO_BUILDINGS, null, NAMES);
        assertEquals("ready", step.variant());
        assertEquals("ready", step.hudVariant());
        assertEquals(Word.raw("Garren Vale"), step.words().get("villager"));
        assertEquals(Word.lang("aetherhaven_workplace.aetherhaven.workplace.villager.blacksmith"), step.words().get("role"));
        assertEquals(Word.building("plot_of_blacksmith"), step.words().get("building"));
        assertEquals(Word.raw("100"), step.words().get("gold"));
        assertEquals(Word.raw("Smelting, Blacksmithing, Armor Crafting"), step.words().get("types"));

        // A second villager moves in: the first is still the one named, and the line says there are others.
        WorkplaceQuestFixture.moveIn(town, "farmer");
        var more = WorkplaceGuide.unlockStep(town, NO_BUILDINGS, null, NAMES);
        assertEquals("readyMore", more.variant());
        assertEquals("ready", more.hudVariant());
        assertEquals(Word.raw("Garren Vale"), more.words().get("villager"));

        // Only what is still locked is offered.
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        assertEquals(Word.raw("Blacksmithing, Armor Crafting"), WorkplaceGuide.unlockStep(town, NO_BUILDINGS, null, NAMES).words().get("types"));
        assertNotEquals(more.signature(), WorkplaceGuide.unlockStep(town, NO_BUILDINGS, null, NAMES).signature(), "The HUD redraws when the offer changes");

        // A quest that wants one type sends the player to that type's villager.
        var farming = WorkplaceGuide.unlockStep(town, NO_BUILDINGS, TownsfolkWorkType.FARMING, NAMES);
        assertEquals("ready", farming.variant());
        assertEquals(Word.raw("Irienne"), farming.words().get("villager"));
        assertEquals(Word.raw("Farming"), farming.words().get("types"));
    }

    @Test
    void aVillagerWhoseNameIsNotKnownIsCalledByTheirTrade() {
        var town = WorkplaceQuestFixture.emptyTown();
        WorkplaceQuestFixture.moveIn(town, "druid");
        var step = WorkplaceGuide.unlockStep(town, NO_BUILDINGS, null, NAMES);
        assertEquals("readyUnnamed", step.variant());
        assertEquals("readyUnnamed", step.hudVariant());
        assertFalse(step.words().containsKey("villager"));
        assertEquals(Word.lang("aetherhaven_workplace.aetherhaven.workplace.villager.druid"), step.words().get("role"));
        assertEquals(Word.raw("Mycologist, Berry Patch"), step.words().get("types"));
    }

    @Test
    void supervisorsAreThoseWhoLiveInAFinishedBuildingInTheOrderOfTheWorkplacePage() {
        var town = WorkplaceQuestFixture.emptyTown();
        for (String kind : List.of("chef", "builder", "blacksmith", "elder")) WorkplaceQuestFixture.moveIn(town, kind);
        var kinds = WorkplaceGuide.supervisors(town, NO_BUILDINGS).stream().map(WorkplaceGuide.Supervisor::kind).toList();
        assertEquals(3, kinds.size(), "The Elder supervises no workplace");
        var order = new ArrayList<String>();
        for (var type : WorkplaceTypePicker.creatable()) {
            String kind = WorkplaceRules.supervisor(type);
            if (kinds.contains(kind) && !order.contains(kind)) order.add(kind);
        }
        assertEquals(order, kinds);
        assertTrue(WorkplaceGuide.supervisorReady(town, NO_BUILDINGS));
        var builder = WorkplaceGuide.supervisors(town, NO_BUILDINGS).stream().filter(s -> s.kind().equals("builder")).findFirst().orElseThrow();
        assertEquals(List.of(TownsfolkWorkType.BUILDING, TownsfolkWorkType.BLOCK_WORKSHOP), builder.locked());
        assertTrue(builder.unlocked().isEmpty());
    }

    @Test
    void theChooseStepNamesTheTypesTheTownHasUnlocked() {
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        var locked = WorkplaceGuide.chooseStep(town, null);
        assertEquals("locked", locked.variant());
        assertEquals("choose", locked.hudVariant());
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.ARMOR_CRAFTING));
        var ready = WorkplaceGuide.chooseStep(town, null);
        assertEquals("ready", ready.variant());
        assertEquals(Word.raw("Smelting, Armor Crafting"), ready.words().get("types"));
        assertEquals("mark", WorkplaceGuide.markStep().variant());
    }

    @Test
    void theBenchStepNamesTheNewestWorkplaceItsBenchAndWhereTheBenchIsMade() {
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        assertEquals("noSite", WorkplaceGuide.benchStep(town, null, WorkplaceGuideTest::station).variant());

        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.SMELTING, "Hill Smeltery");
        var smelt = WorkplaceGuide.benchStep(town, null, WorkplaceGuideTest::station);
        assertEquals("place", smelt.variant());
        assertEquals("place", smelt.hudVariant());
        assertEquals(Word.item("Bench_Furnace"), smelt.words().get("bench"));
        assertEquals(Word.raw("Hill Smeltery"), smelt.words().get("site"));
        assertEquals(Word.raw("Smelting"), smelt.words().get("type"));
        assertEquals(Word.item("Bench_WorkBench"), smelt.words().get("craftedAt"));
        assertEquals("Bench_Furnace", smelt.itemId(), "The journal shows the bench beside the step");

        // A second workplace of another type: the newest one is the one being set up.
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.BLACKSMITHING));
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.BLACKSMITHING, "Forge");
        var forge = WorkplaceGuide.benchStep(town, null, WorkplaceGuideTest::station);
        assertEquals(Word.item("Bench_Weapon"), forge.words().get("bench"));
        assertEquals(Word.raw("Forge"), forge.words().get("site"));
        assertNotEquals(smelt.signature(), forge.signature());

        // The player removes both: the step says so and asks for a new one.
        for (var site : List.copyOf(town.getApprenticeWorkplaces().sites()))
            assertNull(town.getApprenticeWorkplaces().remove(town, town.getOwnerUuid(), site.id()));
        var none = WorkplaceGuide.benchStep(town, null, WorkplaceGuideTest::station);
        assertEquals("noSite", none.variant());
        assertEquals("noSite", none.hudVariant());
        assertNull(none.itemId());
    }

    @Test
    void everyWayABenchCanBeMadeHasItsOwnWording() {
        assertEquals("placeTier", benchVariant(TownsfolkWorkType.ALCHEMY, "alchemist"));
        assertEquals("placePocket", benchVariant(TownsfolkWorkType.CRAFTING, "crafter"));
        assertEquals("placeNoRecipe", benchVariant(TownsfolkWorkType.COOKING, "chef"));
        assertEquals("place", benchVariant(TownsfolkWorkType.BLACKSMITHING, "blacksmith"));

        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.ALCHEMY);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.ALCHEMY));
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.ALCHEMY, "Still room");
        var tier = WorkplaceGuide.benchStep(town, null, WorkplaceGuideTest::station);
        assertEquals(Word.raw("2"), tier.words().get("tier"));
        assertEquals(Word.item("Bench_WorkBench"), tier.words().get("craftedAt"));

        // The assets hold no item for this type's bench (a pack was removed): the step still says what to do.
        var unknown = WorkplaceGuide.benchStep(town, null, type -> null);
        assertEquals("placeUnknown", unknown.variant());
        assertEquals("placeUnknown", unknown.hudVariant());
        assertEquals(Word.raw("Alchemy"), unknown.words().get("type"));
        assertNull(unknown.itemId());

        // A bench whose own item is not loaded is named the way the workplace pages name it.
        var bare = WorkplaceGuide.benchStep(town, null, type -> new Station("Bench_Alchemy", new CraftedAt("Workbench", 1), null));
        assertEquals(Word.raw("Workbench"), bare.words().get("craftedAt"));
    }

    private static String benchVariant(TownsfolkWorkType type, String supervisor) {
        var town = WorkplaceQuestFixture.emptyTown();
        WorkplaceQuestFixture.moveIn(town, supervisor);
        assertEquals(supervisor, WorkplaceRules.supervisor(type));
        assertNull(town.getApprenticeWorkplaces().unlock(town, type));
        WorkplaceQuestFixture.save(town, type, "Site");
        var step = WorkplaceGuide.benchStep(town, null, WorkplaceGuideTest::station);
        assertNotNull(step.words().get("bench"));
        return step.variant();
    }
}
