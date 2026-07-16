package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockMigration;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Read-only preflight for community prefab JSON.
 *
 * <p>This deliberately does not call a Hytale prefab deserializer. Hytale's missing-asset fallback
 * registers an Unknown asset, which attempts to upgrade the world tick's asset read lock to a write
 * lock and can deadlock the game.
 */
public final class CommunityPrefabSafety {
    private static final Gson GSON = new Gson();
    private static final int MIN_PREFAB_VERSION = 8;
    private static final int MAX_PREFAB_VERSION = 8;
    private static final int LEGACY_BLOCK_ID_VERSION = 8;

    private CommunityPrefabSafety() {}

    public enum Status {
        SAFE,
        MALFORMED,
        UNSUPPORTED_VERSION,
        UNRESOLVED_ASSETS
    }

    public record Result(
        @Nonnull Status status,
        @Nonnull List<String> unresolvedAssets,
        @Nonnull List<String> referencedBlocks,
        @Nonnull List<String> referencedFluids,
        @Nonnull String detail
    ) {
        public boolean isSafe() {
            return status == Status.SAFE;
        }
    }

    @Nonnull
    public static Result validate(@Nonnull byte[] prefabBytes) {
        return validate(
            prefabBytes,
            CommunityPrefabSafety::migrateBlockKey,
            key -> {
                int index = BlockType.getAssetMap().getIndex(key);
                if (index == AssetMapWithIndexes.NOT_FOUND) {
                    return false;
                }
                BlockType asset = BlockType.getAssetMap().getAsset(index);
                return asset != null && !asset.isUnknown();
            },
            key -> {
                int index = Fluid.getAssetMap().getIndex(key);
                if (index == AssetMapWithIndexes.NOT_FOUND) {
                    return false;
                }
                Fluid asset = Fluid.getAssetMap().getAsset(index);
                return asset != null && !asset.isUnknown();
            }
        );
    }

    @Nonnull
    static Result validate(
        @Nonnull byte[] prefabBytes,
        @Nonnull Function<VersionedKey, String> blockMigration,
        @Nonnull Function<String, Boolean> blockExists,
        @Nonnull Function<String, Boolean> fluidExists
    ) {
        JsonObject root;
        try {
            root = GSON.fromJson(new String(prefabBytes, StandardCharsets.UTF_8), JsonObject.class);
        } catch (RuntimeException e) {
            return failure(Status.MALFORMED, "Prefab is not valid JSON");
        }
        if (root == null) {
            return failure(Status.MALFORMED, "Prefab JSON root is missing");
        }

        Integer version = integer(root, "version");
        if (version == null) {
            return failure(Status.MALFORMED, "Prefab version is missing");
        }
        if (version < MIN_PREFAB_VERSION || version > MAX_PREFAB_VERSION) {
            return failure(Status.UNSUPPORTED_VERSION, "Unsupported prefab version " + version);
        }
        int blockIdVersion = integer(root, "blockIdVersion") != null
            ? integer(root, "blockIdVersion")
            : LEGACY_BLOCK_ID_VERSION;

        Set<String> blocks = new LinkedHashSet<>();
        Set<String> fluids = new LinkedHashSet<>();
        Set<String> unresolved = new LinkedHashSet<>();
        String malformed = collectNames(root, "blocks", blocks);
        if (malformed == null) {
            malformed = collectNames(root, "fluids", fluids);
        }
        if (malformed != null) {
            return failure(Status.MALFORMED, malformed);
        }

        Set<String> migratedBlocks = new LinkedHashSet<>();
        for (String raw : blocks) {
            String normalized = normalizeChanceName(raw);
            String migrated;
            try {
                migrated = blockMigration.apply(new VersionedKey(blockIdVersion, normalized));
            } catch (RuntimeException e) {
                return failure(Status.MALFORMED, "Could not migrate block " + normalized);
            }
            migratedBlocks.add(migrated);
            if (!Boolean.TRUE.equals(blockExists.apply(migrated))) {
                unresolved.add(migrated);
            }
        }
        for (String raw : fluids) {
            String normalized = normalizeChanceName(raw);
            if (!Boolean.TRUE.equals(fluidExists.apply(normalized))) {
                unresolved.add(normalized);
            }
        }

        return new Result(
            unresolved.isEmpty() ? Status.SAFE : Status.UNRESOLVED_ASSETS,
            List.copyOf(unresolved),
            List.copyOf(migratedBlocks),
            List.copyOf(fluids),
            unresolved.isEmpty() ? "" : "Unresolved prefab assets: " + String.join(", ", unresolved)
        );
    }

    @Nonnull
    static String normalizeChanceName(@Nonnull String name) {
        int percent = name.indexOf('%');
        return percent >= 0 && percent < name.length() - 1 ? name.substring(percent + 1).trim() : name.trim();
    }

    @Nonnull
    private static String migrateBlockKey(@Nonnull VersionedKey versioned) {
        String key = versioned.key();
        Map<Integer, BlockMigration> migrations = BlockMigration.getAssetMap().getAssetMap();
        BlockMigration migration = migrations.get(versioned.version());
        int version = versioned.version();
        while (migration != null) {
            key = migration.getMigration(key);
            migration = migrations.get(++version);
        }
        return key;
    }

    private static String collectNames(
        @Nonnull JsonObject root,
        @Nonnull String field,
        @Nonnull Set<String> output
    ) {
        JsonElement value = root.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonArray()) {
            return "Prefab " + field + " must be an array";
        }
        JsonArray values = value.getAsJsonArray();
        for (int i = 0; i < values.size(); i++) {
            JsonElement element = values.get(i);
            if (!element.isJsonObject()) {
                return "Prefab " + field + "[" + i + "] must be an object";
            }
            JsonElement name = element.getAsJsonObject().get("name");
            if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) {
                return "Prefab " + field + "[" + i + "] has no string name";
            }
            String key = name.getAsString().trim();
            if (key.isEmpty()) {
                return "Prefab " + field + "[" + i + "] has an empty name";
            }
            output.add(key);
        }
        return null;
    }

    private static Integer integer(@Nonnull JsonObject root, @Nonnull String field) {
        JsonElement value = root.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Result failure(@Nonnull Status status, @Nonnull String detail) {
        return new Result(status, List.of(), List.of(), List.of(), detail);
    }

    record VersionedKey(int version, @Nonnull String key) {}
}
