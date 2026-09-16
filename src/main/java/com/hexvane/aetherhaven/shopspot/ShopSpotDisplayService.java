package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class ShopSpotDisplayService {
    private static final float DISPLAY_SCALE = 0.85f;
    private static final double Y_OFFSET = 0.22;

    private ShopSpotDisplayService() {}

    /**
     * Runtime-only floating item props spawned above configured stalls; never save these into prefabs.
     * Distinguished from entity-tool item props by {@link Intangible}, which shop displays always have.
     */
    public static boolean isRuntimeShopDisplayProp(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (!ref.isValid()) {
            return false;
        }
        return isRuntimeShopDisplayPropHolder(
            store.getComponent(ref, ItemComponent.getComponentType()) != null,
            store.getComponent(ref, PreventPickup.getComponentType()) != null,
            store.getComponent(ref, PreventItemMerging.getComponentType()) != null,
            store.getComponent(ref, Intangible.getComponentType()) != null
        );
    }

    public static boolean isRuntimeShopDisplayProp(@Nonnull Holder<EntityStore> holder) {
        return isRuntimeShopDisplayPropHolder(
            holder.getComponent(ItemComponent.getComponentType()) != null,
            holder.getComponent(PreventPickup.getComponentType()) != null,
            holder.getComponent(PreventItemMerging.getComponentType()) != null,
            holder.getComponent(Intangible.getComponentType()) != null
        );
    }

    static boolean isRuntimeShopDisplayPropHolder(
        boolean hasItem,
        boolean hasPreventPickup,
        boolean hasPreventMerging,
        boolean hasIntangible
    ) {
        return hasItem && hasPreventPickup && hasPreventMerging && hasIntangible;
    }

    public static void syncDisplay(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town
    ) {
        syncDisplay(world, store, null, plugin, registry, record, town);
    }

    public static void syncDisplay(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town
    ) {
        queueUpdate(world, plugin, registry, record, town);
    }

    private static void syncDisplayNow(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town,
        @Nonnull DisplayBatch batch
    ) {
        EntityWriter writer = new EntityWriter(store);
        if (!isSpotChunkLoaded(world, record)) {
            record.markDisplayReconciled(null);
            removeDisplayNow(world, store, plugin, registry, record, batch);
            return;
        }
        if (!batch.shouldShow(record, town)) {
            removeDisplayNow(world, store, plugin, registry, record, batch);
            return;
        }
        String itemId = record.getItemId();
        if (itemId == null) {
            removeDisplayNow(world, store, plugin, registry, record, batch);
            return;
        }
        reconcileDisplayEntities(world, store, record, batch);
        Vector3d pos = blockCenter(record);
        pos.y += Y_OFFSET;
        String signature = ShopSpotJewelrySupport.listingDisplaySignature(itemId, record);
        UUID existing = record.getDisplayEntityUuid();
        if (existing != null) {
            Ref<EntityStore> ref = findEntityByUuid(store, existing);
            if (ref != null && ref.isValid() && signature.equals(record.getListingDisplaySignature())) {
                updateTransform(world, store, ref, pos, record);
                return;
            }
        }
        removeDisplayNow(world, store, plugin, registry, record, batch);
        Ref<EntityStore> spawned = spawnDisplay(writer, world, record, itemId, pos);
        if (spawned != null && spawned.isValid()) {
            record.setListingDisplaySignature(signature);
            UUIDComponent uc = store.getComponent(spawned, UUIDComponent.getComponentType());
            if (uc != null) {
                record.setDisplayEntityUuid(uc.getUuid());
            }
        }
    }

    public static void syncAllInWorld(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry
    ) {
        var tm = com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (ShopSpotRecord record : registry.allRecords()) {
            TownRecord town = tm.getTown(record.getTownId());
            if (town != null) {
                syncDisplay(world, store, plugin, registry, record, town);
            } else {
                removeDisplay(world, store, plugin, registry, record);
            }
        }
    }

    public static void removeDisplay(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record
    ) {
        removeDisplay(world, store, null, plugin, registry, record);
    }

    public static void removeDisplay(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record
    ) {
        queueUpdate(world, plugin, registry, record, null);
    }

    /** Synchronous display teardown for plot relocation/removal on the world thread. */
    public static void removeDisplayImmediate(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record
    ) {
        registry.displayUpdates.cancel(record);
        removeDisplayNow(world, store, plugin, registry, record, new DisplayBatch(store, registry));
    }

    private static void removeDisplayNow(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record,
        @Nonnull DisplayBatch batch
    ) {
        EntityWriter writer = new EntityWriter(store);
        reconcileDisplayEntities(world, store, record, batch);
        UUID id = record.getDisplayEntityUuid();
        record.setDisplayEntityUuid(null);
        record.setListingDisplaySignature(null);
        if (id == null) {
            return;
        }
        Ref<EntityStore> ref = findEntityByUuid(store, id);
        if (ref != null && ref.isValid()) {
            writer.removeEntity(ref);
        }
    }

    /** Coalesce overlapping tick, clock and purchase requests outside ECS processing. */
    private static void queueUpdate(World world, AetherhavenPlugin plugin, ShopSpotRegistry registry,
                                    ShopSpotRecord record, TownRecord town) {
        if (!registry.displayUpdates.offer(record, town)) return;
        world.execute(() -> {
            var updates = registry.displayUpdates.drain();
            if (!world.isAlive()) return;
            var store = world.getEntityStore().getStore();
            if (store == null) return;
            var batch = new DisplayBatch(store, registry);
            for (var update : updates) {
                if (update.removal()) {
                    removeDisplayNow(world, store, plugin, registry, update.record(), batch);
                } else if (registry.get(update.record().getSpotId()) == update.record()) {
                    // Removed/replaced stalls must never be respawned by stale queued work.
                    syncDisplayNow(world, store, plugin, registry, update.record(), update.town(), batch);
                }
            }
        });
    }

    /** Removes stale floating item props at this stall (e.g. after server restart). */
    public static void purgeOrphanDisplayEntities(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRegistry registry,
        @Nonnull ShopSpotRecord record
    ) {
        record.markDisplayReconciled(null);
        reconcileDisplayEntities(world, store, record, new DisplayBatch(store, registry));
    }

    private static void reconcileDisplayEntities(World world, Store<EntityStore> store,
                                                  ShopSpotRecord record, DisplayBatch batch) {
        var chunk = activeDisplaySection(world, record);
        if (chunk == null) {
            record.markDisplayReconciled(null);
            return;
        }
        if (!record.needsDisplayReconciliation(chunk)) return;
        batch.purgeOrphans(record);
        record.markDisplayReconciled(chunk);
    }

    /** Lazy per-batch indexes: at most one villager scan and one display scan, never one per stall. */
    private static final class DisplayBatch {
        private final Store<EntityStore> store;
        private final ShopSpotRegistry registry;
        private Set<ShopSpotOpenService.Assignment> staffing;
        private Map<Long, List<Ref<EntityStore>>> displayCandidates;
        private record Workplace(UUID townId, UUID plotId) {}
        private final Map<Workplace, Boolean> staffedPlots = new HashMap<>();

        DisplayBatch(Store<EntityStore> store, ShopSpotRegistry registry) {
            this.store = store;
            this.registry = registry;
        }

        boolean shouldShow(ShopSpotRecord record, TownRecord town) {
            if (!record.hasStock()) return false;
            if (record.isPlayerControlled()) return true;
            if (!ShopSpotOpenService.isGameDay(store)) return false;
            return staffedPlots.computeIfAbsent(new Workplace(town.getTownId(), record.getPlotId()), key ->
                ShopSpotOpenService.hasStaffedWorkplace(record, town, kind -> {
                    if (staffing == null) staffing = ShopSpotOpenService.captureStaffing(store);
                    return staffing.contains(new ShopSpotOpenService.Assignment(key.townId(), key.plotId(), kind));
                }));
        }

        void purgeOrphans(ShopSpotRecord record) {
            if (displayCandidates == null) {
                displayCandidates = new HashMap<>();
                Set<UUID> protectedIds = collectDisplayEntityIds(registry);
                store.forEachChunk(Query.and(ItemComponent.getComponentType(), PreventPickup.getComponentType(),
                        PreventItemMerging.getComponentType(), Intangible.getComponentType(),
                        UUIDComponent.getComponentType(), TransformComponent.getComponentType()),
                    (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> ignored) -> {
                        for (int i = 0; i < chunk.size(); i++) {
                            var ref = chunk.getReferenceTo(i);
                            if (!ref.isValid() || protectedIds.contains(chunk.getComponent(i, UUIDComponent.getComponentType()).getUuid())) continue;
                            var p = chunk.getComponent(i, TransformComponent.getComponentType()).getPosition();
                            long key = ChunkUtil.indexChunkFromBlock((int)Math.floor(p.x), (int)Math.floor(p.z));
                            displayCandidates.computeIfAbsent(key, k -> new ArrayList<>()).add(ref);
                        }
                    });
            }
            // Include neighboring columns for stalls placed against a chunk edge.
            Set<Long> columns = new LinkedHashSet<>();
            for (int dx : new int[]{-2, 0, 2}) for (int dz : new int[]{-2, 0, 2})
                columns.add(ChunkUtil.indexChunkFromBlock(record.getBlockX()+dx, record.getBlockZ()+dz));
            for (long column : columns) for (var ref : displayCandidates.getOrDefault(column, List.of())) {
                if (!ref.isValid()) continue;
                var transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null && isOrphanAtSpot(record, transform.getPosition()))
                    new EntityWriter(store).removeEntity(ref);
            }
        }
    }

    static boolean isOrphanAtSpot(ShopSpotRecord record, Vector3d position) {
        return Math.abs(position.x - (record.getBlockX() + .5)) <= 1.25
            && Math.abs(position.y - record.getBlockY()) <= 2.0
            && Math.abs(position.z - (record.getBlockZ() + .5)) <= 1.25;
    }

    @Nonnull
    private static Set<UUID> collectDisplayEntityIds(@Nonnull ShopSpotRegistry registry) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (ShopSpotRecord r : registry.allRecords()) {
            UUID displayId = r.getDisplayEntityUuid();
            if (displayId != null) {
                ids.add(displayId);
            }
        }
        return ids;
    }

    /** Non-ticking sections immediately unload spawned items; never recreate displays in them every refresh. */
    static boolean isSpotChunkLoaded(@Nonnull World world, @Nonnull ShopSpotRecord record) {
        return activeDisplaySection(world, record) != null;
    }

    @Nullable
    private static Ref<ChunkStore> activeDisplaySection(World world, ShopSpotRecord record) {
        var chunks = world.getChunkStore();
        var section = chunks.getChunkSectionReferenceAtBlock(record.getBlockX(), record.getBlockY(), record.getBlockZ());
        if (section == null || !section.isValid()) return null;
        return chunks.getStore().getArchetype(section).contains(ChunkStore.REGISTRY.getNonTickingComponentType())
            ? null : section;
    }

    @Nonnull
    private static Vector3d blockCenter(@Nonnull ShopSpotRecord record) {
        return new Vector3d(record.getBlockX() + 0.5, record.getBlockY(), record.getBlockZ() + 0.5);
    }

    private static void updateTransform(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Vector3d pos,
        @Nonnull ShopSpotRecord record
    ) {
        Rotation3f rot = ShopSpotDisplayRotation.forRecord(world, record);
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            tc.getPosition().set(pos);
            tc.getRotation().set(rot);
        }
        HeadRotation hr = store.getComponent(ref, HeadRotation.getComponentType());
        if (hr != null) {
            hr.getRotation().set(rot);
        }
    }

    @Nullable
    private static Ref<EntityStore> spawnDisplay(
        @Nonnull EntityWriter writer,
        @Nonnull World world,
        @Nonnull ShopSpotRecord record,
        @Nonnull String itemId,
        @Nonnull Vector3d pos
    ) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return null;
        }
        Rotation3f rot = ShopSpotDisplayRotation.forRecord(world, record);
        Model model = resolveItemModel(item);
        Store<EntityStore> store = writer.store;
        Holder<EntityStore> holder = store.getRegistry().newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        ItemStack stack = ShopSpotJewelrySupport.buildDisplayStack(itemId, record);
        holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(stack));
        holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
        holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rot));
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.ensureComponent(UUIDComponent.getComponentType());
        if (model != null) {
            String modelId = resolveItemModelId(item);
            if (modelId != null) {
                holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
                holder.addComponent(
                    PersistentModel.getComponentType(),
                    new PersistentModel(new Model.ModelReference(modelId, DISPLAY_SCALE, null, true))
                );
            }
        }
        return writer.addEntity(holder);
    }

    @Nullable
    private static String resolveItemModelId(@Nonnull Item item) {
        String modelId = item.getModel();
        if (modelId == null && item.hasBlockType()) {
            BlockType blockType = BlockType.getAssetMap().getAsset(item.getId());
            if (blockType != null && blockType.getCustomModel() != null) {
                modelId = blockType.getCustomModel();
            }
        }
        return modelId;
    }

    @Nullable
    private static Model resolveItemModel(@Nonnull Item item) {
        String modelId = resolveItemModelId(item);
        if (modelId == null) {
            return null;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        return asset != null ? Model.createStaticScaledModel(asset, DISPLAY_SCALE) : null;
    }

    @Nullable
    private static Ref<EntityStore> findEntityByUuid(@Nonnull Store<EntityStore> store, @Nonnull UUID uuid) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(uuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    private static final class EntityWriter {
        private final Store<EntityStore> store;

        private EntityWriter(@Nonnull Store<EntityStore> store) {
            this.store = store;
        }

        void removeEntity(@Nonnull Ref<EntityStore> ref) {
            if (!ref.isValid()) {
                return;
            }
            store.removeEntity(ref, RemoveReason.REMOVE);
        }

        @Nullable
        Ref<EntityStore> addEntity(@Nonnull Holder<EntityStore> holder) {
            return store.addEntity(holder, AddReason.SPAWN);
        }
    }
}
