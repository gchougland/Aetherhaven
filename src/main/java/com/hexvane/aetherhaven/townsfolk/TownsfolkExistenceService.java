package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.guild.GuildHallDisplayAnchor;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Per-town ledger for townsfolk character checkouts (one slot per character per town). */
public final class TownsfolkExistenceService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double SLOT_OCCUPANCY_RADIUS_SQ = 1.5 * 1.5;

    public enum ReleaseReason {
        DEATH,
        DESPAWN,
        RECONCILE,
        ADMIN
    }

    public record LiveTownsfolkEntity(
        @Nonnull String characterId,
        @Nonnull UUID entityUuid,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String assignmentKind,
        @Nullable UUID townId
    ) {}

    private TownsfolkExistenceService() {}

    public static boolean isCharacterOccupied(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId,
        @Nonnull String characterId
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        return pool.isCheckedOut(townId, characterId.trim());
    }

    public static void registerSpawn(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownsfolkPoolCheckoutRecord record
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        pool.checkout(record);
        TownsfolkPoolPersistence.save(world, plugin, pool);
    }

    public static boolean transferInstanceOnHire(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String characterId,
        @Nonnull UUID newEntityUuid,
        @Nonnull UUID townId
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        TownsfolkPoolCheckoutRecord checkout = pool.checkoutForCharacter(townId, characterId);
        if (checkout == null) {
            LOGGER.atWarning().log("Hire transfer failed: no ledger entry for townsfolk %s in town %s", characterId, townId);
            return false;
        }
        checkout.setAssignmentKind(TownsfolkAssignmentKinds.GUARD);
        checkout.setEntityUuid(newEntityUuid.toString());
        checkout.setInstanceGeneration(checkout.getInstanceGeneration() + 1);
        checkout.setTownId(townId.toString());
        TownsfolkPoolPersistence.save(world, plugin, pool);
        return true;
    }

    public static void releaseCharacter(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId,
        @Nonnull String characterId,
        @Nonnull ReleaseReason reason
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        if (pool.release(townId, characterId)) {
            TownsfolkPoolPersistence.save(world, plugin, pool);
            LOGGER.atFine().log("Released townsfolk %s from town %s ledger (%s)", characterId, townId, reason);
        }
    }

    public static void releaseByEntity(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID entityUuid
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        TownsfolkPoolCheckoutRecord rec = pool.checkoutForEntity(entityUuid);
        if (rec != null) {
            UUID townId = parseUuid(rec.getTownId());
            if (townId != null) {
                pool.release(townId, rec.getCharacterId());
                TownsfolkPoolPersistence.save(world, plugin, pool);
            }
        }
    }

    /** Releases all pooled characters assigned to a dissolved town and persists the ledger once. */
    public static int releaseForTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        int released = pool.releaseForTown(townId);
        if (released > 0) {
            TownsfolkPoolPersistence.save(world, plugin, pool);
        }
        return released;
    }

    @Nullable
    public static TownsfolkPoolCheckoutRecord checkoutForCharacter(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId,
        @Nonnull String characterId
    ) {
        return TownsfolkPoolPersistence.getOrLoad(world, plugin).checkoutForCharacter(townId, characterId);
    }

    /**
     * Releases non-guard ledger rows whose character is not live in the world. Keeps guard rows when the entity is
     * unloaded (chunk not loaded). {@code guild_adventurer} rows are always reclaimed when not live so dawn spawns are
     * not blocked by stale ledger entries. Other non-guard rows keep their checkout when the entity ref is unloaded in
     * another chunk until reconcile proves absence.
     */
    public static int reclaimAbsentNonGuardCheckouts(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        List<TownsfolkPoolCheckoutRecord> toRelease = new ArrayList<>();
        for (TownsfolkPoolCheckoutRecord rec : pool.getCheckouts().values()) {
            String characterId = rec.getCharacterId();
            if (characterId.isBlank()) {
                continue;
            }
            UUID townId = parseUuid(rec.getTownId());
            if (townId == null) {
                toRelease.add(rec);
                continue;
            }
            if (TownsfolkAssignmentKinds.GUARD.equalsIgnoreCase(rec.getAssignmentKind().trim())) {
                continue;
            }
            UUID ledgerUuid = parseUuid(rec.getEntityUuid());
            if (ledgerUuid == null) {
                toRelease.add(rec);
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(ledgerUuid);
            if (ref != null && ref.isValid()) {
                continue;
            }
            if (TownsfolkAssignmentKinds.isGuildHallAdventurer(rec.getAssignmentKind())) {
                toRelease.add(rec);
                continue;
            }
            if (ref == null) {
                // Entity unloaded in another chunk — keep checkout until reconcile proves absence.
                continue;
            }
            UUID townUuid = parseUuid(rec.getTownId());
            if (townUuid != null) {
                VillagerAuditService.logDetectedMissing(
                    plugin,
                    world,
                    store,
                    ledgerUuid,
                    townUuid,
                    characterId,
                    rec.getAssignmentKind(),
                    characterId,
                    "townsfolk_reconcile",
                    "Checkout ledger entity ref is invalid"
                );
            }
            toRelease.add(rec);
        }
        for (TownsfolkPoolCheckoutRecord rec : toRelease) {
            UUID townId = parseUuid(rec.getTownId());
            if (townId != null) {
                pool.release(townId, rec.getCharacterId());
            }
        }
        if (!toRelease.isEmpty()) {
            TownsfolkPoolPersistence.save(world, plugin, pool);
        }
        return toRelease.size();
    }

    @Nonnull
    public static PoolSummary summarizePool(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        int guard = 0;
        int adventurer = 0;
        int other = 0;
        String townKey = townId.toString();
        for (TownsfolkPoolCheckoutRecord rec : pool.getCheckouts().values()) {
            if (!townKey.equalsIgnoreCase(rec.getTownId().trim())) {
                continue;
            }
            String kind = rec.getAssignmentKind().trim().toLowerCase();
            if (TownsfolkAssignmentKinds.GUARD.equals(kind)) {
                guard++;
            } else if (TownsfolkAssignmentKinds.isGuildHallAdventurer(kind)) {
                adventurer++;
            } else {
                other++;
            }
        }
        return new PoolSummary(guard + adventurer + other, guard, adventurer, other);
    }

    public record PoolSummary(int total, int guards, int guildAdventurers, int other) {}

    @Nonnull
    public static Map<String, LiveTownsfolkEntity> buildLiveIndex(@Nonnull Store<EntityStore> store) {
        Map<String, LiveTownsfolkEntity> byCharacter = new HashMap<>();
        store.forEachChunk(
            Query.and(TownsfolkCharacterBinding.getComponentType(), UUIDComponent.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    TownsfolkCharacterBinding tb = archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (tb == null || uc == null) {
                        continue;
                    }
                    String characterId = tb.getCharacterId().trim();
                    if (characterId.isEmpty()) {
                        continue;
                    }
                    UUID townId = null;
                    TownVillagerBinding vb = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (vb != null) {
                        townId = vb.getTownId();
                    }
                    byCharacter.put(
                        characterId,
                        new LiveTownsfolkEntity(characterId, uc.getUuid(), ref, tb.getAssignmentKind(), townId)
                    );
                }
            }
        );
        return byCharacter;
    }

    @Nonnull
    public static Map<String, List<LiveTownsfolkEntity>> buildLiveIndexAllInstances(@Nonnull Store<EntityStore> store) {
        Map<String, List<LiveTownsfolkEntity>> all = new HashMap<>();
        for (LiveTownsfolkEntity live : buildLiveIndex(store).values()) {
            all.computeIfAbsent(live.characterId(), k -> new ArrayList<>()).add(live);
        }
        // Rescan to capture duplicates (buildLiveIndex keeps last only)
        store.forEachChunk(
            Query.and(TownsfolkCharacterBinding.getComponentType(), UUIDComponent.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    TownsfolkCharacterBinding tb = archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (tb == null || uc == null) {
                        continue;
                    }
                    String characterId = tb.getCharacterId().trim();
                    if (characterId.isEmpty()) {
                        continue;
                    }
                    UUID townId = null;
                    TownVillagerBinding vb = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (vb != null) {
                        townId = vb.getTownId();
                    }
                    LiveTownsfolkEntity live = new LiveTownsfolkEntity(characterId, uc.getUuid(), ref, tb.getAssignmentKind(), townId);
                    List<LiveTownsfolkEntity> list = all.computeIfAbsent(characterId, k -> new ArrayList<>());
                    boolean seen = false;
                    for (LiveTownsfolkEntity existing : list) {
                        if (existing.entityUuid().equals(live.entityUuid())) {
                            seen = true;
                            break;
                        }
                    }
                    if (!seen) {
                        list.add(live);
                    }
                }
            }
        );
        return all;
    }

    public static void purgeDuplicateEntities(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull String characterId,
        @Nonnull UUID canonicalUuid
    ) {
        List<UUID> duplicateUuids = new ArrayList<>();
        store.forEachChunk(
            Query.and(TownsfolkCharacterBinding.getComponentType(), UUIDComponent.getComponentType(), TownVillagerBinding.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    TownVillagerBinding vb = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (vb == null || !townId.equals(vb.getTownId())) {
                        continue;
                    }
                    TownsfolkCharacterBinding tb = archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (tb == null || uc == null) {
                        continue;
                    }
                    if (!characterId.equalsIgnoreCase(tb.getCharacterId().trim())) {
                        continue;
                    }
                    if (canonicalUuid.equals(uc.getUuid())) {
                        continue;
                    }
                    duplicateUuids.add(uc.getUuid());
                }
            }
        );
        PendingEntityRemovalService.scheduleAll(world, duplicateUuids, "townsfolk_duplicate_purge");
        for (UUID staleUuid : duplicateUuids) {
            LOGGER.atWarning().log(
                "Removed duplicate townsfolk entity for %s (stale uuid %s, canonical %s)",
                characterId,
                staleUuid,
                canonicalUuid
            );
        }
    }

    private record StaleGuildHallAdventurer(@Nonnull UUID entityUuid, @Nonnull String characterId) {}

    /**
     * Despawns guild hall adventurer entities that are not backed by a valid pool checkout. Entity removal is deferred
     * until after chunk save for this tick.
     */
    public static int purgeStaleGuildHallAdventurers(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot,
        @Nullable TownManager tm
    ) {
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        PlotFootprintRecord footprint = hallPlot.toFootprint();
        List<StaleGuildHallAdventurer> stale = new ArrayList<>();

        store.forEachChunk(
            Query.and(TownVillagerBinding.getComponentType(), TownsfolkCharacterBinding.getComponentType(), TransformComponent.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !b.getTownId().equals(town.getTownId()) || !TownVillagerBinding.KIND_TOWNSFOLK.equals(b.getKind())) {
                        continue;
                    }
                    TownsfolkCharacterBinding tb = archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    if (tb == null || !TownsfolkAssignmentKinds.isGuildHallAdventurer(tb.getAssignmentKind())) {
                        continue;
                    }
                    TransformComponent tc = archetypeChunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d pos = tc.getPosition();
                    int bx = (int) Math.floor(pos.x);
                    int by = (int) Math.floor(pos.y);
                    int bz = (int) Math.floor(pos.z);
                    if (!footprint.containsBlock(bx, by, bz)) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null) {
                        continue;
                    }
                    String characterId = tb.getCharacterId().trim();
                    if (characterId.isEmpty()) {
                        continue;
                    }
                    TownsfolkPoolCheckoutRecord checkout = pool.checkoutForCharacter(town.getTownId(), characterId);
                    boolean isStale =
                        checkout == null
                            || !TownsfolkAssignmentKinds.isGuildHallAdventurer(checkout.getAssignmentKind())
                            || !uc.getUuid().toString().equalsIgnoreCase(checkout.getEntityUuid());
                    if (isStale) {
                        stale.add(new StaleGuildHallAdventurer(uc.getUuid(), characterId));
                    }
                }
            }
        );

        if (stale.isEmpty()) {
            return 0;
        }

        List<UUID> despawnUuids = new ArrayList<>(stale.size());
        boolean townChanged = false;
        for (StaleGuildHallAdventurer entry : stale) {
            despawnUuids.add(entry.entityUuid());
            releaseByEntity(world, plugin, entry.entityUuid());
            String uuidStr = entry.entityUuid().toString();
            if (town.getGuildHallAdventurerNpcIds().removeIf(s -> s != null && uuidStr.equalsIgnoreCase(s.trim()))) {
                townChanged = true;
            }
            if (town.getGuildHallAdventurerSlotByNpcId().remove(uuidStr) != null) {
                townChanged = true;
            }
        }
        if (townChanged && tm != null) {
            tm.updateTown(town);
        }
        PendingEntityRemovalService.scheduleAll(world, despawnUuids, "guild_hall_stale_adventurer_purge");
        return stale.size();
    }

    public static boolean isSlotOccupiedByLiveAdventurer(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot,
        @Nonnull Vector3d slotPosition,
        int slot
    ) {
        PlotFootprintRecord footprint = hallPlot.toFootprint();
        double slotX = slotPosition.x;
        double slotZ = slotPosition.z;
        boolean[] occupied = { false };

        store.forEachChunk(
            Query.and(TownVillagerBinding.getComponentType(), TownsfolkCharacterBinding.getComponentType(), TransformComponent.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                if (occupied[0]) {
                    return;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !b.getTownId().equals(town.getTownId()) || !TownVillagerBinding.KIND_TOWNSFOLK.equals(b.getKind())) {
                        continue;
                    }
                    TownsfolkCharacterBinding tb = archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    if (tb == null || !TownsfolkAssignmentKinds.isGuildHallAdventurer(tb.getAssignmentKind())) {
                        continue;
                    }
                    TransformComponent tc = archetypeChunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d pos = tc.getPosition();
                    int bx = (int) Math.floor(pos.x);
                    int by = (int) Math.floor(pos.y);
                    int bz = (int) Math.floor(pos.z);
                    if (!footprint.containsBlock(bx, by, bz)) {
                        continue;
                    }
                    GuildHallDisplayAnchor anchor = archetypeChunk.getComponent(i, GuildHallDisplayAnchor.getComponentType());
                    if (anchor != null) {
                        Vector3d ap = anchor.getPosition();
                        double dx = ap.x - slotX;
                        double dz = ap.z - slotZ;
                        if (dx * dx + dz * dz <= SLOT_OCCUPANCY_RADIUS_SQ) {
                            occupied[0] = true;
                            return;
                        }
                    }
                    double dx = pos.x - slotX;
                    double dz = pos.z - slotZ;
                    if (dx * dx + dz * dz <= SLOT_OCCUPANCY_RADIUS_SQ) {
                        occupied[0] = true;
                        return;
                    }
                }
            }
        );
        return occupied[0];
    }

    public static void reconcileAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> reconcileOnWorldThread(world, plugin));
    }

    private static void reconcileOnWorldThread(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        var entityStore = world.getEntityStore();
        Store<EntityStore> store = entityStore != null ? entityStore.getStore() : null;
        if (store == null) {
            LOGGER.atWarning().log("Townsfolk existence reconcile skipped: entity store not ready for world %s", world.getName());
            return;
        }

        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        Map<String, List<LiveTownsfolkEntity>> liveAll = buildLiveIndexAllInstances(store);
        List<TownsfolkPoolCheckoutRecord> toRelease = new ArrayList<>();
        boolean poolChanged = false;

        for (TownsfolkPoolCheckoutRecord rec : new ArrayList<>(pool.getCheckouts().values())) {
            String characterId = rec.getCharacterId();
            UUID townId = parseUuid(rec.getTownId());
            if (townId == null || characterId.isBlank()) {
                toRelease.add(rec);
                continue;
            }
            List<LiveTownsfolkEntity> liveList = liveInstancesForTown(liveAll, townId, characterId);
            UUID ledgerUuid = parseUuid(rec.getEntityUuid());

            if (!liveList.isEmpty()) {
                UUID canonical = resolveCanonicalUuid(rec, liveList);
                if (canonical != null && ledgerUuid != null && !canonical.equals(ledgerUuid)) {
                    rec.setEntityUuid(canonical.toString());
                    poolChanged = true;
                }
                purgeDuplicateEntities(
                    world,
                    store,
                    townId,
                    characterId,
                    canonical != null ? canonical : liveList.get(0).entityUuid()
                );
                continue;
            }

            if (ledgerUuid == null) {
                toRelease.add(rec);
                continue;
            }

            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(ledgerUuid);
            if (ref != null && !ref.isValid()) {
                toRelease.add(rec);
            }
            // ref == null: entity unloaded — keep ledger entry
        }

        for (TownsfolkPoolCheckoutRecord rec : toRelease) {
            UUID townId = parseUuid(rec.getTownId());
            if (townId != null && !rec.getCharacterId().isBlank()) {
                pool.release(townId, rec.getCharacterId());
                poolChanged = true;
            }
        }

        if (poolChanged) {
            TownsfolkPoolPersistence.save(world, plugin, pool);
            LOGGER.atInfo().log("Townsfolk existence reconcile updated ledger in world %s", world.getName());
        }

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName()) || !town.isGuildHallActive()) {
                continue;
            }
            PlotInstance hallPlot =
                town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL);
            if (hallPlot == null) {
                continue;
            }
            purgeStaleGuildHallAdventurers(world, plugin, town, store, hallPlot, tm);
            syncTownGuildHallLists(world, plugin, town, store, tm, pool);
        }
    }

    private static void syncTownGuildHallLists(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm,
        @Nonnull TownsfolkPoolState pool
    ) {
        Set<String> validNpcIds = new HashSet<>();

        for (TownsfolkPoolCheckoutRecord rec : pool.getCheckouts().values()) {
            if (!town.getTownId().toString().equals(rec.getTownId())) {
                continue;
            }
            if (!TownsfolkAssignmentKinds.isGuildHallAdventurer(rec.getAssignmentKind())) {
                continue;
            }
            UUID u = parseUuid(rec.getEntityUuid());
            if (u == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            if (!isLiveGuildAdventurer(town, store, ref, u)) {
                continue;
            }
            String uuidStr = u.toString();
            validNpcIds.add(uuidStr.toLowerCase());
        }

        boolean changed = false;
        List<String> npcIds = town.getGuildHallAdventurerNpcIds();
        List<String> pruned = new ArrayList<>();
        for (String sid : npcIds) {
            if (sid != null && validNpcIds.contains(sid.trim().toLowerCase())) {
                pruned.add(sid.trim());
            } else if (sid != null && !sid.isBlank()) {
                changed = true;
            }
        }
        if (pruned.size() != npcIds.size()) {
            npcIds.clear();
            npcIds.addAll(pruned);
            changed = true;
        }

        var slotMap = town.getGuildHallAdventurerSlotByNpcId();
        Set<String> slotKeysToRemove = new HashSet<>();
        for (String key : slotMap.keySet()) {
            if (key == null || !validNpcIds.contains(key.trim().toLowerCase())) {
                slotKeysToRemove.add(key);
            }
        }
        if (!slotKeysToRemove.isEmpty()) {
            for (String key : slotKeysToRemove) {
                slotMap.remove(key);
            }
            changed = true;
        }

        if (changed) {
            tm.updateTown(town);
        }
    }

    @Nonnull
    private static List<LiveTownsfolkEntity> liveInstancesForTown(
        @Nonnull Map<String, List<LiveTownsfolkEntity>> liveAll,
        @Nonnull UUID townId,
        @Nonnull String characterId
    ) {
        List<LiveTownsfolkEntity> list = liveAll.get(characterId.trim());
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<LiveTownsfolkEntity> out = new ArrayList<>();
        for (LiveTownsfolkEntity live : list) {
            if (townId.equals(live.townId())) {
                out.add(live);
            }
        }
        return out;
    }

    @Nullable
    private static UUID resolveCanonicalUuid(
        @Nonnull TownsfolkPoolCheckoutRecord rec,
        @Nonnull List<LiveTownsfolkEntity> liveList
    ) {
        UUID ledgerUuid = parseUuid(rec.getEntityUuid());
        if (ledgerUuid != null) {
            for (LiveTownsfolkEntity live : liveList) {
                if (ledgerUuid.equals(live.entityUuid())) {
                    return ledgerUuid;
                }
            }
        }
        if (liveList.size() == 1) {
            return liveList.get(0).entityUuid();
        }
        for (LiveTownsfolkEntity live : liveList) {
            if (rec.getAssignmentKind().equalsIgnoreCase(live.assignmentKind())) {
                return live.entityUuid();
            }
        }
        return liveList.get(0).entityUuid();
    }

    private static boolean isLiveGuildAdventurer(
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

    @Nullable
    private static UUID parseUuid(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
