package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.wall.WallPieceDefinition;
import com.hexvane.aetherhaven.wall.WallStyle;
import com.hexvane.aetherhaven.wall.WallStyleCatalog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Opens a whole wall style in the building editor: every piece is pasted side by side in front of the player so its
 * build box and join spots can be re-marked, then the wizard starts on the wall pieces step.
 */
public final class BuildingEditorWallStyleSessionStarter {
    private static final String MSG = "aetherhaven_building_editor.aetherhaven.buildingeditor";
    private static final int INSTANT_BLOCKS_PER_BATCH = 500_000;
    private static final long INSTANT_BATCH_DELAY_MS = 1L;
    private static final int PASTE_FORWARD_BLOCKS = 10;
    /** Empty blocks left between pasted pieces so their build boxes never touch. */
    private static final int PASTE_GAP_BLOCKS = 6;

    private BuildingEditorWallStyleSessionStarter() {}

    /** One piece queued for pasting, with the world origin it goes to. */
    private record Pending(
        @Nonnull WallStyle.Piece piece,
        @Nonnull ConstructionDefinition def,
        @Nonnull IPrefabBuffer buffer,
        @Nonnull Vector3i origin
    ) {}

    public static void start(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String styleId
    ) {
        if (!BuildingEditorSessionStarter.requireCreative(playerRef, ref, store)) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        WallStyle style = WallStyleCatalog.get(catalog).style(styleId);
        String baseId = style == null ? null : PlotCreatorWallStyleLoader.baseIdForStyle(style);
        if (style == null || baseId == null || !PlotCreatorWallStyleLoader.isEditable(catalog, style)) {
            playerRef.sendMessage(Message.translation(MSG + ".error.wallStyleNotEditable"));
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.pasteFailed"));
            return;
        }
        List<Pending> pending = layOutPieces(catalog, style, transform.getPosition(), transform.getRotation().yaw());
        if (pending.isEmpty()) {
            playerRef.sendMessage(Message.translation(MSG + ".error.prefabMissing"));
            return;
        }
        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        World world = store.getExternalData().getWorld();
        playerRef.sendMessage(
            Message.translation(MSG + ".hint.loading").param("name", styleDisplayName(catalog, style))
        );
        pasteFrom(plugin, world, store, pending, 0, () -> world.execute(() -> {
            if (ref.isValid()) {
                finishSession(plugin, playerRef, ref, store, world, catalog, style, baseId, pending);
            }
        }));
    }

    /** Pastes pieces one after another so the batched block writes never overlap. */
    private static void pasteFrom(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<Pending> pending,
        int index,
        @Nonnull Runnable done
    ) {
        if (index >= pending.size()) {
            done.run();
            return;
        }
        Pending entry = pending.get(index);
        ConstructionAnimator.start(
            plugin,
            world,
            new Vector3i(entry.origin()),
            Rotation.None,
            true,
            entry.def().isPreserveWater(),
            entry.buffer(),
            store,
            INSTANT_BLOCKS_PER_BATCH,
            INSTANT_BATCH_DELAY_MS,
            () -> world.execute(() -> pasteFrom(plugin, world, store, pending, index + 1, done))
        );
    }

    @Nonnull
    private static List<Pending> layOutPieces(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull WallStyle style,
        @Nonnull Vector3d playerPos,
        float yawRad
    ) {
        Vector3i start = pasteOriginNearPlayer(playerPos, yawRad);
        List<Pending> out = new ArrayList<>();
        int cursorX = start.x;
        for (WallStyle.Piece piece : style.piecesInOrder()) {
            ConstructionDefinition def = catalog.get(piece.constructionId());
            IPrefabBuffer buffer = def == null ? null : PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
            if (def == null || buffer == null) {
                return List.of();
            }
            WallPieceDefinition shape = piece.definition();
            Vector3i min = shape.boundsMinLocal();
            Vector3i max = shape.boundsMaxLocal();
            // cursorX walks the west edge of each box, so the sign column sits that far in from the edge.
            int signX = cursorX - min.x;
            out.add(new Pending(piece, def, buffer, new Vector3i(signX, start.y, start.z)));
            cursorX = signX + max.x + 1 + PASTE_GAP_BLOCKS;
        }
        return out;
    }

    private static void finishSession(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull WallStyle style,
        @Nonnull String baseId,
        @Nonnull List<Pending> pending
    ) {
        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        PlotCreatorSession session = new PlotCreatorSession(uuid, world);
        PlotCreatorDraft draft = session.getDraft();
        draft.setBuildingEditorMode(true);
        draft.setEditingConstructionId(baseId);
        List<PlotCreatorWallStyleLoader.PastedPiece> pasted = new ArrayList<>(pending.size());
        for (Pending entry : pending) {
            pasted.add(
                new PlotCreatorWallStyleLoader.PastedPiece(
                    entry.piece().role(), entry.piece().constructionId(), new Vector3i(entry.origin())
                )
            );
        }
        PlotCreatorWallStyleLoader.loadIntoDraft(draft, catalog, style, baseId, pasted);
        draft.setStep(PlotCreatorStep.WALL_PIECES);
        draft.setMaxReachedStepIndex(
            Math.max(0, PlotCreatorService.stepOrder(draft).indexOf(PlotCreatorStep.WALL_PIECES))
        );
        PlotCreatorSessions.put(session);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            PlotCreatorHudSupport.refreshAll(player, playerRef, session);
        }
        PlotCreatorService.refreshWireframe(session, playerRef);
        playerRef.sendMessage(
            Message
                .translation(MSG + ".hint.loadedWallStyle")
                .param("name", styleDisplayName(catalog, style))
                .param("id", baseId)
        );
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    @Nonnull
    private static Vector3i pasteOriginNearPlayer(@Nonnull Vector3d pos, float yawRad) {
        double forwardX = -Math.sin(yawRad);
        double forwardZ = -Math.cos(yawRad);
        return new Vector3i(
            (int) Math.floor(pos.x + forwardX * PASTE_FORWARD_BLOCKS),
            (int) Math.floor(pos.y),
            (int) Math.floor(pos.z + forwardZ * PASTE_FORWARD_BLOCKS)
        );
    }

    /** Name to show for a style, taken from its plain wall segment. */
    @Nonnull
    public static String styleDisplayName(@Nonnull ConstructionCatalog catalog, @Nonnull WallStyle style) {
        WallStyle.Piece segment = style.piece(com.hexvane.aetherhaven.wall.WallPieceRole.SEGMENT);
        ConstructionDefinition def = segment == null ? null : catalog.get(segment.constructionId());
        String name = def != null ? def.getDisplayName() : null;
        return name != null && !name.isBlank() ? name : style.displayName();
    }

}
