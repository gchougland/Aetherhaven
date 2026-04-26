package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves which surface blocks the path may replace, using explicit block ids, item resource type ids, or a
 * {@code Soil_} / dirt-style heuristic when both allowlists are empty. Placing over an existing Aetherhaven path
 * (pathway, mud, grass) is always allowed for those block ids.
 */
public final class PathToolReplacePredicate {
    private PathToolReplacePredicate() {}

    public static boolean isReplaceable(
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull World world,
        int x,
        int y,
        int z
    ) {
        return isReplaceable(cfg, world.getBlockType(x, y, z));
    }

    public static boolean isReplaceable(@Nonnull AetherhavenPluginConfig cfg, @Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        String id = blockType.getId();
        Set<String> idAllow = new HashSet<>(parseCsv(cfg.getPathToolReplaceableBlockIds()));
        Set<String> rtAllow = new HashSet<>(parseCsv(cfg.getPathToolReplaceableResourceTypeIds()));
        if (idAllow.isEmpty() && rtAllow.isEmpty()) {
            return defaultHeuristicSurfaceReplaceable(id);
        }
        if (idAllow.contains(id)) {
            return true;
        }
        if (rtAllow.isEmpty()) {
            return false;
        }
        @Nullable
        Item item = Item.getAssetMap().getAsset(id);
        if (item == null) {
            return false;
        }
        ItemResourceType[] rts = item.getResourceTypes();
        if (rts == null) {
            return false;
        }
        for (ItemResourceType t : rts) {
            if (t == null) {
                continue;
            }
            if (t.id != null && rtAllow.contains(t.id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * When no explicit allowlist is set: all {@code Soil_*} (including {@code Soil_Grass}, {@code Soil_Grass_Deep},
     * {@code Soil_Pathway}, other soils, and prior path output blocks) plus name patterns such as
     * {@code *Dirt*}. Aetherhaven path output ids are not excluded — you can place over an existing path.
     */
    private static boolean defaultHeuristicSurfaceReplaceable(@Nonnull String id) {
        return id.startsWith("Soil_") || id.contains("Dirt");
    }

    @Nonnull
    public static Set<String> parseCsv(@Nullable String s) {
        Set<String> out = new HashSet<>();
        if (s == null || s.isBlank()) {
            return out;
        }
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
