package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabYaw;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerEntity;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Pastes a catalog building near the player and starts a building-editor plot creator session. */
public final class BuildingEditorSessionStarter {
    private static final String MSG = "aetherhaven_building_editor.aetherhaven.buildingeditor";
    private static final int INSTANT_BLOCKS_PER_BATCH = 500_000;
    private static final long INSTANT_BATCH_DELAY_MS = 1L;
    private static final int PASTE_FORWARD_BLOCKS = 6;

    private BuildingEditorSessionStarter() {}

    public static boolean requireCreative(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && player.getGameMode() == GameMode.Creative) {
            return true;
        }
        playerRef.sendMessage(Message.translation(MSG + ".error.creativeOnly"));
        return false;
    }

    public static void startFromConstructionId(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String constructionId
    ) {
        if (!requireCreative(playerRef, ref, store)) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(constructionId.trim());
        if (def == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.unknownBuilding").param("id", constructionId));
            return;
        }
        if (def.isWallSegment()) {
            playerRef.sendMessage(Message.translation(MSG + ".error.wallNotSupported"));
            return;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buffer == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.prefabMissing"));
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.pasteFailed"));
            return;
        }
        Vector3d pos = transform.getPosition();
        float yawRad = transform.getRotation().yaw();
        Vector3i signPos = pasteSignNearPlayer(pos, yawRad);
        Rotation yaw = PlotCreatorJsonWriter.parseRotationYaw(def.getRotationYaw());
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(signPos, yaw);

        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        World world = store.getExternalData().getWorld();
        playerRef.sendMessage(Message.translation(MSG + ".hint.loading").param("name", displayName(def)));

        Vector3i signCopy = new Vector3i(signPos);
        Vector3i originCopy = new Vector3i(prefabOrigin);
        ConstructionAnimator.start(
            plugin,
            world,
            prefabOrigin,
            yaw,
            true,
            buffer,
            store,
            INSTANT_BLOCKS_PER_BATCH,
            INSTANT_BATCH_DELAY_MS,
            () ->
                world.execute(
                    () ->
                        finishSessionAfterPaste(
                            plugin,
                            playerRef,
                            ref,
                            store,
                            world,
                            def,
                            buffer,
                            originCopy,
                            signCopy,
                            yaw
                        )
                )
        );
    }

    private static void finishSessionAfterPaste(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull ConstructionDefinition def,
        @Nonnull IPrefabBuffer buffer,
        @Nonnull Vector3i prefabOrigin,
        @Nonnull Vector3i signPos,
        @Nonnull Rotation yaw
    ) {
        if (!ref.isValid()) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        PlotCreatorSession session = new PlotCreatorSession(uuid, world);
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorDraftLoader.loadIntoDraft(draft, def);
        draft.setBuildingEditorMode(true);
        draft.setConstructionIdUserEdited(true);
        draft.setSubmitToCommunity(false);
        draft.setLockedPrefabPathKey(def.getPrefabPath());
        Path existing = BuildingEditorSavePaths.findExistingBuildingFile(plugin, def.getId());
        Map<String, Object> snapshot = BuildingEditorJsonWriter.loadSnapshot(existing);
        draft.setOriginalBuildingJsonSnapshot(snapshot);

        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, yaw, buffer);
        draft.setCornerFirst(new Vector3i(fp.getMinX(), fp.getMinY(), fp.getMinZ()));
        draft.setCornerSecond(new Vector3i(fp.getMaxX(), fp.getMaxY(), fp.getMaxZ()));
        draft.setPlotAnchor(signPos);
        convertPrefabLocalsToSignSpace(draft, def.getPlotAnchorOffset());
        snapPlotSignToOutsideCorner(draft);
        seedSpecialBlocksFromPois(draft);
        seedAdventurerSpawnsFromWorldMarkers(world, draft, fp);
        PlotCreatorStep startStep = editorStartStep(draft);
        draft.setStep(startStep);
        PlotCreatorService.seedImportantSpotsIfEmpty(draft);
        PlotCreatorSessions.put(session);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            PlotCreatorHudSupport.refreshAll(player, playerRef, session);
        }
        PlotCreatorService.refreshWireframe(session, playerRef);
        PlotCreatorService.refreshSpawnMarkers(session, playerRef);
        playerRef.sendMessage(
            Message.translation(MSG + ".hint.loaded").param("name", displayName(def)).param("id", def.getId())
        );
        if (startStep == PlotCreatorStep.IMPORTANT_SPOTS) {
            // Replaces the building list page without setPage(None) (avoids PageManager ACK race).
            PlotCreatorInteractions.openImportantSpotsPanel(playerRef, ref, store, session);
        } else if (player != null) {
            // Decoration start at Identity with no follow-up panel — dismiss the picker here.
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    /**
     * Decoration/placements skip important spots in the wizard order. Landing on that missing step makes progress show
     * Welcome and blocks E/Q advance — start at Identity instead.
     */
    @Nonnull
    private static PlotCreatorStep editorStartStep(@Nonnull PlotCreatorDraft draft) {
        List<PlotCreatorStep> order = PlotCreatorService.stepOrder(draft);
        if (order.contains(PlotCreatorStep.IMPORTANT_SPOTS)) {
            return PlotCreatorStep.IMPORTANT_SPOTS;
        }
        if (order.contains(PlotCreatorStep.IDENTITY)) {
            return PlotCreatorStep.IDENTITY;
        }
        return PlotCreatorStep.IDENTITY;
    }

    /**
     * Paste poses the sign near the player, which often is not a legal plot-creator outside corner. Snap the draft
     * sign to the nearest outside corner and shift local coords so world spot positions stay put.
     */
    private static void snapPlotSignToOutsideCorner(@Nonnull PlotCreatorDraft draft) {
        Vector3i current = draft.getPlotAnchor();
        if (current == null || !PlotCreatorAnchorRules.hasBounds(draft)) {
            return;
        }
        if (PlotCreatorAnchorRules.isOutsideCorner(draft, current)) {
            return;
        }
        Vector3i snapped = nearestOutsideCorner(draft, current);
        int dx = current.x - snapped.x;
        int dy = current.y - snapped.y;
        int dz = current.z - snapped.z;
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        shiftAllDraftLocals(draft, dx, dy, dz);
        draft.setPlotAnchor(snapped);
    }

    @Nonnull
    private static Vector3i nearestOutsideCorner(@Nonnull PlotCreatorDraft draft, @Nonnull Vector3i from) {
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        int y = from.y;
        Vector3i[] corners = {
            new Vector3i(min.x - 1, y, min.z - 1),
            new Vector3i(min.x - 1, y, max.z + 1),
            new Vector3i(max.x + 1, y, min.z - 1),
            new Vector3i(max.x + 1, y, max.z + 1)
        };
        Vector3i best = corners[0];
        int bestDist = Integer.MAX_VALUE;
        for (Vector3i corner : corners) {
            int dist = Math.abs(corner.x - from.x) + Math.abs(corner.z - from.z);
            if (dist < bestDist) {
                bestDist = dist;
                best = corner;
            }
        }
        return best;
    }

    private static void shiftAllDraftLocals(@Nonnull PlotCreatorDraft draft, int dx, int dy, int dz) {
        shiftLocal(draft.getManagementBlockLocalPos(), dx, dy, dz, draft::setManagementBlockLocalPos);
        shiftLocal(draft.getProductionStorageLocalPos(), dx, dy, dz, draft::setProductionStorageLocalPos);
        shiftLocal(draft.getTreasuryLocalPos(), dx, dy, dz, draft::setTreasuryLocalPos);
        shiftLocal(draft.getShopSafeLocalPos(), dx, dy, dz, draft::setShopSafeLocalPos);
        shiftLocal(draft.getInnBellLocalPos(), dx, dy, dz, draft::setInnBellLocalPos);
        shiftLocal(draft.getInnkeeperSpawnLocal(), dx, dy, dz, draft::setInnkeeperSpawnLocal);
        shiftLocal(draft.getGuildMasterSpawnLocal(), dx, dy, dz, draft::setGuildMasterSpawnLocal);
        for (int[] visitor : draft.getVisitorSpawnLocals()) {
            if (visitor != null && visitor.length >= 3) {
                visitor[0] += dx;
                visitor[1] += dy;
                visitor[2] += dz;
            }
        }
        for (int i = 0; i < draft.getAdventurerSpawns().size(); i++) {
            PlotCreatorAdventurerSpawnEntry entry = draft.getAdventurerSpawns().get(i);
            draft.getAdventurerSpawns().set(
                i,
                new PlotCreatorAdventurerSpawnEntry(
                    entry.getLocalX() + dx,
                    entry.getLocalY() + dy,
                    entry.getLocalZ() + dz,
                    entry.getYawRadians()
                )
            );
        }
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            poi.setLocal(poi.getLocalX() + dx, poi.getLocalY() + dy, poi.getLocalZ() + dz);
            Integer tx = poi.getInteractionTargetLocalX();
            Integer ty = poi.getInteractionTargetLocalY();
            Integer tz = poi.getInteractionTargetLocalZ();
            if (tx != null && ty != null && tz != null) {
                poi.setInteractionTargetLocal(tx + dx, ty + dy, tz + dz);
            }
        }
    }

    /** Keep world spot positions when the plot sign moves during building-editor re-marking. */
    public static void rebaseLocalsForNewPlotSign(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i oldSign,
        @Nonnull Vector3i newSign
    ) {
        shiftAllDraftLocals(draft, oldSign.x - newSign.x, oldSign.y - newSign.y, oldSign.z - newSign.z);
    }

    /**
     * Construction JSON locals are relative to prefab buffer origin. Plot creator click handlers store locals relative
     * to the plot sign. Shift by the definition's plotAnchorOffset (and sign Y lift) so markers and clicks line up.
     */
    private static void convertPrefabLocalsToSignSpace(@Nonnull PlotCreatorDraft draft, @Nonnull int[] plotAnchorOffset) {
        int ox = plotAnchorOffset.length > 0 ? plotAnchorOffset[0] : 0;
        int oy = plotAnchorOffset.length > 1 ? plotAnchorOffset[1] : 0;
        int oz = plotAnchorOffset.length > 2 ? plotAnchorOffset[2] : 0;
        int dy = oy - AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR;
        shiftLocal(draft.getManagementBlockLocalPos(), ox, dy, oz, draft::setManagementBlockLocalPos);
        shiftLocal(draft.getProductionStorageLocalPos(), ox, dy, oz, draft::setProductionStorageLocalPos);
        shiftLocal(draft.getTreasuryLocalPos(), ox, dy, oz, draft::setTreasuryLocalPos);
        shiftLocal(draft.getShopSafeLocalPos(), ox, dy, oz, draft::setShopSafeLocalPos);
        shiftLocal(draft.getInnBellLocalPos(), ox, dy, oz, draft::setInnBellLocalPos);
        shiftLocal(draft.getInnkeeperSpawnLocal(), ox, dy, oz, draft::setInnkeeperSpawnLocal);
        shiftLocal(draft.getGuildMasterSpawnLocal(), ox, dy, oz, draft::setGuildMasterSpawnLocal);
        for (int[] visitor : draft.getVisitorSpawnLocals()) {
            if (visitor != null && visitor.length >= 3) {
                visitor[0] += ox;
                visitor[1] += dy;
                visitor[2] += oz;
            }
        }
        for (int i = 0; i < draft.getAdventurerSpawns().size(); i++) {
            PlotCreatorAdventurerSpawnEntry entry = draft.getAdventurerSpawns().get(i);
            draft.getAdventurerSpawns().set(
                i,
                new PlotCreatorAdventurerSpawnEntry(
                    entry.getLocalX() + ox,
                    entry.getLocalY() + dy,
                    entry.getLocalZ() + oz,
                    entry.getYawRadians()
                )
            );
        }
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            poi.setLocal(poi.getLocalX() + ox, poi.getLocalY() + dy, poi.getLocalZ() + oz);
            Integer tx = poi.getInteractionTargetLocalX();
            Integer ty = poi.getInteractionTargetLocalY();
            Integer tz = poi.getInteractionTargetLocalZ();
            if (tx != null && ty != null && tz != null) {
                poi.setInteractionTargetLocal(tx + ox, ty + dy, tz + oz);
            }
        }
    }

    private static void shiftLocal(
        @Nullable int[] pos,
        int ox,
        int dy,
        int oz,
        @Nonnull java.util.function.Consumer<int[]> setter
    ) {
        if (pos == null || pos.length < 3) {
            return;
        }
        setter.accept(new int[] {pos[0] + ox, pos[1] + dy, pos[2] + oz});
    }

    private static void seedSpecialBlocksFromPois(@Nonnull PlotCreatorDraft draft) {
        draft.getPlacedSpecialBlocks().clear();
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            String blockId = poi.getBlockTypeId();
            if (!AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID.equals(blockId)
                && !AetherhavenConstants.TOURIST_PORTAL_BLOCK_TYPE_ID.equals(blockId)) {
                continue;
            }
            Vector3i world = PlotCreatorLocalCoords.toWorldBlock(
                draft,
                new int[] {poi.getLocalX(), poi.getLocalY(), poi.getLocalZ()}
            );
            draft.getPlacedSpecialBlocks().add(world);
        }
    }

    /**
     * Guild halls (and similar) often keep adventurer posts as prefab marker entities with an empty JSON list.
     * After paste, copy those world markers into the draft so editor spot markers and checklist counts work.
     */
    private static void seedAdventurerSpawnsFromWorldMarkers(
        @Nonnull World world,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotFootprintRecord fp
    ) {
        if (!draft.getAdventurerSpawns().isEmpty() || draft.getPlotAnchor() == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<SeededAdventurer> found = new ArrayList<>();
        store.forEachChunk(
            Query.and(AdventurerSpawnMarkerEntity.getComponentType(), TransformComponent.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d p = tc.getPosition();
                    int bx = (int) Math.floor(p.x);
                    int by = (int) Math.floor(p.y);
                    int bz = (int) Math.floor(p.z);
                    if (bx < fp.getMinX()
                        || bx > fp.getMaxX()
                        || by < fp.getMinY()
                        || by > fp.getMaxY()
                        || bz < fp.getMinZ()
                        || bz > fp.getMaxZ()) {
                        continue;
                    }
                    int[] local = PlotCreatorLocalCoords.toLocal(draft, new Vector3i(bx, by, bz));
                    float prefabYaw =
                        PrefabYaw.prefabFromWorld(PlotCreatorPrefabCoords.placementYaw(draft), tc.getRotation().yaw());
                    found.add(new SeededAdventurer(local[0], local[1], local[2], prefabYaw));
                }
            }
        );
        if (found.isEmpty()) {
            return;
        }
        found.sort(
            Comparator.comparingInt((SeededAdventurer a) -> a.x)
                .thenComparingInt(a -> a.z)
                .thenComparingInt(a -> a.y)
        );
        for (SeededAdventurer a : found) {
            draft.getAdventurerSpawns().add(new PlotCreatorAdventurerSpawnEntry(a.x, a.y, a.z, a.yaw));
        }
        ensureAdventurerSpotSelected(draft, found.size());
    }

    private static void ensureAdventurerSpotSelected(@Nonnull PlotCreatorDraft draft, int count) {
        for (PlotCreatorSpotEntry entry : draft.getSelectedSpots()) {
            if (entry.type() == PlotCreatorSubstepType.ADVENTURER_SPAWN) {
                return;
            }
        }
        draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.ADVENTURER_SPAWN, Math.max(1, count)));
    }

    private record SeededAdventurer(int x, int y, int z, float yaw) {}

    @Nonnull
    private static Vector3i pasteSignNearPlayer(@Nonnull Vector3d pos, float yawRad) {
        double forwardX = -Math.sin(yawRad);
        double forwardZ = -Math.cos(yawRad);
        int sx = (int) Math.floor(pos.x + forwardX * PASTE_FORWARD_BLOCKS);
        int sy = (int) Math.floor(pos.y);
        int sz = (int) Math.floor(pos.z + forwardZ * PASTE_FORWARD_BLOCKS);
        return new Vector3i(sx, sy, sz);
    }

    @Nonnull
    private static String displayName(@Nonnull ConstructionDefinition def) {
        String name = def.getDisplayName();
        return name != null && !name.isBlank() ? name : def.getId();
    }
}
