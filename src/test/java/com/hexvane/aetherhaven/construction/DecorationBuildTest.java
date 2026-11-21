package com.hexvane.aetherhaven.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.town.PlotInstance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
final class DecorationBuildTest {
    private static final Path PREFABS = Path.of("src/main/resources/Server/Prefabs");
    /** Marks that a block entity's scale was migrated; the only mod data on a prefab block that does no job. */
    private static final String INERT_COMPONENT = "AetherhavenBlockEntityScaleMigrated";
    private static final Gson GSON = new Gson();

    @Test
    void everyBlockInAShippedPrefabThatLinksToTheTownIsLeftOut() throws IOException {
        List<String> missed = new ArrayList<>();
        for (Path prefab : prefabs()) {
            for (JsonElement element : blocks(prefab)) {
                JsonObject block = element.getAsJsonObject();
                String name = block.get("name").getAsString();
                if (hasTownData(block) && !DecorationBuild.isFunctionalBlock(name)) {
                    missed.add(prefab.getFileName() + ": " + name);
                }
            }
        }
        assertEquals(List.of(), missed);
    }

    @Test
    void workingBlocksInShippedPrefabsAreLeftOutAndSceneryStays() throws IOException {
        Set<String> modBlocks = new TreeSet<>();
        for (Path prefab : prefabs()) {
            for (JsonElement element : blocks(prefab)) {
                String name = element.getAsJsonObject().get("name").getAsString();
                if (name.startsWith("Aetherhaven_")) {
                    modBlocks.add(name);
                }
            }
        }
        Set<String> kept = new TreeSet<>(modBlocks);
        kept.removeIf(DecorationBuild::isFunctionalBlock);
        assertEquals(Set.of("Aetherhaven_Heartberry", "Aetherhaven_Smokestack", "Aetherhaven_Wood_Scaffold"), kept);
        assertTrue(modBlocks.contains("Aetherhaven_Management_Block"));
        assertTrue(modBlocks.contains("Aetherhaven_Production_Storage"));
    }

    @Test
    void vanillaBlocksAndBenchesStayAndStateVariantsFollowTheirBlock() {
        assertFalse(DecorationBuild.isFunctionalBlock("Bench_Farming"));
        assertFalse(DecorationBuild.isFunctionalBlock("Wood_Softwood_Planks"));
        assertFalse(DecorationBuild.isFunctionalBlock(null));
        assertTrue(DecorationBuild.isFunctionalBlock("*Aetherhaven_Production_Storage_State_Definitions_OpenWindow"));
    }

    @Test
    void functionalCellsBecomeAirInPlaceAndKeepTheirFluid() {
        PendingBlock wall = new PendingBlock(0, 0, 0, 5, null, 0, 0, 0, 0, 0);
        PendingBlock shelf = new PendingBlock(1, 2, 3, 9, null, 1, 2, 3, 4, 6);
        PendingBlock filler = new PendingBlock(1, 3, 3, 9, null, 0, 2, 7, 0, 0);

        List<PendingBlock> out = DecorationBuild.withoutFunctionalBlocks(List.of(wall, shelf, filler), id -> id == 9);

        assertEquals(3, out.size());
        assertSame(wall, out.get(0));
        assertEquals(new PendingBlock(1, 2, 3, 0, null, 0, 0, 0, 4, 6), out.get(1));
        assertEquals(new PendingBlock(1, 3, 3, 0, null, 0, 0, 0, 0, 0), out.get(2));
    }

    @Test
    void onlyOrdinaryBuildingsOfferTheChoice() {
        assertTrue(DecorationBuild.canBuildAsDecoration(definition("{\"id\":\"plot_inn\"}")));
        assertFalse(DecorationBuild.canBuildAsDecoration(definition("{\"id\":\"simple\",\"decorationPlot\":true}")));
        assertFalse(DecorationBuild.canBuildAsDecoration(definition("{\"id\":\"plot_decoration_arch\"}")));
        assertFalse(DecorationBuild.canBuildAsDecoration(definition("{\"id\":\"wall\",\"wallSegment\":true}")));
    }

    @Test
    void theChoiceSurvivesASaveAndOldSavesAreNotDecorations() {
        PlotInstance plot = new PlotInstance();
        assertFalse(plot.isBuildAsDecoration());
        plot.setBuildAsDecoration(true);
        assertTrue(GSON.fromJson(GSON.toJson(plot), PlotInstance.class).isBuildAsDecoration());
        plot.setBuildAsDecoration(false);
        assertNull(GSON.toJsonTree(plot).getAsJsonObject().get("buildAsDecoration"));
        assertFalse(GSON.fromJson("{}", PlotInstance.class).isBuildAsDecoration());
    }

    private static ConstructionDefinition definition(String json) {
        return GSON.fromJson(json, ConstructionDefinition.class);
    }

    private static boolean hasTownData(JsonObject block) {
        JsonElement components = block.get("components");
        if (components == null || !components.isJsonObject()) {
            return false;
        }
        JsonObject holder = components.getAsJsonObject();
        JsonObject inner = holder.has("Components") ? holder.getAsJsonObject("Components") : holder;
        for (Map.Entry<String, JsonElement> entry : inner.entrySet()) {
            if (entry.getKey().startsWith("Aetherhaven") && !entry.getKey().equals(INERT_COMPONENT)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> prefabs() throws IOException {
        try (Stream<Path> files = Files.walk(PREFABS)) {
            return files.filter(path -> path.toString().endsWith(".prefab.json")).toList();
        }
    }

    private static List<JsonElement> blocks(Path prefab) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(prefab)).getAsJsonObject();
        List<JsonElement> out = new ArrayList<>();
        if (root.has("blocks")) {
            root.getAsJsonArray("blocks").forEach(out::add);
        }
        return out;
    }
}
