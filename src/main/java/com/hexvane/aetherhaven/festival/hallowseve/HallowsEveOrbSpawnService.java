package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Spawns maze orbs from festival JSON and clears leftover ice essence markers. */
public final class HallowsEveOrbSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double DEDUPE_DIST_SQ = 0.25;

    private HallowsEveOrbSpawnService() {}

    public static void captureMarkers(
        @Nonnull World world,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival,
        @Nonnull HallowsEveSession session
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        var entityStore = world.getEntityStore();
        if (plugin == null || entityStore == null) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        List<Vector3d> leftoverIce = collectAndRemoveIceEssence(store, festivalPlot);
        List<Vector3d> fromJson = new ArrayList<>();
        for (FestivalDefinition.OrbSpawnRow row : festival.getOrbSpawns()) {
            fromJson.add(
                FestivalPrefabSwapService.spotWorldPositionAuthorLocal(
                    plugin,
                    festivalPlot,
                    row.getLocalX(),
                    row.getLocalY(),
                    row.getLocalZ()
                )
            );
        }
        if (!fromJson.isEmpty()) {
            session.setOrbWorldPositions(fromJson);
            return;
        }
        session.setOrbWorldPositions(leftoverIce);
        if (leftoverIce.isEmpty()) {
            LOGGER.atWarning().log("Hallow's Eve: no orb markers found for the maze");
        }
    }

    @Nonnull
    private static List<Vector3d> collectAndRemoveIceEssence(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance plot
    ) {
        PlotFootprintRecord fp = plot.toFootprint();
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        List<Vector3d> positions = new ArrayList<>();
        store.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    ItemComponent item = chunk.getComponent(i, ItemComponent.getComponentType());
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (item == null || tc == null) {
                        continue;
                    }
                    ItemStack stack = item.getItemStack();
                    if (stack == null || !HallowsEveIds.isIceEssenceMarker(stack.getItemId())) {
                        continue;
                    }
                    Vector3d pos = new Vector3d(tc.getPosition());
                    if (!containsBlock(fp, pos)) {
                        continue;
                    }
                    if (isDuplicate(positions, pos)) {
                        Ref<EntityStore> dup = chunk.getReferenceTo(i);
                        if (dup != null && dup.isValid()) {
                            toRemove.add(dup);
                        }
                        continue;
                    }
                    positions.add(pos);
                    Ref<EntityStore> r = chunk.getReferenceTo(i);
                    if (r != null && r.isValid()) {
                        toRemove.add(r);
                    }
                }
            }
        );
        for (Ref<EntityStore> r : toRemove) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
            }
        }
        return positions;
    }

    private static boolean isDuplicate(@Nonnull List<Vector3d> existing, @Nonnull Vector3d pos) {
        for (Vector3d other : existing) {
            if (other.distanceSquared(pos) < DEDUPE_DIST_SQ) {
                return true;
            }
        }
        return false;
    }

    public static void spawnRaceOrbs(@Nonnull World world, @Nonnull UUID townId, @Nonnull HallowsEveSession session) {
        if (!HallowsEveOrbComponent.isRegistered()) {
            return;
        }
        var entityStore = world.getEntityStore();
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (entityStore == null || npcPlugin == null) {
            return;
        }
        despawnRaceOrbs(world, townId);
        Store<EntityStore> store = entityStore.getStore();
        session.clearActiveOrbs();
        for (Vector3d stored : session.orbWorldPositionsView()) {
            Vector3d pos = new Vector3d(stored);
            var pair = npcPlugin.spawnNPC(store, HallowsEveIds.ORB_NPC_ROLE, null, pos, Rotation3f.ZERO);
            if (pair == null) {
                continue;
            }
            Ref<EntityStore> ref = pair.first();
            HallowsEveOrbComponent orb = new HallowsEveOrbComponent();
            orb.setTownId(townId);
            store.putComponent(ref, HallowsEveOrbComponent.getComponentType(), orb);
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                session.addActiveOrb(uc.getUuid());
            }
        }
    }

    public static void despawnRaceOrbs(@Nonnull World world, @Nonnull UUID townId) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !HallowsEveOrbComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        List<Ref<EntityStore>> refs = new ArrayList<>();
        store.forEachChunk(
            Query.and(HallowsEveOrbComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HallowsEveOrbComponent orb = chunk.getComponent(i, HallowsEveOrbComponent.getComponentType());
                    if (orb == null || !townId.equals(orb.getTownId())) {
                        continue;
                    }
                    Ref<EntityStore> r = chunk.getReferenceTo(i);
                    if (r != null && r.isValid()) {
                        refs.add(r);
                    }
                }
            }
        );
        for (Ref<EntityStore> r : refs) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
            }
        }
        HallowsEveSession session = HallowsEveSessionIndex.get(townId);
        if (session != null) {
            session.clearActiveOrbs();
        }
    }

    public static void despawnOrb(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref
    ) {
        if (ref.isValid()) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    private static boolean containsBlock(@Nonnull PlotFootprintRecord fp, @Nonnull Vector3d p) {
        int bx = (int) Math.floor(p.x);
        int by = (int) Math.floor(p.y);
        int bz = (int) Math.floor(p.z);
        return bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && by >= fp.getMinY() - 1
            && by <= fp.getMaxY() + 2
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ();
    }
}
