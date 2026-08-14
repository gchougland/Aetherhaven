package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Spawns maze orbs from festival JSON and clears leftover ice essence markers. */
public final class HallowsEveOrbSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double DEDUPE_DIST_SQ = 0.25;

    /** From {@code Aetherhaven_Festival_Maze_Orb} hitbox Min/Max. */
    private static final Box ORB_BOX = new Box(-0.35, 0.0, -0.35, 0.35, 0.75, 0.35);

    /** JSON cells sit one block above the walkway; keep a small hover after dropping them. */
    private static final double SPAWN_Y_OFFSET = -1.0 + 0.35;

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
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    festivalPlot,
                    (int) Math.round(row.getLocalX()),
                    (int) Math.round(row.getLocalY()),
                    (int) Math.round(row.getLocalZ())
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
        if (entityStore == null) {
            return;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(HallowsEveIds.ORB_MODEL_ASSET_ID);
        if (asset == null) {
            LOGGER.atWarning().log("Hallow's Eve maze orb model missing: %s", HallowsEveIds.ORB_MODEL_ASSET_ID);
            return;
        }
        despawnRaceOrbs(world, townId);
        Store<EntityStore> store = entityStore.getStore();
        session.clearActiveOrbs();
        for (Vector3d stored : session.orbWorldPositionsView()) {
            Vector3d pos = new Vector3d(stored.x, stored.y + SPAWN_Y_OFFSET, stored.z);
            UUID entityUuid = spawnStillOrb(store, townId, pos, asset);
            if (entityUuid != null) {
                session.addActiveOrb(entityUuid);
            }
        }
    }

    @Nullable
    private static UUID spawnStillOrb(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull Vector3d pos,
        @Nonnull ModelAsset asset
    ) {
        Model model = Model.createUnitScaleModel(asset);
        UUID entityUuid = UUID.randomUUID();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, Rotation3f.ZERO));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(Rotation3f.ZERO));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(
                new Model.ModelReference(
                    HallowsEveIds.ORB_MODEL_ASSET_ID,
                    model.getScale(),
                    model.getRandomAttachmentIds(),
                    false
                )
            )
        );
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(ORB_BOX));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        HallowsEveOrbComponent orb = new HallowsEveOrbComponent();
        orb.setTownId(townId);
        holder.addComponent(HallowsEveOrbComponent.getComponentType(), orb);
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return entityUuid;
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
