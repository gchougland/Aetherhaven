package com.hexvane.aetherhaven.poi.tool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerEntity;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerDataComponent;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerEntity;
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
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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
import java.util.concurrent.ConcurrentHashMap;
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
    private static final ConcurrentHashMap<UUID, Integer> LAST_HUD_MODE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> LAST_HOLDING_POI_TOOL = new ConcurrentHashMap<>();

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
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null || !pr.hasPermission(AetherhavenConstants.PERMISSION_POI_TOOL)) {
            removeHudIfPresent(player, pr);
            clearLabelsIfPresent(world, store, commandBuffer, playerRef);
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        boolean holding = PoiToolInteractions.isPoiToolItem(hand);
        boolean customPageOpen = player.getPageManager().getCustomPage() != null;
        boolean poiHudApplicable = holding && !customPageOpen;
        if (!poiHudApplicable) {
            removeHudIfPresent(player, pr);
            clearLabelsIfPresent(world, store, commandBuffer, playerRef);
            return;
        }

        PoiToolInteractions.ensureState(playerRef, commandBuffer);
        PoiToolPlayerComponent state = commandBuffer.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        if (state == null) {
            return;
        }
        UUID playerUuid = pr.getUuid();
        int modeOrdinal = state.getMode().ordinal();
        Integer prevMode = LAST_HUD_MODE.get(playerUuid);
        boolean modeChanged = prevMode == null || prevMode != modeOrdinal;
        if (modeChanged) {
            LAST_HUD_MODE.put(playerUuid, modeOrdinal);
            PoiToolHudSupport.obtainPoiToolHud(player, pr).refresh(state);
        } else if (!PoiToolHudSupport.isPoiToolHudActive(player)) {
            PoiToolHudSupport.obtainPoiToolHud(player, pr).refresh(state);
        }

        Boolean wasHolding = LAST_HOLDING_POI_TOOL.get(playerUuid);
        boolean holdingChanged = wasHolding == null || wasHolding != holding;
        LAST_HOLDING_POI_TOOL.put(playerUuid, holding);

        long tick = world.getTick();
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        TransformComponent t = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (t == null) {
            return;
        }
        Vector3d ppos = t.getPosition();
        List<PoiEntry> nearby = new ArrayList<>();
        if (PoiToolMarkerVisibility.showsRegistryAndPrefabPoiLabels(state.getMode())) {
            for (PoiEntry e : reg.allEntries()) {
                double dx = (e.getX() + 0.5) - ppos.x();
                double dy = (e.getY() + 0.5) - ppos.y();
                double dz = (e.getZ() + 0.5) - ppos.z();
                if (dx * dx + dy * dy + dz * dz <= VIZ_RANGE_SQ) {
                    nearby.add(e);
                }
            }
        }

        if (modeChanged || holdingChanged || tick % LABEL_REFRESH_TICKS == 0) {
            UUIDComponent ownerComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
            if (ownerComp != null) {
                List<PoiEntry> nearbyCopy = new ArrayList<>(nearby);
                PoiToolMode modeCopy = state.getMode();
                Vector3d pposCopy = new Vector3d(ppos);
                world.execute(() -> refreshVisualizationDeferred(world, ownerComp.getUuid(), nearbyCopy, modeCopy, pposCopy));
            }
        }
    }

    /** Schedules an immediate overlay refresh after mode cycle (tick may miss the change via {@link #noteHudMode}). */
    public static void scheduleRefreshForPlayer(@Nonnull World world, @Nonnull UUID playerUuid, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> refreshForPlayerNow(world, playerUuid, plugin));
    }

    private static void refreshForPlayerNow(
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull AetherhavenPlugin plugin
    ) {
        Ref<EntityStore> playerRef = world.getEntityRef(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || pr == null || !pr.hasPermission(AetherhavenConstants.PERMISSION_POI_TOOL)) {
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(store, playerRef);
        if (!PoiToolInteractions.isPoiToolItem(hand) || player.getPageManager().getCustomPage() != null) {
            return;
        }
        PoiToolPlayerComponent state = store.getComponent(playerRef, PoiToolPlayerComponent.getComponentType());
        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (state == null || tc == null) {
            return;
        }
        Vector3d ppos = tc.getPosition();
        List<PoiEntry> nearby = new ArrayList<>();
        if (PoiToolMarkerVisibility.showsRegistryAndPrefabPoiLabels(state.getMode())) {
            PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            for (PoiEntry e : reg.allEntries()) {
                double dx = (e.getX() + 0.5) - ppos.x();
                double dy = (e.getY() + 0.5) - ppos.y();
                double dz = (e.getZ() + 0.5) - ppos.z();
                if (dx * dx + dy * dy + dz * dz <= VIZ_RANGE_SQ) {
                    nearby.add(e);
                }
            }
        }
        new PoiToolVisualizationSystem(plugin).refreshVisualizationDeferred(world, playerUuid, nearby, state.getMode(), ppos);
    }

    static void noteHudMode(@Nonnull UUID playerUuid, @Nonnull PoiToolMode mode) {
        LAST_HUD_MODE.put(playerUuid, mode.ordinal());
    }

    private static void removeHudIfPresent(@Nullable Player player, @Nullable PlayerRef pr) {
        if (pr != null) {
            UUID uuid = pr.getUuid();
            LAST_HUD_MODE.remove(uuid);
            LAST_HOLDING_POI_TOOL.remove(uuid);
        }
        if (player == null || pr == null) {
            return;
        }
        if (PoiToolHudSupport.isPoiToolHudActive(player)) {
            PoiToolHudSupport.removePoiToolHud(player, pr);
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

    static void removeLabelEntities(@Nonnull World world, @Nonnull PoiToolPlayerComponent state) {
        removeLabelEntities(world, state, null);
    }

    static void removeLabelEntities(
        @Nonnull World world,
        @Nonnull PoiToolPlayerComponent state,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID id : new ArrayList<>(state.getDebugLabelEntityUuids())) {
            Ref<EntityStore> labelRef = world.getEntityRef(id);
            if (labelRef == null || !labelRef.isValid()) {
                continue;
            }
            if (commandBuffer != null) {
                commandBuffer.removeEntity(labelRef, RemoveReason.REMOVE);
            } else {
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
        @Nonnull Rotation3f rotation
    ) {
        if (!EntityModule.get().isKnown(entity)) {
            throw new IllegalArgumentException("Unknown entity");
        }
        // Caller must associate the entity with this world (e.g. loadIntoWorld) before addEntity.
        if (!world.equals(entity.getWorld())) {
            throw new IllegalStateException("Expected entity to already have its world set to " + world.getName());
        }
        if (entity.getReference() != null && entity.getReference().isValid()) {
            throw new IllegalArgumentException("Entity already has a valid EntityReference: " + entity.getReference());
        }
        if (position.y() < -32.0) {
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
    private void refreshVisualizationDeferred(
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull List<PoiEntry> nearbyRegistry,
        @Nonnull PoiToolMode mode,
        @Nonnull Vector3d playerPos
    ) {
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
        ModelAsset markerAsset = resolveMarkerModelAsset();
        if (markerAsset == null) {
            return;
        }
        Model markerModel = Model.createUnitScaleModel(markerAsset);
        Model anchorModel = Model.createScaledModel(markerAsset, NAMEPLATE_ANCHOR_MODEL_SCALE);
        UUID ownerUuid = playerUuid;
        Rotation3f defaultRot = new Rotation3f(0.0F, 0.0F, 0.0F);

        if (PoiToolMarkerVisibility.showsRegistryAndPrefabPoiLabels(mode)) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            for (PoiEntry poi : nearbyRegistry) {
                Vector3d markerPos = new Vector3d(
                    poi.getX() + POI_BLOCK_CENTER,
                    poi.getY() + POI_BLOCK_CENTER + MARKER_MODEL_Y_OFFSET,
                    poi.getZ() + POI_BLOCK_CENTER
                );
                spawnDebugMarkerPair(
                    world,
                    ownerUuid,
                    store,
                    state,
                    markerPos,
                    defaultRot,
                    buildLabelText(poi, tm),
                    markerModel,
                    anchorModel
                );
                if (poi.hasInteractionTarget()) {
                    Double tx = poi.getInteractionTargetX();
                    Double ty = poi.getInteractionTargetY();
                    Double tz = poi.getInteractionTargetZ();
                    if (tx != null && ty != null && tz != null) {
                        Vector3d targetMarkerPos = new Vector3d(
                            tx,
                            ty + POI_BLOCK_CENTER + MARKER_MODEL_Y_OFFSET,
                            tz
                        );
                        float targetYaw = poi.getInteractionTargetYawRadians() != null ? poi.getInteractionTargetYawRadians() : 0f;
                        Rotation3f targetRot = new Rotation3f(0.0F, targetYaw, 0.0F);
                        spawnDebugMarkerPair(
                            world,
                            ownerUuid,
                            store,
                            state,
                            targetMarkerPos,
                            targetRot,
                            buildInteractionTargetLabelText(poi, tm, tx, ty, tz),
                            markerModel,
                            anchorModel
                        );
                    }
                }
            }
            spawnPrefabPoiMarkerOverlays(world, store, state, ownerUuid, playerPos, markerModel, anchorModel);
        }

        if (PoiToolMarkerVisibility.showsAdventurerSpawnLabels(mode)) {
            spawnAdventurerMarkerOverlays(world, store, state, ownerUuid, playerPos, markerModel, anchorModel);
        }

        PlayerRef playerRefComp = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComp != null && PoiToolMarkerVisibility.showsRegistryAndPrefabPoiLabels(mode)) {
            for (PoiEntry poi : nearbyRegistry) {
                if (!poi.hasInteractionTarget()) {
                    continue;
                }
                Double tx = poi.getInteractionTargetX();
                Double ty = poi.getInteractionTargetY();
                Double tz = poi.getInteractionTargetZ();
                if (tx == null || ty == null || tz == null) {
                    continue;
                }
                double sx = tx;
                double sy = ty + 1.0;
                double sz = tz;
                double ex = poi.getX() + POI_BLOCK_CENTER;
                double ey = poi.getY() + POI_BLOCK_CENTER;
                double ez = poi.getZ() + POI_BLOCK_CENTER;
                PoiDebugLineHelper.addLineToPlayer(
                    playerRefComp,
                    sx,
                    sy,
                    sz,
                    ex,
                    ey,
                    ez,
                    DebugUtils.COLOR_CYAN,
                    0.06,
                    2.5F,
                    0
                );
            }
        }
    }

    private void spawnPrefabPoiMarkerOverlays(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull PoiToolPlayerComponent state,
        @Nonnull UUID ownerUuid,
        @Nonnull Vector3d playerPos,
        @Nonnull Model markerModel,
        @Nonnull Model anchorModel
    ) {
        List<PrefabMarkerOverlay> overlays = new ArrayList<>();
        store.forEachChunk(
            Query.and(PoiMarkerEntity.getComponentType(), PoiMarkerDataComponent.getComponentType(), TransformComponent.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    PoiMarkerDataComponent data = chunk.getComponent(i, PoiMarkerDataComponent.getComponentType());
                    if (tc == null || data == null) {
                        continue;
                    }
                    Vector3d p = tc.getPosition();
                    if (distanceSq(playerPos, p) > VIZ_RANGE_SQ) {
                        continue;
                    }
                    overlays.add(new PrefabMarkerOverlay(new Vector3d(p), tc.getRotation(), buildPrefabPoiLabel(data)));
                }
            }
        );
        for (PrefabMarkerOverlay overlay : overlays) {
            Vector3d markerPos = new Vector3d(
                overlay.position().x,
                overlay.position().y + MARKER_MODEL_Y_OFFSET,
                overlay.position().z
            );
            spawnDebugMarkerPair(
                world,
                ownerUuid,
                store,
                state,
                markerPos,
                overlay.rotation(),
                overlay.label(),
                markerModel,
                anchorModel
            );
        }
    }

    private void spawnAdventurerMarkerOverlays(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull PoiToolPlayerComponent state,
        @Nonnull UUID ownerUuid,
        @Nonnull Vector3d playerPos,
        @Nonnull Model markerModel,
        @Nonnull Model anchorModel
    ) {
        List<PrefabMarkerOverlay> overlays = new ArrayList<>();
        store.forEachChunk(
            Query.and(AdventurerSpawnMarkerEntity.getComponentType(), TransformComponent.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d p = tc.getPosition();
                    if (distanceSq(playerPos, p) > VIZ_RANGE_SQ) {
                        continue;
                    }
                    overlays.add(
                        new PrefabMarkerOverlay(
                            new Vector3d(p),
                            tc.getRotation(),
                            "Adventurer spot"
                        )
                    );
                }
            }
        );
        for (PrefabMarkerOverlay overlay : overlays) {
            Vector3d markerPos = new Vector3d(
                overlay.position().x,
                overlay.position().y + MARKER_MODEL_Y_OFFSET,
                overlay.position().z
            );
            spawnDebugMarkerPair(
                world,
                ownerUuid,
                store,
                state,
                markerPos,
                overlay.rotation(),
                overlay.label(),
                markerModel,
                anchorModel
            );
        }
    }

    @Nonnull
    private static String buildPrefabPoiLabel(@Nonnull PoiMarkerDataComponent data) {
        StringBuilder sb = new StringBuilder("POI spot");
        if (!data.getTags().isEmpty()) {
            sb.append(" | ").append(String.join(", ", data.getTags()));
        }
        sb.append(" | ").append(data.getInteractionKind().name());
        return sb.toString();
    }

    private static double distanceSq(@Nonnull Vector3d from, @Nonnull Vector3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record PrefabMarkerOverlay(@Nonnull Vector3d position, @Nonnull Rotation3f rotation, @Nonnull String label) {}

    private void spawnDebugMarkerPair(
        @Nonnull World world,
        @Nonnull UUID ownerUuid,
        @Nonnull Store<EntityStore> store,
        @Nonnull PoiToolPlayerComponent state,
        @Nonnull Vector3d markerPos,
        @Nonnull Rotation3f rot,
        @Nonnull String text,
        @Nonnull Model markerModel,
        @Nonnull Model anchorModel
    ) {
        PoiDebugLabelEntity markerEnt = new PoiDebugLabelEntity();
        markerEnt.loadIntoWorld(world);
        markerEnt.setOwnerPlayerUuid(ownerUuid);
        PoiDebugLabelEntity markerSpawned = addPoiDebugLabelEntity(world, markerEnt, markerPos, rot);
        if (markerSpawned == null) {
            return;
        }
        Ref<EntityStore> markerRef = markerSpawned.getReference();
        if (markerRef == null || !markerRef.isValid()) {
            return;
        }
        store.putComponent(markerRef, ModelComponent.getComponentType(), new ModelComponent(markerModel));
        store.addComponent(markerRef, Intangible.getComponentType(), Intangible.INSTANCE);
        UUIDComponent markerUc = store.getComponent(markerRef, UUIDComponent.getComponentType());
        if (markerUc != null) {
            state.getDebugLabelEntityUuids().add(markerUc.getUuid());
        }

        Vector3d nameplatePos = new Vector3d(markerPos.x(), markerPos.y() + NAMEPLATE_PIVOT_OFFSET_Y, markerPos.z());
        PoiDebugLabelEntity nameplateEnt = new PoiDebugLabelEntity();
        nameplateEnt.loadIntoWorld(world);
        nameplateEnt.setOwnerPlayerUuid(ownerUuid);
        PoiDebugLabelEntity nameplateSpawned = addPoiDebugLabelEntity(world, nameplateEnt, nameplatePos, rot);
        if (nameplateSpawned == null) {
            store.removeEntity(markerRef, RemoveReason.REMOVE);
            if (markerUc != null) {
                state.getDebugLabelEntityUuids().remove(markerUc.getUuid());
            }
            return;
        }
        Ref<EntityStore> nameplateRef = nameplateSpawned.getReference();
        if (nameplateRef == null || !nameplateRef.isValid()) {
            store.removeEntity(markerRef, RemoveReason.REMOVE);
            if (markerUc != null) {
                state.getDebugLabelEntityUuids().remove(markerUc.getUuid());
            }
            return;
        }
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

    @Nonnull
    private String buildInteractionTargetLabelText(
        @Nonnull PoiEntry poi,
        @Nonnull TownManager tm,
        double wx,
        double wy,
        double wz
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Target ")
            .append(String.format("%.2f", wx))
            .append(",")
            .append(String.format("%.2f", wy))
            .append(",")
            .append(String.format("%.2f", wz));
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
            Vector3i local = PoiPrefabCoords.tryLocalFromWorldPoint(wx, wy, wz, poi, town, def);
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
