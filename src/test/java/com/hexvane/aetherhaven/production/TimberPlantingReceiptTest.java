package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

@Tag("town")
class TimberPlantingReceiptTest {
    private static final Position ROOT = new Position(-1, 100, 31);
    private static final String SAPLING = "Plant_Sapling_Beech", WOOD = "Wood_Beech_Trunk";
    private TimberPlantingReceipt receipt(List<Cell> intermediate, List<Cell> mature) {
        return new TimberPlantingReceipt(UUID.randomUUID(), SAPLING, WOOD, ROOT, 0, List.of(
            new TimberPlantingReceipt.Stage(0, SAPLING, List.of(new Cell(ROOT, SAPLING, 0, Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1, "young.prefab.json", intermediate),
            new TimberPlantingReceipt.Stage(2, "mature.prefab.json", mature)));
    }
    private Cell wood(Position p) { return new Cell(p, WOOD, 0, Kind.TIMBER); }

    @Test void savedIdentityRetainsRootsAndIntermediateCanopyAfterMatureOriginDisappears() {
        var underground = new Position(-1, 98, 31);
        var earlyBranch = new Position(-4, 102, 31);
        var mature = new Position(0, 105, 31);
        var fruit = new Cell(new Position(1, 104, 31), "Plant_Crop_Apple_Block_Wall", 0, 0, Kind.FOLIAGE, true);
        var matureCells = List.of(wood(mature), fruit);
        var receipt = receipt(List.of(wood(ROOT), wood(underground), wood(earlyBranch)), matureCells);
        assertNull(receipt.issue(p -> true));
        var restored = new Gson().fromJson(new Gson().toJson(receipt), TimberPlantingReceipt.class);
        assertEquals(receipt, restored); assertNull(restored.issue(p -> true));
        assertEquals(Set.of(ROOT, underground, earlyBranch, mature, fruit.position()), restored.footprint());
        assertEquals(matureCells, restored.mature().cells());
        assertTrue(restored.mature().cells().getLast().nativeFarming());
        assertThrows(UnsupportedOperationException.class, () -> restored.stages().clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.mature().cells().clear());
        assertNotNull(restored.issue(p -> !p.equals(earlyBranch)), "Intermediate growth must fit even if mature canopy would fit");
    }

    @Test void onlyExactFillersMatchAndTheyDoNotMultiplyTheTimberYield() {
        int filler = FillerBlockUtil.pack(1, 0, 0);
        var side = new Position(0, 100, 31);
        var cells = List.of(wood(ROOT), new Cell(side, WOOD, 0, filler, Kind.TIMBER));
        Map<Position, Observed> world = new HashMap<>();
        world.put(ROOT, new Observed(true, WOOD, 0, false));
        world.put(side, new Observed(true, WOOD, 0, filler, false));
        var plan = inspect(cells, WOOD, 1, 1, p -> true, world::get).plan();
        assertNotNull(plan); assertEquals(1, plan.outputCount());
        assertTrue(plan.recheck(1, p -> true, world::get).ready());
        world.put(side, new Observed(true, WOOD, 0, FillerBlockUtil.pack(-1, 0, 0), false));
        assertFalse(plan.recheck(1, p -> true, world::get).ready());
    }

    @Test void nativeSectionLocalYawAndExplicitIntegrationRegistrationAreStable() {
        assertEquals(NativeTimberGrowth.growthRotation(ROOT), NativeTimberGrowth.growthRotation(new Position(31, 132, -1)));
        assertEquals("Plant_Sapling_Spruce", NativeTimberGrowth.species("Wood_Fir_Trunk").sapling());
        assertNull(NativeTimberGrowth.species("Foreign_Trunk"));
        var addon = new NativeTimberGrowth.Species("Test_Timber", "Test_Sapling", Set.of("Test_Log"), Set.of("Test_Leaf"));
        NativeTimberGrowth.register(addon); NativeTimberGrowth.register(addon);
        assertEquals(addon, NativeTimberGrowth.species("Test_Timber"));
        assertNull(addon.kind("Test_Chest"));
        assertThrows(IllegalArgumentException.class, () -> NativeTimberGrowth.register(
            new NativeTimberGrowth.Species("Test_Timber", "Different_Sapling", Set.of("Test_Log"), Set.of("Test_Leaf"))));
    }

    @Test void permittedFamiliesCoverEveryInstalledNativeTreeStage() throws Exception {
        String appData = System.getenv("APPDATA"); Assumptions.assumeTrue(appData != null);
        Path assets = Path.of(appData, "Hytale/install/release/package/game/latest/Assets.zip");
        Assumptions.assumeTrue(Files.isRegularFile(assets));
        int variants = 0;
        try (var zip = new ZipFile(assets.toFile())) {
            for (var species : NativeTimberGrowth.species()) {
                if (!species.output().startsWith("Wood_")) continue;
                var sapling = json(zip, "Server/Item/Items/Plant/" + species.sapling() + ".json");
                var stages = sapling.getAsJsonObject("BlockType").getAsJsonObject("Farming")
                        .getAsJsonObject("Stages").getAsJsonArray("Default");
                assertEquals("BlockType", stages.get(0).getAsJsonObject().get("Type").getAsString());
                assertEquals(species.sapling(), stages.get(0).getAsJsonObject().get("Block").getAsString());
                for (int i = 0; i < stages.size()-1; i++) assertTrue(stages.get(i).getAsJsonObject().has("Duration"));
                assertFalse(stages.get(stages.size()-1).getAsJsonObject().has("Duration"), "Native maturity removes its growth component");
                for (var value : stages) {
                    var stage = value.getAsJsonObject(); if (!stage.has("Prefabs")) continue;
                    for (var candidate : stage.getAsJsonArray("Prefabs")) {
                        String path = candidate.getAsJsonObject().get("Path").getAsString();
                        var prefab = json(zip, "Server/Prefabs/" + path); variants++;
                        for (var raw : prefab.getAsJsonArray("blocks")) {
                            var cell = raw.getAsJsonObject(); String id = cell.get("name").getAsString();
                            assertNotNull(species.kind(id), path + " has an unrecognized tree block " + id);
                            assertTrue(!cell.has("chance") || cell.get("chance").getAsFloat() == 1, path);
                        }
                    }
                }
            }
        }
        assertTrue(variants > 300, "The reference check must cover the native variations, not one example tree");
    }
    private com.google.gson.JsonObject json(ZipFile zip, String path) throws Exception {
        var entry = zip.getEntry(path); assertNotNull(entry, path);
        try (var in = zip.getInputStream(entry)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
