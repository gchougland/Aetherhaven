package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.QuestRewardService;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardHuntEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRaidEntryJson;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.TownVillagerDirectory;
import com.hexvane.aetherhaven.ui.TownVillagerRow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestBoardService {
    public static final String JOURNAL_ROW_PREFIX = "board:";

    private static final Map<String, QuestBoardQuestTypeHandler> HANDLERS = new HashMap<>();

    static {
        register(new FetchQuestBoardHandler());
        register(new HuntQuestBoardHandler());
        register(new RaidQuestBoardHandler());
    }

    private QuestBoardService() {}

    public static void register(@Nonnull QuestBoardQuestTypeHandler handler) {
        HANDLERS.put(handler.typeId(), handler);
    }

    @Nullable
    public static QuestBoardQuestTypeHandler handlerFor(@Nullable String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return null;
        }
        return HANDLERS.get(typeId.trim());
    }

    @Nonnull
    public static String journalRowId(@Nonnull String instanceId) {
        return JOURNAL_ROW_PREFIX + instanceId.trim();
    }

    @Nullable
    public static String parseJournalInstanceId(@Nullable String rowId) {
        if (rowId == null || !rowId.startsWith(JOURNAL_ROW_PREFIX)) {
            return null;
        }
        String id = rowId.substring(JOURNAL_ROW_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }

    public static boolean isBoardJournalRow(@Nullable String rowId) {
        return parseJournalInstanceId(rowId) != null;
    }

    /** Whether {@code rowId} is an active quest in the town journal (dialogue or board slot). */
    public static boolean isActiveJournalQuest(@Nonnull TownRecord town, @Nonnull String rowId) {
        String id = rowId.trim();
        if (id.isEmpty()) {
            return false;
        }
        if (town.getActiveQuestIdsSnapshot().contains(id)) {
            return true;
        }
        if (!isBoardJournalRow(id)) {
            return false;
        }
        String instanceId = parseJournalInstanceId(id);
        if (instanceId == null) {
            return false;
        }
        QuestBoardSlotRecord slot = town.findBoardSlotByInstanceId(instanceId);
        return slot != null && slot.isAccepted();
    }

    public static void ensureBoardInitialized(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng
    ) {
        town.ensureQuestBoardSlotCount(catalog.slotCount());
        Set<String> batchExclude = QuestBoardDrawPool.occupiedBoardEntryKeys(town);
        for (int i = 0; i < catalog.slotCount(); i++) {
            QuestBoardSlotRecord slot = town.getQuestBoardSlots().get(i);
            if (slot.stateEnum() == QuestBoardSlotState.EMPTY) {
                if (generateOffer(town, store, catalog, i, rng, batchExclude)) {
                    trackSlotEntryKey(batchExclude, slot);
                }
            } else {
                trackSlotEntryKey(batchExclude, slot);
            }
        }
    }

    public static void refreshUnacceptedSlots(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng
    ) {
        town.ensureQuestBoardSlotCount(catalog.slotCount());
        Set<String> batchExclude = new HashSet<>();
        for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
            if (slot.stateEnum() == QuestBoardSlotState.ACCEPTED) {
                trackSlotEntryKey(batchExclude, slot);
            }
        }
        for (int i = 0; i < catalog.slotCount(); i++) {
            QuestBoardSlotRecord slot = town.getQuestBoardSlots().get(i);
            if (slot.stateEnum() == QuestBoardSlotState.ACCEPTED) {
                continue;
            }
            if (generateOffer(town, store, catalog, i, rng, batchExclude)) {
                trackSlotEntryKey(batchExclude, slot);
            }
        }
    }

    public static boolean generateOffer(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog,
        int slotIndex,
        @Nonnull Random rng
    ) {
        Set<String> exclude = QuestBoardDrawPool.occupiedBoardEntryKeys(town);
        QuestBoardSlotRecord current = town.getQuestBoardSlots().get(slotIndex);
        if (current != null && current.stateEnum() != QuestBoardSlotState.EMPTY) {
            String role = current.getGiverRoleId();
            String entry = current.getConfigEntryId();
            String type = current.getQuestType();
            if (role != null && entry != null) {
                String questType = type != null && !type.isBlank() ? type.trim() : QuestBoardDrawPool.TYPE_FETCH;
                exclude.remove(QuestBoardDrawPool.entryKey(role, questType, entry));
            }
        }
        return generateOffer(town, store, catalog, slotIndex, rng, exclude);
    }

    public static boolean generateOffer(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog,
        int slotIndex,
        @Nonnull Random rng,
        @Nonnull Set<String> excludeKeys
    ) {
        town.ensureQuestBoardSlotCount(catalog.slotCount());
        if (slotIndex < 0 || slotIndex >= catalog.slotCount()) {
            return false;
        }
        QuestBoardSlotRecord slot = town.getQuestBoardSlots().get(slotIndex);
        if (slot.stateEnum() == QuestBoardSlotState.ACCEPTED) {
            return false;
        }

        int townRankIndex = TownQuestBoardRank.rankIndex(TownQuestBoardRank.tierIdForXp(town.getQuestBoardRankXp(), catalog));
        List<String> eligibleKeys = buildEligibleEntryKeys(town, store, catalog, townRankIndex);
        List<String> typeFiltered = filterEligibleByQuestType(eligibleKeys, excludeKeys, catalog, rng);
        if (typeFiltered.isEmpty()) {
            typeFiltered = eligibleKeys.stream().filter(k -> !excludeKeys.contains(k)).distinct().toList();
        }
        String pickedKey = QuestBoardDrawPool.pickEntryKey(town, typeFiltered, excludeKeys, rng);
        if (pickedKey == null) {
            slot.clearToEmpty();
            return false;
        }

        QuestBoardDrawPool.ParsedEntryKey parsed = QuestBoardDrawPool.parseEntryKey(pickedKey);
        if (parsed == null) {
            slot.clearToEmpty();
            return false;
        }
        List<TownVillagerRow> givers = giversForRole(town, store, parsed.roleId());
        if (givers.isEmpty()) {
            slot.clearToEmpty();
            return false;
        }
        TownVillagerRow giver = givers.get(rng.nextInt(givers.size()));

        slot.clearToEmpty();
        boolean ok = false;
        if (QuestBoardDrawPool.TYPE_HUNT.equalsIgnoreCase(parsed.questType())) {
            QuestBoardHuntEntryJson huntEntry = findHuntEntry(catalog, parsed.roleId(), parsed.entryId());
            if (huntEntry != null) {
                QuestBoardQuestTypeHandler handler = handlerFor(HuntQuestBoardHandler.TYPE_ID);
                if (handler instanceof HuntQuestBoardHandler huntHandler) {
                    ok =
                        huntHandler.populateSlot(
                            slot,
                            town,
                            store,
                            giver.roleId(),
                            giver.entityUuid().toString(),
                            huntEntry,
                            catalog,
                            rng
                        );
                }
            }
        } else if (QuestBoardDrawPool.TYPE_RAID.equalsIgnoreCase(parsed.questType())) {
            QuestBoardRaidEntryJson raidEntry = findRaidEntry(catalog, parsed.roleId(), parsed.entryId());
            if (raidEntry != null) {
                QuestBoardQuestTypeHandler handler = handlerFor(RaidQuestBoardHandler.TYPE_ID);
                if (handler instanceof RaidQuestBoardHandler raidHandler) {
                    ok =
                        raidHandler.populateSlot(
                            slot,
                            town,
                            store,
                            giver.roleId(),
                            giver.entityUuid().toString(),
                            raidEntry,
                            catalog,
                            rng
                        );
                }
            }
        } else {
            QuestBoardFetchEntryJson fetchEntry = findFetchEntry(catalog, parsed.roleId(), parsed.entryId());
            if (fetchEntry != null) {
                QuestBoardQuestTypeHandler handler = handlerFor(FetchQuestBoardHandler.TYPE_ID);
                if (handler != null) {
                    ok =
                        handler.populateSlot(
                            slot,
                            town,
                            store,
                            giver.roleId(),
                            giver.entityUuid().toString(),
                            fetchEntry,
                            catalog,
                            rng
                        );
                }
            }
        }
        if (!ok) {
            slot.clearToEmpty();
        }
        return ok;
    }

    private static void trackSlotEntryKey(@Nonnull Set<String> excludeKeys, @Nonnull QuestBoardSlotRecord slot) {
        if (!slot.occupiesBoardSlot()) {
            return;
        }
        String role = slot.getGiverRoleId();
        String entry = slot.getConfigEntryId();
        if (role != null && !role.isBlank() && entry != null && !entry.isBlank()) {
            String questType = slot.getQuestType() != null && !slot.getQuestType().isBlank()
                ? slot.getQuestType().trim()
                : QuestBoardDrawPool.TYPE_FETCH;
            excludeKeys.add(QuestBoardDrawPool.entryKey(role, questType, entry));
        }
    }

    @Nonnull
    private static List<String> filterEligibleByQuestType(
        @Nonnull List<String> eligibleKeys,
        @Nonnull Set<String> excludeKeys,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng
    ) {
        Map<String, List<String>> byType = new HashMap<>();
        for (String key : eligibleKeys) {
            if (excludeKeys.contains(key)) {
                continue;
            }
            QuestBoardDrawPool.ParsedEntryKey parsed = QuestBoardDrawPool.parseEntryKey(key);
            if (parsed == null) {
                continue;
            }
            byType.computeIfAbsent(parsed.questType(), t -> new ArrayList<>()).add(key);
        }
        if (byType.isEmpty()) {
            return List.of();
        }
        String pickedType = rollQuestType(catalog, byType.keySet(), rng);
        List<String> filtered = byType.get(pickedType);
        return filtered != null ? filtered : List.of();
    }

    @Nonnull
    private static String rollQuestType(@Nonnull QuestBoardCatalog catalog, @Nonnull Set<String> availableTypes, @Nonnull Random rng) {
        int total = 0;
        List<String> types = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (String type : availableTypes) {
            int w = catalog.questTypeWeight(type);
            if (w <= 0) {
                w = QuestBoardDrawPool.TYPE_FETCH.equals(type) ? 100 : 1;
            }
            types.add(type);
            weights.add(w);
            total += w;
        }
        if (total <= 0) {
            return types.get(rng.nextInt(types.size()));
        }
        int roll = rng.nextInt(total);
        int acc = 0;
        for (int i = 0; i < types.size(); i++) {
            acc += weights.get(i);
            if (roll < acc) {
                return types.get(i);
            }
        }
        return types.get(types.size() - 1);
    }

    @Nonnull
    private static List<String> buildEligibleEntryKeys(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog,
        int townRankIndex
    ) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            String roleId = row.roleId();
            for (QuestBoardFetchEntryJson entry : catalog.fetchEntriesForRole(roleId)) {
                if (!TownQuestBoardRank.townRankWithinWindow(townRankIndex, entry.minRank(), entry.maxRank())) {
                    continue;
                }
                if (entry.id() == null || entry.id().isBlank()) {
                    continue;
                }
                String key = QuestBoardDrawPool.entryKey(roleId, QuestBoardDrawPool.TYPE_FETCH, entry.id());
                if (seen.add(key)) {
                    out.add(key);
                }
            }
            for (QuestBoardHuntEntryJson entry : catalog.huntEntriesForRole(roleId)) {
                if (!TownQuestBoardRank.townRankWithinWindow(townRankIndex, entry.minRank(), entry.maxRank())) {
                    continue;
                }
                if (entry.id() == null || entry.id().isBlank()) {
                    continue;
                }
                String key = QuestBoardDrawPool.entryKey(roleId, QuestBoardDrawPool.TYPE_HUNT, entry.id());
                if (seen.add(key)) {
                    out.add(key);
                }
            }
            for (QuestBoardRaidEntryJson entry : catalog.raidEntriesForRole(roleId)) {
                if (!TownQuestBoardRank.townRankWithinWindow(townRankIndex, entry.minRank(), entry.maxRank())) {
                    continue;
                }
                if (entry.id() == null || entry.id().isBlank()) {
                    continue;
                }
                String key = QuestBoardDrawPool.entryKey(roleId, QuestBoardDrawPool.TYPE_RAID, entry.id());
                if (seen.add(key)) {
                    out.add(key);
                }
            }
        }
        return out;
    }

    @Nullable
    private static QuestBoardHuntEntryJson findHuntEntry(
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull String roleId,
        @Nonnull String entryId
    ) {
        for (QuestBoardHuntEntryJson entry : catalog.huntEntriesForRole(roleId)) {
            if (entryId.equalsIgnoreCase(entry.id())) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    private static QuestBoardRaidEntryJson findRaidEntry(
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull String roleId,
        @Nonnull String entryId
    ) {
        for (QuestBoardRaidEntryJson entry : catalog.raidEntriesForRole(roleId)) {
            if (entryId.equalsIgnoreCase(entry.id())) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    private static QuestBoardFetchEntryJson findFetchEntry(
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull String roleId,
        @Nonnull String entryId
    ) {
        for (QuestBoardFetchEntryJson entry : catalog.fetchEntriesForRole(roleId)) {
            if (entryId.equalsIgnoreCase(entry.id())) {
                return entry;
            }
        }
        return null;
    }

    @Nonnull
    private static List<TownVillagerRow> giversForRole(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull String roleId
    ) {
        List<TownVillagerRow> out = new ArrayList<>();
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            if (roleId.equals(row.roleId())) {
                out.add(row);
            }
        }
        return out;
    }

    public static boolean acceptOffer(
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        int slotIndex,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        town.ensureQuestBoardSlotCount(catalog.slotCount());
        if (slotIndex < 0 || slotIndex >= catalog.slotCount()) {
            return false;
        }
        if (!town.playerCanAcceptQuests(playerUuid)) {
            return false;
        }
        QuestBoardSlotRecord slot = town.getQuestBoardSlots().get(slotIndex);
        if (slot.stateEnum() != QuestBoardSlotState.OFFER) {
            return false;
        }
        slot.setState(QuestBoardSlotState.ACCEPTED);
        slot.setAcceptedByPlayerUuid(playerUuid.toString());
        slot.setOnlineDaysElapsed(0);
        refreshGiverEntityUuid(slot, town, store);
        if (slot.isHuntQuest()) {
            slot.setHuntKillProgress(0);
        }
        if (slot.isRaidQuest()) {
            slot.setRaidKillProgress(0);
            slot.setRaidSpawnedEntityUuids(new ArrayList<>());
            if (!RaidQuestSpawnService.startRaid(town, slot, world, store, playerUuid)) {
                RaidQuestSpawnService.cleanupRaid(slot, world, store, town.getTownId());
                slot.revertAcceptance();
                return false;
            }
        }
        return true;
    }

    public static boolean abandonOffer(
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        int slotIndex,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        town.ensureQuestBoardSlotCount(catalog.slotCount());
        if (slotIndex < 0 || slotIndex >= catalog.slotCount()) {
            return false;
        }
        if (!town.playerCanAbandonQuests(playerUuid)) {
            return false;
        }
        QuestBoardSlotRecord slot = town.getQuestBoardSlots().get(slotIndex);
        if (slot.stateEnum() != QuestBoardSlotState.ACCEPTED) {
            return false;
        }
        if (slot.isRaidQuest()) {
            RaidQuestSpawnService.cleanupRaid(slot, world, store, town.getTownId());
        }
        slot.markCompleted();
        return true;
    }

    public static boolean abandonByInstanceId(
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull String instanceId,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        town.ensureQuestBoardSlotCount(catalog.slotCount());
        for (int i = 0; i < town.getQuestBoardSlots().size(); i++) {
            QuestBoardSlotRecord slot = town.getQuestBoardSlots().get(i);
            if (instanceId.equals(slot.instanceIdOrEmpty()) && slot.isAccepted()) {
                if (!town.playerCanAbandonQuests(playerUuid)) {
                    return false;
                }
                if (slot.isRaidQuest()) {
                    RaidQuestSpawnService.cleanupRaid(slot, world, store, town.getTownId());
                }
                slot.markCompleted();
                return true;
            }
        }
        return false;
    }

    public static int slotIndexForInstanceId(@Nonnull TownRecord town, @Nonnull String instanceId) {
        List<QuestBoardSlotRecord> slots = town.getQuestBoardSlots();
        for (int i = 0; i < slots.size(); i++) {
            if (instanceId.equals(slots.get(i).instanceIdOrEmpty())) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    public static QuestBoardSlotRecord findAcceptedForGiver(@Nonnull TownRecord town, @Nonnull UUID giverEntityUuid) {
        return town.findAcceptedBoardQuestForGiver(giverEntityUuid);
    }

    @Nullable
    public static QuestBoardSlotRecord findAcceptedForGiver(
        @Nonnull TownRecord town,
        @Nonnull UUID giverEntityUuid,
        @Nullable String giverRoleId,
        @Nonnull Store<EntityStore> store
    ) {
        QuestBoardSlotRecord slot = town.findAcceptedBoardQuestForGiver(giverEntityUuid);
        if (slot != null) {
            return slot;
        }
        if (giverRoleId == null || giverRoleId.isBlank()) {
            return null;
        }
        List<QuestBoardSlotRecord> forRole = town.findAcceptedBoardQuestsForRole(giverRoleId.trim());
        if (forRole.isEmpty()) {
            return null;
        }
        List<QuestBoardSlotRecord> stale = new ArrayList<>();
        for (QuestBoardSlotRecord candidate : forRole) {
            if (!isGiverEntityLive(store, candidate.getGiverEntityUuid())) {
                stale.add(candidate);
            }
        }
        QuestBoardSlotRecord picked = null;
        if (stale.size() == 1) {
            picked = stale.get(0);
        } else if (stale.size() > 1) {
            picked = stale.get(0);
        } else if (forRole.size() == 1) {
            picked = forRole.get(0);
        }
        if (picked != null) {
            picked.setGiverEntityUuid(giverEntityUuid.toString());
        }
        return picked;
    }

    private static void refreshGiverEntityUuid(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store
    ) {
        String roleId = slot.getGiverRoleId();
        if (roleId == null || roleId.isBlank()) {
            return;
        }
        List<TownVillagerRow> givers = giversForRole(town, store, roleId.trim());
        if (!givers.isEmpty()) {
            slot.setGiverEntityUuid(givers.get(0).entityUuid().toString());
        }
    }

    private static boolean isGiverEntityLive(@Nonnull Store<EntityStore> store, @Nullable String giverEntityUuid) {
        if (giverEntityUuid == null || giverEntityUuid.isBlank()) {
            return false;
        }
        try {
            UUID u = UUID.fromString(giverEntityUuid.trim());
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            return ref != null && ref.isValid();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean completeFetchQuest(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID giverEntityUuid,
        @Nullable String giverRoleId,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng
    ) {
        return completeBoardQuest(town, tm, playerRef, store, giverEntityUuid, giverRoleId, catalog, rng);
    }

    public static boolean completeBoardQuest(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID giverEntityUuid,
        @Nullable String giverRoleId,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng
    ) {
        QuestBoardSlotRecord slot = findAcceptedForGiver(town, giverEntityUuid, giverRoleId, store);
        if (slot == null || slot.getQuestType() == null || slot.getQuestType().isBlank()) {
            return false;
        }
        QuestBoardQuestTypeHandler handler = handlerFor(slot.getQuestType());
        if (handler == null || !handler.consumeRequiredItems(playerRef, store, slot)) {
            return false;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        grantRewards(slot, town, tm, player, playerRef, store, giverEntityUuid);
        int oldXp = town.getQuestBoardRankXp();
        Message completedName = displayTitle(slot, town, store, catalog);
        town.addQuestBoardRankXp(slot.getRankXpReward());
        int newXp = town.getQuestBoardRankXp();
        String oldTier = TownQuestBoardRank.tierIdForXp(oldXp, catalog);
        String newTier = TownQuestBoardRank.tierIdForXp(newXp, catalog);

        if (slot.isRaidQuest()) {
            RaidQuestSpawnService.cleanupRaid(slot, store.getExternalData().getWorld(), store, town.getTownId());
        }
        slot.markCompleted();
        tm.updateTown(town);

        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            QuestBoardCompletionEffects.notifyCompleted(pr, playerRef, store, completedName);
            pr.sendMessage(
                Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.completedToast")
                    .param("name", completedName)
            );
            if (!oldTier.equalsIgnoreCase(newTier)) {
                pr.sendMessage(
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.rankUpToast")
                        .param("rank", newTier)
                );
            }
        }
        return true;
    }

    private static void grantRewards(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID giverEntityUuid
    ) {
        for (QuestReward r : slot.rewardsOrEmpty()) {
            if (r.kind() == null) {
                continue;
            }
            if ("item".equalsIgnoreCase(r.kind().trim())) {
                String itemId = r.itemId();
                if (itemId != null && !itemId.isBlank()) {
                    player.giveItem(new ItemStack(itemId.trim(), Math.max(1, r.count())), playerRef, store);
                }
            }
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        String giverRoleId = slot.getGiverRoleId();
        if (pu == null || giverRoleId == null || giverRoleId.isBlank()) {
            return;
        }
        World world = store.getExternalData().getWorld();
        QuestRewardService.grantVillagerReputationRewards(
            slot.rewardsOrEmpty(),
            pu.getUuid(),
            giverEntityUuid,
            giverRoleId.trim(),
            world,
            town,
            tm
        );
    }

    public static void failExpiredQuest(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog,
        @Nonnull Random rng,
        @Nullable PlayerRef notifyPlayer
    ) {
        if (!slot.isAccepted()) {
            return;
        }
        if (slot.isRaidQuest()) {
            RaidQuestSpawnService.cleanupRaid(slot, store.getExternalData().getWorld(), store, town.getTownId());
        }
        int slotIndex = slotIndexForInstanceId(town, slot.instanceIdOrEmpty());
        slot.clearToEmpty();
        if (slotIndex >= 0) {
            generateOffer(town, store, catalog, slotIndex, rng);
        }
        tm.updateTown(town);
        if (notifyPlayer != null) {
            notifyPlayer.sendMessage(Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.failedToast"));
        }
    }

    @Nonnull
    public static Message displayTitle(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog
    ) {
        QuestBoardQuestTypeHandler handler = handlerFor(slot.getQuestType());
        if (handler != null) {
            return handler.displayTitle(slot, town, store, catalog);
        }
        return Message.raw("");
    }

    @Nonnull
    public static Message displayDescription(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog
    ) {
        QuestBoardQuestTypeHandler handler = handlerFor(slot.getQuestType());
        if (handler != null) {
            return handler.displayDescription(slot, town, store, catalog);
        }
        return Message.raw("");
    }

    @Nonnull
    public static Message objectivesText(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestBoardCatalog catalog
    ) {
        QuestBoardQuestTypeHandler handler = handlerFor(slot.getQuestType());
        if (handler != null) {
            return handler.objectivesText(slot, town, store, catalog);
        }
        return Message.raw("");
    }

    public static int daysRemaining(@Nonnull QuestBoardSlotRecord slot) {
        return Math.max(0, slot.getDaysLimit() - slot.getOnlineDaysElapsed());
    }

    @Nonnull
    public static List<QuestReward> itemRewards(@Nonnull QuestBoardSlotRecord slot) {
        List<QuestReward> out = new ArrayList<>();
        for (QuestReward r : slot.rewardsOrEmpty()) {
            if (r.kind() != null && "item".equalsIgnoreCase(r.kind().trim()) && r.itemId() != null && !r.itemId().isBlank()) {
                out.add(r);
            }
        }
        return out;
    }

    @Nullable
    public static QuestReward firstItemReward(@Nonnull QuestBoardSlotRecord slot) {
        List<QuestReward> items = itemRewards(slot);
        return items.isEmpty() ? null : items.get(0);
    }

    @Nullable
    public static QuestRewardService.ReputationRewardPreview firstReputationReward(@Nonnull QuestBoardSlotRecord slot) {
        return QuestRewardService.firstQuestBoardReputationReward(slot.rewardsOrEmpty());
    }

    @Nonnull
    public static String hudProgressKey(@Nonnull QuestBoardSlotRecord slot) {
        if (slot.isHuntQuest()) {
            return slot.instanceIdOrEmpty() + ":hunt:" + slot.getHuntKillProgress() + '/' + slot.getHuntKillRequired();
        }
        if (slot.isRaidQuest()) {
            return slot.instanceIdOrEmpty() + ":raid:" + slot.getRaidKillProgress() + '/' + slot.getRaidKillRequired();
        }
        return slot.instanceIdOrEmpty();
    }
}
