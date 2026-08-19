package com.hexvane.aetherhaven.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.SetMusicEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.SetWeatherEffect;
import java.util.List;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Trigger volumes that come in with a prefab have to go back out again when the prefab is cleared. */
@Tag("town")
final class PrefabTriggerVolumeCleanupTest {
    @Test
    void aVolumeSittingOverTheSquareIsInsideTheReservedBoxEvenWhenItFloatsAboveTheBuild() {
        // Solid blocks only reach y 8, but the prefab reserves the full 55 high box the festival square is saved at.
        PlotFootprintRecord solid = new PlotFootprintRecord(0, 0, 0, 29, 8, 29);
        PlotFootprintRecord reserved = new PlotFootprintRecord(0, 0, 0, 29, 54, 29);
        Vector3d volumeCentre = new Vector3d(1.0, 12.5, 1.0);

        assertFalse(
            PrefabTriggerVolumeCleanup.footprintContains(solid, volumeCentre),
            "the solid footprint stops at the tallest block, so it misses volumes hanging above the square"
        );
        assertTrue(PrefabTriggerVolumeCleanup.footprintContains(reserved, volumeCentre));
    }

    @Test
    void volumesOutsideThePlotAreLeftAlone() {
        PlotFootprintRecord reserved = new PlotFootprintRecord(0, 0, 0, 29, 54, 29);

        assertFalse(PrefabTriggerVolumeCleanup.footprintContains(reserved, new Vector3d(-3.0, 10.0, 10.0)));
        assertFalse(PrefabTriggerVolumeCleanup.footprintContains(reserved, new Vector3d(10.0, 10.0, 42.0)));
        assertTrue(PrefabTriggerVolumeCleanup.footprintContains(reserved, new Vector3d(29.9, 54.0, 29.9)));
    }

    @Test
    void pastedVolumesMatchWorldNameCaseInsensitively() {
        // TriggerVolumePasteHandler stores world.getName().toLowerCase; world names are often mixed case.
        assertTrue(PrefabTriggerVolumeCleanup.sameWorld("default", "default"));
        assertTrue(PrefabTriggerVolumeCleanup.sameWorld("default", "Default"));
        assertTrue(PrefabTriggerVolumeCleanup.sameWorld("MyWorld", "myworld"));
        assertFalse(PrefabTriggerVolumeCleanup.sameWorld("default", "other"));
        assertTrue(PrefabTriggerVolumeCleanup.sameWorld("default", ""));
        assertTrue(PrefabTriggerVolumeCleanup.sameWorld(null, "default"));
    }

    @Test
    void exitEffectsKeepWeatherResetAndMusicClearAndSkipEnterRows() {
        SetWeatherEffect weatherEnter = new SetWeatherEffect();
        weatherEnter.setEventType(TriggerEventType.ENTER);
        SetWeatherEffect weatherExit = new SetWeatherEffect();
        weatherExit.setEventType(TriggerEventType.EXIT);
        SetMusicEffect musicEnter = new SetMusicEffect();
        musicEnter.setEventType(TriggerEventType.ENTER);
        SetMusicEffect musicExit = new SetMusicEffect();
        musicExit.setEventType(TriggerEventType.EXIT);

        List<TriggerEffect> chosen =
            PrefabTriggerVolumeCleanup.exitEffects(List.of(weatherEnter, weatherExit, musicEnter, musicExit));

        assertEquals(List.of(weatherExit, musicExit), chosen);
    }

    @Test
    void exitEffectsIgnoreNullRowsAndEmptyLists() {
        assertTrue(PrefabTriggerVolumeCleanup.exitEffects(List.of()).isEmpty());
        assertTrue(PrefabTriggerVolumeCleanup.exitEffects(null).isEmpty());

        SetWeatherEffect untitled = new SetWeatherEffect();
        SetWeatherEffect exit = new SetWeatherEffect();
        exit.setEventType(TriggerEventType.EXIT);
        assertEquals(List.of(exit), PrefabTriggerVolumeCleanup.exitEffects(List.of(untitled, exit)));
    }

    @Test
    void footprintUnionCoversCornerPropsFromEitherPrefab() {
        PlotFootprintRecord square = new PlotFootprintRecord(0, 0, 0, 29, 54, 29);
        PlotFootprintRecord festival = new PlotFootprintRecord(-1, 0, -1, 28, 54, 28);
        PlotFootprintRecord combined = PlotFootprintRecord.union(square, festival);
        assertEquals(-1, combined.getMinX());
        assertEquals(29, combined.getMaxX());
        assertTrue(combined.containsBlock(29, 6, 29));
        assertTrue(combined.containsBlock(-1, 6, -1));
    }
}
