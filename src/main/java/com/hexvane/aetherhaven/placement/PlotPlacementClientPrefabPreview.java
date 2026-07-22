package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.community.CommunityPrefabSafety;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.packets.buildertools.ClipboardEntityChange;
import com.hypixel.hytale.protocol.packets.interface_.BlockChange;
import com.hypixel.hytale.protocol.packets.interface_.EditorBlocksChange;
import com.hypixel.hytale.protocol.packets.interface_.FluidChange;
import com.hypixel.hytale.protocol.packets.player.HideTriggerVolumePastePrefabPreview;
import com.hypixel.hytale.protocol.packets.player.ShowTriggerVolumePastePrefabPreview;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.prefab.selection.standard.RotateBlockMode;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Client-side world prefab ghost for plot placement ({@link ShowTriggerVolumePastePrefabPreview}), matching Trigger
 * Volumes paste preview: full clipboard once per construction/rotation, position-only updates when nudging.
 */
public final class PlotPlacementClientPrefabPreview {
    private static final int DEFAULT_BIOME_TINT =
        ColorParseUtil.colorToARGBInt(com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSessionManager.DEFAULT_TINT)
            & 16777215;
    private static final int DEFAULT_WATER_TINT =
        ColorParseUtil.colorToARGBInt(Environment.getUnknownFor("").getWaterTint()) & 16777215;

    private PlotPlacementClientPrefabPreview() {}

    /** Cached block/fluid/entity arrays for one construction + rotation (session lifetime). */
    public record Payload(
        @Nullable BlockChange[] blocksChange,
        @Nullable FluidChange[] fluidsChange,
        @Nullable ClipboardEntityChange[] entityChanges
    ) {}

    public static void hide(@Nonnull PlayerRef playerRef) {
        playerRef.getPacketHandler().write(new HideTriggerVolumePastePrefabPreview());
    }

    /**
     * Sends full prefab data at {@code prefabOriginWorld} (floored). Updates {@link PlotPlacementSession} payload cache.
     *
     * @return {@code false} when prefab could not be loaded (caller should {@link #hide})
     */
    public static boolean sendFull(
        @Nonnull PlayerRef playerRef,
        @Nonnull String prefabPathKey,
        int rotationSteps,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull PlotPlacementSession session
    ) {
        Payload payload = resolvePayload(prefabPathKey, rotationSteps, session);
        if (payload == null) {
            return false;
        }
        writeShow(playerRef, flooredPosition(prefabOriginWorld), payload);
        return true;
    }

    /**
     * Moves an active client ghost without resending block data.
     *
     * @return {@code true} when a packet was sent
     */
    public static boolean sendPositionOnly(@Nonnull PlayerRef playerRef, @Nonnull Vector3i prefabOriginWorld) {
        Vector3f pos = flooredPosition(prefabOriginWorld);
        ShowTriggerVolumePastePrefabPreview packet = new ShowTriggerVolumePastePrefabPreview();
        packet.position = pos;
        applyTintFromPlayerPosition(playerRef, packet);
        playerRef.getPacketHandler().write(packet);
        return true;
    }

    @Nonnull
    public static Vector3i flooredOrigin(@Nonnull Vector3i prefabOriginWorld) {
        return new Vector3i(
            MathUtil.floor(prefabOriginWorld.x),
            MathUtil.floor(prefabOriginWorld.y),
            MathUtil.floor(prefabOriginWorld.z)
        );
    }

    @Nonnull
    public static String previewDataKey(@Nonnull String constructionId, int rotationSteps) {
        return constructionId + '|' + ((rotationSteps % 4 + 4) % 4);
    }

    public static void clearSessionCache(@Nonnull PlotPlacementSession session) {
        session.clearClientPrefabPreviewCache();
    }

    @Nullable
    private static Payload resolvePayload(
        @Nonnull String prefabPathKey,
        int rotationSteps,
        @Nonnull PlotPlacementSession session
    ) {
        int steps = (rotationSteps % 4 + 4) % 4;
        String pathKey = prefabPathKey;
        Payload cached = session.getClientPrefabPreviewPayload();
        if (cached != null && steps == session.getClientPrefabPreviewRotationSteps() && pathKey.equals(session.getClientPrefabPreviewPathKey())) {
            return cached;
        }
        Path resolved = PrefabResolveUtil.resolvePrefabPath(prefabPathKey);
        if (resolved == null) {
            return null;
        }
        try {
            if (!CommunityPrefabSafety.validate(Files.readAllBytes(resolved)).isSafe()) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        BlockSelection selection;
        try {
            selection = PrefabStore.get().getPrefab(resolved);
        } catch (Exception e) {
            return null;
        }
        if (selection == null) {
            return null;
        }
        BlockSelection rotated = selection.cloneSelection();
        if (steps != 0) {
            rotated = rotated.rotate(Axis.Y, 90 * steps, RotateBlockMode.ALL);
        }
        EditorBlocksChange editor = rotated.toPacket();
        Payload payload =
            new Payload(editor.blocksChange, editor.fluidsChange, editor.entityChanges);
        session.setClientPrefabPreviewCache(pathKey, steps, payload);
        return payload;
    }

    private static void writeShow(@Nonnull PlayerRef playerRef, @Nonnull Vector3f position, @Nonnull Payload payload) {
        ShowTriggerVolumePastePrefabPreview packet = new ShowTriggerVolumePastePrefabPreview();
        packet.position = position;
        packet.blocksChange = payload.blocksChange();
        packet.fluidsChange = payload.fluidsChange();
        packet.entityChanges = payload.entityChanges();
        applyTintFromPlayerPosition(playerRef, packet);
        playerRef.getPacketHandler().write(packet);
    }

    @Nonnull
    private static Vector3f flooredPosition(@Nonnull Vector3i prefabOriginWorld) {
        return new Vector3f(
            (float) MathUtil.floor(prefabOriginWorld.x),
            (float) MathUtil.floor(prefabOriginWorld.y),
            (float) MathUtil.floor(prefabOriginWorld.z)
        );
    }

    private static void applyTintFromPlayerPosition(
        @Nonnull PlayerRef playerRef,
        @Nonnull ShowTriggerVolumePastePrefabPreview packet
    ) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            packet.biomeTint = DEFAULT_BIOME_TINT;
            packet.waterTint = DEFAULT_WATER_TINT;
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        Vector3d pos = playerRef.getTransform().getPosition();
        int x = MathUtil.floor(pos.x);
        int y = MathUtil.floor(pos.y);
        int z = MathUtil.floor(pos.z);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getNonTickingChunk(chunkIndex);
        if (chunk != null && chunk.getBlockChunk() != null) {
            BlockChunk blockChunk = chunk.getBlockChunk();
            packet.biomeTint = blockChunk.getTint(x, z);
            int envId = blockChunk.getEnvironment(x, y, z);
            Environment environment = Environment.getAssetMap().getAsset(envId);
            if (environment != null) {
                com.hypixel.hytale.protocol.Color waterColor = environment.getWaterTint();
                if (waterColor != null) {
                    packet.waterTint =
                        (waterColor.red & 255) << 16 | (waterColor.green & 255) << 8 | waterColor.blue & 255;
                    return;
                }
            }
            packet.waterTint = DEFAULT_WATER_TINT;
        } else {
            packet.biomeTint = DEFAULT_BIOME_TINT;
            packet.waterTint = DEFAULT_WATER_TINT;
        }
    }
}
