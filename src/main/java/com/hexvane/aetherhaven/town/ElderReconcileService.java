package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Removes duplicate loaded elders for a town; keeps the canonical {@link TownRecord#getElderEntityUuid()}. */
public final class ElderReconcileService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ElderReconcileService() {}

    public static void scheduleAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> reconcileAllTownsOnWorldThread(world, plugin));
        plugin.scheduleOnWorld(world, () -> reconcileAllTownsOnWorldThread(world, plugin), 2_000L);
    }

    public static void onTownMemberPlayerReady(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull UUID playerUuid) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(playerUuid);
        if (town == null) {
            return;
        }
        world.execute(() -> reconcileTownOnWorldThread(world, plugin, town));
    }

    private static void reconcileAllTownsOnWorldThread(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Set<UUID> onlinePlayers = TownOnlinePresence.collectOnlinePlayerUuids(world);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (!TownOnlinePresence.hasAffiliatedPlayerOnline(town, onlinePlayers)) {
                continue;
            }
            reconcileTownOnWorldThread(world, plugin, town);
        }
    }

    static void reconcileTownOnWorldThread(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        if (!TownTerritoryChunkUtil.isCharterChunkLoaded(world, town)) {
            return;
        }
        UUID canonical = town.getElderEntityUuid();
        if (canonical == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store == null) {
            return;
        }
        UUID townId = town.getTownId();
        List<UUID> duplicateUuids = new ArrayList<>();
        store.forEachChunk(
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) ->
                collectDuplicateElders(chunk, townId, canonical, duplicateUuids)
        );
        if (duplicateUuids.isEmpty()) {
            return;
        }
        for (UUID duplicateUuid : duplicateUuids) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(duplicateUuid);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        LOGGER.atInfo().log(
            "Elder reconcile removed %d duplicate elder(s) for town %s (canonical %s)",
            duplicateUuids.size(),
            town.getTownId(),
            canonical
        );
    }

    private static void collectDuplicateElders(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull UUID townId,
        @Nonnull UUID canonicalUuid,
        @Nonnull List<UUID> duplicateUuids
    ) {
        for (int i = 0; i < chunk.size(); i++) {
            TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
            UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
            NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
            if (binding == null || uc == null || npc == null) {
                continue;
            }
            if (!townId.equals(binding.getTownId())) {
                continue;
            }
            if (!TownVillagerBinding.KIND_ELDER.equals(binding.getKind())) {
                continue;
            }
            String role = npc.getRoleName();
            if (role == null || !AetherhavenConstants.ELDER_NPC_ROLE_ID.equalsIgnoreCase(role.trim())) {
                continue;
            }
            UUID entityUuid = uc.getUuid();
            if (entityUuid == null || canonicalUuid.equals(entityUuid)) {
                continue;
            }
            duplicateUuids.add(entityUuid);
        }
    }
}
