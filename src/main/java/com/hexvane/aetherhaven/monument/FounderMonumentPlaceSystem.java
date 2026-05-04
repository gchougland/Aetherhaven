package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * When a player places {@link AetherhavenConstants#ITEM_FOUNDER_MONUMENT}, validates town territory and spawns the
 * founder statue entity. Multiple monuments per town are allowed; the tax bonus still applies only once.
 */
public final class FounderMonumentPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AetherhavenPlugin plugin;

    public FounderMonumentPlaceSystem(@Nonnull AetherhavenPlugin plugin) {
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
        if (hand == null || hand.isEmpty() || !AetherhavenConstants.ITEM_FOUNDER_MONUMENT.equals(hand.getItemId())) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        UUIDComponent uuidComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        PlayerSkinComponent skinComp = store.getComponent(playerRef, PlayerSkinComponent.getComponentType());
        if (uuidComp == null || pr == null || skinComp == null) {
            return;
        }
        PlayerSkin skin = skinComp.getPlayerSkin();
        if (skin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i pos = event.getTargetBlock();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownContainingBlock(world.getName(), pos.getX(), pos.getZ());
        if (town == null) {
            event.setCancelled(true);
            pr.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.founder.inTerritory"));
            return;
        }
        if (!town.getOwnerUuid().equals(uuidComp.getUuid()) && !town.playerCanManageConstructions(uuidComp.getUuid())) {
            event.setCancelled(true);
            pr.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.founder.cannotPlace"));
            return;
        }
        Ref<EntityStore> placingEntityRef = archetypeChunk.getReferenceTo(index);
        Vector3f statueRotation = statueFacingOppositePlayerLook(store, placingEntityRef);
        world.execute(() -> afterMonumentPlaced(world, store, tm, town, pr, skin, pos, statueRotation));
    }

    /**
     * Faces opposite the <em>player's</em> look direction (yaw + π). {@link PlaceBlockEvent#getRotation()} reflects
     * block variant rules (e.g. {@code DoublePipe} connectivity), not where the player faces, so it cannot steer the
     * statue.
     */
    @Nonnull
    private static Vector3f statueFacingOppositePlayerLook(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> placerRef) {
        TransformComponent tc = store.getComponent(placerRef, TransformComponent.getComponentType());
        if (tc == null) {
            return new Vector3f(0f, (float) Math.PI, 0f);
        }
        var r = tc.getRotation();
        float yawOpp = r.getYaw() + (float) Math.PI;
        return new Vector3f(r.getPitch(), yawOpp, r.getRoll());
    }

    private void afterMonumentPlaced(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlayerRef pr,
        @Nonnull PlayerSkin skin,
        @Nonnull Vector3i pos,
        @Nonnull Vector3f statueRotation
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ()));
        if (chunk == null) {
            return;
        }
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(pos.getX(), pos.getY(), pos.getZ());
        if (blockRef == null || !blockRef.isValid()) {
            LOGGER.atWarning().log("Founder monument placed at %s but no block entity", pos);
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        String label = pr.getUsername();
        UUID statueUuid =
            FounderMonumentSpawnService.spawnFounderStatue(
                world,
                store,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                skin,
                label,
                statueRotation
            );
        if (statueUuid == null) {
            pr.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.founder.createFailed"));
            return;
        }
        cs.putComponent(
            blockRef,
            FounderMonumentBlock.getComponentType(),
            new FounderMonumentBlock(town.getTownId().toString(), statueUuid.toString())
        );
        town.incrementFounderMonumentPlaced();
        tm.updateTown(town);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
