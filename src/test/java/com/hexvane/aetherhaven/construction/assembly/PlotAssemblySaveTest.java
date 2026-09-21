package com.hexvane.aetherhaven.construction.assembly;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hexvane.aetherhaven.town.TownManager;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PlotAssemblySaveTest {
    @Test void idleAssemblyDoesNotTouchTownPersistence() throws Exception {
        var flush = PlotAssemblyService.class.getDeclaredMethod("flushDirtyAssemblyTowns", TownManager.class, Map.class);
        flush.setAccessible(true);
        // No assembly changes means no calls to the manager at all. A null sentinel
        // catches the old unconditional flush without booting a Hytale world/server.
        assertDoesNotThrow(() -> flush.invoke(null, null, Map.of()));
    }
}
