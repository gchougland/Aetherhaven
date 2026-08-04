package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.time.GameTimeEpochs;
import com.hexvane.aetherhaven.villager.AetherhavenRoleLabels;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * At dawn, town villagers who <em>confirmed died</em> ({@link ResidentNpcRecord#isPendingDawnRevival()}) return to the
 * charter. Prevents softlocks when a key villager dies before the Gaia altar is built. Never infers death from a
 * missing entity ref — unloaded chunks and dead entities look identical in the entity index.
 */
public final class CitizenDawnRevivalService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String REVIVAL_CHAT_KEY = "aetherhaven_ui_town.aetherhaven.town.citizenDawnRevival.chat";
    /** {@code worldName:townId} → last game epoch day revival ran for that town. */
    private static final ConcurrentHashMap<String, Long> LAST_MORNING_REVIVAL_EPOCH_DAY_BY_TOWN = new ConcurrentHashMap<>();

    private static final double[][] CHARTER_SPAWN_OFFSETS = {
        {2.5, 0.5},
        {0.5, 2.5},
        {-1.5, 0.5},
        {0.5, -1.5},
        {1.5, 1.5},
        {-1.5, -1.5},
    };

    private CitizenDawnRevivalService() {}

    public static void clearWorldState(@Nonnull String worldName) {
        String prefix = worldName + ":";
        LAST_MORNING_REVIVAL_EPOCH_DAY_BY_TOWN.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Nonnull
    private static String townRevivalKey(@Nonnull String worldName, @Nonnull UUID townId) {
        return worldName + ":" + townId;
    }

    public static void scheduleTickFromHub(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldTimeResource wtr
    ) {
        world.execute(() -> tick(world, plugin, wtr));
    }

    public static void catchUpAfterTimeJump(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Instant from,
        @Nonnull Instant to
    ) {
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        LinkedHashSet<Long> days = new LinkedHashSet<>();
        GameTimeEpochs.collectEpochDaysWhereMorningStartOccurred(
            from, to, morningStart, WorldTimeResource.ZONE_OFFSET, days
        );
        if (days.isEmpty()) {
            return;
        }
        for (long epochDay : days) {
            performMorningRevival(world, plugin, store, epochDay);
        }
    }

    private static void tick(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        var es = world.getEntityStore();
        Store<EntityStore> store = es != null ? es.getStore() : null;
        if (store == null) {
            return;
        }
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        int morningEndEx = plugin.getConfig().get().getGameMorningEndHourExclusive();
        if (!AetherhavenMorningWindow.isGameMorning(wtr, morningStart, morningEndEx)) {
            return;
        }
        long epochDay = VillagerReputationService.currentGameEpochDay(store);
        performMorningRevival(world, plugin, store, epochDay);
    }

    private static void performMorningRevival(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        long epochDay
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var onlinePlayers = TownOnlinePresence.collectOnlinePlayerUuids(world);
        String worldName = world.getName();
        for (TownRecord town : tm.allTowns()) {
            if (!worldName.equals(town.getWorldName())) {
                continue;
            }
            if (!TownOnlinePresence.hasAffiliatedPlayerOnline(town, onlinePlayers)) {
                continue;
            }
            if (!TownTerritoryChunkUtil.isCharterChunkLoaded(world, town)) {
                continue;
            }
            String revivalKey = townRevivalKey(worldName, town.getTownId());
            Long last = LAST_MORNING_REVIVAL_EPOCH_DAY_BY_TOWN.get(revivalKey);
            if (last != null && last >= epochDay) {
                continue;
            }
            TownResidentReconcileService.reconcileTownOnWorldThread(world, plugin, town);
            List<ResidentNpcRecord> candidates = ResidentRegistryService.dawnRevivalCandidates(town, tm, store);
            if (candidates.isEmpty()) {
                LAST_MORNING_REVIVAL_EPOCH_DAY_BY_TOWN.put(revivalKey, epochDay);
                continue;
            }
            Vector3d spawnPos = charterSpawnPos(world, town);
            boolean anyRevived = false;
            for (ResidentNpcRecord record : candidates) {
                if (ResidentRegistryService.hasLiveTownRevivalNpcForRole(store, town, record)) {
                    TownResidentReconcileService.syncRegistryToLiveEntityIfNeeded(town, tm, store, record);
                    continue;
                }
                if (VillagerRevivalService.validateCanRevive(store, town, record) != null) {
                    record.setPendingDawnRevival(false);
                    tm.updateTown(town);
                    continue;
                }
                boolean ok = VillagerRevivalService.reviveResident(world, plugin, town, tm, store, record, spawnPos);
                if (ok) {
                    anyRevived = true;
                    notifyTownMembers(store, town, record.getNpcRoleId(), record.getKind());
                }
            }
            LAST_MORNING_REVIVAL_EPOCH_DAY_BY_TOWN.put(revivalKey, epochDay);
            if (anyRevived) {
                LOGGER.atInfo().log(
                    "Citizen dawn revival for town %s (%s) on dawn day %d",
                    town.getDisplayName(),
                    town.getTownId(),
                    epochDay
                );
            }
        }
    }

    @Nonnull
    private static Vector3d charterSpawnPos(@Nonnull World world, @Nonnull TownRecord town) {
        int cx = town.getCharterX();
        int cy = town.getCharterY();
        int cz = town.getCharterZ();
        for (double[] off : CHARTER_SPAWN_OFFSETS) {
            Vector3d raw = new Vector3d(cx + off[0], cy, cz + off[1]);
            Vector3d snapped = VillagerBlockUtil.snapNpcFeetToStand(world, raw);
            if (VillagerBlockUtil.isNpcStandColumn(world, (int) Math.floor(snapped.x), (int) Math.floor(snapped.y), (int) Math.floor(snapped.z))) {
                return snapped;
            }
        }
        return VillagerBlockUtil.snapNpcFeetToStand(world, new Vector3d(cx + 2.5, cy, cz + 0.5));
    }

    private static void notifyTownMembers(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String roleId,
        @Nonnull String kind
    ) {
        String displayName = AetherhavenRoleLabels.displayNameForRoleId(roleId);
        if (displayName.isBlank()) {
            displayName = AetherhavenRoleLabels.listLinePlainEnglish(null, kind);
        }
        Message chat = Message.translation(REVIVAL_CHAT_KEY).param("name", displayName);
        Query<EntityStore> q = Query.and(Player.getComponentType(), UUIDComponent.getComponentType(), PlayerRef.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                    if (uc == null || pr == null) {
                        continue;
                    }
                    if (!town.hasMemberOrOwner(uc.getUuid())) {
                        continue;
                    }
                    pr.sendMessage(chat);
                }
            }
        );
    }
}
