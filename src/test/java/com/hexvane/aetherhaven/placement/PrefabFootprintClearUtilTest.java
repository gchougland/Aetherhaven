package com.hexvane.aetherhaven.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PrefabFootprintClearUtilTest {

    @Test
    void footprintAabbBlockCount_isFullBoxVolume() {
        PlotFootprintRecord fp = new PlotFootprintRecord(0, 0, 0, 9, 4, 9);
        assertEquals(500, PrefabFootprintClearUtil.footprintAabbBlockCount(fp));
    }

    @Test
    void sparseClearCanTargetFewerCellsThanAabbClear() {
        PlotFootprintRecord fp = new PlotFootprintRecord(0, 0, 0, 10, 10, 10);
        int aabbCells = PrefabFootprintClearUtil.footprintAabbBlockCount(fp);
        int sparsePrefabListedCells = 8;
        assertTrue(sparsePrefabListedCells < aabbCells);
    }
}
