package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.VillagerRevivalService;
import com.hexvane.aetherhaven.ui.TownVillagerDirectory;
import com.hexvane.aetherhaven.ui.TownVillagerRow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Picks snowball filler villagers who are actually in the loaded world. Missing story villagers are revived from
 * dialogue (not the fight tick) when there are not enough live NPCs.
 */
final class SnowballFillerVillagers {
    private SnowballFillerVillagers() {}

    static int leftoverPads(@Nonnull SnowballSession session, int playerCount) {
        int pads = session.teamAPadsView().size() + session.teamBPadsView().size();
        return Math.max(0, pads - playerCount);
    }

    static boolean isLiveNpc(@Nonnull Store<EntityStore> store, @Nullable UUID uuid) {
        if (!EntityPresenceUtil.isLoadedLive(EntityPresenceUtil.resolve(store, uuid))) {
            return false;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(uuid);
        return ref != null && ref.isValid() && store.getComponent(ref, NPCEntity.getComponentType()) != null;
    }

    static int countAvailableFillers(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Collection<UUID> exclude
    ) {
        int count = 0;
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            UUID uuid = row.entityUuid();
            if (uuid == null || exclude.contains(uuid)) {
                continue;
            }
            if (isLiveNpc(store, uuid) || isReviveableMissing(store, town, uuid)) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    static List<UUID> pickLive(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull List<UUID> players,
        int need
    ) {
        if (need <= 0) {
            return List.of();
        }
        List<UUID> live = new ArrayList<>();
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            UUID uuid = row.entityUuid();
            if (uuid != null && !players.contains(uuid) && isLiveNpc(store, uuid)) {
                live.add(uuid);
            }
        }
        return pickFromLivePool(live, need, ThreadLocalRandom.current());
    }

    @Nonnull
    static List<UUID> pickFromLivePool(@Nonnull List<UUID> livePool, int need, @Nonnull Random rng) {
        if (need <= 0 || livePool.isEmpty()) {
            return List.of();
        }
        List<UUID> pool = new ArrayList<>(livePool);
        Collections.shuffle(pool, rng);
        if (pool.size() > need) {
            return new ArrayList<>(pool.subList(0, need));
        }
        return pool;
    }

    static void reviveMissingForFight(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull SnowballSession session,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        int need = leftoverPads(session, session.joinedPlayerCount());
        int live = countLiveFillers(store, town, session);
        int stillNeed = need - live;
        if (stillNeed <= 0) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null) {
            return;
        }
        Vector3d spawn = spawnPos(plugin, town, store, playerRef);
        if (spawn == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            if (stillNeed <= 0) {
                break;
            }
            UUID uuid = row.entityUuid();
            if (uuid == null || session.isJoined(uuid) || isLiveNpc(store, uuid)) {
                continue;
            }
            ResidentNpcRecord record = findReviveableMissing(store, town, uuid);
            if (record == null) {
                continue;
            }
            if (VillagerRevivalService.reviveResident(world, plugin, town, tm, store, record, spawn)) {
                stillNeed--;
            }
        }
    }

    static int countLiveFillers(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull SnowballSession session
    ) {
        int count = 0;
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            UUID uuid = row.entityUuid();
            if (uuid != null && !session.isJoined(uuid) && isLiveNpc(store, uuid)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isReviveableMissing(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID uuid
    ) {
        return findReviveableMissing(store, town, uuid) != null;
    }

    @Nullable
    private static ResidentNpcRecord findReviveableMissing(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID uuid
    ) {
        EntityPresence presence = EntityPresenceUtil.resolve(store, uuid);
        if (!EntityPresenceUtil.isConfirmedAbsent(presence)) {
            return null;
        }
        for (ResidentNpcRecord record : town.getResidentNpcRecords()) {
            if (!uuid.equals(record.getLastEntityUuid())) {
                continue;
            }
            if (ResidentRegistryService.isGaiaRevivalEligible(record.getKind(), record.getNpcRoleId())) {
                return record;
            }
        }
        return null;
    }

    @Nullable
    private static Vector3d spawnPos(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        Vector3d square = SnowballAudio.squareCenter(plugin, town);
        if (square != null) {
            return square;
        }
        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        return tc != null ? tc.getPosition() : null;
    }
}
