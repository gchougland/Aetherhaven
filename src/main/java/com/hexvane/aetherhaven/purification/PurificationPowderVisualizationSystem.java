package com.hexvane.aetherhaven.purification;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * While holding {@link AetherhavenConstants#ITEM_PURIFICATION_POWDER}, shows particles and a spinning model at nearby
 * spawn beacons and spawn markers. Spawn markers with two or more resolvable mob roles in their weighted list cycle the
 * preview model every {@link PurificationSpawnSupport#SPAWN_MARKER_PREVIEW_MODEL_CYCLE_TICKS} world ticks.
 */
public final class PurificationPowderVisualizationSystem extends EntityTickingSystem<EntityStore> {
    private static final double VIZ_RANGE = 96.0;
    private static final float SPIN_RAD_PER_SEC = 1.1f;
    private static final double PREVIEW_Y_OFFSET = 0.2;
    private static final int PARTICLE_EVERY_TICKS = 10;
    private static final float PREVIEW_MODEL_SCALE_EPS = 1.0e-4f;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    @Nonnull
    private final List<PurificationSpawnSupport.Target> mergedScratch = new ArrayList<>();

    public PurificationPowderVisualizationSystem(@Nonnull AetherhavenPlugin plugin) {
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
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        boolean holding =
            hand != null
                && !hand.isEmpty()
                && AetherhavenConstants.ITEM_PURIFICATION_POWDER.equals(hand.getItemId());
        if (!holding) {
            clearPreviewsIfPresent(world, store, commandBuffer, playerRef);
            return;
        }
        ensureState(commandBuffer, playerRef);
        PurificationPowderPlayerComponent st = commandBuffer.getComponent(playerRef, PurificationPowderPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }
        TransformComponent ptc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (ptc == null) {
            return;
        }
        Vector3d ppos = ptc.getPosition();
        long wtick = world.getTick();
        if (wtick - st.getLastLegacyScanTick() >= PurificationSpawnSupport.getLegacyScanIntervalTicks()) {
            st.setLastLegacyScanTick(wtick);
            st.clearCachedLegacy();
            PurificationSpawnSupport.collectLegacyInRange(store, ppos, VIZ_RANGE, st.getCachedLegacy());
        }
        PurificationSpawnSupport.collectInRange(store, ppos, VIZ_RANGE, st.getCachedLegacy(), mergedScratch);
        for (Iterator<Map.Entry<UUID, UUID>> it = st.getSpawnEntityIdToPreviewEntityId().entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, UUID> e = it.next();
            if (!st.getPendingPreviewSpawn().contains(e.getKey()) && !containsSpawn(store, e.getKey(), mergedScratch)) {
                final UUID prevId = e.getValue();
                it.remove();
                world.execute(() -> removePreviewByUuid(world, prevId));
            }
        }

        UUIDComponent ownerU = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (ownerU == null) {
            return;
        }
        final UUID playerEntityUuid = ownerU.getUuid();

        float dyaw = SPIN_RAD_PER_SEC * dt;
        for (PurificationSpawnSupport.Target t : mergedScratch) {
            Ref<EntityStore> sref = t.ref();
            if (sref == null || !sref.isValid()) {
                continue;
            }
            @Nullable
            UUID spawnKey = PurificationSpawnSupport.spawnKey(store, sref);
            if (spawnKey == null) {
                continue;
            }
            if (wtick % PARTICLE_EVERY_TICKS == 0) {
                ParticleUtil.spawnParticleEffect(
                    PurificationSpawnSupport.PARTICLE_AURA_SYSTEM_ID,
                    t.position(),
                    store
                );
            }
            @Nullable
            Model model = PurificationSpawnSupport.toPreviewDisplayModel(
                PurificationSpawnSupport.resolveCyclingPreviewModel(
                    store,
                    t,
                    wtick,
                    PurificationSpawnSupport.SPAWN_MARKER_PREVIEW_MODEL_CYCLE_TICKS
                )
            );
            if (model == null) {
                continue;
            }
            Vector3d base = t.position();
            Vector3d pposM = new Vector3d(base.getX(), base.getY() + PREVIEW_Y_OFFSET, base.getZ());
            @Nullable
            UUID existingPrev = st.getSpawnEntityIdToPreviewEntityId().get(spawnKey);
            if (existingPrev == null) {
                if (st.getPendingPreviewSpawn().add(spawnKey)) {
                    final Vector3d posCopy = new Vector3d(pposM);
                    final Model mcopy = model;
                    world.execute(() -> spawnOnePreview(world, playerEntityUuid, spawnKey, posCopy, mcopy));
                }
                continue;
            }
            Ref<EntityStore> prevRef = world.getEntityRef(existingPrev);
            if (prevRef == null || !prevRef.isValid()) {
                st.getSpawnEntityIdToPreviewEntityId().remove(spawnKey);
                if (st.getPendingPreviewSpawn().add(spawnKey)) {
                    final Vector3d posCopy = new Vector3d(pposM);
                    final Model mcopy = model;
                    world.execute(() -> spawnOnePreview(world, playerEntityUuid, spawnKey, posCopy, mcopy));
                }
                continue;
            }
            TransformComponent tco = store.getComponent(prevRef, TransformComponent.getComponentType());
            if (tco != null) {
                Vector3f r = tco.getRotation();
                float y = r.getYaw() + dyaw;
                Vector3f nr = new Vector3f(r.getPitch(), y, r.getRoll());
                commandBuffer.putComponent(prevRef, TransformComponent.getComponentType(), new TransformComponent(tco.getPosition(), nr));
                commandBuffer.putComponent(prevRef, HeadRotation.getComponentType(), new HeadRotation(nr));
            }
            ModelComponent mc = store.getComponent(prevRef, ModelComponent.getComponentType());
            @Nullable
            Model cur = mc == null ? null : mc.getModel();
            @Nullable
            String newAssetId = model.getModelAssetId();
            @Nullable
            String curAssetId = cur == null ? null : cur.getModelAssetId();
            float newSc = model.getScale();
            float curSc = cur == null ? -1.0F : cur.getScale();
            boolean sameAsset = Objects.equals(curAssetId, newAssetId);
            boolean sameScale = sameAsset && Math.abs(curSc - newSc) <= PREVIEW_MODEL_SCALE_EPS;
            if (!sameAsset || !sameScale) {
                commandBuffer.putComponent(prevRef, ModelComponent.getComponentType(), new ModelComponent(model));
                commandBuffer.putComponent(prevRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            }
        }
    }

    private static boolean containsSpawn(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID spawnKey,
        @Nonnull List<PurificationSpawnSupport.Target> list
    ) {
        for (PurificationSpawnSupport.Target t : list) {
            Ref<EntityStore> r = t.ref();
            if (r == null || !r.isValid()) {
                continue;
            }
            UUID u = PurificationSpawnSupport.spawnKey(store, r);
            if (spawnKey.equals(u)) {
                return true;
            }
        }
        return false;
    }

    private static void ensureState(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        if (commandBuffer.getComponent(playerRef, PurificationPowderPlayerComponent.getComponentType()) == null) {
            commandBuffer.addComponent(
                playerRef,
                PurificationPowderPlayerComponent.getComponentType(),
                new PurificationPowderPlayerComponent()
            );
        }
    }

    private static void clearPreviewsIfPresent(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        PurificationPowderPlayerComponent st = commandBuffer.getComponent(playerRef, PurificationPowderPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }
        for (UUID previewId : new ArrayList<>(st.getSpawnEntityIdToPreviewEntityId().values())) {
            world.execute(() -> removePreviewByUuid(world, previewId));
        }
        st.getSpawnEntityIdToPreviewEntityId().clear();
        st.getPendingPreviewSpawn().clear();
        st.clearCachedLegacy();
        st.setLastLegacyScanTick(-1L);
    }

    private static void removePreviewByUuid(@Nonnull World world, @Nullable UUID entityUuid) {
        if (entityUuid == null) {
            return;
        }
        Ref<EntityStore> r = world.getEntityRef(entityUuid);
        if (r != null && r.isValid()) {
            world.getEntityStore().getStore().removeEntity(r, RemoveReason.REMOVE);
        }
    }

    private static void spawnOnePreview(
        @Nonnull World world,
        @Nonnull UUID ownerEntityUuid,
        @Nonnull UUID spawnKey,
        @Nonnull Vector3d position,
        @Nonnull Model model
    ) {
        try {
            Ref<EntityStore> pref = world.getEntityRef(ownerEntityUuid);
            if (pref == null || !pref.isValid()) {
                return;
            }
            Store<EntityStore> pstore = pref.getStore();
            PurificationPowderPlayerComponent stc = pstore.getComponent(pref, PurificationPowderPlayerComponent.getComponentType());
            if (stc == null) {
                return;
            }
            PurificationPreviewEntity ent = new PurificationPreviewEntity();
            if (!EntityModule.get().isKnown(ent)) {
                return;
            }
            ent.loadIntoWorld(world);
            ent.setOwnerPlayerUuid(ownerEntityUuid);
            Vector3f rot = new Vector3f(0.0F, 0.0F, 0.0F);
            ent.unloadFromWorld();
            Holder<EntityStore> holder = ent.toHolder();
            HeadRotation head = holder.ensureAndGetComponent(HeadRotation.getComponentType());
            head.teleportRotation(rot);
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rot));
            holder.ensureComponent(UUIDComponent.getComponentType());
            pstore = world.getEntityStore().getStore();
            pstore.addEntity(holder, AddReason.SPAWN);
            Ref<EntityStore> bref = ent.getReference();
            if (bref == null || !bref.isValid()) {
                return;
            }
            pstore.putComponent(bref, ModelComponent.getComponentType(), new ModelComponent(model));
            pstore.putComponent(bref, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            pstore.addComponent(bref, Intangible.getComponentType(), Intangible.INSTANCE);
            // Expose a localized interaction hint while aiming at the preview proxy.
            Interactions interactions = new Interactions(
                java.util.Map.of(InteractionType.Use, AetherhavenConstants.ROOT_INTERACTION_PURIFY_SPAWN_USE)
            );
            interactions.setInteractionHint("server.interactionHints.purifySpawn");
            pstore.putComponent(bref, Interactions.getComponentType(), interactions);
            Ref<EntityStore> pRef = world.getEntityRef(ownerEntityUuid);
            if (pRef == null || !pRef.isValid()) {
                pstore.removeEntity(bref, RemoveReason.REMOVE);
                return;
            }
            stc = pRef.getStore().getComponent(pRef, PurificationPowderPlayerComponent.getComponentType());
            if (stc == null) {
                pstore.removeEntity(bref, RemoveReason.REMOVE);
                return;
            }
            UUIDComponent u = pstore.getComponent(bref, UUIDComponent.getComponentType());
            if (u != null) {
                stc.getSpawnEntityIdToPreviewEntityId().put(spawnKey, u.getUuid());
            }
        } finally {
            Ref<EntityStore> pRef2 = world.getEntityRef(ownerEntityUuid);
            if (pRef2 != null && pRef2.isValid()) {
                PurificationPowderPlayerComponent stc2 = pRef2.getStore().getComponent(pRef2, PurificationPowderPlayerComponent.getComponentType());
                if (stc2 != null) {
                    stc2.getPendingPreviewSpawn().remove(spawnKey);
                }
            }
        }
    }
}
