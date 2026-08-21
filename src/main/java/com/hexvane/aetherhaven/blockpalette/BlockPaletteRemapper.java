package com.hexvane.aetherhaven.blockpalette;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Remaps prefab block type ids according to per-plot palette selections. Missing target variants are left unchanged;
 * mossy cobble/brick falls back to the non-mossy target when the mossy form does not exist.
 *
 * <p>Hytale registers connected/corner state variants under generated ids prefixed with {@code *}
 * (see {@code ExtraInfo.GENERATED_ID_PREFIX}), e.g. {@code *Wood_Softwood_Roof_State_Definitions_Corner_Left}.
 * Remapping preserves that form whenever the target state asset exists.
 */
public final class BlockPaletteRemapper {
    /** Same as {@code ExtraInfo.GENERATED_ID_PREFIX}; state-definition block keys use this. */
    private static final String GENERATED_ID_PREFIX = "*";

    /** Longest-first so GreyDark / RedDark / Sandstone_Red win over shorter prefixes. */
    private static final String[] WALL_COLORS = Arrays.stream(new String[] {
            "GreyDark",
            "RedDark",
            "Black",
            "Blue",
            "Cyan",
            "Green",
            "Grey",
            "Lime",
            "Ocean",
            "Orange",
            "Pink",
            "Purple",
            "Red",
            "White",
            "Yellow"
        })
        .sorted(Comparator.comparingInt(String::length).reversed())
        .toArray(String[]::new);

    private BlockPaletteRemapper() {}

    @Nonnull
    public static String remapBlockTypeId(@Nonnull String blockTypeId, @Nullable Map<String, String> categoryToPaletteId) {
        if (categoryToPaletteId == null || categoryToPaletteId.isEmpty()) {
            return blockTypeId;
        }
        String id = stripPrefabStar(blockTypeId);

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        BlockPaletteCatalog catalog = plugin != null ? plugin.getBlockPaletteCatalog() : null;

        if (catalog != null) {
            String custom = remapViaGroup(id, categoryToPaletteId, catalog);
            if (custom != null) {
                return custom;
            }
        }

        ParsedBlock parsed = parse(id);
        if (parsed == null) {
            return blockTypeId;
        }
        String selectedPaletteId = categoryToPaletteId.get(parsed.category);
        if (selectedPaletteId == null || selectedPaletteId.isBlank()) {
            return blockTypeId;
        }
        if (plugin == null) {
            return blockTypeId;
        }
        BlockPaletteDefinition def = catalog != null ? catalog.get(selectedPaletteId.trim()) : null;
        if (def == null || !def.getCategory().equals(parsed.category)) {
            return blockTypeId;
        }
        if (def.getRemapGroupId() != null) {
            return blockTypeId;
        }
        if (def.getFamilyKey().equals(parsed.familyKey)) {
            // Still allow mossy → non-mossy when staying on same family but target lacks mossy
            if (!parsed.suffix.toLowerCase(Locale.ROOT).contains("mossy")) {
                return blockTypeId;
            }
        }
        String rebuilt = rebuild(parsed, def.getFamilyKey());
        if (rebuilt == null || rebuilt.equals(id)) {
            return blockTypeId;
        }
        String resolved = resolveExisting(rebuilt);
        return resolved != null ? resolved : blockTypeId;
    }

    @Nullable
    private static String remapViaGroup(
        @Nonnull String blockTypeId,
        @Nonnull Map<String, String> categoryToPaletteId,
        @Nonnull BlockPaletteCatalog catalog
    ) {
        BlockPaletteCatalog.RemapHit hit = catalog.findRemapMatch(blockTypeId);
        if (hit == null) {
            return null;
        }
        BlockPaletteRemapGroup group = hit.group();
        String selectedPaletteId = categoryToPaletteId.get(group.getCategory());
        if (selectedPaletteId == null || selectedPaletteId.isBlank()) {
            return null;
        }
        BlockPaletteRemapGroup.Variant target = group.findVariantByPaletteId(selectedPaletteId.trim());
        if (target == null) {
            BlockPaletteDefinition def = catalog.get(selectedPaletteId.trim());
            if (def != null && group.getId().equals(def.getRemapGroupId())) {
                target = group.findVariantByPaletteId(def.getId());
            }
        }
        if (target == null) {
            return null;
        }
        if (target.paletteId().equals(hit.match().variant().paletteId())) {
            return null;
        }
        String rebuilt = target.blockPrefix() + hit.match().suffix();
        return resolveExisting(rebuilt);
    }

    public static int remapBlockId(int blockId, @Nullable Map<String, String> categoryToPaletteId) {
        if (blockId == 0 || categoryToPaletteId == null || categoryToPaletteId.isEmpty()) {
            return blockId;
        }
        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
        if (bt == null) {
            return blockId;
        }
        String remapped = remapBlockTypeId(bt.getId(), categoryToPaletteId);
        if (remapped.equals(bt.getId())) {
            return blockId;
        }
        int idx = BlockType.getAssetMap().getIndex(remapped);
        if (idx >= 0) {
            return idx;
        }
        String alt = remapped.startsWith(GENERATED_ID_PREFIX)
            ? remapped.substring(GENERATED_ID_PREFIX.length())
            : (GENERATED_ID_PREFIX + remapped);
        idx = BlockType.getAssetMap().getIndex(alt);
        return idx >= 0 ? idx : blockId;
    }

    /**
     * Resolve a remapped id to an asset that exists. State variants are registered with a leading {@code *};
     * try both starred and bare forms. When the exact state twin is missing, fall back to the parent base block.
     */
    @Nullable
    private static String resolveExisting(@Nonnull String blockTypeId) {
        String found = firstExistingForm(blockTypeId);
        if (found != null) {
            return found;
        }
        // Many colors lack every connected/corner twin; fall back to the base block id (never starred).
        String bare = stripPrefabStar(blockTypeId);
        String withoutState = stripStateDefinitionSegments(bare);
        if (!withoutState.equals(bare)) {
            found = firstExistingForm(withoutState);
            if (found != null) {
                return found;
            }
        }
        String withoutMossy = stripMossySegments(withoutState);
        if (!withoutMossy.equals(withoutState)) {
            found = firstExistingForm(withoutMossy);
            if (found != null) {
                return found;
            }
        }
        String withoutBoth = stripStateDefinitionSegments(stripMossySegments(bare));
        if (!withoutBoth.equals(bare)) {
            return firstExistingForm(withoutBoth);
        }
        return null;
    }

    /**
     * Prefer the exact id, then the starred generated-state twin, then the bare parent id.
     */
    @Nullable
    private static String firstExistingForm(@Nonnull String blockTypeId) {
        if (assetExists(blockTypeId)) {
            return blockTypeId;
        }
        String bare = stripPrefabStar(blockTypeId);
        if (blockTypeId.startsWith(GENERATED_ID_PREFIX)) {
            if (assetExists(bare)) {
                return bare;
            }
            return null;
        }
        String starred = GENERATED_ID_PREFIX + bare;
        if (assetExists(starred)) {
            return starred;
        }
        return null;
    }

    private static boolean assetExists(@Nonnull String blockTypeId) {
        return BlockType.getAssetMap().getAsset(blockTypeId) != null;
    }

    /**
     * Drop {@code _State_Definitions…} / {@code _State_Definition…} tails (connected walls, stair corners, roofs).
     */
    @Nonnull
    static String stripStateDefinitionSegments(@Nonnull String id) {
        String bare = stripPrefabStar(id);
        int idx = indexOfStateDefinition(bare);
        if (idx < 0) {
            return bare;
        }
        return bare.substring(0, idx);
    }

    private static int indexOfStateDefinition(@Nonnull String id) {
        int a = id.indexOf("_State_Definitions");
        int b = id.indexOf("_State_Definition");
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    /** Drop {@code _Mossy} (and keep Half/Stairs etc.) when the mossy twin does not exist for the target family. */
    @Nonnull
    static String stripMossySegments(@Nonnull String id) {
        String out = stripPrefabStar(id);
        out = out.replace("_Mossy_Half", "_Half");
        out = out.replace("_Mossy_Stairs", "_Stairs");
        out = out.replace("_Mossy_Slab", "_Slab");
        if (out.endsWith("_Mossy")) {
            out = out.substring(0, out.length() - "_Mossy".length());
        } else {
            out = out.replace("_Mossy_", "_");
        }
        return out;
    }

    @Nonnull
    static String stripPrefabStar(@Nonnull String id) {
        return id.startsWith(GENERATED_ID_PREFIX) ? id.substring(GENERATED_ID_PREFIX.length()) : id;
    }

    @Nullable
    static ParsedBlock parse(@Nonnull String rawId) {
        String id = stripPrefabStar(rawId);
        ParsedBlock walls = parseWalls(id);
        if (walls != null) {
            return walls;
        }
        ParsedBlock roofs = parseRoofs(id);
        if (roofs != null) {
            return roofs;
        }
        ParsedBlock cloth = parseCloth(id);
        if (cloth != null) {
            return cloth;
        }
        ParsedBlock cobble = parseCobble(id);
        if (cobble != null) {
            return cobble;
        }
        ParsedBlock bricks = parseBricks(id);
        if (bricks != null) {
            return bricks;
        }
        ParsedBlock trunks = parseTrunks(id);
        if (trunks != null) {
            return trunks;
        }
        return parsePlanks(id);
    }

    @Nullable
    private static ParsedBlock parseWalls(@Nonnull String id) {
        String prefix = "Wood_Village_Wall_";
        if (!id.startsWith(prefix)) {
            return null;
        }
        String rest = id.substring(prefix.length());
        if (rest.isEmpty()) {
            return null;
        }
        // Numbered / special walls are not palette-swappable.
        if (Character.isDigit(rest.charAt(0)) || rest.startsWith("Beige")) {
            return null;
        }

        // Wood_Village_Wall_N_Blue / Wood_Village_Wall_U_Red
        if (rest.startsWith("N_") || rest.startsWith("U_")) {
            String mode = rest.substring(0, 2); // N_ or U_
            String color = rest.substring(2);
            if (isWallColor(color)) {
                return new ParsedBlock(
                    BlockPaletteConstants.CATEGORY_WALLS, color, mode + color, RoofKind.NONE, WallForm.NU_PREFIX);
            }
            return null;
        }

        // Natural: Full / Full_State_Definitions_*
        if (rest.startsWith("Full")) {
            return new ParsedBlock(
                BlockPaletteConstants.CATEGORY_WALLS, "", rest, RoofKind.NONE, WallForm.STANDARD);
        }

        // Colored: Blue, Blue_Full..., Blue_Middle, Blue_Full_State_Definitions_*
        for (String color : WALL_COLORS) {
            if (rest.equals(color)) {
                return new ParsedBlock(
                    BlockPaletteConstants.CATEGORY_WALLS, color, "", RoofKind.NONE, WallForm.STANDARD);
            }
            String withUnderscore = color + "_";
            if (rest.startsWith(withUnderscore)) {
                String suffix = rest.substring(withUnderscore.length());
                return new ParsedBlock(
                    BlockPaletteConstants.CATEGORY_WALLS, color, suffix, RoofKind.NONE, WallForm.STANDARD);
            }
        }
        return null;
    }

    private static boolean isWallColor(@Nonnull String token) {
        for (String color : WALL_COLORS) {
            if (color.equals(token)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ParsedBlock parseCloth(@Nonnull String id) {
        String prefix = "Cloth_Block_Wool_";
        if (!id.startsWith(prefix)) {
            return null;
        }
        String rest = id.substring(prefix.length());
        String[] markers = {"_Stairs", "_Half"};
        for (String marker : markers) {
            int idx = rest.lastIndexOf(marker);
            if (idx > 0 && rest.indexOf(marker) == idx) {
                // Prefer end-anchored marker, allow trailing state text after marker
                if (rest.endsWith(marker) || rest.contains(marker + "_")) {
                    int at = rest.indexOf(marker);
                    String familyKey = rest.substring(0, at);
                    String suffix = rest.substring(at + 1); // Stairs... or Half...
                    if (!familyKey.isEmpty()) {
                        return new ParsedBlock(
                            BlockPaletteConstants.CATEGORY_CLOTH, familyKey, suffix, RoofKind.NONE, WallForm.NONE);
                    }
                }
            }
        }
        if (rest.isEmpty()) {
            return null;
        }
        return new ParsedBlock(BlockPaletteConstants.CATEGORY_CLOTH, rest, "", RoofKind.NONE, WallForm.NONE);
    }

    @Nullable
    private static ParsedBlock parseCobble(@Nonnull String id) {
        if (!id.startsWith("Rock_") || !id.contains("_Cobble") || id.contains("_Cobble_Roof")) {
            return null;
        }
        int cobbleIdx = id.indexOf("_Cobble");
        String type = id.substring("Rock_".length(), cobbleIdx);
        String after = id.substring(cobbleIdx + "_Cobble".length());
        String suffix = after.startsWith("_") ? after.substring(1) : after;
        return new ParsedBlock(BlockPaletteConstants.CATEGORY_COBBLE, type, suffix, RoofKind.NONE, WallForm.NONE);
    }

    @Nullable
    private static ParsedBlock parseBricks(@Nonnull String id) {
        if (!id.startsWith("Rock_") || !id.contains("_Brick") || id.contains("_Brick_Roof")) {
            return null;
        }
        int brickIdx = id.indexOf("_Brick");
        String type = id.substring("Rock_".length(), brickIdx);
        String after = id.substring(brickIdx + "_Brick".length());
        String suffix = after.startsWith("_") ? after.substring(1) : after;
        return new ParsedBlock(BlockPaletteConstants.CATEGORY_BRICKS, type, suffix, RoofKind.NONE, WallForm.NONE);
    }

    @Nullable
    private static ParsedBlock parseTrunks(@Nonnull String id) {
        if (!id.startsWith("Wood_")) {
            return null;
        }
        String[] markers = {
            "_Trunk_Full",
            "_Trunk_Stairs",
            "_Trunk_Half",
            "_Trunk",
            "_Branch_Corner",
            "_Branch_Long",
            "_Branch_Short",
            "_Roots"
        };
        for (String marker : markers) {
            int idx = id.indexOf(marker);
            if (idx <= "Wood_".length()) {
                continue;
            }
            String species = id.substring("Wood_".length(), idx);
            if (species.isEmpty()) {
                continue;
            }
            String after = id.substring(idx + marker.length());
            String suffix = marker.substring(1) + after;
            return new ParsedBlock(BlockPaletteConstants.CATEGORY_TRUNKS, species, suffix, RoofKind.NONE, WallForm.NONE);
        }
        return null;
    }

    @Nullable
    private static ParsedBlock parsePlanks(@Nonnull String id) {
        if (!id.startsWith("Wood_")) {
            return null;
        }
        if (id.contains("_Roof") || id.contains("_Trunk") || id.contains("_Branch") || id.contains("_Village_")) {
            return null;
        }
        String[] markers = {
            "_Planks_Half",
            "_Planks",
            "_Fence_Gate",
            "_Fence",
            "_Stairs",
            "_Beam",
            "_Ornate",
            "_Decorative"
        };
        for (String marker : markers) {
            int idx = id.indexOf(marker);
            if (idx <= "Wood_".length()) {
                continue;
            }
            String species = id.substring("Wood_".length(), idx);
            if (species.isEmpty()) {
                continue;
            }
            // Require marker at a segment boundary (already via _prefix) and keep trailing state text.
            String after = id.substring(idx + marker.length());
            String suffix = marker.substring(1) + after;
            return new ParsedBlock(BlockPaletteConstants.CATEGORY_PLANKS, species, suffix, RoofKind.NONE, WallForm.NONE);
        }
        return null;
    }

    @Nullable
    private static ParsedBlock parseRoofs(@Nonnull String id) {
        if (id.startsWith("Wood_") && id.contains("_Roof")) {
            int roofIdx = id.indexOf("_Roof");
            String species = id.substring("Wood_".length(), roofIdx);
            String after = id.substring(roofIdx + "_Roof".length());
            String shape = after.startsWith("_") ? after.substring(1) : after;
            if (species.isEmpty()) {
                return null;
            }
            return new ParsedBlock(
                BlockPaletteConstants.CATEGORY_ROOFS, "wood:" + species, shape, RoofKind.WOOD, WallForm.NONE);
        }
        if (id.startsWith("Cloth_Roof_")) {
            String rest = id.substring("Cloth_Roof_".length());
            // Known solid colors / hide first (longest), remainder is shape / state text.
            String[] clothColors = {
                "Yellow", "Orange", "White", "Green", "Blue", "Red", "Hide", "Black", "Cyan", "Pink", "Purple", "Gray"
            };
            Arrays.sort(clothColors, Comparator.comparingInt(String::length).reversed());
            for (String color : clothColors) {
                if (rest.equals(color)) {
                    return new ParsedBlock(
                        BlockPaletteConstants.CATEGORY_ROOFS, "cloth:" + color, "", RoofKind.CLOTH, WallForm.NONE);
                }
                if (rest.startsWith(color + "_")) {
                    return new ParsedBlock(
                        BlockPaletteConstants.CATEGORY_ROOFS,
                        "cloth:" + color,
                        rest.substring(color.length() + 1),
                        RoofKind.CLOTH,
                        WallForm.NONE);
                }
            }
            return new ParsedBlock(
                BlockPaletteConstants.CATEGORY_ROOFS, "cloth:" + rest, "", RoofKind.CLOTH, WallForm.NONE);
        }
        if (id.startsWith("Rock_") && id.contains("_Cobble_Roof")) {
            int idx = id.indexOf("_Cobble_Roof");
            String type = id.substring("Rock_".length(), idx);
            String after = id.substring(idx + "_Cobble_Roof".length());
            String shape = after.startsWith("_") ? after.substring(1) : after;
            return new ParsedBlock(
                BlockPaletteConstants.CATEGORY_ROOFS, "cobble_roof:" + type, shape, RoofKind.COBBLE, WallForm.NONE);
        }
        if (id.startsWith("Rock_") && id.contains("_Brick_Roof")) {
            int idx = id.indexOf("_Brick_Roof");
            String type = id.substring("Rock_".length(), idx);
            String after = id.substring(idx + "_Brick_Roof".length());
            String shape = after.startsWith("_") ? after.substring(1) : after;
            return new ParsedBlock(
                BlockPaletteConstants.CATEGORY_ROOFS, "brick_roof:" + type, shape, RoofKind.BRICK, WallForm.NONE);
        }
        return null;
    }

    @Nullable
    static String rebuild(@Nonnull ParsedBlock parsed, @Nonnull String newFamilyKey) {
        return switch (parsed.category) {
            case BlockPaletteConstants.CATEGORY_WALLS -> rebuildWalls(parsed, newFamilyKey);
            case BlockPaletteConstants.CATEGORY_CLOTH -> rebuildCloth(newFamilyKey, parsed.suffix);
            case BlockPaletteConstants.CATEGORY_COBBLE -> rebuildRock("Cobble", newFamilyKey, parsed.suffix);
            case BlockPaletteConstants.CATEGORY_BRICKS -> rebuildRock("Brick", newFamilyKey, parsed.suffix);
            case BlockPaletteConstants.CATEGORY_TRUNKS -> "Wood_" + newFamilyKey + "_" + parsed.suffix;
            case BlockPaletteConstants.CATEGORY_PLANKS -> "Wood_" + newFamilyKey + "_" + parsed.suffix;
            case BlockPaletteConstants.CATEGORY_ROOFS -> rebuildRoof(parsed, newFamilyKey);
            default -> null;
        };
    }

    @Nonnull
    private static String rebuildWalls(@Nonnull ParsedBlock parsed, @Nonnull String familyKey) {
        if (parsed.wallForm == WallForm.NU_PREFIX) {
            if (familyKey.isEmpty()) {
                return null;
            }
            String mode = parsed.suffix.startsWith("U_") ? "U_" : "N_";
            return "Wood_Village_Wall_" + mode + familyKey;
        }
        if (familyKey.isEmpty()) {
            if (parsed.suffix.isEmpty()) {
                return "Wood_Village_Wall_Full";
            }
            return "Wood_Village_Wall_" + parsed.suffix;
        }
        if (parsed.suffix.isEmpty()) {
            return "Wood_Village_Wall_" + familyKey;
        }
        return "Wood_Village_Wall_" + familyKey + "_" + parsed.suffix;
    }

    @Nonnull
    private static String rebuildCloth(@Nonnull String familyKey, @Nonnull String suffix) {
        if (suffix.isEmpty()) {
            return "Cloth_Block_Wool_" + familyKey;
        }
        return "Cloth_Block_Wool_" + familyKey + "_" + suffix;
    }

    @Nonnull
    private static String rebuildRock(@Nonnull String kind, @Nonnull String type, @Nonnull String suffix) {
        if (suffix.isEmpty()) {
            return "Rock_" + type + "_" + kind;
        }
        return "Rock_" + type + "_" + kind + "_" + suffix;
    }

    @Nullable
    private static String rebuildRoof(@Nonnull ParsedBlock parsed, @Nonnull String newFamilyKey) {
        RoofKind targetKind = roofKindFromFamily(newFamilyKey);
        if (targetKind == RoofKind.NONE) {
            return null;
        }
        // Cloth roofs stay on their own look; everything else (wood, cobble, brick) can swap freely.
        if (parsed.roofKind == RoofKind.CLOTH || targetKind == RoofKind.CLOTH) {
            if (parsed.roofKind != targetKind) {
                return null;
            }
        }
        String key = stripRoofFamilyPrefix(newFamilyKey);
        String shape = parsed.suffix;
        return switch (targetKind) {
            case WOOD -> shape.isEmpty() ? "Wood_" + key + "_Roof" : "Wood_" + key + "_Roof_" + shape;
            case CLOTH -> shape.isEmpty() ? "Cloth_Roof_" + key : "Cloth_Roof_" + key + "_" + shape;
            case COBBLE ->
                shape.isEmpty() ? "Rock_" + key + "_Cobble_Roof" : "Rock_" + key + "_Cobble_Roof_" + shape;
            case BRICK ->
                shape.isEmpty() ? "Rock_" + key + "_Brick_Roof" : "Rock_" + key + "_Brick_Roof_" + shape;
            default -> null;
        };
    }

    @Nonnull
    private static RoofKind roofKindFromFamily(@Nonnull String familyKey) {
        String k = familyKey.toLowerCase(Locale.ROOT);
        if (k.startsWith("wood:")) {
            return RoofKind.WOOD;
        }
        if (k.startsWith("cloth:")) {
            return RoofKind.CLOTH;
        }
        if (k.startsWith("cobble_roof:")) {
            return RoofKind.COBBLE;
        }
        if (k.startsWith("brick_roof:")) {
            return RoofKind.BRICK;
        }
        return RoofKind.NONE;
    }

    @Nonnull
    private static String stripRoofFamilyPrefix(@Nonnull String familyKey) {
        int colon = familyKey.indexOf(':');
        return colon >= 0 ? familyKey.substring(colon + 1) : familyKey;
    }

    private enum RoofKind {
        NONE,
        WOOD,
        CLOTH,
        COBBLE,
        BRICK
    }

    private enum WallForm {
        NONE,
        STANDARD,
        NU_PREFIX
    }

    record ParsedBlock(
        @Nonnull String category,
        @Nonnull String familyKey,
        @Nonnull String suffix,
        @Nonnull RoofKind roofKind,
        @Nonnull WallForm wallForm
    ) {}
}
