package com.hexvane.aetherhaven.festival.market;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Shared stall inventory, floating displays, Use pad, and leftover drops. */
public final class MarketStallService {
    private static final float DISPLAY_SCALE = 0.85f;
    private static final Box STALL_BOX = new Box(-1.6, 0.0, -1.6, 1.6, 1.6, 1.6);

    private MarketStallService() {}

    public static void captureAndSpawn(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        MarketSession session = MarketSessionIndex.getOrCreate(town.getTownId());
        List<Vector3d> displays = new ArrayList<>();
        List<FestivalDefinition.OrbSpawnRow> displayRows = festival.getMarketDisplaySlots();
        int displayLimit = Math.min(displayRows.size(), MarketIds.SLOT_COUNT);
        for (int i = 0; i < displayLimit; i++) {
            FestivalDefinition.OrbSpawnRow row = displayRows.get(i);
            displays.add(cellCenteredWorldPosition(plugin, square, row.getLocalX(), row.getLocalY(), row.getLocalZ()));
        }
        session.setDisplayPositions(displays);
        List<Vector3d> stands = new ArrayList<>();
        List<FestivalDefinition.RaceStartSpotRow> standRows = festival.getMarketStands();
        int standLimit = Math.min(standRows.size(), MarketIds.STAND_COUNT);
        for (int i = 0; i < standLimit; i++) {
            FestivalDefinition.RaceStartSpotRow row = standRows.get(i);
            stands.add(cellCenteredWorldPosition(plugin, square, row.getLocalX(), row.getLocalY(), row.getLocalZ()));
        }
        session.setStandPositions(stands);
        if (!stands.isEmpty()) {
            Vector3d first = stands.get(0);
            session.setStallDropPos(new Vector3d(first.x, first.y + 0.4, first.z));
        } else if (!displays.isEmpty()) {
            session.setStallDropPos(new Vector3d(displays.get(0)));
        }
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (store == null) {
                return;
            }
            spawnPadNow(world, store, town.getTownId(), session);
            syncPlayerDisplaysNow(world, store, session);
        });
    }

    public static boolean openStall(
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull MarketSession session,
        @Nonnull UUID townId
    ) {
        SimpleItemContainer container = session.getLiveContainer();
        if (container == null) {
            container = new SimpleItemContainer((short) MarketIds.SLOT_COUNT);
            for (short i = 0; i < MarketIds.SLOT_COUNT; i++) {
                String id = session.slotItemId(i);
                int qty = session.slotQuantity(i);
                if (id != null && !id.isBlank() && qty > 0) {
                    container.setItemStackForSlot(i, new ItemStack(id, qty));
                }
            }
            session.setLiveContainer(container);
        }
        player.getPageManager().setPageWithWindows(playerRef, store, Page.Bench, true, new MarketStallWindow(container, townId));
        return true;
    }

    /** Hands the town stall goods back to the player who collects judging rewards. Once only. */
    public static void returnStallGoodsToPlayer(
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull MarketSession session,
        @Nonnull UUID townId
    ) {
        if (session.isGoodsReturned()) {
            return;
        }
        session.snapshotFromContainer(session.getLiveContainer());
        SimpleItemContainer container = session.getLiveContainer();
        if (container != null) {
            for (short i = 0; i < container.getCapacity(); i++) {
                ItemStack stack = container.getItemStack(i);
                if (stack == null || ItemStack.isEmpty(stack)) {
                    continue;
                }
                player.giveItem(stack, playerRef, store);
                container.setItemStackForSlot(i, ItemStack.EMPTY);
            }
        } else {
            for (int i = 0; i < MarketIds.SLOT_COUNT; i++) {
                String id = session.slotItemId(i);
                int qty = session.slotQuantity(i);
                if (id == null || id.isBlank() || qty <= 0) {
                    continue;
                }
                player.giveItem(new ItemStack(id, qty), playerRef, store);
            }
        }
        session.snapshotFromContainer(session.getLiveContainer());
        session.markGoodsReturned();
        World world = store.getExternalData().getWorld();
        if (world != null) {
            syncPlayerDisplays(world, townId);
        }
    }

    public static void syncPlayerDisplays(@Nonnull World world, @Nonnull UUID townId) {
        MarketSession session = MarketSessionIndex.get(townId);
        if (session == null) {
            return;
        }
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (store != null) {
                syncPlayerDisplaysNow(world, store, session);
            }
        });
    }

    public static void despawnAll(@Nonnull World world, @Nonnull UUID townId) {
        MarketSession session = MarketSessionIndex.get(townId);
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (store == null) {
                return;
            }
            if (session != null) {
                removeUuids(store, session.playerDisplayUuidsView());
                removeUuids(store, session.rivalDisplayUuidsView());
                if (session.getStallPadUuid() != null) {
                    removeUuids(store, List.of(session.getStallPadUuid()));
                }
                session.clearPlayerDisplayUuids();
                session.clearRivalDisplayUuids();
                session.setStallPadUuid(null);
            }
            removePadsByComponent(store, townId);
        });
    }

    public static void dropLeftovers(@Nonnull World world, @Nonnull UUID townId) {
        MarketSession session = MarketSessionIndex.get(townId);
        if (session == null || session.isGoodsReturned()) {
            return;
        }
        session.snapshotFromContainer(session.getLiveContainer());
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < MarketIds.SLOT_COUNT; i++) {
            String id = session.slotItemId(i);
            int qty = session.slotQuantity(i);
            if (id != null && !id.isBlank() && qty > 0) {
                stacks.add(new ItemStack(id, qty));
            }
        }
        Vector3d pos = session.getStallDropPos();
        if (pos == null) {
            pos = new Vector3d();
        }
        Vector3d dropPos = pos;
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (store == null || stacks.isEmpty()) {
                return;
            }
            Holder<EntityStore>[] holders =
                ItemComponent.generateItemDrops(store, stacks, dropPos, Rotation3f.ZERO);
            for (Holder<EntityStore> holder : holders) {
                store.addEntity(holder, AddReason.SPAWN);
            }
        });
    }

    private static void spawnPadNow(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull MarketSession session
    ) {
        if (!MarketStallComponent.isRegistered()) {
            return;
        }
        Vector3d pos = session.getStallDropPos();
        if (pos == null) {
            return;
        }
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        Rotation3f rot = new Rotation3f();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(pos), rot));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rot));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(STALL_BOX));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));
        holder.addComponent(Interactable.getComponentType(), Interactable.INSTANCE);
        holder.addComponent(Interactions.getComponentType(), MarketStallInteractSync.stallInteractions());
        MarketStallComponent pad = new MarketStallComponent();
        pad.setTownId(townId);
        holder.addComponent(MarketStallComponent.getComponentType(), pad);
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref != null && ref.isValid()) {
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                session.setStallPadUuid(uc.getUuid());
            }
        }
    }

    /**
     * Plot creator stores integer block cells. {@link FestivalPrefabSwapService#spotWorldPositionExact} without
     * centering lands on the block corner, so display items miss the marked spots.
     */
    @Nonnull
    private static Vector3d cellCenteredWorldPosition(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance square,
        double localX,
        double localY,
        double localZ
    ) {
        double x = isNearlyInteger(localX) ? Math.floor(localX) + 0.5 : localX;
        double z = isNearlyInteger(localZ) ? Math.floor(localZ) + 0.5 : localZ;
        return FestivalPrefabSwapService.spotWorldPositionExact(plugin, square, x, localY, z);
    }

    private static boolean isNearlyInteger(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0e-6;
    }

    private static void syncPlayerDisplaysNow(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull MarketSession session
    ) {
        session.snapshotFromContainer(session.getLiveContainer());
        removeUuids(store, session.playerDisplayUuidsView());
        List<UUID> spawned = new ArrayList<>();
        List<Vector3d> slots = session.displayPositionsView();
        int n = Math.min(MarketIds.SLOT_COUNT, slots.size());
        for (int i = 0; i < n; i++) {
            String id = session.slotItemId(i);
            if (id == null || id.isBlank()) {
                continue;
            }
            UUID uuid = spawnDisplayNow(store, id, slots.get(i));
            if (uuid != null) {
                spawned.add(uuid);
            }
        }
        session.setPlayerDisplayUuids(spawned);
    }

    @Nullable
    private static UUID spawnDisplayNow(
        @Nonnull Store<EntityStore> store,
        @Nonnull String itemId,
        @Nonnull Vector3d pos
    ) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return null;
        }
        Rotation3f rot = new Rotation3f();
        Holder<EntityStore> holder = store.getRegistry().newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(pos), rot));
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(new ItemStack(itemId, 1)));
        holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
        holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rot));
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.ensureComponent(UUIDComponent.getComponentType());
        Model model = resolveItemModel(item);
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
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    private static void removeUuids(@Nonnull Store<EntityStore> store, @Nonnull List<UUID> uuids) {
        for (UUID id : uuids) {
            if (id == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(id);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
    }

    private static void removePadsByComponent(@Nonnull Store<EntityStore> store, @Nonnull UUID townId) {
        if (!MarketStallComponent.isRegistered()) {
            return;
        }
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            com.hypixel.hytale.component.query.Query.and(MarketStallComponent.getComponentType()),
            (chunk, ignored) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    MarketStallComponent pad = chunk.getComponent(i, MarketStallComponent.getComponentType());
                    if (pad != null && townId.equals(pad.getTownId())) {
                        toRemove.add(chunk.getReferenceTo(i));
                    }
                }
            }
        );
        for (Ref<EntityStore> ref : toRemove) {
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
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
}
