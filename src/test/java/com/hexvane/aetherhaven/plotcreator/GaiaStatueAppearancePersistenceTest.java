package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.GaiaStatueAppearance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Tag("construction")
class GaiaStatueAppearancePersistenceTest {
    @TempDir Path directory;
    private final Gson gson = new Gson();

    @ParameterizedTest
    @EnumSource(GaiaStatueAppearance.class)
    void creatorThenBuildingEditorRoundTripKeepsAppearance(GaiaStatueAppearance appearance) throws Exception {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setConstructionId("plot_gaia_marketplace_" + appearance.name().toLowerCase());
        draft.setDisplayName("Marketplace altar");
        draft.setKinds(List.of(PlotBuildingKind.VARIANT));
        draft.setCountsAsConstructionIds(List.of(AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR));
        draft.setPrefabPath("custom_altar.prefab.json");
        draft.setPlotAnchorOffset(new int[] {0, 1, 0});
        draft.setGaiaStatueLocalPos(new int[] {-3, 5, 9});
        draft.setGaiaStatueBlockTypeId(appearance.blockTypeId());
        PlotCreatorPoiDraft unrelated = new PlotCreatorPoiDraft();
        unrelated.setLocal(6, 7, 8);
        unrelated.setBlockTypeId("Furniture_Temple_Light_Statue");
        draft.getPois().add(unrelated);

        Path file = directory.resolve("building.json");
        PlotCreatorJsonWriter.writeBuilding(file, draft);
        for (int cycle = 0; cycle < 3; cycle++) {
            ConstructionDefinition definition = gson.fromJson(Files.readString(file), ConstructionDefinition.class);
            PlotCreatorDraft reopened = new PlotCreatorDraft();
            PlotCreatorDraftLoader.loadIntoDraft(reopened, definition);
            assertArrayEquals(new int[] {-3, 5, 9}, reopened.getGaiaStatueLocalPos());
            assertEquals(appearance.blockTypeId(), reopened.getGaiaStatueBlockTypeId());
            assertEquals(2, reopened.getPois().size());
            assertEquals(1, reopened.getPois().stream()
                .filter(p -> appearance.blockTypeId().equals(p.getBlockTypeId())).count());
            assertTrue(reopened.getPois().stream().anyMatch(p -> "Furniture_Temple_Light_Statue".equals(p.getBlockTypeId())));
            // Editor placement changes the world origin; the stored local statue position/appearance must not change.
            reopened.setPlotAnchor(new Vector3i(100, 50, -200));
            assertEquals(new Vector3i(97, 55, -191), PlotCreatorLocalCoords.toWorldBlock(reopened, reopened.getGaiaStatueLocalPos()));
            BuildingEditorJsonWriter.writeMerged(file, reopened, BuildingEditorJsonWriter.loadSnapshot(file));
        }
    }

    @ParameterizedTest
    @EnumSource(GaiaStatueAppearance.class)
    void changingAppearanceReplacesOnlyGaiaPoiAndClearResetsDefault(GaiaStatueAppearance appearance) {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        draft.setGaiaStatueLocalPos(new int[] {1, 2, 3});
        PlotCreatorGaiaStatueSupport.syncPoiFromLocalPos(draft);
        draft.setGaiaStatueBlockTypeId(appearance.blockTypeId());
        PlotCreatorGaiaStatueSupport.syncPoiFromLocalPos(draft);
        PlotCreatorGaiaStatueSupport.syncPoiFromLocalPos(draft);
        assertEquals(1, draft.getPois().size());
        assertEquals(appearance.blockTypeId(), draft.getPois().getFirst().getBlockTypeId());
        PlotCreatorGaiaStatueSupport.clear(draft);
        assertNull(draft.getGaiaStatueLocalPos());
        assertTrue(draft.getPois().isEmpty());
        assertEquals(GaiaStatueAppearance.LIGHT.blockTypeId(), draft.getGaiaStatueBlockTypeId());
    }

    @Test
    void legacyPoiWithoutAppearanceFieldLoadsAsOriginalStatue() {
        PlotCreatorDraft draft = new PlotCreatorDraft();
        PlotCreatorPoiDraft poi = new PlotCreatorPoiDraft();
        poi.setLocal(0, 2, 0);
        poi.setBlockTypeId(AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID);
        draft.getPois().add(poi);
        PlotCreatorGaiaStatueSupport.extractLocalPosFromPois(draft);
        assertEquals(GaiaStatueAppearance.LIGHT.blockTypeId(), draft.getGaiaStatueBlockTypeId());
        assertArrayEquals(new int[] {0, 2, 0}, draft.getGaiaStatueLocalPos());
    }

    @Test
    void invalidIdsCannotMasqueradeAsStatueVariants() {
        for (String id : List.of("", "Aetherhaven_Statue_Of_Gaia_Unknown", "Furniture_Temple_Light_Statue", "Stone")) {
            assertNull(GaiaStatueAppearance.fromBlockTypeId(id));
            assertFalse(PlotCreatorGaiaStatueSupport.isGaiaStatueBlockTypeId(id));
            assertFalse(GaiaStatueAppearance.sameStatueFamily(GaiaStatueAppearance.LIGHT.blockTypeId(), id));
        }
        assertNull(GaiaStatueAppearance.fromBlockTypeId(null));
        for (GaiaStatueAppearance appearance : GaiaStatueAppearance.values()) {
            assertSame(appearance, GaiaStatueAppearance.fromBlockTypeId(" " + appearance.blockTypeId().toLowerCase() + " "));
            assertTrue(GaiaStatueAppearance.sameStatueFamily(GaiaStatueAppearance.LIGHT.blockTypeId(), appearance.blockTypeId()));
        }
    }
}
