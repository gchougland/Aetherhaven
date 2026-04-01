package com.hexvane.aetherhaven.poi.tool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiPrefabCoords;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * While the POI tool is held (and permission is granted), shows per-player marker entities + nameplate labels near
 * registered POIs. Labels are recreated periodically; cleared when the tool is put away.
 */
public final class PoiToolVisualizationSystem extends EntityTickingSystem<EntityStore> {
    private static final double VIZ_RANGE = 96.0;
    private static final double VIZ_RANGE_SQ = VIZ_RANGE * VIZ_RANGE;
    private static final int LABEL_REFRESH_TICKS = 20;

    /** POI block cell origin is integer (x,y,z); world center of that cell is +0.5 on each axis. */
    private static final double POI_BLOCK_CENTER = 0.5;
    /** Extra Y for the visible marker model (negative = lower). */
    private static final double MARKER_MODEL_Y_OFFSET = -0.5;
    /**
     * Nameplate uses a separate entity; offset from the marker pivot ({@link Nameplate} has no per-component vertical offset).
     */
    private static final double NAMEPLATE_PIVOT_OFFSET_Y = 1.0;
    /**
     * The nameplate-only entity still needs a {@link ModelComponent} so the client/network pipeline treats it like a
     * normal entity (otherwise the nameplate never syncs). Same asset as the marker, scaled near-zero so it is not visible.
     */
    private static final float NAMEPLATE_ANCHOR_MODEL_SCALE = 0.001f;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    private final AetherhavenPlugin plugin;

    public PoiToolVisualizationSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        World world = store.getExternalData().getWorld();
        if (player == null) {
            return;
        }
        if (!player.hasPermission(AetherhavenConstants.PERMISSION_POI_TOOL)) {
            clearLabelsIfPresent(world, store, commandBuffer, playerRef);
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        boolean holding = PoiToolInteractions.isPoiToolItem(hand);
        if (!holding) {
            clearLabelsIfPresent(world, store, commandBuffer, playerRef);
            return;
        }

        PoiToolPlayerComponent state = store.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        if (state == null) {
            commandBuffer.addComponent(playerRef, PoiToolPlayerComponent.getComponentType(), new PoiToolPlayerComponent());
            state = commandBuffer.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        }
        if (state == null) {
            return;
        }
        long tick = world.getTick();
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        TransformComponent t = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (t == null) {
            return;
        }
        Vector3d ppos = t.getPosition();
        List<PoiEntry> nearby = new ArrayList<>();
        for (PoiEntry e : reg.allEntries()) {
            double dx = (e.getX() + 0.5) - ppos.getX();
            double dy = (e.getY() + 0.5) - ppos.getY();
            double dz = (e.getZ() + 0.5) - ppos.getZ();
            if (dx * dx + dy * dy + dz * dz <= VIZ_RANGE_SQ) {
                nearby.add(e);
            }
        }

        if (tick % LABEL_REFRESH_TICKS == 0) {
            UUIDComponent ownerComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
            if (ownerComp != null) {
                UUID playerUuid = ownerComp.getUuid();
                List<PoiEntry> nearbyCopy = new ArrayList<>(nearby);
                world.execute(() -> refreshLabelsDeferred(world, playerUuid, nearbyCopy));
            }
        }
    }

    private static void clearLabelsIfPresent(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        PoiToolPlayerComponent state = store.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        if (state == null) {
            return;
        }
        UUIDComponent puc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (puc == null) {
            return;
        }
        UUID playerUuid = puc.getUuid();
        world.execute(() -> clearLabelsDeferred(world, playerUuid));
    }

    private static void clearLabelsDeferred(@Nonnull World world, @Nonnull UUID playerUuid) {
        Ref<EntityStore> pref = world.getEntityRef(playerUuid);
        if (pref == null || !pref.isValid()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        PoiToolPlayerComponent state = store.getComponent(pref, PoiToolPlayerComponent.getComponentType());
        if (state == null) {
            return;
        }
        removeLabelEntities(world, state);
    }

    private static void removeLabelEntities(@Nonnull World world, @Nonnull PoiToolPlayerComponent state) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID id : new ArrayList<>(state.getDebugLabelEntityUuids())) {
            Ref<EntityStore> labelRef = world.getEntityRef(id);
            if (labelRef != null && labelRef.isValid()) {
                store.removeEntity(labelRef, RemoveReason.REMOVE);
            }
        }
        state.clearDebugLabels();
    }

    /**
     * Mirrors {@code World.addEntity(..., AddReason.SPAWN)} without calling deprecated {@link World} spawn helpers —
     * uses {@link Store#addEntity} on {@link World#getEntityStore()} instead.
     */
    @Nullable
    private static PoiDebugLabelEntity addPoiDebugLabelEntity(
        @Nonnull World world,
        @Nonnull PoiDebugLabelEntity entity,
        @Nonnull Vector3d position,
        @Nonnull Vector3f rotation
    ) {
        if (!EntityModule.get().isKnown(entity)) {
            throw new IllegalArgumentException("Unknown entity");
        }
        // Avoid Entity#getNetworkId() (deprecated for removal): caller must use e.g. new PoiDebugLabelEntity(world).
        if (!world.equals(entity.getWorld())) {
            throw new IllegalStateException("Expected entity to already have its world set to " + world.getName());
        }
        if (entity.getReference() != null && entity.getReference().isValid()) {
            throw new IllegalArgumentException("Entity already has a valid EntityReference: " + entity.getReference());
        }
        if (position.getY() < -32.0) {
            throw new IllegalArgumentException("Unable to spawn entity below the world! -32 < " + position);
        }
        entity.unloadFromWorld();
        Holder<EntityStore> holder = entity.toHolder();
        HeadRotation headRotation = holder.ensureAndGetComponent(HeadRotation.getComponentType());
        headRotation.teleportRotation(rotation);
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.ensureComponent(UUIDComponent.getComponentType());
        world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
        return entity;
    }

    /**
     * Must not run inside an ECS system tick: schedules via {@link World#execute(Runnable)} so entity add/remove runs
     * when the store is not in {@code assertWriteProcessing}.
     */
    private void refreshLabelsDeferred(@Nonnull World world, @Nonnull UUID playerUuid, @Nonnull List<PoiEntry> nearby) {
        Ref<EntityStore> playerRef = world.getEntityRef(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        PoiToolPlayerComponent state = store.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        if (state == null) {
            return;
        }
        removeLabelEntities(world, state);
        UUID ownerUuid = playerUuid;
        ModelAsset markerAsset = resolveMarkerModelAsset();
        if (markerAsset == null) {
            return;
        }
        Model markerModel = Model.createUnitScaleModel(markerAsset);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (PoiEntry poi : nearby) {
            String text = buildLabelText(poi, tm);
            Vector3d markerPos = new Vector3d(
                poi.getX() + POI_BLOCK_CENTER,
                poi.getY() + POI_BLOCK_CENTER + MARKER_MODEL_Y_OFFSET,
                poi.getZ() + POI_BLOCK_CENTER
            );
            Vector3f rot = new Vector3f(0.0F, 0.0F, 0.0F);

            PoiDebugLabelEntity markerEnt = new PoiDebugLabelEntity(world);
            markerEnt.setOwnerPlayerUuid(ownerUuid);
            PoiDebugLabelEntity markerSpawned = addPoiDebugLabelEntity(world, markerEnt, markerPos, rot);
            if (markerSpawned == null) {
                continue;
            }
            Ref<EntityStore> markerRef = markerSpawned.getReference();
            if (markerRef == null || !markerRef.isValid()) {
                continue;
            }
            store.putComponent(markerRef, ModelComponent.getComponentType(), new ModelComponent(markerModel));
            store.addComponent(markerRef, Intangible.getComponentType(), Intangible.INSTANCE);
            UUIDComponent markerUc = store.getComponent(markerRef, UUIDComponent.getComponentType());
            if (markerUc != null) {
                state.getDebugLabelEntityUuids().add(markerUc.getUuid());
            }

            Vector3d nameplatePos = new Vector3d(
                markerPos.getX(),
                markerPos.getY() + NAMEPLATE_PIVOT_OFFSET_Y,
                markerPos.getZ()
            );
            PoiDebugLabelEntity nameplateEnt = new PoiDebugLabelEntity(world);
            nameplateEnt.setOwnerPlayerUuid(ownerUuid);
            PoiDebugLabelEntity nameplateSpawned = addPoiDebugLabelEntity(world, nameplateEnt, nameplatePos, rot);
            if (nameplateSpawned == null) {
                store.removeEntity(markerRef, RemoveReason.REMOVE);
                if (markerUc != null) {
                    state.getDebugLabelEntityUuids().remove(markerUc.getUuid());
                }
                continue;
            }
            Ref<EntityStore> nameplateRef = nameplateSpawned.getReference();
            if (nameplateRef == null || !nameplateRef.isValid()) {
                store.removeEntity(markerRef, RemoveReason.REMOVE);
                if (markerUc != null) {
                    state.getDebugLabelEntityUuids().remove(markerUc.getUuid());
                }
                continue;
            }
            Model anchorModel = Model.createScaledModel(markerAsset, NAMEPLATE_ANCHOR_MODEL_SCALE);
            store.putComponent(nameplateRef, ModelComponent.getComponentType(), new ModelComponent(anchorModel));
            store.putComponent(nameplateRef, Nameplate.getComponentType(), new Nameplate(truncate(text, 240)));
            store.addComponent(nameplateRef, Intangible.getComponentType(), Intangible.INSTANCE);
            Message msg = Message.raw(truncate(text, 120));
            store.putComponent(nameplateRef, DisplayNameComponent.getComponentType(), new DisplayNameComponent(msg));
            UUIDComponent nameplateUc = store.getComponent(nameplateRef, UUIDComponent.getComponentType());
            if (nameplateUc != null) {
                state.getDebugLabelEntityUuids().add(nameplateUc.getUuid());
            }
        }
    }

    @Nonnull
    private String buildLabelText(@Nonnull PoiEntry poi, @Nonnull TownManager tm) {
        StringBuilder sb = new StringBuilder();
        sb.append("POI ").append(poi.getX()).append(",").append(poi.getY()).append(",").append(poi.getZ());
        TownRecord town = tm.getTown(poi.getTownId());
        ConstructionDefinition def = null;
        UUID plotUuid = poi.getPlotId();
        if (town != null && plotUuid != null) {
            PlotInstance plot = town.findPlotById(plotUuid);
            if (plot != null) {
                def = plugin.getConstructionCatalog().get(plot.getConstructionId());
            }
        }
        if (town != null && def != null) {
            Vector3i local = PoiPrefabCoords.tryLocalFromWorld(poi, town, def);
            if (local != null) {
                sb.append(" | L ").append(local.x).append(",").append(local.y).append(",").append(local.z);
            }
        }
        return sb.toString();
    }

    @Nullable
    private static ModelAsset resolveMarkerModelAsset() {
        return ModelAsset.getAssetMap().getAsset("NPC_Spawn_Marker");
    }

    @Nonnull
    private static String truncate(@Nonnull String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
