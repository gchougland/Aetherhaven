package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import com.hexvane.aetherhaven.map.RaidQuestCompassCache;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Drops dead or despawned raid mobs from slot tracking and compass cache (world thread only). */
public final class RaidQuestReconcile {
    private static final long RECONCILE_INTERVAL_MS = 1000L;
    private static final ConcurrentHashMap<String, Long> LAST_RECONCILE_MS = new ConcurrentHashMap<>();

    private RaidQuestReconcile() {}

    /** Only confirmed absences may drop tracking; unloaded chunks are not despawned. */
    static boolean shouldDropRaidMob(@Nonnull EntityPresence presence) {
        return EntityPresenceUtil.isConfirmedAbsent(presence);
    }

    public static void maybeReconcileWorld(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        String worldName = world.getName();
        long now = System.currentTimeMillis();
        Long last = LAST_RECONCILE_MS.get(worldName);
        if (last != null && now - last < RECONCILE_INTERVAL_MS) {
            return;
        }
        LAST_RECONCILE_MS.put(worldName, now);
        reconcileWorld(world, store, plugin);
    }

    public static void reconcileWorld(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        String worldName = world.getName();
        Set<TownRecord> changedTowns = new HashSet<>();
        for (TownRecord town : tm.allTowns()) {
            if (!worldName.equals(town.getWorldName())) {
                continue;
            }
            for (QuestBoardSlotRecord slot : town.acceptedBoardQuestsSnapshot()) {
                if (!slot.isRaidQuest()) {
                    continue;
                }
                if (reconcileSlot(worldName, store, town, slot, plugin)) {
                    changedTowns.add(town);
                }
            }
        }
        for (TownRecord town : changedTowns) {
            tm.updateTown(town);
        }
    }

    private static boolean reconcileSlot(
        @Nonnull String worldName,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull AetherhavenPlugin plugin
    ) {
        List<String> uuids = new ArrayList<>(slot.raidSpawnedEntityUuidsOrEmpty());
        if (uuids.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Iterator<String> it = uuids.iterator();
        while (it.hasNext()) {
            String uuidStr = it.next();
            if (uuidStr == null || uuidStr.isBlank()) {
                it.remove();
                changed = true;
                continue;
            }
            UUID mobUuid;
            try {
                mobUuid = UUID.fromString(uuidStr.trim());
            } catch (IllegalArgumentException e) {
                it.remove();
                changed = true;
                continue;
            }
            EntityPresence presence = EntityPresenceUtil.resolve(store, mobUuid);
            if (EntityPresenceUtil.isLoadedLive(presence)) {
                continue;
            }
            if (!shouldDropRaidMob(presence)) {
                if (presence == EntityPresence.UNKNOWN_UNLOADED) {
                    RaidQuestMarchDebugLog.logReconcileSkipUnloaded(plugin, slot.instanceIdOrEmpty(), mobUuid);
                }
                continue;
            }
            int need = slot.getRaidKillRequired();
            int progress = slot.getRaidKillProgress();
            RaidQuestMarchDebugLog.logReconcileDrop(
                plugin,
                slot.instanceIdOrEmpty(),
                mobUuid,
                presence,
                need,
                progress
            );
            RaidQuestCompassCache.removeMob(worldName, mobUuid);
            it.remove();
            slot.setRaidKillRequired(Math.max(progress, need - 1));
            changed = true;
        }
        if (changed) {
            slot.setRaidSpawnedEntityUuids(uuids);
        }

        for (RaidQuestCompassCache.Entry entry : RaidQuestCompassCache.entriesForTown(worldName, town.getTownId())) {
            if (!slot.instanceIdOrEmpty().equals(entry.instanceId())) {
                continue;
            }
            if (!uuids.contains(entry.mobUuid().toString())) {
                RaidQuestCompassCache.removeMob(worldName, entry.mobUuid());
            }
        }
        return changed;
    }
}
