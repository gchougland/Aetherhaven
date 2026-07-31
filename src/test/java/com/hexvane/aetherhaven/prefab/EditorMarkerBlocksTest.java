package com.hexvane.aetherhaven.prefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditorMarkerBlocksTest {

    @Test
    void normalizePrefabBlockId_leavesSolidBlocksAlone() {
        assertEquals(42, EditorMarkerBlocks.normalizePrefabBlockId(42));
    }

    @Test
    void isEditorMarkerTypeId_recognizesEditorMarkers() {
        assertTrue(EditorMarkerBlocks.isEditorMarkerTypeId("Editor_Empty"));
        assertTrue(EditorMarkerBlocks.isEditorMarkerTypeId("Editor_Block"));
        assertTrue(EditorMarkerBlocks.isEditorMarkerTypeId("Editor_Anchor"));
        assertFalse(EditorMarkerBlocks.isEditorMarkerTypeId("Rock_Stone"));
        assertFalse(EditorMarkerBlocks.isEditorMarkerTypeId(null));
    }
}
