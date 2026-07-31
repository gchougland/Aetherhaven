package com.hexvane.aetherhaven.prefab;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import javax.annotation.Nullable;

/** World-editor marker blocks that must not appear in saved prefabs or construction paste. */
public final class EditorMarkerBlocks {
    private static final int EDITOR_EMPTY_INDEX = BlockType.getAssetMap().getIndex("Editor_Empty");

    private EditorMarkerBlocks() {}

    public static boolean isEditorEmpty(int blockId) {
        return EDITOR_EMPTY_INDEX != Integer.MIN_VALUE && blockId == EDITOR_EMPTY_INDEX;
    }

    /** Prefab air cells use block id {@code 0}; editor empty in-world markers map to that. */
    public static int normalizePrefabBlockId(int blockId) {
        return isEditorEmpty(blockId) ? 0 : blockId;
    }

    public static boolean isEditorMarkerTypeId(@Nullable String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return false;
        }
        return switch (typeId) {
            case "Editor_Empty", "Editor_Block", "Editor_Anchor" -> true;
            default -> false;
        };
    }
}
