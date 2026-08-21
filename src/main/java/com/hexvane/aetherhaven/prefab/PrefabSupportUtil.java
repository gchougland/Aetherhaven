package com.hexvane.aetherhaven.prefab;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Keeps deco support on prefab export/paste for blocks that need neighbor support (leaves, branches, etc.). */
public final class PrefabSupportUtil {
    private PrefabSupportUtil() {}

    public static boolean requiresNeighborSupport(@Nullable BlockType blockType, int rotation) {
        if (blockType == null || blockType.isUnknown()) {
            return false;
        }
        Map<?, ?> required = blockType.getSupport(rotation);
        return required != null && !required.isEmpty();
    }

    /** True when the block type participates in support physics (face support and/or max distance). */
    public static boolean needsSupportPhysics(@Nullable BlockType blockType, int rotation) {
        if (blockType == null || blockType.isUnknown()) {
            return false;
        }
        return blockType.hasSupport() || requiresNeighborSupport(blockType, rotation);
    }

    public static int effectiveSupportForExport(@Nullable BlockType blockType, int rotation, int worldSupport) {
        if (worldSupport != BlockPhysics.NULL_SUPPORT) {
            return worldSupport;
        }
        if (requiresNeighborSupport(blockType, rotation)) {
            return BlockPhysics.IS_DECO_VALUE;
        }
        return BlockPhysics.NULL_SUPPORT;
    }

    public static int effectiveSupportForPaste(int prefabSupport, @Nullable BlockType blockType, int rotation) {
        if (prefabSupport != BlockPhysics.NULL_SUPPORT) {
            return prefabSupport;
        }
        if (requiresNeighborSupport(blockType, rotation)) {
            return BlockPhysics.IS_DECO_VALUE;
        }
        return BlockPhysics.NULL_SUPPORT;
    }

    public static void applyEffectiveSupport(
        @Nonnull Store<ChunkStore> store,
        @Nonnull Ref<ChunkStore> section,
        int bx,
        int by,
        int bz,
        int prefabSupport,
        @Nullable BlockType blockType,
        int rotation
    ) {
        int support = effectiveSupportForPaste(prefabSupport, blockType, rotation);
        if (support != BlockPhysics.NULL_SUPPORT) {
            BlockPhysics.setSupportValue(store, section, bx, by, bz, support);
        }
    }

    /**
     * Creative-style no-physics for incremental assembly: mark deco so floating trunks/leaves do not break
     * before neighbors exist. Completion force-paste should use {@link #applyEffectiveSupport} instead.
     */
    public static void markDecoForAssemblyPaste(
        @Nonnull Store<ChunkStore> store,
        @Nonnull Ref<ChunkStore> section,
        int bx,
        int by,
        int bz,
        @Nullable BlockType blockType,
        int rotation
    ) {
        if (!needsSupportPhysics(blockType, rotation)) {
            return;
        }
        BlockPhysics.markDeco(store, section, bx, by, bz);
    }
}
