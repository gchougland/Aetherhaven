package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSessions;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberBlockAccess;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.ShopSpotConfigPage;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ShopSpotPlaceEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop";

    private final AetherhavenPlugin plugin;

    public ShopSpotPlaceEventSystem(@Nonnull AetherhavenPlugin plugin) {
        super(PlaceBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlaceBlockEvent event
    ) {
        ItemStack hand = event.getItemInHand();
        if (hand == null || hand.isEmpty() || !AetherhavenConstants.SHOP_SPOT_ITEM_ID.equals(hand.getItemId())) {
            return;
        }
        Vector3i pos = new Vector3i(event.getTargetBlock());
        World world = store.getExternalData().getWorld();
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        boolean creative = player != null && player.getGameMode() == GameMode.Creative;
        boolean plotCreatorBounds = false;
        if (uc != null) {
            var session = PlotCreatorSessions.get(uc.getUuid());
            plotCreatorBounds = session != null && session.getDraft().isInsideBounds(pos);
        }

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownContainingBlock(world.getName(), pos.x(), pos.z());
        PlotInstance plot = town != null ? town.findCompletePlotContaining(pos.x(), pos.y(), pos.z()) : null;
        boolean inCompletePlot = plot != null && plot.getState() == PlotInstanceState.COMPLETE;

        if (!inCompletePlot && !plotCreatorBounds && !creative) {
            event.setCancelled(true);
            if (town == null) {
                send(archetypeChunk, commandBuffer, index, Message.translation(MSG + ".notInTown"));
            } else {
                send(archetypeChunk, commandBuffer, index, Message.translation(MSG + ".notInPlot"));
            }
            return;
        }

        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            event.setCancelled(true);
            return;
        }

        if (town != null && uc != null && !creative && !plotCreatorBounds) {
            if (TownMemberBlockAccess.denyIfNotMember(pr, town, uc.getUuid())) {
                event.setCancelled(true);
                return;
            }
        }

        UUID spotId = UUID.randomUUID();
        String townId = town != null ? town.getTownId().toString() : "";
        String plotId = inCompletePlot && plot != null ? plot.getPlotId().toString() : "";
        ShopSpotBlock block =
            new ShopSpotBlock(spotId.toString(), townId, plotId, false, "", false);

        if (!ShopSpotBlockUtil.writeBlockComponent(world, pos, block)) {
            world.execute(() -> finishPlacementDeferred(world, pos, spotId, town, plot, inCompletePlot, playerRef, pr));
            return;
        }
        finishPlacement(world, pos, spotId, town, plot, inCompletePlot, playerRef, pr, commandBuffer);
    }

    private void finishPlacement(
        @Nonnull World world,
        @Nonnull Vector3i pos,
        @Nonnull UUID spotId,
        @Nullable TownRecord town,
        @Nullable PlotInstance plot,
        boolean inCompletePlot,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull PlayerRef playerRefComp,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotRecord record = new ShopSpotRecord();
        record.setSpotId(spotId);
        record.setWorldName(world.getName());
        record.setBlockPosition(pos);
        if (town != null) {
            record.setTownId(town.getTownId());
        }
        if (inCompletePlot && plot != null) {
            record.setPlotId(plot.getPlotId());
        }
        record.setLootTableId("");
        record.setDisplayYawRadians(ShopSpotDisplayRotation.yawFromBlockAt(world, pos));
        registry.put(record);
        ShopSpotPersistence.save(world, plugin, registry);

        ShopSpotPlayerComponent st = commandBuffer.getComponent(playerRef, ShopSpotPlayerComponent.getComponentType());
        if (st == null) {
            st = new ShopSpotPlayerComponent();
        }
        UUID townId = town != null ? town.getTownId() : new UUID(0L, 0L);
        UUID plotId = inCompletePlot && plot != null ? plot.getPlotId() : new UUID(0L, 0L);
        st.setPendingPlacement(spotId, townId, plotId, pos);
        commandBuffer.putComponent(playerRef, ShopSpotPlayerComponent.getComponentType(), st);

        Player player = commandBuffer.getComponent(playerRef, Player.getComponentType());
        if (player != null && player.getPageManager().getCustomPage() == null) {
            player.getPageManager().openCustomPage(playerRef, commandBuffer.getStore(), new ShopSpotConfigPage(playerRefComp));
        }
    }

    private void finishPlacementDeferred(
        @Nonnull World world,
        @Nonnull Vector3i pos,
        @Nonnull UUID spotId,
        @Nullable TownRecord town,
        @Nullable PlotInstance plot,
        boolean inCompletePlot,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull PlayerRef playerRefComp
    ) {
        String townId = town != null ? town.getTownId().toString() : "";
        String plotId = inCompletePlot && plot != null ? plot.getPlotId().toString() : "";
        ShopSpotBlockUtil.writeBlockComponent(
            world,
            pos,
            new ShopSpotBlock(spotId.toString(), townId, plotId, false, "", false)
        );
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotRecord record = new ShopSpotRecord();
        record.setSpotId(spotId);
        record.setWorldName(world.getName());
        record.setBlockPosition(pos);
        if (town != null) {
            record.setTownId(town.getTownId());
        }
        if (inCompletePlot && plot != null) {
            record.setPlotId(plot.getPlotId());
        }
        record.setLootTableId("");
        record.setDisplayYawRadians(ShopSpotDisplayRotation.yawFromBlockAt(world, pos));
        registry.put(record);
        ShopSpotPersistence.save(world, plugin, registry);

        ShopSpotPlayerComponent st = store.getComponent(playerRef, ShopSpotPlayerComponent.getComponentType());
        if (st == null) {
            st = new ShopSpotPlayerComponent();
        }
        UUID pendingTownId = town != null ? town.getTownId() : new UUID(0L, 0L);
        UUID pendingPlotId = inCompletePlot && plot != null ? plot.getPlotId() : new UUID(0L, 0L);
        st.setPendingPlacement(spotId, pendingTownId, pendingPlotId, pos);
        store.putComponent(playerRef, ShopSpotPlayerComponent.getComponentType(), st);

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null && player.getPageManager().getCustomPage() == null) {
            player.getPageManager().openCustomPage(playerRef, store, new ShopSpotConfigPage(playerRefComp));
        }
    }

    private void send(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        int index,
        @Nonnull Message msg
    ) {
        PlayerRef pr = commandBuffer.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(msg);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
