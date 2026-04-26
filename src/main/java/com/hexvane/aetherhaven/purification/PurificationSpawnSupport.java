package com.hexvane.aetherhaven.purification;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.common.map.IWeightedMap;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawningContext;
import com.hypixel.hytale.server.spawning.SpawningPlugin;
import com.hypixel.hytale.server.spawning.assets.spawns.config.BeaconNPCSpawn;
import com.hypixel.hytale.server.spawning.assets.spawns.config.NPCSpawn;
import com.hypixel.hytale.server.spawning.assets.spawns.config.RoleSpawnParameters;
import com.hypixel.hytale.server.spawning.assets.spawnmarker.config.SpawnMarker;
import com.hypixel.hytale.server.spawning.beacons.LegacySpawnBeaconEntity;
import com.hypixel.hytale.server.spawning.beacons.SpawnBeacon;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;
import com.hypixel.hytale.server.spawning.wrappers.BeaconSpawnWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Discovers manual spawn beacons, legacy beacons, and {@link SpawnMarkerEntity} spawns; resolves preview {@link Model}.
 */
public final class PurificationSpawnSupport {
    public static final String PARTICLE_AURA_SYSTEM_ID = "Aetherhaven_Purification_Aura";

    /**
     * How long each mob preview is shown (in world ticks) when a {@link SpawnMarker} has at least two resolvable roles in
     * its weighted NPC list. At 20 ticks/s this is 4 seconds per model.
     */
    public static final int SPAWN_MARKER_PREVIEW_MODEL_CYCLE_TICKS = 80;

    /**
     * Applied to resolved preview {@link Model}s for the powder visualization (relative to the spawn system’s model
     * scale).
     */
    public static final float PREVIEW_MODEL_DISPLAY_SCALE = 1.0F / 3.0F;

    private static final Map<String, List<String>> SPAWN_MARKER_CYCLE_ROLE_NAMES = new ConcurrentHashMap<>();

    public enum Kind {
        MANUAL_BEACON,
        LEGACY_BEACON,
        SPAWN_MARKER
    }

    public record Target(@Nonnull Ref<EntityStore> ref, @Nonnull Kind kind, @Nonnull Vector3d position) {

        public double distSqTo(@Nonnull Vector3d p) {
            double dx = position.getX() - p.getX();
            double dy = position.getY() - p.getY();
            double dz = position.getZ() - p.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final int LEGACY_SCAN_EVERY_TICKS = 5;

    private PurificationSpawnSupport() {}

    public static int getLegacyScanIntervalTicks() {
        return LEGACY_SCAN_EVERY_TICKS;
    }

    /**
     * Merges spatial (manual beacons, spawn markers) and optional pre-scanned legacy beacon list in range of {@code
     * center} (squared range).
     */
    public static void collectInRange(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d center,
        double range,
        @Nonnull List<PurificationSpawnSupport.Target> legacyInRange,
        @Nonnull List<PurificationSpawnSupport.Target> out
    ) {
        out.clear();
        Set<Ref<EntityStore>> seen = new HashSet<>();
        double rangeSq = range * range;
        SpawningPlugin sp = SpawningPlugin.get();
        if (sp != null) {
            collectFromSpatial(
                store,
                center,
                rangeSq,
                sp.getManualSpawnBeaconSpatialResource(),
                Kind.MANUAL_BEACON,
                seen,
                out
            );
            collectFromSpatial(
                store,
                center,
                rangeSq,
                sp.getSpawnMarkerSpatialResource(),
                Kind.SPAWN_MARKER,
                seen,
                out
            );
        }
        for (Target t : legacyInRange) {
            if (t.distSqTo(center) <= rangeSq) {
                Ref<EntityStore> r = t.ref();
                if (r != null && r.isValid() && !seen.contains(r)) {
                    seen.add(r);
                    out.add(t);
                }
            }
        }
    }

    private static void collectFromSpatial(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d center,
        double rangeSq,
        @Nonnull com.hypixel.hytale.component.ResourceType<
            EntityStore,
            SpatialResource<Ref<EntityStore>, EntityStore>
        > spatialType,
        @Nonnull Kind kind,
        @Nonnull Set<Ref<EntityStore>> seen,
        @Nonnull List<Target> out
    ) {
        SpatialResource<Ref<EntityStore>, EntityStore> res = store.getResource(spatialType);
        if (res == null) {
            return;
        }
        double range = Math.sqrt(rangeSq);
        List<Ref<EntityStore>> list = SpatialResource.getThreadLocalReferenceList();
        res.getSpatialStructure().collect(center, range, list);
        for (Ref<EntityStore> r : list) {
            if (r == null || !r.isValid() || seen.contains(r)) {
                continue;
            }
            TransformComponent tc = store.getComponent(r, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            if (center.distanceSquaredTo(tc.getPosition()) > rangeSq) {
                continue;
            }
            if (Kind.MANUAL_BEACON == kind && store.getComponent(r, SpawnBeacon.getComponentType()) == null) {
                continue;
            }
            if (Kind.SPAWN_MARKER == kind && store.getComponent(r, SpawnMarkerEntity.getComponentType()) == null) {
                continue;
            }
            seen.add(r);
            out.add(new Target(r, kind, new Vector3d(tc.getPosition())));
        }
    }

    /**
     * All legacy beacons in loaded chunks, filtered to range (for periodic scan).
     */
    public static void collectLegacyInRange(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d center,
        double range,
        @Nonnull List<Target> out
    ) {
        out.clear();
        double rangeSq = range * range;
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> cons =
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> r = archetypeChunk.getReferenceTo(i);
                    if (r == null || !r.isValid()) {
                        continue;
                    }
                    LegacySpawnBeaconEntity leg = store.getComponent(r, LegacySpawnBeaconEntity.getComponentType());
                    if (leg == null) {
                        continue;
                    }
                    TransformComponent tc = store.getComponent(r, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    if (center.distanceSquaredTo(tc.getPosition()) > rangeSq) {
                        continue;
                    }
                    out.add(new Target(r, Kind.LEGACY_BEACON, new Vector3d(tc.getPosition())));
                }
            };
        store.forEachChunk(Query.and(LegacySpawnBeaconEntity.getComponentType(), TransformComponent.getComponentType()), cons);
    }

    /**
     * Full list for item interaction (one-shot, includes legacy without throttle).
     */
    public static void collectAllInRange(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d center,
        double range,
        @Nonnull List<Target> out
    ) {
        out.clear();
        double rangeSq = range * range;
        Set<Ref<EntityStore>> seen = new HashSet<>();
        SpawningPlugin sp = SpawningPlugin.get();
        if (sp != null) {
            collectFromSpatial(
                store,
                center,
                rangeSq,
                sp.getManualSpawnBeaconSpatialResource(),
                Kind.MANUAL_BEACON,
                seen,
                out
            );
            collectFromSpatial(
                store,
                center,
                rangeSq,
                sp.getSpawnMarkerSpatialResource(),
                Kind.SPAWN_MARKER,
                seen,
                out
            );
        }
        List<Target> legacy = new ArrayList<>();
        collectLegacyInRange(store, center, range, legacy);
        for (Target t : legacy) {
            Ref<EntityStore> r = t.ref();
            if (r != null && r.isValid() && !seen.contains(r)) {
                seen.add(r);
                out.add(t);
            }
        }
    }

    @Nullable
    public static Target findNearest(
        @Nonnull List<Target> candidates,
        @Nonnull Vector3d point,
        double maxDistance
    ) {
        double best = maxDistance * maxDistance;
        Target bestT = null;
        for (Target t : candidates) {
            double d2 = t.distSqTo(point);
            if (d2 < best) {
                best = d2;
                bestT = t;
            }
        }
        return bestT;
    }

    /**
     * Shrink the spinning preview to {@link #PREVIEW_MODEL_DISPLAY_SCALE} of the resolved source model (keeps
     * attachments/scale from spawning context, then re-scales from the same {@link ModelAsset}).
     */
    @Nullable
    public static Model toPreviewDisplayModel(@Nullable Model source) {
        if (source == null) {
            return null;
        }
        String assetId = source.getModelAssetId();
        if (assetId == null) {
            return source;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(assetId);
        if (asset == null) {
            return source;
        }
        float s = source.getScale() * PREVIEW_MODEL_DISPLAY_SCALE;
        if (s <= 0.0F) {
            s = PREVIEW_MODEL_DISPLAY_SCALE;
        }
        return Model.createScaledModel(asset, s, source.getRandomAttachmentIds());
    }

    @Nullable
    public static Model resolvePreviewModel(@Nonnull Store<EntityStore> store, @Nonnull Target t) {
        SpawningPlugin sp = SpawningPlugin.get();
        Model fallback = sp != null ? sp.getSpawnMarkerModel() : null;
        return switch (t.kind()) {
            case MANUAL_BEACON -> {
                SpawnBeacon b = store.getComponent(t.ref(), SpawnBeacon.getComponentType());
                if (b == null) {
                    yield fallback;
                }
                yield beaconModel(b.getSpawnWrapper(), fallback);
            }
            case LEGACY_BEACON -> {
                LegacySpawnBeaconEntity e = store.getComponent(t.ref(), LegacySpawnBeaconEntity.getComponentType());
                if (e == null) {
                    yield fallback;
                }
                yield beaconModel(e.getSpawnWrapper(), fallback);
            }
            case SPAWN_MARKER -> {
                SpawnMarkerEntity me = store.getComponent(t.ref(), SpawnMarkerEntity.getComponentType());
                if (me == null) {
                    yield fallback;
                }
                yield spawnMarkerModel(me.getSpawnMarkerId(), fallback);
            }
        };
    }

    /**
     * Preview model for viz: {@link #resolvePreviewModel} for beacons; for spawn markers with 2+ resolvable weighted NPCs,
     * cycles the shown mob model every {@code modelCyclePeriodTicks} based on {@code worldTick}. Otherwise same as
     * {@link #resolvePreviewModel}.
     */
    @Nullable
    public static Model resolveCyclingPreviewModel(
        @Nonnull Store<EntityStore> store,
        @Nonnull Target t,
        long worldTick,
        int modelCyclePeriodTicks
    ) {
        if (t.kind() != Kind.SPAWN_MARKER) {
            return resolvePreviewModel(store, t);
        }
        SpawnMarkerEntity me = store.getComponent(t.ref(), SpawnMarkerEntity.getComponentType());
        if (me == null) {
            return resolvePreviewModel(store, t);
        }
        String markerId = me.getSpawnMarkerId();
        List<String> cycle = getOrBuildSpawnMarkerCycleRoleNames(markerId);
        if (cycle.size() < 2) {
            return resolvePreviewModel(store, t);
        }
        SpawningPlugin sp = SpawningPlugin.get();
        Model spFallback = sp != null ? sp.getSpawnMarkerModel() : null;
        int period = Math.max(1, modelCyclePeriodTicks);
        int slot = (int) ((worldTick / period) % (long) cycle.size());
        String role = cycle.get(slot);
        return modelForRoleName(role, markerCreativeModelFromAsset(markerId, spFallback));
    }

    @Nonnull
    private static List<String> getOrBuildSpawnMarkerCycleRoleNames(@Nonnull String markerId) {
        return SPAWN_MARKER_CYCLE_ROLE_NAMES.computeIfAbsent(markerId, id -> {
            SpawnMarker sm = SpawnMarker.getAssetMap().getAsset(id);
            if (sm == null) {
                return List.of();
            }
            IWeightedMap<SpawnMarker.SpawnConfiguration> w = sm.getWeightedConfigurations();
            if (w == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (SpawnMarker.SpawnConfiguration cfg : w.toArray()) {
                if (cfg == null) {
                    continue;
                }
                String npc = cfg.getNpc();
                if (npc == null || npc.isEmpty()) {
                    continue;
                }
                if (modelForRoleName(npc, null) != null) {
                    out.add(npc);
                }
            }
            if (out.isEmpty()) {
                return List.of();
            }
            return Collections.unmodifiableList(out);
        });
    }

    @Nullable
    private static Model markerCreativeModelFromAsset(@Nonnull String markerId, @Nullable Model outerFallback) {
        SpawnMarker sm = SpawnMarker.getAssetMap().getAsset(markerId);
        if (sm == null) {
            return outerFallback;
        }
        String m = sm.getModel();
        if (m == null || m.isBlank()) {
            return outerFallback;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(m);
        if (asset == null) {
            return outerFallback;
        }
        return Model.createUnitScaleModel(asset);
    }

    @Nullable
    private static Model beaconModel(@Nullable BeaconSpawnWrapper wrap, @Nullable Model fallback) {
        if (wrap == null) {
            return fallback;
        }
        BeaconNPCSpawn sp = wrap.getSpawn();
        if (sp == null) {
            return fallback;
        }
        @Nullable
        Model fromNpc = firstNpcPreviewModelFromSpawn(sp);
        if (fromNpc != null) {
            return fromNpc;
        }
        String m = sp.getModel();
        if (m == null || m.isBlank()) {
            return fallback;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(m);
        if (asset == null) {
            return fallback;
        }
        return Model.createUnitScaleModel(asset);
    }

    /**
     * Uses the first weighted NPC role that resolves to a spawn model (same family of logic as {@link
     * com.hypixel.hytale.server.spawning.beacons.SpawnBeacon} / spawn jobs). {@link BeaconNPCSpawn#getModel()} is the
     * <em>beacon</em> placeholder in-world, not the mob, so it is not used for preview when NPCs are present.
     */
    @Nullable
    private static Model firstNpcPreviewModelFromSpawn(@Nonnull NPCSpawn spawn) {
        RoleSpawnParameters[] npcs = spawn.getNPCs();
        if (npcs == null) {
            return null;
        }
        for (RoleSpawnParameters p : npcs) {
            if (p == null) {
                continue;
            }
            String id = p.getId();
            if (id == null || id.isBlank()) {
                continue;
            }
            @Nullable
            Model m = modelForRoleName(id, null);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    @Nullable
    private static Model modelForRoleName(@Nullable String roleName, @Nullable Model fallback) {
        if (roleName == null || roleName.isBlank()) {
            return fallback;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return fallback;
        }
        int roleIndex = npc.getIndex(roleName.trim());
        if (roleIndex < 0) {
            return fallback;
        }
        @Nullable
        Builder<Role> roleBuilder = npc.tryGetCachedValidRole(roleIndex);
        if (!(roleBuilder instanceof ISpawnableWithModel spawnable)) {
            return fallback;
        }
        SpawningContext ctx = new SpawningContext();
        try {
            if (!ctx.setSpawnable(spawnable, true)) {
                return fallback;
            }
            @Nullable
            Model model = ctx.getModel();
            return model != null ? model : fallback;
        } finally {
            ctx.releaseFull();
        }
    }

    @Nullable
    private static Model spawnMarkerModel(@Nonnull String markerId, @Nullable Model fallback) {
        SpawnMarker sm = SpawnMarker.getAssetMap().getAsset(markerId);
        if (sm == null) {
            return fallback;
        }
        IWeightedMap<SpawnMarker.SpawnConfiguration> weighted = sm.getWeightedConfigurations();
        if (weighted != null) {
            for (SpawnMarker.SpawnConfiguration cfg : weighted.toArray()) {
                if (cfg == null) {
                    continue;
                }
                String npc = cfg.getNpc();
                if (npc == null || npc.isEmpty()) {
                    continue;
                }
                @Nullable
                Model m = modelForRoleName(npc, null);
                if (m != null) {
                    return m;
                }
            }
        }
        String m = sm.getModel();
        if (m == null || m.isBlank()) {
            return fallback;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(m);
        if (asset == null) {
            return fallback;
        }
        return Model.createUnitScaleModel(asset);
    }

    @Nullable
    public static UUID spawnKey(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        UUIDComponent u = store.getComponent(ref, UUIDComponent.getComponentType());
        return u != null ? u.getUuid() : null;
    }
}
