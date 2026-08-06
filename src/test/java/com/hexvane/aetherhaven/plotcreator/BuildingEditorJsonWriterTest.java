package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hexvane.aetherhaven.AetherhavenConstants;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("crossmod")
class BuildingEditorJsonWriterTest {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();

    @TempDir
    Path tempDir;

    @Test
    void writeMerged_writesTouristDestinationWhenDraftTrue() throws Exception {
        PlotCreatorDraft draft = minimalDraft("plot_test_shop");
        draft.setTouristDestination(true);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", "plot_test_shop");
        snapshot.put("displayName", "Old Name");
        snapshot.put("prefabPath", "test.prefab.json");
        snapshot.put("plotAnchorOffset", List.of(0, 1, 0));

        Path out = tempDir.resolve("plot_test_shop.json");
        BuildingEditorJsonWriter.writeMerged(out, draft, snapshot);

        Map<String, Object> written = readJson(out);
        assertEquals(true, written.get("touristDestination"));
    }

    @Test
    void writeMerged_removesTouristDestinationWhenDraftFalse() throws Exception {
        PlotCreatorDraft draft = minimalDraft("plot_test_shop");
        draft.setTouristDestination(false);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", "plot_test_shop");
        snapshot.put("displayName", "Shop");
        snapshot.put("prefabPath", "test.prefab.json");
        snapshot.put("plotAnchorOffset", List.of(0, 1, 0));
        snapshot.put("touristDestination", true);

        Path out = tempDir.resolve("plot_test_shop.json");
        BuildingEditorJsonWriter.writeMerged(out, draft, snapshot);

        Map<String, Object> written = readJson(out);
        assertFalse(written.containsKey("touristDestination"));
    }

    @Test
    void applyDefaultTagsForKind_setsTouristDestinationForShopVariant() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setKinds(List.of(PlotBuildingKind.VARIANT));
        draft.setCountsAsConstructionIds(List.of(AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP));
        draft.setConstructionId("plot_stormwind_flower_shop");
        draft.setSelfBuildGameDays(1.0);
        draft.setSelfBuildDaysInput("1");

        assertFalse(draft.isTouristDestination());

        PlotCreatorService.applyDefaultTagsForKind(draft);

        assertTrue(draft.isTouristDestination());
    }

    @Nonnull
    private static PlotCreatorDraft minimalDraft(@Nonnull String id) {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setConstructionId(id);
        draft.setDisplayName("Test");
        draft.setPrefabPath("test.prefab.json");
        draft.setPrefabFileName("test.prefab.json");
        draft.setPlotAnchorOffset(new int[] {0, 1, 0});
        draft.setSelfBuildGameDays(1.0);
        draft.setSelfBuildDaysInput("1");
        draft.setRotationYaw("None");
        return draft;
    }

    @Nonnull
    private static Map<String, Object> readJson(@Nonnull Path file) throws Exception {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Object> parsed = GSON.fromJson(raw, MAP_TYPE);
        return parsed != null ? parsed : Map.of();
    }
}
