package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.prefab.PrefabJsonStream;
import com.hexvane.aetherhaven.prefab.PrefabJsonStream.Scan;
import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockMigration;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Read-only preflight for community prefab JSON.
 *
 * <p>This deliberately does not call a Hytale prefab deserializer. Hytale's missing-asset fallback
 * registers an Unknown asset, which attempts to upgrade the world tick's asset read lock to a write
 * lock and can deadlock the game.
 */
public final class CommunityPrefabSafety {
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
        return validate(PrefabJsonStream.scan(prefabBytes));
    }

    @Nonnull
    public static Result validate(@Nonnull Path prefabPath) {
        Scan scan;
        try {
            scan = PrefabJsonStream.scan(prefabPath);
        } catch (IOException e) {
            return failure(Status.MALFORMED, "Prefab is not valid JSON");
        }
        return validate(scan);
    }

    @Nonnull
    static Result validate(@Nonnull Scan scan) {
        return validate(
            scan,
            CommunityPrefabSafety::migrateBlockKey,
            CommunityPrefabSafety::blockExists,
            CommunityPrefabSafety::fluidExists
        );
    }

    @Nonnull
    static Result validate(
        @Nonnull byte[] prefabBytes,
        @Nonnull Function<VersionedKey, String> blockMigration,
        @Nonnull Function<String, Boolean> blockExists,
        @Nonnull Function<String, Boolean> fluidExists
    ) {
        return validate(PrefabJsonStream.scan(prefabBytes), blockMigration, blockExists, fluidExists);
    }

    @Nonnull
    static Result validate(
        @Nonnull Scan scan,
        @Nonnull Function<VersionedKey, String> blockMigration,
        @Nonnull Function<String, Boolean> blockExists,
        @Nonnull Function<String, Boolean> fluidExists
    ) {
        if (isRootParseError(scan.malformed())) {
            return failure(Status.MALFORMED, scan.malformed());
        }
        Integer version = scan.version();
        if (version == null) {
            return failure(Status.MALFORMED, "Prefab version is missing");
        }
        if (version < MIN_PREFAB_VERSION || version > MAX_PREFAB_VERSION) {
            return failure(Status.UNSUPPORTED_VERSION, "Unsupported prefab version " + version);
        }
        if (scan.malformed() != null) {
            return failure(Status.MALFORMED, scan.malformed());
        }
        int blockIdVersion = scan.blockIdVersion() != null ? scan.blockIdVersion() : LEGACY_BLOCK_ID_VERSION;

        Set<String> unresolved = new LinkedHashSet<>();
        Set<String> migratedBlocks = new LinkedHashSet<>();
        for (String raw : scan.blockNames()) {
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
        for (String raw : scan.fluidNames()) {
            String normalized = normalizeChanceName(raw);
            if (!Boolean.TRUE.equals(fluidExists.apply(normalized))) {
                unresolved.add(normalized);
            }
        }

        return new Result(
            unresolved.isEmpty() ? Status.SAFE : Status.UNRESOLVED_ASSETS,
            List.copyOf(unresolved),
            List.copyOf(migratedBlocks),
            List.copyOf(scan.fluidNames()),
            unresolved.isEmpty() ? "" : "Unresolved prefab assets: " + String.join(", ", unresolved)
        );
    }

    @Nonnull
    static String normalizeChanceName(@Nonnull String name) {
        int percent = name.indexOf('%');
        return percent >= 0 && percent < name.length() - 1 ? name.substring(percent + 1).trim() : name.trim();
    }

    private static boolean blockExists(@Nonnull String key) {
        int index = BlockType.getAssetMap().getIndex(key);
        if (index == AssetMapWithIndexes.NOT_FOUND) {
            return false;
        }
        BlockType asset = BlockType.getAssetMap().getAsset(index);
        return asset != null && !asset.isUnknown();
    }

    private static boolean fluidExists(@Nonnull String key) {
        int index = Fluid.getAssetMap().getIndex(key);
        if (index == AssetMapWithIndexes.NOT_FOUND) {
            return false;
        }
        Fluid asset = Fluid.getAssetMap().getAsset(index);
        return asset != null && !asset.isUnknown();
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

    private static boolean isRootParseError(@Nullable String detail) {
        return "Prefab is not valid JSON".equals(detail) || "Prefab JSON root is missing".equals(detail);
    }

    private static Result failure(@Nonnull Status status, @Nonnull String detail) {
        return new Result(status, List.of(), List.of(), List.of(), detail);
    }

    record VersionedKey(int version, @Nonnull String key) {}
}
