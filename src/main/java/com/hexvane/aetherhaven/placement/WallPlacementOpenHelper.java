package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.ui.WallPlacementPage;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WallPlacementOpenHelper {
    private WallPlacementOpenHelper() {}

    @Nullable
    public static CustomUIPage tryOpenBuild(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        BlockPosition tb = context.getTargetBlock();
        Store<EntityStore> store = ref.getStore();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        WallPlacementSession existing = WallPlacementSessions.get(uc.getUuid());
        if (existing != null && existing.getWorld().getName().equals(world.getName())) {
            return new WallPlacementPage(playerRef, existing);
        }
        if (tb == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_wall_placement.aetherhaven.ui.wallplacement.errorLookAtBlock"));
            return null;
        }
        cancelOtherPlacementPreviews(ref, store, playerRef, uc.getUuid());
        Vector3i anchor = pickAnchor(world, tb);
        WallPlacementSession session = new WallPlacementSession(world, anchor);
        WallPlacementSessions.put(uc.getUuid(), session);
        if (session.isDebugLogging()) {
            com.hexvane.aetherhaven.placement.WallPlacementDebug.logState(playerRef, session, "sessionOpen");
        }
        return new WallPlacementPage(playerRef, session);
    }

    @Nonnull
    public static Vector3i pickAnchor(@Nonnull World world, @Nonnull BlockPosition tb) {
        Vector3i above = new Vector3i(tb.x, tb.y + 1, tb.z);
        Vector3i picked;
        if (isReplaceable(world, above.x, above.y, above.z)) {
            picked = above;
        } else {
            Vector3i on = new Vector3i(tb.x, tb.y, tb.z);
            picked = isReplaceable(world, on.x, on.y, on.z) ? on : above;
        }
        return new Vector3i(
            picked.x,
            picked.y + AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
            picked.z
        );
    }

    private static boolean isReplaceable(@Nonnull World world, int x, int y, int z) {
        BlockType t = ChunkSectionBlockUtil.blockType(world, x, y, z);
        return t == null || t.getMaterial() == BlockMaterial.Empty;
    }

    static void cancelOtherPlacementPreviews(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef pr, @Nonnull UUID playerUuid
    ) {
        PlotPlacementOpenHelper.cancelActivePlotPlacement(ref, store, pr);
        CharterRelocationSession charter = CharterRelocationSessions.removeAndGet(playerUuid);
        if (charter != null) {
            PlotPreviewSpawner.clear(store, charter.getPreviewEntityRefs());
            PlotPlacementWireframeOverlay.clearFor(pr);
        }
    }

    public static void cancelActive(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef pr) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        WallPlacementSession s = WallPlacementSessions.removeAndGet(uc.getUuid());
        if (s == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                PlotPreviewSpawner.clear(store, s.getPreviewEntityRefs());
                WallPlacementWireframeOverlay.clearFor(pr);
                PlotPlacementCameraUtil.resetToPlayerCamera(pr);
            }
        );
    }

    public static boolean isWallConstruction(@Nullable ConstructionDefinition def) {
        return def != null && def.isWallSegment();
    }
}
