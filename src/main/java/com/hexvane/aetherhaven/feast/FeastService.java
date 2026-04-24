package com.hexvane.aetherhaven.feast;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FeastService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private FeastService() {}

    public static void pruneExpiredActiveFeast(@Nonnull TownRecord town, long currentDawnEpochDay) {
        Long end = town.getActiveFeastEndExclusiveDawnDay();
        if (town.getActiveFeastKind() != null && !town.getActiveFeastKind().isBlank() && end != null && currentDawnEpochDay >= end) {
            town.setActiveFeastKind(null);
            town.setActiveFeastEndExclusiveDawnDay(null);
        }
    }

    /** Prune timed feast expiry for all towns in this world; persists towns that changed. */
    public static void pruneExpiredForWorld(
        @Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull Store<EntityStore> store
    ) {
        long day = VillagerReputationService.currentGameEpochDay(store);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            boolean had = town.getActiveFeastKind() != null && !town.getActiveFeastKind().isBlank();
            pruneExpiredActiveFeast(town, day);
            if (had && town.getActiveFeastKind() == null) {
                tm.updateTown(town);
            }
        }
    }

    /** True while a timed tax or decay feast is in effect (not yet past exclusive end dawn day). */
    public static boolean hasActiveTimedFeast(@Nonnull TownRecord town, long currentDawnEpochDay) {
        pruneExpiredActiveFeast(town, currentDawnEpochDay);
        String k = town.getActiveFeastKind();
        if (k == null || k.isBlank()) {
            return false;
        }
        Long end = town.getActiveFeastEndExclusiveDawnDay();
        return end != null && currentDawnEpochDay < end;
    }

    public static boolean isStewardsTaxActive(@Nonnull TownRecord town, long currentDawnEpochDay) {
        return hasActiveTimedFeast(town, currentDawnEpochDay)
            && FeastCatalog.STEWARDS_LEDGER.id().equals(town.getActiveFeastKind());
    }

    public static boolean isHearthglassDecayActive(@Nonnull TownRecord town, long currentDawnEpochDay) {
        return hasActiveTimedFeast(town, currentDawnEpochDay)
            && FeastCatalog.HEARTHGLASS_VIGIL.id().equals(town.getActiveFeastKind());
    }

    public static boolean isBerrycircleOnCooldown(@Nonnull TownRecord town, long currentDawnEpochDay) {
        Long c = town.getFeastBerrycircleCooldownEndExclusiveDawnDay();
        return c != null && currentDawnEpochDay < c;
    }

    public static int timedFeastDaysRemaining(@Nonnull TownRecord town, long currentDawnEpochDay) {
        if (!hasActiveTimedFeast(town, currentDawnEpochDay)) {
            return 0;
        }
        Long end = town.getActiveFeastEndExclusiveDawnDay();
        if (end == null) {
            return 0;
        }
        return (int) Math.max(0L, end - currentDawnEpochDay);
    }

    public static int berrycircleCooldownDaysRemaining(@Nonnull TownRecord town, long currentDawnEpochDay) {
        Long c = town.getFeastBerrycircleCooldownEndExclusiveDawnDay();
        if (c == null) {
            return 0;
        }
        return (int) Math.max(0L, c - currentDawnEpochDay);
    }

    public static void checkGatherTimeoutsForWorld(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        long now = wallNowMs(world);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (town.getFeastGatherPoiId() == null || town.getFeastGatherPoiId().isBlank()) {
                continue;
            }
            long deadline = town.getFeastGatherDeadlineEpochMs();
            if (deadline > 0L && now >= deadline) {
                endFeastGather(world, plugin, tm, town);
            }
        }
    }

    public static void endFeastGather(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town
    ) {
        String sid = town.getFeastGatherPoiId();
        if (sid == null || sid.isBlank()) {
            return;
        }
        try {
            UUID poiId = UUID.fromString(sid.trim());
            PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            reg.unregisterEphemeral(poiId);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().withCause(e).log("Invalid feast gather POI id for town %s", town.getTownId());
        }
        town.setFeastGatherPoiId(null);
        town.setFeastGatherDeadlineEpochMs(0L);
        tm.updateTown(town);
    }

    /**
     * @return null on success, or a {@code Message.translation} key (e.g. {@code server.aetherhaven.ui.feast.err.*})
     */
    @Nullable
    public static String tryBeginFeast(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String feastId,
        int tableBlockX,
        int tableBlockY,
        int tableBlockZ
    ) {
        FeastDefinition def = FeastCatalog.findById(feastId);
        if (def == null) {
            return "server.aetherhaven.ui.feast.err.unknownFeast";
        }
        long dawn = VillagerReputationService.currentGameEpochDay(store);
        pruneExpiredActiveFeast(town, dawn);

        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (pu == null || player == null) {
            return "server.aetherhaven.ui.feast.err.invalidPlayer";
        }
        if (!town.hasMemberOrOwner(pu.getUuid()) || !town.playerHasBuildPermission(pu.getUuid())) {
            return "server.aetherhaven.ui.feast.noPermission";
        }

        UUID innkeeper = town.getInnkeeperEntityUuid();
        if (innkeeper == null) {
            return "server.aetherhaven.ui.feast.err.noInnkeeper";
        }
        int rep = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), innkeeper).getReputation();
        if (rep < def.minInnkeeperRep()) {
            return "server.aetherhaven.ui.feast.err.lowInnkeeperRep";
        }

        if (def.effectKind() != FeastEffectKind.BERRYCIRCLE_REP) {
            if (hasActiveTimedFeast(town, dawn)) {
                return "server.aetherhaven.ui.feast.err.timedFeastActive";
            }
        } else {
            if (hasActiveTimedFeast(town, dawn)) {
                return "server.aetherhaven.ui.feast.err.waitForTimedFeastEnd";
            }
            if (isBerrycircleOnCooldown(town, dawn)) {
                return "server.aetherhaven.ui.feast.err.berrycircleCooldown";
            }
        }

        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (!InventoryMaterials.hasAll(inv, def.costs())) {
            return "server.aetherhaven.ui.feast.err.notEnoughIngredients";
        }
        InventoryMaterials.removeAll(inv, def.costs());

        switch (def.effectKind()) {
            case STEWARDS_TAX, HEARTHGLASS_DECAY -> {
                town.setActiveFeastKind(def.id());
                town.setActiveFeastEndExclusiveDawnDay(dawn + 7L);
            }
            case BERRYCIRCLE_REP -> {
                applyBerrycircleRep(world, town, tm, pu.getUuid(), store);
                town.setFeastBerrycircleCooldownEndExclusiveDawnDay(dawn + 7L);
            }
        }

        startFeastGather(world, plugin, tm, town, tableBlockX, tableBlockY, tableBlockZ);
        tm.updateTown(town);
        return null;
    }

    private static void applyBerrycircleRep(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid,
        @Nonnull Store<EntityStore> store
    ) {
        UUID tid = town.getTownId();
        Query<EntityStore> q =
            Query.and(
                TownVillagerBinding.getComponentType(),
                UUIDComponent.getComponentType(),
                NPCEntity.getComponentType()
            );
        Set<UUID> seen = new HashSet<>();
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    UUIDComponent nu = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (nu == null) {
                        continue;
                    }
                    UUID npcUuid = nu.getUuid();
                    if (!seen.add(npcUuid)) {
                        continue;
                    }
                    VillagerReputationService.addReputationInternal(
                        town,
                        world,
                        playerUuid,
                        npcUuid,
                        VillagerReputationService.getOrCreateEntry(town, playerUuid, npcUuid),
                        10,
                        tm
                    );
                }
            }
        );
    }

    private static void startFeastGather(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        int bx,
        int by,
        int bz
    ) {
        endFeastGather(world, plugin, tm, town);

        UUID townId = town.getTownId();
        UUID poiId =
            UUID.nameUUIDFromBytes(
                ("feast:" + townId + ":" + bx + "," + by + "," + bz).getBytes(StandardCharsets.UTF_8)
            );
        Set<String> tags = new HashSet<>();
        tags.add("EAT");
        tags.add(AetherhavenConstants.POI_TAG_FEAST);
        tags.add(AetherhavenConstants.POI_TAG_FEAST_EPHEMERAL);

        double tx = bx - 1 + 0.5;
        double ty = by;
        double tz = bz + 0.5;

        PoiEntry entry =
            new PoiEntry(
                poiId,
                townId,
                bx,
                by,
                bz,
                tags,
                16,
                null,
                AetherhavenConstants.ITEM_BANQUET_TABLE,
                PoiInteractionKind.USE_BENCH,
                tx,
                ty,
                tz
            );
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        reg.registerEphemeral(entry);

        long timeoutMs = plugin.getConfig().get().getFeastGatherTimeoutSeconds() * 1000L;
        town.setFeastGatherPoiId(poiId.toString());
        town.setFeastGatherDeadlineEpochMs(wallNowMs(world) + Math.max(30_000L, timeoutMs));
        tm.updateTown(town);
    }

    private static long wallNowMs(@Nonnull World world) {
        var es = world.getEntityStore();
        if (es != null) {
            Store<EntityStore> store = es.getStore();
            TimeModule mod = TimeModule.get();
            if (mod != null) {
                TimeResource tr = store.getResource(mod.getTimeResourceType());
                if (tr != null) {
                    return tr.getNow().toEpochMilli();
                }
            }
        }
        return System.currentTimeMillis();
    }
}
