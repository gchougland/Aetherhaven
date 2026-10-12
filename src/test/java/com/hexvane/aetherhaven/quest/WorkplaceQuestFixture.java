package com.hexvane.aetherhaven.quest;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.ClaimedTerritoryChunkRecord;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hexvane.aetherhaven.workplace.WorkplaceQuestSteps;
import com.hexvane.aetherhaven.workplace.WorkplaceRules;
import com.hexvane.aetherhaven.workplace.WorkplaceTypePicker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Towns, the shipped quest and a pretend world for the tests of the first workplace quest. */
public final class WorkplaceQuestFixture {
    private WorkplaceQuestFixture() {}

    public static final String QUEST = "q_first_workplace";
    public static final Path QUEST_FILE = Path.of("subplugin-assets/Quests/Server/Aetherhaven/Quests/Town/q_first_workplace.json");
    public static final ConstructionCatalog NO_BUILDINGS = ConstructionCatalog.empty();

    /** The quest as it ships, so a change to the data is tested and not a copy of it. */
    public static QuestDefinition shipped() {
        try {
            return new Gson().fromJson(Files.readString(QUEST_FILE), QuestDefinition.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static QuestCatalog catalog(QuestDefinition... quests) {
        var byId = new HashMap<String, QuestDefinition>();
        for (var quest : quests) byId.put(quest.idOrEmpty(), quest);
        return QuestCatalog.of(byId);
    }

    /** A town with its home chunk claimed and nobody living in it. */
    public static TownRecord emptyTown() {
        var town = new TownRecord();
        town.setTownId(UUID.randomUUID());
        town.setOwner(UUID.randomUUID(), "Owner");
        town.getClaimedTerritoryChunks().add(ClaimedTerritoryChunkRecord.of(0, 0));
        return town;
    }

    /** Moves a supervising villager in, working at a finished building of their own. */
    public static PlotInstance moveIn(TownRecord town, String kind) {
        var plot = new PlotInstance(UUID.randomUUID(), "plot_of_" + kind, PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0, 100, 0, 8, 110, 8), 0, 100, 0, 0L);
        town.addPlotInstance(plot);
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Role_" + kind, kind, plot.getPlotId(), UUID.randomUUID()));
        return plot;
    }

    /** A town whose only supervising villager is the one a type needs. */
    public static TownRecord townFor(TownsfolkWorkType type) {
        var town = emptyTown();
        moveIn(town, WorkplaceRules.supervisor(type));
        return town;
    }

    /** Every type a workplace can be marked for. The quest must work the same for each. */
    public static Stream<TownsfolkWorkType> everyType() {
        return WorkplaceTypePicker.creatable().stream();
    }

    private static int nextSite;

    /** A free 4 by 4 patch of the claimed chunk. Each call gives another one, so sites never overlap. */
    public static WorkplaceArea area() {
        int slot = nextSite++ % 49;
        return WorkplaceArea.corners(slot % 7 * 4, 100, slot / 7 * 4, slot % 7 * 4 + 3, slot / 7 * 4 + 3);
    }

    /** Saves a workplace the way its page does, and fails loudly when the town refuses it. */
    public static WorkplaceArea save(TownRecord town, TownsfolkWorkType type, String name) {
        var area = area();
        String refused = town.getApprenticeWorkplaces().save(town, null, type, area, name);
        if (refused != null) throw new AssertionError("Workplace not saved: " + refused);
        return area;
    }

    public static QuestObjective objective(QuestDefinition quest, String id) {
        for (var objective : quest.objectivesOrEmpty()) if (id.equals(objective.id())) return objective;
        throw new AssertionError("No objective " + id);
    }

    public static List<String> done(QuestDefinition quest, TownRecord town) {
        return quest.objectivesOrEmpty().stream()
            .filter(o -> QuestProgressionService.isObjectiveComplete(town, quest.idOrEmpty(), o)).map(QuestObjective::id).toList();
    }

    public static String current(QuestDefinition quest, TownRecord town) {
        var objective = QuestProgressionService.currentObjective(quest, town, quest.idOrEmpty());
        return objective == null ? null : objective.id();
    }

    /** A pretend world: blocks by position, and chunks that can be out of sight. */
    public static final class World implements WorkplaceQuestSteps.Blocks {
        private final Map<String, WorkplaceQuestSteps.Standing> blocks = new HashMap<>();
        private boolean loaded = true;

        public World unloaded() { loaded = false; return this; }
        public World loaded() { loaded = true; return this; }

        /** A bench block. Its base block is the bench item's own block unless another is given. */
        public World bench(int x, int y, int z, String benchId, String block) {
            blocks.put(x + "," + y + "," + z, new WorkplaceQuestSteps.Standing(benchId, block, block));
            return this;
        }

        public World block(int x, int y, int z, String block) {
            blocks.put(x + "," + y + "," + z, new WorkplaceQuestSteps.Standing(null, null, block));
            return this;
        }

        @Override public boolean loaded(int chunkX, int chunkZ) { return loaded; }
        @Override public WorkplaceQuestSteps.Standing at(int x, int y, int z) { return blocks.get(x + "," + y + "," + z); }
    }

    /** The station of each type as the base game and this mod ship it: bench id, and the block it is. */
    public static void placeStation(World world, TownsfolkWorkType type, WorkplaceArea area) {
        int x = area.minX() + 1, y = area.floorY(), z = area.minZ() + 1;
        switch (type) {
            case SMELTING -> world.bench(x, y, z, "Furnace", "Bench_Furnace");
            case BLACKSMITHING -> world.bench(x, y, z, "Weapon_Bench", "Bench_Weapon");
            case ARMOR_CRAFTING -> world.bench(x, y, z, "Armor_Bench", "Bench_Armour");
            case FARMING, BERRIES, CULTIVATION -> world.bench(x, y, z, "Farmingbench", "Bench_Farming");
            case RANCHING -> world.bench(x, y, z, "Farmingbench", "Bench_Trough");
            case MYCOLOGY -> world.block(x, y, z, "Aetherhaven_Mushroom_Log");
            case MINING, CRAFTING -> world.bench(x, y, z, "Workbench", "Bench_WorkBench");
            case BUILDING, BLOCK_WORKSHOP -> world.bench(x, y, z, "Builders", "Bench_Builders");
            case ALCHEMY -> world.bench(x, y, z, "Alchemybench", "Bench_Alchemy");
            case LEATHERWORKING -> world.bench(x, y, z, "Tannery", "Bench_Tannery");
            case COOKING -> world.bench(x, y, z, "Cookingbench", "Bench_Cooking");
            case JEWELRY -> world.bench(x, y, z, "Aetherhaven_Jewelry_Crafting_Bench", "Aetherhaven_Jewelry_Crafting_Bench");
            case SALVAGING -> world.bench(x, y, z, "Salvagebench", "Bench_Salvage");
            case LOGISTICS, COMMERCE -> throw new IllegalArgumentException(type + " has no workplace");
        }
    }
}
