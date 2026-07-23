package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerLocator;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnSlot;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolState;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterCatalog;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Dawn cycling pool of guard eligible townsfolk at the guild hall (unhired adventurers). */
public final class GuildHallAdventurerPoolService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float ADVENTURER_FILL_CHANCE = 0.5f;

    private record AdventurerInPlotDespawn(@Nullable String characterId, @Nonnull UUID entityUuid) {}

    public record ForceRespawnResult(
        int reclaimedCheckouts,
        int despawnedAdventurers,
        int slotsRolled,
        int spawned,
        int poolAvailable
    ) {}

    private GuildHallAdventurerPoolService() {}

    public static void scheduleTickFromHub(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        world.execute(() -> tick(world, plugin, wtr));
    }

    public static void tick(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store == null) {
            return;
        }
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        int morningEndEx = plugin.getConfig().get().getGameMorningEndHourExclusive();
        TownsfolkExistenceService.reclaimAbsentNonGuardCheckouts(world, plugin, store);

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            PlotInstance hallPlot =
                resolveGuildHallPlot(town, plugin.getConstructionCatalog(), store);
            if (hallPlot == null) {
                continue;
            }
            ConstructionDefinition hallDef = plugin.getConstructionCatalog().get(hallPlot.getConstructionId());
            if (hallDef == null) {
                continue;
            }
            if (!GuildHallStaffing.hasGuildMasterAssigned(town, store, town.getTownId(), hallPlot.getPlotId())) {
                clearAdventurersForHall(world, plugin, town, tm, store, hallPlot);
                continue;
            }
            List<AdventurerSpawnSlot> spawnSlots = AdventurerSpawnMarkerLocator.resolveSpawnSlots(store, hallPlot, hallDef);
            if (spawnSlots.isEmpty()) {
                continue;
            }
            if (!isManagementChunkLoaded(world, hallPlot, hallDef)) {
                continue;
            }

            dedupeAdventurerIds(town, tm);
            pruneMissingAdventurers(town, store, tm);
            TownsfolkExistenceService.purgeStaleGuildHallAdventurers(world, plugin, town, store, hallPlot, tm);
            morningRefreshIfDue(world, plugin, town, tm, store, hallPlot, spawnSlots, wtr, morningStart, morningEndEx);
            fillEmptySlots(world, plugin, town, tm, store, hallPlot, spawnSlots, wtr);
        }
    }

    /**
     * Admin / debug: despawn current hall adventurers, roll today's slots, and fill immediately (does not require dawn).
     */
    @Nonnull
    public static ForceRespawnResult forceRespawnAdventurers(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldTimeResource wtr
    ) {
        PlotInstance hallPlot = resolveGuildHallPlot(town, plugin.getConstructionCatalog(), store);
        if (hallPlot == null) {
            return new ForceRespawnResult(0, 0, 0, 0, 0);
        }
        if (!GuildHallStaffing.hasGuildMasterAssigned(town, store, town.getTownId(), hallPlot.getPlotId())) {
            int cleared = clearAdventurersForHall(world, plugin, town, tm, store, hallPlot);
            return new ForceRespawnResult(0, cleared, 0, 0, availableGuardEligibleCount(world, plugin, town.getTownId()));
        }
        ConstructionDefinition hallDef = plugin.getConstructionCatalog().get(hallPlot.getConstructionId());
        if (hallDef == null) {
            return new ForceRespawnResult(0, 0, 0, 0, 0);
        }
        List<AdventurerSpawnSlot> spawnSlots = AdventurerSpawnMarkerLocator.resolveSpawnSlots(store, hallPlot, hallDef);
        if (spawnSlots.isEmpty()) {
            return new ForceRespawnResult(0, 0, 0, 0, 0);
        }

        int reclaimed = TownsfolkExistenceService.reclaimAbsentNonGuardCheckouts(world, plugin, store);
        int despawned = despawnGuildHallAdventurersInPlot(world, plugin, town, store, hallPlot);
        town.getGuildHallAdventurerNpcIds().clear();
        town.getGuildHallAdventurerSlotByNpcId().clear();

        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        rollTodayAdventurerSlots(town, tm, spawnSlots.size(), epochDay);

        int spawned = fillEmptySlots(world, plugin, town, tm, store, hallPlot, spawnSlots, wtr);
        int available = availableGuardEligibleCount(world, plugin, town.getTownId());
        return new ForceRespawnResult(reclaimed, despawned, town.getGuildHallAdventurerFilledSlots().size(), spawned, available);
    }

    /**
     * Despawns guild hall adventurers in this plot and clears town slot state. Safe to call when no master is assigned or
     * the workplace dropdown is set to Unassigned.
     */
    public static int clearAdventurersForHall(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot
    ) {
        if (town.getGuildHallAdventurerNpcIds().isEmpty()
            && town.getGuildHallAdventurerSlotByNpcId().isEmpty()
            && town.getGuildHallAdventurerFilledSlots().isEmpty()) {
            return 0;
        }
        int despawned = despawnGuildHallAdventurersInPlot(world, plugin, town, store, hallPlot);
        town.getGuildHallAdventurerNpcIds().clear();
        town.getGuildHallAdventurerSlotByNpcId().clear();
        town.getGuildHallAdventurerFilledSlots().clear();
        tm.updateTown(town);
        return despawned;
    }

    /** Despawns hired guard NPCs for this town and releases their ledger rows. */
    public static int clearHiredGuardsInTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        int despawned = 0;
        for (var rec : new ArrayList<>(town.getHiredGuardRecords())) {
            UUID entityUuid = rec.getEntityUuid();
            if (entityUuid != null) {
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
                if (ref != null && ref.isValid()) {
                    PendingEntityRemovalService.schedule(world, entityUuid);
                    despawned++;
                }
            }
            String characterId = rec.getCharacterId();
            if (characterId != null && !characterId.isBlank()) {
                TownsfolkExistenceService.releaseCharacter(
                    world,
                    plugin,
                    town.getTownId(),
                    characterId,
                    TownsfolkExistenceService.ReleaseReason.ADMIN
                );
            }
        }
        town.getHiredGuardRecords().clear();
        tm.updateTown(town);
        return despawned;
    }

    private static int availableGuardEligibleCount(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        TownsfolkCharacterCatalog catalog = plugin.getTownsfolkCharacterCatalog();
        return pool.availableGuardEligibleCharacterIds(townId, catalog).size();
    }

    private static void dedupeAdventurerIds(@Nonnull TownRecord town, @Nonnull TownManager tm) {
        List<String> ids = town.getGuildHallAdventurerNpcIds();
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String s : ids) {
            if (s != null && !s.isBlank() && seen.add(s.trim().toLowerCase())) {
                out.add(s.trim());
            }
        }
        if (out.size() != ids.size()) {
            ids.clear();
            ids.addAll(out);
            Set<String> lower = new HashSet<>();
            for (String s : out) {
                lower.add(s.toLowerCase());
            }
            town.getGuildHallAdventurerSlotByNpcId().keySet().removeIf(k -> k == null || !lower.contains(k.trim().toLowerCase()));
            tm.updateTown(town);
        }
    }

    private static void pruneMissingAdventurers(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm
    ) {
        Iterator<String> it = town.getGuildHallAdventurerNpcIds().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            String sid = it.next();
            UUID u = parseUuid(sid);
            if (u == null) {
                it.remove();
                town.getGuildHallAdventurerSlotByNpcId().remove(sid);
                changed = true;
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref != null && ref.isValid()) {
                continue;
            }
            it.remove();
            town.getGuildHallAdventurerSlotByNpcId().remove(u.toString());
            changed = true;
        }
        if (changed) {
            tm.updateTown(town);
        }
    }

    /**
     * After assigning a guild master, roll today's adventurer slots (if not already rolled) and spawn any open slots
     * immediately — same-day assignment should not wait until the next dawn.
     */
    public static void tryFillAfterGuildMasterAssigned(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID guildHallPlotId,
        @Nonnull Store<EntityStore> store
    ) {
        world.execute(
            () -> {
                PlotInstance hallPlot = town.findPlotById(guildHallPlotId);
                if (hallPlot == null || hallPlot.getState() != PlotInstanceState.COMPLETE) {
                    return;
                }
                String gameplayId =
                    plugin.getConstructionCatalog().resolveGameplayConstructionId(hallPlot.getConstructionId());
                if (!AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(gameplayId)) {
                    return;
                }
                if (!GuildHallStaffing.hasGuildMasterAssigned(town, store, town.getTownId(), guildHallPlotId)) {
                    return;
                }
                ConstructionDefinition hallDef = plugin.getConstructionCatalog().get(hallPlot.getConstructionId());
                if (hallDef == null) {
                    return;
                }
                List<AdventurerSpawnSlot> spawnSlots = AdventurerSpawnMarkerLocator.resolveSpawnSlots(store, hallPlot, hallDef);
                if (spawnSlots.isEmpty()) {
                    return;
                }
                if (!isManagementChunkLoaded(world, hallPlot, hallDef)) {
                    return;
                }
                WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
                if (wtr == null) {
                    return;
                }
                long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
                ensureTodayAdventurerSlotsRolled(town, tm, spawnSlots.size(), epochDay);
                fillEmptySlots(world, plugin, town, tm, store, hallPlot, spawnSlots, wtr);
            }
        );
    }

    private static void morningRefreshIfDue(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot,
        @Nonnull List<AdventurerSpawnSlot> spawnSlots,
        @Nonnull WorldTimeResource wtr,
        int morningStart,
        int morningEndEx
    ) {
        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        Long lastDay = town.getGuildHallLastMorningEpochDay();
        if (lastDay != null && lastDay >= epochDay) {
            return;
        }
        boolean morning = AetherhavenMorningWindow.isGameMorning(wtr, morningStart, morningEndEx);
        boolean firstRoll = lastDay == null;
        boolean newDayCatchUp = lastDay != null && lastDay < epochDay;
        if (!morning && !firstRoll && !newDayCatchUp) {
            return;
        }

        if (newDayCatchUp) {
            despawnGuildHallAdventurersInPlot(world, plugin, town, store, hallPlot);
            town.getGuildHallAdventurerNpcIds().clear();
            town.getGuildHallAdventurerSlotByNpcId().clear();
        }

        rollTodayAdventurerSlots(town, tm, spawnSlots.size(), epochDay);
    }

    /**
     * Prefer the guild hall where a guild master is actually assigned (e.g. a plot-creator variant). Falling back to the
     * first complete hall avoids targeting an older {@code plot_guild_hall} plot when the master works at a variant.
     */
    @Nullable
    private static PlotInstance resolveGuildHallPlot(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nonnull Store<EntityStore> store
    ) {
        String gameplayId = AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL;
        PlotInstance fallback = null;
        for (PlotInstance p : town.getPlotInstances()) {
            if (p.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!gameplayId.equals(constructionCatalog.resolveGameplayConstructionId(p.getConstructionId()))) {
                continue;
            }
            if (fallback == null) {
                fallback = p;
            }
            if (GuildHallStaffing.hasGuildMasterAssigned(town, store, town.getTownId(), p.getPlotId())) {
                return p;
            }
        }
        return fallback;
    }

    private static void ensureTodayAdventurerSlotsRolled(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        int slotCount,
        long epochDay
    ) {
        Long lastDay = town.getGuildHallLastMorningEpochDay();
        if (lastDay != null && lastDay == epochDay) {
            return;
        }
        rollTodayAdventurerSlots(town, tm, slotCount, epochDay);
    }

    private static void rollTodayAdventurerSlots(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        int slotCount,
        long epochDay
    ) {
        town.getGuildHallAdventurerFilledSlots().clear();
        long seed = town.getTownId().getLeastSignificantBits() ^ epochDay * 0x9E3779B97F4A7C15L;
        Random slotRandom = new Random(seed);
        for (int slot = 0; slot < slotCount; slot++) {
            if (slotRandom.nextFloat() < ADVENTURER_FILL_CHANCE) {
                town.getGuildHallAdventurerFilledSlots().add(slot);
            }
        }
        town.setGuildHallLastMorningEpochDay(epochDay);
        tm.updateTown(town);
    }

    private static int despawnGuildHallAdventurersInPlot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot
    ) {
        PlotFootprintRecord footprint = hallPlot.toFootprint();
        Set<UUID> toRemove = ConcurrentHashMap.newKeySet();
        List<UUID> despawnEntityUuids = new ArrayList<>();
        int despawned = 0;

        for (String sid : new ArrayList<>(town.getGuildHallAdventurerNpcIds())) {
            UUID u = parseUuid(sid);
            if (u == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref != null && ref.isValid() && isAdventurerEntity(town, store, ref, u)) {
                String characterId = adventurerCharacterId(store, ref);
                despawnEntityUuids.add(u);
                releaseAdventurerCheckout(world, plugin, characterId, u);
                despawned++;
            }
            toRemove.add(u);
        }

        List<AdventurerInPlotDespawn> extraInPlot = Collections.synchronizedList(new ArrayList<>());
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !b.getTownId().equals(town.getTownId()) || !TownVillagerBinding.KIND_TOWNSFOLK.equals(b.getKind())) {
                return;
            }
            TownsfolkCharacterBinding tb = archetypeChunk.getComponent(index, TownsfolkCharacterBinding.getComponentType());
            if (tb == null || !TownsfolkAssignmentKinds.isGuildHallAdventurer(tb.getAssignmentKind())) {
                return;
            }
            TransformComponent tc = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
            if (tc == null) {
                return;
            }
            Vector3d pos = tc.getPosition();
            int bx = (int) Math.floor(pos.x);
            int by = (int) Math.floor(pos.y);
            int bz = (int) Math.floor(pos.z);
            if (!footprint.containsBlock(bx, by, bz)) {
                return;
            }
            UUIDComponent uc = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            UUID u = uc.getUuid();
            if (toRemove.add(u)) {
                extraInPlot.add(new AdventurerInPlotDespawn(tb.getCharacterId(), u));
            }
        });
        for (AdventurerInPlotDespawn entry : extraInPlot) {
            despawnEntityUuids.add(entry.entityUuid());
            releaseAdventurerCheckout(world, plugin, entry.characterId(), entry.entityUuid());
            despawned++;
        }
        PendingEntityRemovalService.scheduleAll(world, despawnEntityUuids);

        for (UUID u : toRemove) {
            town.getGuildHallAdventurerNpcIds().removeIf(s -> u.toString().equalsIgnoreCase(s != null ? s.trim() : ""));
            town.getGuildHallAdventurerSlotByNpcId().remove(u.toString());
        }
        return despawned;
    }

    @Nullable
    private static String adventurerCharacterId(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TownsfolkCharacterBinding tb = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        if (tb == null || tb.getCharacterId().isBlank()) {
            return null;
        }
        return tb.getCharacterId();
    }

    private static void releaseAdventurerCheckout(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable String characterId,
        @Nullable UUID entityUuid
    ) {
        if (entityUuid != null) {
            TownsfolkExistenceService.releaseByEntity(world, plugin, entityUuid);
            return;
        }
        if (characterId != null && !characterId.isBlank()) {
            LOGGER.atWarning().log("Adventurer checkout release missing entity uuid for character %s", characterId);
        }
    }

    private static int fillEmptySlots(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot,
        @Nonnull List<AdventurerSpawnSlot> spawnSlots,
        @Nonnull WorldTimeResource wtr
    ) {
        Long lastDay = town.getGuildHallLastMorningEpochDay();
        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        if (lastDay == null || lastDay != epochDay) {
            return 0;
        }

        int spawned = 0;
        for (int slot : town.getGuildHallAdventurerFilledSlots()) {
            if (slot < 0 || slot >= spawnSlots.size()) {
                continue;
            }
            if (isSlotOccupied(town, store, hallPlot, spawnSlots, slot)) {
                continue;
            }
            AdventurerSpawnSlot spawnSlot = spawnSlots.get(slot);
            Vector3d markerPos = spawnSlot.position();
            Vector3d pos =
                com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.resolveGuildHallAdventurerFeetPosition(world, markerPos);

            long seed =
                town.getTownId().getLeastSignificantBits()
                    ^ (long) world.getName().hashCode() << 1
                    ^ epochDay * 0x9E3779B97F4A7C15L
                    ^ slot;
            var result =
                TownsfolkSpawnService.trySpawn(
                    world,
                    plugin,
                    town,
                    store,
                    pos,
                    TownsfolkAssignmentKinds.GUILD_ADVENTURER,
                    null,
                    new Random(seed),
                    new Rotation3f(0.0F, spawnSlot.yawRadians(), 0.0F),
                    spawnSlot.yawRadians(),
                    markerPos
                );
            if (result.isEmpty()) {
                continue;
            }
            String uuidStr = result.get().entityUuid().toString();
            town.getGuildHallAdventurerNpcIds().add(uuidStr);
            town.getGuildHallAdventurerSlotByNpcId().put(uuidStr, slot);
            tm.updateTown(town);
            spawned++;
        }
        return spawned;
    }

    private static boolean isSlotOccupied(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot,
        @Nonnull List<AdventurerSpawnSlot> spawnSlots,
        int slot
    ) {
        if (slot < 0 || slot >= spawnSlots.size()) {
            return false;
        }
        Vector3d slotPos = spawnSlots.get(slot).position();
        if (TownsfolkExistenceService.isSlotOccupiedByLiveAdventurer(town, store, hallPlot, slotPos, slot)) {
            return true;
        }
        for (Map.Entry<String, Integer> entry : town.getGuildHallAdventurerSlotByNpcId().entrySet()) {
            if (entry.getValue() == null || entry.getValue() != slot) {
                continue;
            }
            UUID u = parseUuid(entry.getKey());
            if (u == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref != null && ref.isValid() && isAdventurerEntity(town, store, ref, u)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAdventurerEntity(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UUID entityUuid
    ) {
        TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (b == null || !b.getTownId().equals(town.getTownId()) || !TownVillagerBinding.KIND_TOWNSFOLK.equals(b.getKind())) {
            return false;
        }
        TownsfolkCharacterBinding tb = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        if (tb == null || !TownsfolkAssignmentKinds.isGuildHallAdventurer(tb.getAssignmentKind())) {
            return false;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null && entityUuid.equals(uc.getUuid());
    }

    public static boolean isGuildHallAdventurer(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        String s = entityUuid.toString();
        for (String id : town.getGuildHallAdventurerNpcIds()) {
            if (s.equalsIgnoreCase(id != null ? id.trim() : "")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isManagementChunkLoaded(
        @Nonnull World world,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def
    ) {
        Vector3i pos = managementBlockWorldPos(plot, def);
        if (pos == null) {
            pos = new Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ());
        }
        return world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) != null;
    }

    @Nullable
    private static Vector3i managementBlockWorldPos(@Nonnull PlotInstance plot, @Nonnull ConstructionDefinition def) {
        int[] m = def.getManagementBlockLocalPos();
        if (m == null || m.length != 3) {
            return null;
        }
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        var yaw = plot.resolvePrefabYaw();
        Vector3i d = PrefabLocalOffset.rotate(yaw, m[0], m[1], m[2]);
        return new Vector3i(anchor.x + d.x, anchor.y + d.y, anchor.z + d.z);
    }

    @Nullable
    private static UUID parseUuid(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
