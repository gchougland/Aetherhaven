package com.hexvane.aetherhaven.prefab;

import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** World-space prefab holograms via vanilla {@link PersistentPrefabPreview}. */
public final class AetherhavenWorldPrefabPreview {
    public static final int ALL_LAYERS = Integer.MAX_VALUE;

    private static final int DEFAULT_BIOME_TINT =
        ColorParseUtil.colorToARGBInt(
            com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSessionManager.DEFAULT_TINT
        ) & 16777215;
    private static final int DEFAULT_WATER_TINT =
        ColorParseUtil.colorToARGBInt(Environment.getUnknownFor("").getWaterTint()) & 16777215;

    private AetherhavenWorldPrefabPreview() {}

    @Nonnull
    public static Rotation3f rotationFromYaw(@Nonnull Rotation yaw) {
        return new Rotation3f(0f, (float) yaw.getRadians(), 0f);
    }

    @Nullable
    public static Ref<EntityStore> spawn(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull Rotation3f rotation,
        @Nonnull String prefabPathKey,
        int rotationSteps,
        int visibleLayerCount
    ) {
        String browsableKey = BrowsablePrefabKeyBridge.resolveBrowsableKey(prefabPathKey, rotationSteps);
        if (browsableKey == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        Tint tint = sampleTint(world, MathUtil.floor(position.x), MathUtil.floor(position.y), MathUtil.floor(position.z));
        return PersistentPrefabPreview.spawn(
            store,
            position,
            rotation,
            browsableKey,
            visibleLayerCount,
            tint.biomeTint,
            tint.waterTint
        );
    }

    @Nullable
    public static Ref<EntityStore> spawnAtBlockCorner(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i blockCorner,
        @Nonnull Rotation3f rotation,
        @Nonnull String prefabPathKey,
        int rotationSteps,
        int visibleLayerCount
    ) {
        return spawn(
            store,
            new Vector3d(blockCorner.x, blockCorner.y, blockCorner.z),
            rotation,
            prefabPathKey,
            rotationSteps,
            visibleLayerCount
        );
    }

    public static void updatePosition(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Vector3d position,
        @Nonnull Rotation3f rotation
    ) {
        TransformComponentUtil.replacePreservingChunk(ref, store, position, rotation);
    }

    public static void updatePositionAtBlockCorner(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Vector3i blockCorner,
        @Nonnull Rotation3f rotation
    ) {
        updatePosition(store, ref, new Vector3d(blockCorner.x, blockCorner.y, blockCorner.z), rotation);
    }

    public static void updateLayers(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, int visibleLayerCount) {
        if (!ref.isValid()) {
            return;
        }
        PersistentPrefabPreview.updateLayers(store, ref, visibleLayerCount);
    }

    public static void remove(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (ref.isValid()) {
            PersistentPrefabPreview.remove(store, ref);
        }
    }

    public static void clearAll(@Nonnull Store<EntityStore> store, @Nonnull List<Ref<EntityStore>> refs) {
        for (Ref<EntityStore> ref : refs) {
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        refs.clear();
    }

    private record Tint(int biomeTint, int waterTint) {}

    @Nonnull
    private static Tint sampleTint(@Nonnull World world, int x, int y, int z) {
        BlockChunk blockChunk = ChunkSectionBlockUtil.blockChunkAt(world, x, z);
        if (blockChunk == null) {
            return new Tint(DEFAULT_BIOME_TINT, DEFAULT_WATER_TINT);
        }
        int water = DEFAULT_WATER_TINT;
        Environment environment = Environment.getAssetMap().getAsset(blockChunk.getEnvironment(x, y, z));
        if (environment != null && environment.getWaterTint() != null) {
            com.hypixel.hytale.protocol.Color waterColor = environment.getWaterTint();
            water = (waterColor.red & 255) << 16 | (waterColor.green & 255) << 8 | waterColor.blue & 255;
        }
        return new Tint(blockChunk.getTint(x, z), water);
    }
}
