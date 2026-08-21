package com.hexvane.aetherhaven.villager.audit;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.guild.GuardHireService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryChunkUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Scans tracked town villager UUIDs for confirmed absence while territory chunks are loaded. */
public final class VillagerAuditMissingScanService {
    private static final Set<String> LOGGED_MISSING_KEYS = ConcurrentHashMap.newKeySet();

    private VillagerAuditMissingScanService() {}

    public static void scanTown(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        if (!TownTerritoryChunkUtil.isCharterChunkLoaded(world, town)) {
            return;
        }
        UUID townId = town.getTownId();
        UUID nil = new UUID(0L, 0L);
        UUID elder = town.getElderEntityUuid();
        if (elder != null && !nil.equals(elder)) {
            checkTracked(
                world,
                store,
                plugin,
                townId,
                elder,
                AetherhavenConstants.ELDER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_ELDER,
                null
            );
        }
        UUID innkeeper = town.getInnkeeperEntityUuid();
        if (innkeeper != null && !nil.equals(innkeeper)) {
            checkTracked(
                world,
                store,
                plugin,
                townId,
                innkeeper,
                AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_INNKEEPER,
                null
            );
        }
        for (ResidentNpcRecord rec : town.getResidentNpcRecords()) {
            UUID u = rec.getLastEntityUuid();
            if (u.equals(nil)) {
                continue;
            }
            checkTracked(world, store, plugin, townId, u, rec.getNpcRoleId(), rec.getKind(), null);
        }
        for (HiredGuardRecord guard : town.getHiredGuardRecords()) {
            UUID u = guard.getEntityUuid();
            if (u == null) {
                continue;
            }
            checkTracked(
                world,
                store,
                plugin,
                townId,
                u,
                AetherhavenConstants.NPC_GUARD_KNIGHT,
                TownVillagerBinding.KIND_GUARD,
                null
            );
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        GuardHireService.pruneDeadHiredGuards(world, plugin, town, tm, store);
    }

    private static void checkTracked(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId,
        @Nonnull UUID entityUuid,
        @Nullable String roleId,
        @Nullable String bindingKind,
        @Nullable String displayNameHint
    ) {
        if (!EntityPresenceUtil.isConfirmedAbsent(EntityPresenceUtil.resolve(store, entityUuid))) {
            return;
        }
        String dedupKey = world.getName() + ":" + entityUuid;
        if (!LOGGED_MISSING_KEYS.add(dedupKey)) {
            return;
        }
        VillagerAuditService.logDetectedMissing(
            plugin,
            world,
            store,
            entityUuid,
            townId,
            roleId,
            bindingKind,
            displayNameHint,
            "missing_scan",
            "Tracked villager uuid has invalid ref while town territory is loaded"
        );
    }
}
