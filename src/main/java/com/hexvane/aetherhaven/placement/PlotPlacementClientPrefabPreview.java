package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.community.CommunityPrefabSafety;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.prefab.AetherhavenWorldPrefabPreview;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.packets.buildertools.ClipboardEntityChange;
import com.hypixel.hytale.protocol.packets.interface_.BlockChange;
import com.hypixel.hytale.protocol.packets.interface_.EditorBlocksChange;
import com.hypixel.hytale.protocol.packets.interface_.FluidChange;
import com.hypixel.hytale.protocol.packets.player.HideTriggerVolumePastePrefabPreview;
import com.hypixel.hytale.protocol.packets.player.ShowTriggerVolumePastePrefabPreview;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.prefab.selection.standard.RotateBlockMode;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Hybrid prefab ghosts: block/fluid holograms via {@link com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview}
 * (world entity, visible to nearby players). Prefab entity markers use per-player {@link ShowTriggerVolumePastePrefabPreview}
 * overlays because the hologram API does not include clipboard entities.
 */
public final class PlotPlacementClientPrefabPreview {
    private static final int DEFAULT_BIOME_TINT =
        ColorParseUtil.colorToARGBInt(com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSessionManager.DEFAULT_TINT)
            & 16777215;
    private static final int DEFAULT_WATER_TINT =
        ColorParseUtil.colorToARGBInt(Environment.getUnknownFor("").getWaterTint()) & 16777215;
    private static final BlockChange[] NO_BLOCKS = new BlockChange[0];
    private static final FluidChange[] NO_FLUIDS = new FluidChange[0];

    private PlotPlacementClientPrefabPreview() {}

    /** Cached anchor metadata for one construction + rotation (session lifetime). */
    public record Payload(
        @Nullable BlockChange[] blocksChange,
        @Nullable FluidChange[] fluidsChange,
        @Nullable ClipboardEntityChange[] entityChanges,
        int anchorX,
        int anchorY,
        int anchorZ
    ) {}

    /** Clears per-player entity overlay and any legacy full paste preview on the client. */
    public static void hide(@Nonnull PlayerRef playerRef) {
        playerRef.getPacketHandler().write(new HideTriggerVolumePastePrefabPreview());
    }

    public static boolean hasEntityOverlay(@Nonnull Payload payload) {
        ClipboardEntityChange[] changes = payload.entityChanges();
        return changes != null && changes.length > 0;
    }

    public static void clearWorldPreview(@Nonnull Store<EntityStore> store, @Nonnull List<Ref<EntityStore>> previewRefs) {
        AetherhavenWorldPrefabPreview.clearAll(store, previewRefs);
    }

    public static void clearWorldPreview(@Nonnull Store<EntityStore> store, @Nonnull PlotPlacementSession session) {
        clearWorldPreview(store, session.getPreviewEntityRefs());
    }

    /**
     * Spawns or moves the world hologram for plot placement.
     *
     * @return {@code false} when prefab could not be loaded
     */
    public static boolean showWorldPreview(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotPlacementSession session,
        @Nonnull String prefabPathKey,
        int rotationSteps,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull Rotation placementYaw,
        boolean respawn
    ) {
        Payload payload = resolvePayload(prefabPathKey, rotationSteps, session);
        if (payload == null) {
            clearWorldPreview(store, session);
            return false;
        }
        Vector3i spawnCorner = flooredOrigin(resolveClientPreviewPosition(prefabOriginWorld, payload, placementYaw));
        var rotation = AetherhavenWorldPrefabPreview.rotationFromYaw(placementYaw);
        List<Ref<EntityStore>> refs = session.getPreviewEntityRefs();
        if (respawn || refs.isEmpty()) {
            clearWorldPreview(store, session);
            Ref<EntityStore> ref =
                AetherhavenWorldPrefabPreview.spawnAtBlockCorner(
                    store,
                    spawnCorner,
                    rotation,
                    prefabPathKey,
                    rotationSteps,
                    AetherhavenWorldPrefabPreview.ALL_LAYERS
                );
            if (ref == null) {
                return false;
            }
            refs.add(ref);
            return true;
        }
        Ref<EntityStore> existing = refs.getFirst();
        if (existing != null && existing.isValid()) {
            AetherhavenWorldPrefabPreview.updatePositionAtBlockCorner(store, existing, spawnCorner, rotation);
            return true;
        }
        clearWorldPreview(store, session);
        Ref<EntityStore> ref =
            AetherhavenWorldPrefabPreview.spawnAtBlockCorner(
                store,
                spawnCorner,
                rotation,
                prefabPathKey,
                rotationSteps,
                AetherhavenWorldPrefabPreview.ALL_LAYERS
            );
        if (ref != null) {
            refs.add(ref);
        }
        return ref != null;
    }

    public static boolean sendFull(
        @Nonnull PlayerRef playerRef,
        @Nonnull String prefabPathKey,
        int rotationSteps,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull Rotation placementYaw,
        @Nonnull PlotPlacementSession session
    ) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return false;
        }
        boolean ok =
            showWorldPreview(
                ref.getStore(),
                session,
                prefabPathKey,
                rotationSteps,
                prefabOriginWorld,
                placementYaw,
                true
            );
        if (!ok) {
            return false;
        }
        Payload payload = session.getClientPrefabPreviewPayload();
        if (payload != null) {
            sendEntityOverlayFull(playerRef, prefabOriginWorld, payload, placementYaw);
        }
        return true;
    }

    public static boolean sendPositionOnly(
        @Nonnull PlayerRef playerRef,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull Payload payload,
        @Nonnull Rotation placementYaw,
        @Nonnull PlotPlacementSession session
    ) {
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null) {
            return false;
        }
        Store<EntityStore> store = entityRef.getStore();
        Vector3i spawnCorner = flooredOrigin(resolveClientPreviewPosition(prefabOriginWorld, payload, placementYaw));
        List<Ref<EntityStore>> refs = session.getPreviewEntityRefs();
        if (refs.isEmpty()) {
            return false;
        }
        Ref<EntityStore> previewRef = refs.getFirst();
        if (previewRef == null || !previewRef.isValid()) {
            return false;
        }
        AetherhavenWorldPrefabPreview.updatePositionAtBlockCorner(
            store,
            previewRef,
            spawnCorner,
            AetherhavenWorldPrefabPreview.rotationFromYaw(placementYaw)
        );
        if (hasEntityOverlay(payload)) {
            sendEntityOverlayPositionOnly(playerRef, prefabOriginWorld, payload, placementYaw);
        }
        return true;
    }

    public static void sendFullToViewer(
        @Nonnull PlayerRef viewer,
        @Nonnull World world,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull Payload payload,
        @Nonnull Rotation placementYaw
    ) {
        if (!hasEntityOverlay(payload)) {
            return;
        }
        Vector3i clientPos = resolveClientPreviewPosition(prefabOriginWorld, payload, placementYaw);
        writeEntityOverlayToViewer(viewer, world, flooredPosition(clientPos), payload);
    }

    public static void sendPositionOnlyToViewer(
        @Nonnull PlayerRef viewer,
        @Nonnull World world,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull Payload payload,
        @Nonnull Rotation placementYaw
    ) {
        if (!hasEntityOverlay(payload)) {
            return;
        }
        Vector3i clientPos = resolveClientPreviewPosition(prefabOriginWorld, payload, placementYaw);
        Vector3f pos = flooredPosition(clientPos);
        ShowTriggerVolumePastePrefabPreview packet = buildEntityOverlayPositionPacket(pos);
        applyTintFromWorldPosition(world, MathUtil.floor(clientPos.x), MathUtil.floor(clientPos.y), MathUtil.floor(clientPos.z), packet);
        viewer.getPacketHandler().write(packet);
    }

    public static void sendEntityOverlayFull(
        @Nonnull PlayerRef playerRef,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull Payload payload,
        @Nonnull Rotation placementYaw
    ) {
        if (!hasEntityOverlay(payload)) {
            return;
        }
        Vector3i clientPos = resolveClientPreviewPosition(prefabOriginWorld, payload, placementYaw);
        writeEntityOverlay(playerRef, flooredPosition(clientPos), payload);
    }

    public static void sendEntityOverlayPositionOnly(
        @Nonnull PlayerRef playerRef,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull Payload payload,
        @Nonnull Rotation placementYaw
    ) {
        if (!hasEntityOverlay(payload)) {
            return;
        }
        Vector3i clientPos = resolveClientPreviewPosition(prefabOriginWorld, payload, placementYaw);
        ShowTriggerVolumePastePrefabPreview packet = buildEntityOverlayPositionPacket(flooredPosition(clientPos));
        applyTintFromPlayerPosition(playerRef, packet);
        playerRef.getPacketHandler().write(packet);
    }

    @Nonnull
    public static Vector3i resolveClientPreviewPosition(
        @Nonnull Vector3i prefabBufferOriginWorld,
        @Nonnull Payload payload,
        @Nonnull Rotation placementYaw
    ) {
        Vector3i anchorOffset =
            PrefabLocalOffset.rotate(placementYaw, payload.anchorX(), payload.anchorY(), payload.anchorZ());
        return new Vector3i(
            prefabBufferOriginWorld.x + anchorOffset.x,
            prefabBufferOriginWorld.y + anchorOffset.y,
            prefabBufferOriginWorld.z + anchorOffset.z
        );
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

    @Nonnull
    public static Vector3i flooredClientPreviewOrigin(
        @Nonnull Vector3i prefabBufferOriginWorld,
        @Nonnull PlotPlacementSession session,
        @Nonnull Rotation placementYaw
    ) {
        Payload payload = session.getClientPrefabPreviewPayload();
        if (payload == null) {
            return flooredOrigin(prefabBufferOriginWorld);
        }
        return flooredOrigin(resolveClientPreviewPosition(prefabBufferOriginWorld, payload, placementYaw));
    }

    public static boolean sendFullStandalone(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<Ref<EntityStore>> previewRefs,
        @Nonnull String prefabPathKey,
        int rotationSteps,
        @Nonnull Vector3i prefabOriginWorld,
        @Nonnull Rotation placementYaw
    ) {
        Payload payload = loadPayload(prefabPathKey, rotationSteps);
        if (payload == null) {
            clearWorldPreview(store, previewRefs);
            return false;
        }
        clearWorldPreview(store, previewRefs);
        Vector3i spawnCorner = flooredOrigin(resolveClientPreviewPosition(prefabOriginWorld, payload, placementYaw));
        Ref<EntityStore> ref =
            AetherhavenWorldPrefabPreview.spawnAtBlockCorner(
                store,
                spawnCorner,
                AetherhavenWorldPrefabPreview.rotationFromYaw(placementYaw),
                prefabPathKey,
                rotationSteps,
                AetherhavenWorldPrefabPreview.ALL_LAYERS
            );
        if (ref == null) {
            return false;
        }
        previewRefs.add(ref);
        sendEntityOverlayFull(playerRef, prefabOriginWorld, payload, placementYaw);
        return true;
    }

    @Nullable
    public static Payload loadPayload(@Nonnull String prefabPathKey, int rotationSteps) {
        int steps = (rotationSteps % 4 + 4) % 4;
        Path resolved = PrefabResolveUtil.resolvePrefabPath(prefabPathKey);
        if (resolved == null) {
            return null;
        }
        try {
            if (!CommunityPrefabSafety.validate(resolved).isSafe()) {
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
        return new Payload(
            editor.blocksChange,
            editor.fluidsChange,
            editor.entityChanges,
            selection.getAnchorX(),
            selection.getAnchorY(),
            selection.getAnchorZ()
        );
    }

    @Nullable
    static Payload resolvePayload(
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
        Payload payload = loadPayload(prefabPathKey, steps);
        if (payload == null) {
            return null;
        }
        session.setClientPrefabPreviewCache(pathKey, steps, payload);
        return payload;
    }

    private static void writeEntityOverlay(
        @Nonnull PlayerRef playerRef,
        @Nonnull Vector3f position,
        @Nonnull Payload payload
    ) {
        ShowTriggerVolumePastePrefabPreview packet = buildEntityOverlayPacket(position, payload);
        applyTintFromPlayerPosition(playerRef, packet);
        playerRef.getPacketHandler().write(packet);
    }

    private static void writeEntityOverlayToViewer(
        @Nonnull PlayerRef viewer,
        @Nonnull World world,
        @Nonnull Vector3f position,
        @Nonnull Payload payload
    ) {
        ShowTriggerVolumePastePrefabPreview packet = buildEntityOverlayPacket(position, payload);
        applyTintFromWorldPosition(world, MathUtil.floor(position.x), MathUtil.floor(position.y), MathUtil.floor(position.z), packet);
        viewer.getPacketHandler().write(packet);
    }

    @Nonnull
    private static ShowTriggerVolumePastePrefabPreview buildEntityOverlayPacket(
        @Nonnull Vector3f position,
        @Nonnull Payload payload
    ) {
        ShowTriggerVolumePastePrefabPreview packet = new ShowTriggerVolumePastePrefabPreview();
        packet.position = position;
        packet.blocksChange = NO_BLOCKS;
        packet.fluidsChange = NO_FLUIDS;
        packet.entityChanges = payload.entityChanges();
        return packet;
    }

    @Nonnull
    private static ShowTriggerVolumePastePrefabPreview buildEntityOverlayPositionPacket(@Nonnull Vector3f position) {
        ShowTriggerVolumePastePrefabPreview packet = new ShowTriggerVolumePastePrefabPreview();
        packet.position = position;
        return packet;
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
        applyTintFromWorldPosition(
            world,
            MathUtil.floor(pos.x),
            MathUtil.floor(pos.y),
            MathUtil.floor(pos.z),
            packet
        );
    }

    static void applyTintFromWorldPosition(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull ShowTriggerVolumePastePrefabPreview packet
    ) {
        BlockChunk blockChunk = ChunkSectionBlockUtil.blockChunkAt(world, x, z);
        if (blockChunk != null) {
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
