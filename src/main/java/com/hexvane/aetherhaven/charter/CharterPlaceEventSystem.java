package com.hexvane.aetherhaven.charter;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
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
import com.hypixel.hytale.server.npc.NPCPlugin;
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
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (tm.findTownForPlayerInWorld(owner) != null) {
            event.setCancelled(true);
            pr.sendMessage(Message.translation("server.aetherhaven.charter.alreadyInTown"));
            return;
        }

        Vector3i pos = event.getTargetBlock().clone();
        world.execute(() -> finishCharterPlacement(world, pos, owner, pr));
    }

    private void finishCharterPlacement(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull UUID owner, @Nonnull PlayerRef playerRef) {
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

        UUID townId = UUID.randomUUID();
        int radius = TownManager.defaultTerritoryRadiusChunks(plugin.getConfig().get());
        TownRecord record = new TownRecord(
            townId,
            owner,
            world.getName(),
            pos.x,
            pos.y,
            pos.z,
            0,
            radius,
            System.currentTimeMillis()
        );

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        record.setDisplayName(plugin.getTownNameCatalog().pickUniqueDisplayName(tm, ThreadLocalRandom.current()));
        tm.putTown(record);

        charter.setTownId(townId.toString());
        cstore.putComponent(blockRef, CharterBlock.getComponentType(), charter);

        if (!record.isElderSpawned()) {
            spawnElder(world, record, tm);
            record.setElderSpawned(true);
            tm.updateTown(record);
        }

        playerRef.sendMessage(
            Message.translation("server.aetherhaven.charter.townFounded").param("name", record.getDisplayName())
        );
        LOGGER.atInfo().log("Aetherhaven town %s created for %s at %s", townId, owner, pos);
    }

    private static void spawnElder(@Nonnull World world, @Nonnull TownRecord town, @Nonnull TownManager tm) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return;
        }
        Vector3d p = new Vector3d(town.getCharterX() + 2.5, town.getCharterY(), town.getCharterZ() + 0.5);
        Store<EntityStore> store = world.getEntityStore().getStore();
        var pair = npc.spawnNPC(store, AetherhavenConstants.ELDER_NPC_ROLE_ID, null, p, Vector3f.ZERO);
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn elder NPC for town %s", town.getTownId());
            return;
        }
        Ref<EntityStore> elderRef = pair.first();
        store.putComponent(elderRef, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        String handle = elderDebugHandle(town.getTownId());
        store.putComponent(elderRef, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle));
        store.putComponent(
            elderRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_ELDER, null)
        );
        UUIDComponent elderUuid = store.getComponent(elderRef, UUIDComponent.getComponentType());
        if (elderUuid != null) {
            town.setElderEntityUuid(elderUuid.getUuid());
            ResidentRegistryService.upsert(
                town,
                tm,
                AetherhavenConstants.ELDER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_ELDER,
                null,
                elderUuid.getUuid()
            );
            tm.updateTown(town);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Nonnull
    private static String elderDebugHandle(@Nonnull UUID townId) {
        String hex = townId.toString().replace("-", "");
        String suffix = hex.length() >= 8 ? hex.substring(0, 8) : hex;
        return "Villager_Elder_" + suffix;
    }
}
