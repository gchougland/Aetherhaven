package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPrefabPreview;
import com.hypixel.hytale.protocol.packets.interface_.EditorBlocksChange;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Sends {@link BuilderToolPrefabPreview} packets for the plot crafting bench preview panel. */
public final class PlotCraftingPrefabPreview {
    private static final int DEFAULT_BIOME_TINT =
        ColorParseUtil.colorToARGBInt(com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSessionManager.DEFAULT_TINT)
            & 16777215;
    private static final int DEFAULT_WATER_TINT =
        ColorParseUtil.colorToARGBInt(Environment.getUnknownFor("").getWaterTint()) & 16777215;

    private PlotCraftingPrefabPreview() {}

    public static void send(@Nonnull PlayerRef playerRef, @Nullable String prefabPathKey) {
        if (prefabPathKey == null || prefabPathKey.isBlank()) {
            clear(playerRef);
            return;
        }
        Path resolved = PrefabResolveUtil.resolvePrefabPath(prefabPathKey);
        if (resolved == null) {
            clear(playerRef);
            return;
        }
        BlockSelection selection;
        try {
            selection = PrefabStore.get().getPrefab(resolved);
        } catch (Exception e) {
            clear(playerRef);
            return;
        }
        send(playerRef, selection);
    }

    public static void send(@Nonnull PlayerRef playerRef, @Nullable BlockSelection selection) {
        BuilderToolPrefabPreview packet = new BuilderToolPrefabPreview();
        if (selection != null) {
            packet.tilt = 23;
            packet.spinSpeed = 27;
            packet.previewScale = 100;
            EditorBlocksChange editorPacket = selection.toPacket();
            packet.blocksChange = editorPacket.blocksChange;
            packet.fluidsChange = editorPacket.fluidsChange;
            packet.entityChanges = editorPacket.entityChanges;
            applyTintFromPlayerPosition(playerRef, packet);
        }
        playerRef.getPacketHandler().writeNoCache(packet);
    }

    public static void clear(@Nonnull PlayerRef playerRef) {
        send(playerRef, (BlockSelection) null);
    }

    private static void applyTintFromPlayerPosition(@Nonnull PlayerRef playerRef, @Nonnull BuilderToolPrefabPreview packet) {
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
