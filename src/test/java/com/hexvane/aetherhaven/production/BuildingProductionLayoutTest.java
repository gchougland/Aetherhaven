package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class BuildingProductionLayoutTest {
    private final Gson gson = new Gson();
    private ConstructionDefinition mining(int x) {
        return gson.fromJson("""
            {"id":"mine","productionLayout":{"modRules":"4.0","mode":"mining_nodes",
            "nodes":[{"x":%d,"y":2,"z":3,"expectedBlockId":"Rock_Stone"}]}}
            """.formatted(x), ConstructionDefinition.class);
    }
    @Test void oldDefinitionsAndOldPlacedSavesRemainLegacyWithoutCatalogInference() {
        var old = gson.fromJson("{\"id\":\"mine\",\"version\":99}", ConstructionDefinition.class);
        var placed = gson.fromJson("{\"constructionId\":\"mine\"}", PlotInstance.class);
        assertTrue(old.getProductionLayout().legacy());
        assertTrue(placed.getProductionLayout().legacy());
        assertFalse(mining(4).getProductionLayout().legacy());
        assertTrue(placed.getProductionLayout().legacy());
    }
    @Test void initialPlacementCapturesAnImmutableLayoutAcrossDownloadsAndSaveReload() {
        var placed = new PlotInstance();
        placed.captureProductionLayout(mining(4));
        placed.captureProductionLayout(mining(9));
        var saved = gson.fromJson(gson.toJson(placed), PlotInstance.class);
        assertEquals(4, saved.getProductionLayout().nodes().getFirst().x());
        assertEquals("4.0", saved.getProductionLayout().modRules());
        assertNull(saved.getProductionLayout().validationError());
        assertThrows(UnsupportedOperationException.class, () -> saved.getProductionLayout().nodes().clear());
    }
    @Test void placingAnOldDesignDuringFourDoesNotOptInOnLaterCapture() {
        var placed = new PlotInstance();
        placed.captureProductionLayout(gson.fromJson("{\"id\":\"mine\"}", ConstructionDefinition.class));
        placed.captureProductionLayout(mining(9));
        assertTrue(placed.getProductionLayout().legacy());
    }
    @Test void malformedAndFutureDeclarationsDoNotFallBackToTimerProduction() {
        var cell = new BuildingProductionLayout.Node(1,2,3,"Rock_Stone");
        for (var layout : List.of(
                new BuildingProductionLayout("5.0","mining_nodes",List.of(cell)),
                new BuildingProductionLayout("4.0","mining_nodes",List.of()),
                new BuildingProductionLayout("4.0","mining_nodes",List.of(cell,cell)),
                new BuildingProductionLayout("4.0","mining_nodes",List.of(new BuildingProductionLayout.Node(1,2,3,""))),
                new BuildingProductionLayout("4.0","mining_nodes",Collections.nCopies(65,cell)),
                new BuildingProductionLayout("4.0","mining_nodes",Arrays.asList((BuildingProductionLayout.Node)null)))) {
            assertFalse(layout.legacy());
            assertNotNull(layout.validationError());
        }
    }
}
