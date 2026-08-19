package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class PlotCreatorPrefabExporterFestivalAirTest {
    @Test
    void festivalExportOmitsWorldAirAndEditorEmptyEvenWhenWaterIsPresent() {
        assertTrue(PlotCreatorPrefabExporter.omitFestivalAirBlock(true, 0, false));
        assertTrue(PlotCreatorPrefabExporter.omitFestivalAirBlock(true, 42, true));
        assertFalse(PlotCreatorPrefabExporter.omitFestivalAirBlock(true, 42, false));
        assertFalse(PlotCreatorPrefabExporter.omitFestivalAirBlock(false, 0, false));
        assertFalse(PlotCreatorPrefabExporter.omitFestivalAirBlock(false, 0, true));
    }
}
