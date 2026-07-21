package com.hexvane.aetherhaven.charter;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.placement.CharterRelocationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Shared, world-thread-only town founding used by charter placement and admin generators. */
public final class TownFoundingService {
    private TownFoundingService() {}

    /**
     * Registers a town for an already placed charter block.
     */
    @Nullable
    public static TownRecord foundFromPlacedCharter(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID owner,
        @Nullable String ownerUsername,
        @Nonnull Vector3i charterPos,
        @Nonnull Random random
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (tm.findTownForPlayerInWorld(owner) != null) {
            return null;
        }
        int territoryRadius = TownManager.defaultTerritoryRadiusChunks(plugin.getConfig().get());
        if (tm.findTerritoryOverlapAtCharter(world.getName(), charterPos.x, charterPos.z, territoryRadius, null) != null) {
            return null;
        }
        TownRecord town = createRecord(world, plugin, tm, owner, ownerUsername, charterPos, random);
        tm.putTown(town);
        spawnElder(world, town, tm);
        town.setElderSpawned(true);
        tm.updateTown(town);
        return town;
    }

    /**
     * Places and links a charter, then registers the town. Rolls persistence back if the block cannot be linked.
     */
    @Nullable
    public static TownRecord foundWithNewCharter(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID owner,
        @Nullable String ownerUsername,
        @Nonnull Vector3i charterPos,
        @Nonnull Rotation yaw,
        @Nonnull Random random
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (tm.findTownForPlayerInWorld(owner) != null) {
            return null;
        }
        int territoryRadius = TownManager.defaultTerritoryRadiusChunks(plugin.getConfig().get());
        if (tm.findTerritoryOverlapAtCharter(world.getName(), charterPos.x, charterPos.z, territoryRadius, null) != null) {
            return null;
        }
        TownRecord town = createRecord(world, plugin, tm, owner, ownerUsername, charterPos, random);
        tm.putTown(town);
        CharterRelocationService.LinkRepairResult linked =
            CharterRelocationService.repairCharterLink(world, town, yaw);
        if (linked != CharterRelocationService.LinkRepairResult.PLACED
            && linked != CharterRelocationService.LinkRepairResult.RELINKED
            && linked != CharterRelocationService.LinkRepairResult.ALREADY_OK) {
            tm.removeTown(town.getTownId());
            return null;
        }
        spawnElder(world, town, tm);
        town.setElderSpawned(true);
        tm.updateTown(town);
        return town;
    }

    @Nonnull
    private static TownRecord createRecord(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull UUID owner,
        @Nullable String ownerUsername,
        @Nonnull Vector3i pos,
        @Nonnull Random random
    ) {
        TownRecord town = new TownRecord(
            UUID.randomUUID(),
            owner,
            world.getName(),
            pos.x,
            pos.y,
            pos.z,
            0,
            TownManager.defaultTerritoryRadiusChunks(plugin.getConfig().get()),
            System.currentTimeMillis()
        );
        town.setDisplayName(plugin.getTownNameCatalog().pickUniqueDisplayName(tm, random));
        if (ownerUsername != null && !ownerUsername.isBlank()) {
            town.setOwnerUsername(ownerUsername.trim());
        }
        return town;
    }

    public static void spawnElder(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm
    ) {
        if (town.getElderEntityUuid() != null) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null || world.getEntityStore() == null) {
            return;
        }
        Vector3d position = new Vector3d(
            town.getCharterX() + 2.5,
            town.getCharterY(),
            town.getCharterZ() + 0.5
        );
        Store<EntityStore> store = world.getEntityStore().getStore();
        var pair = npc.spawnNPC(
            store,
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            null,
            position,
            Rotation3f.ZERO
        );
        if (pair == null) {
            return;
        }
        Ref<EntityStore> elderRef = pair.first();
        store.putComponent(elderRef, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        store.putComponent(
            elderRef,
            AetherhavenVillagerHandle.getComponentType(),
            new AetherhavenVillagerHandle("Villager_Elder_" + shortHex(town.getTownId()))
        );
        store.putComponent(
            elderRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_ELDER, null)
        );
        NpcSpawnOriginUtil.attach(
            store,
            elderRef,
            "CHARTER_ELDER",
            "townId=" + town.getTownId(),
            world,
            position
        );
        UUIDComponent uuid = store.getComponent(elderRef, UUIDComponent.getComponentType());
        if (uuid != null) {
            town.setElderEntityUuid(uuid.getUuid());
            ResidentRegistryService.upsert(
                town,
                tm,
                AetherhavenConstants.ELDER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_ELDER,
                null,
                uuid.getUuid()
            );
        }
    }

    @Nonnull
    private static String shortHex(@Nonnull UUID id) {
        String hex = id.toString().replace("-", "");
        return hex.length() >= 8 ? hex.substring(0, 8) : hex;
    }
}
