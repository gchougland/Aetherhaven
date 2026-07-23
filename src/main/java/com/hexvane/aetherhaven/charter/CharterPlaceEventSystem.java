package com.hexvane.aetherhaven.charter;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.difficulty.WorldDifficultyState;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.ui.DifficultyPage;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.system.EntityEventSystem;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CharterPlaceEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AetherhavenPlugin plugin;

    public CharterPlaceEventSystem(@Nonnull AetherhavenPlugin plugin) {
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
        if (hand == null || hand.isEmpty() || !AetherhavenConstants.CHARTER_ITEM_ID.equals(hand.getItemId())) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        UUIDComponent uuidComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (uuidComp == null || pr == null) {
            return;
        }
        UUID owner = uuidComp.getUuid();
        World world = store.getExternalData().getWorld();
        if (PersistentWorldSupport.isTemporaryInstance(world)) {
            event.setCancelled(true);
            pr.sendMessage(Message.translation("aetherhaven_common.aetherhaven.charter.notInPersistentWorld"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (tm.findTownForOwnerInWorld(owner) != null) {
            event.setCancelled(true);
            pr.sendMessage(Message.translation("aetherhaven_common.aetherhaven.charter.alreadyInTown"));
            return;
        }

        Vector3i pos = new Vector3i(event.getTargetBlock());
        int territoryRadius = TownManager.defaultTerritoryRadiusChunks(plugin.getConfig().get());
        TownRecord overlap = tm.findTerritoryOverlapAtCharter(world.getName(), pos.x, pos.z, territoryRadius, null);
        if (overlap != null) {
            event.setCancelled(true);
            pr.sendMessage(
                Message.translation("aetherhaven_common.aetherhaven.charter.tooCloseToTown")
                    .param("name", overlap.getDisplayName())
            );
            return;
        }
        world.execute(() -> finishCharterPlacement(world, pos, owner, pr, playerRef));
    }

    private void finishCharterPlacement(
        @Nonnull World world,
        @Nonnull Vector3i pos,
        @Nonnull UUID owner,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            return;
        }
        int blockId = chunk.getBlock(pos.x, pos.y, pos.z);
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        if (type == null || !AetherhavenConstants.CHARTER_BLOCK_TYPE_ID.equals(type.getId())) {
            return;
        }
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(pos.x, pos.y, pos.z);
        if (blockRef == null) {
            LOGGER.atWarning().log("Charter placed at %s but no block entity ref", pos);
            return;
        }
        Store<ChunkStore> cstore = blockRef.getStore();
        CharterBlock charter = cstore.getComponent(blockRef, CharterBlock.getComponentType());
        if (charter == null) {
            LOGGER.atWarning().log("Charter block missing AetherhavenCharter component at %s", pos);
            return;
        }
        if (!charter.getTownId().isEmpty()) {
            return;
        }

        TownRecord record =
            TownFoundingService.foundFromPlacedCharter(
                world, plugin, owner, playerRef.getUsername(), pos, ThreadLocalRandom.current());
        if (record == null) {
            return;
        }

        charter.setTownId(record.getTownId().toString());
        cstore.putComponent(blockRef, CharterBlock.getComponentType(), charter);

        playerRef.sendMessage(
            Message.translation("aetherhaven_common.aetherhaven.charter.townFounded").param("name", record.getDisplayName())
        );
        LOGGER.atInfo().log("Aetherhaven town %s created for %s at %s", record.getTownId(), owner, pos);

        WorldDifficultyState difficulty = AetherhavenWorldRegistries.getOrLoadWorldDifficulty(world, plugin);
        if (!difficulty.isDifficultyChosen()) {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Player player = entityStore.getComponent(entityRef, Player.getComponentType());
            if (player != null && player.getPageManager().getCustomPage() == null) {
                player.getPageManager().openCustomPage(entityRef, entityStore, new DifficultyPage(playerRef));
            }
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

}
