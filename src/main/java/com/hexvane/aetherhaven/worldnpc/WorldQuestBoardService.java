package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.questboard.QuestBoardGoldRewardScaling;
import com.hexvane.aetherhaven.questboard.HuntQuestBoardHandler;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotState;
import com.hexvane.aetherhaven.questboard.TownQuestBoardRank;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRankTierJson;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-player world quest board offers and ranks. */
public final class WorldQuestBoardService {
    private WorldQuestBoardService() {}

    public static void ensureInitialized(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String profileId
    ) {
        WorldQuestBoardProfileJson profile = plugin.getWorldQuestBoardCatalog().get(profileId);
        if (profile == null) {
            return;
        }
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        List<QuestBoardSlotRecord> slots = progress.boardSlots(profileId);
        if (slots.size() >= profile.slotCount()) {
            return;
        }
        refreshUnaccepted(world, plugin, playerUuid, profileId, new Random());
    }

    public static void refreshUnaccepted(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String profileId,
        @Nonnull Random rng
    ) {
        WorldQuestBoardProfileJson profile = plugin.getWorldQuestBoardCatalog().get(profileId);
        if (profile == null) {
            return;
        }
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        List<QuestBoardSlotRecord> slots = progress.boardSlots(profileId);
        List<QuestBoardSlotRecord> kept = new ArrayList<>();
        for (QuestBoardSlotRecord slot : slots) {
            if (slot != null && slot.isAccepted()) {
                kept.add(slot);
            }
        }
        slots.clear();
        slots.addAll(kept);
        int need = profile.slotCount() - slots.size();
        int xp = progress.boardRankXp(profileId);
        String rankId = tierIdForXp(xp, profile);
        for (int i = 0; i < need; i++) {
            WorldQuestBoardPoolEntryJson entry = pickEntry(profile, rankId, rng);
            if (entry == null) {
                break;
            }
            QuestBoardSlotRecord slot = new QuestBoardSlotRecord();
            slot.setInstanceId(UUID.randomUUID().toString());
            slot.setState(QuestBoardSlotState.OFFER);
            slot.setQuestType(entry.questTypeOrFetch());
            slot.setQuestRank(rankId);
            slot.setTitleLangKey(entry.titleLangKey());
            slot.setDescriptionLangKey(entry.descriptionLangKey());
            slot.setConfigEntryId(entry.idOrEmpty());
            slot.setRankXpReward(entry.rankXpReward());
            slot.setDaysLimit(entry.daysLimit());
            slot.setGenerationSeed(rng.nextLong());
            if (!entry.rewardsOrEmpty().isEmpty()) {
                double goldMultiplier =
                    HuntQuestBoardHandler.TYPE_ID.equalsIgnoreCase(entry.questTypeOrFetch())
                        ? plugin.getQuestBoardCatalog().goldCoinMultiplierForType(HuntQuestBoardHandler.TYPE_ID)
                        : 1.0;
                slot.setRewards(
                    QuestBoardGoldRewardScaling.applyGoldCoinMultiplier(entry.rewardsOrEmpty(), goldMultiplier)
                );
            }
            slots.add(slot);
        }
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
    }

    public static boolean accept(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String profileId,
        @Nonnull String instanceId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        for (QuestBoardSlotRecord slot : progress.boardSlots(profileId)) {
            if (slot != null && instanceId.equals(slot.getInstanceId())) {
                if (slot.isAccepted()) {
                    return false;
                }
                slot.setState(QuestBoardSlotState.ACCEPTED);
                slot.setAcceptedByPlayerUuid(playerUuid.toString());
                registry.markPlayerDirty();
                WorldNpcPersistence.save(world, plugin, registry);
                return true;
            }
        }
        return false;
    }

    public static boolean complete(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String profileId,
        @Nonnull String instanceId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        List<QuestBoardSlotRecord> slots = progress.boardSlots(profileId);
        for (int i = 0; i < slots.size(); i++) {
            QuestBoardSlotRecord slot = slots.get(i);
            if (slot == null || !instanceId.equals(slot.getInstanceId()) || !slot.isAccepted()) {
                continue;
            }
            progress.addBoardRankXp(profileId, slot.getRankXpReward());
            slots.remove(i);
            registry.markPlayerDirty();
            WorldNpcPersistence.save(world, plugin, registry);
            return true;
        }
        return false;
    }

    public static boolean abandon(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String profileId,
        @Nonnull String instanceId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        List<QuestBoardSlotRecord> slots = progress.boardSlots(profileId);
        for (int i = 0; i < slots.size(); i++) {
            QuestBoardSlotRecord slot = slots.get(i);
            if (slot == null || !instanceId.equals(slot.getInstanceId())) {
                continue;
            }
            slots.remove(i);
            registry.markPlayerDirty();
            WorldNpcPersistence.save(world, plugin, registry);
            return true;
        }
        return false;
    }

    public static boolean abandonByInstanceId(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String instanceId
    ) {
        WorldNpcPlayerProgress progress =
            AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin).getOrCreatePlayerProgress(playerUuid);
        String profileId = progress.findBoardProfileIdForInstance(instanceId);
        if (profileId == null) {
            return false;
        }
        return abandon(world, plugin, playerUuid, profileId, instanceId);
    }

    @Nullable
    public static QuestBoardSlotRecord findAcceptedSlot(
        @Nonnull WorldNpcPlayerProgress progress,
        @Nonnull String instanceId
    ) {
        for (QuestBoardSlotRecord slot : progress.allBoardSlotsFlat()) {
            if (slot != null && instanceId.equals(slot.getInstanceId()) && slot.isAccepted()) {
                return slot;
            }
        }
        return null;
    }

    @Nonnull
    public static String tierIdForXp(int xp, @Nonnull WorldQuestBoardProfileJson profile) {
        String current = "E";
        for (QuestBoardRankTierJson tier : profile.ranksOrEmpty()) {
            if (xp >= tier.xpRequired()) {
                current = tier.idOrEmpty().isEmpty() ? current : tier.idOrEmpty();
            }
        }
        if (profile.ranksOrEmpty().isEmpty()) {
            return TownQuestBoardRank.rankOrder().get(0);
        }
        return current;
    }

    @Nullable
    private static WorldQuestBoardPoolEntryJson pickEntry(
        @Nonnull WorldQuestBoardProfileJson profile,
        @Nonnull String rankId,
        @Nonnull Random rng
    ) {
        List<WorldQuestBoardPoolEntryJson> eligible = new ArrayList<>();
        int total = 0;
        int rankIndex = TownQuestBoardRank.rankIndex(rankId);
        for (WorldQuestBoardPoolEntryJson entry : profile.poolOrEmpty()) {
            if (entry == null || entry.idOrEmpty().isEmpty()) {
                continue;
            }
            if (!TownQuestBoardRank.townRankWithinWindow(rankIndex, entry.minRank(), entry.maxRank())) {
                continue;
            }
            eligible.add(entry);
            total += entry.weight();
        }
        if (eligible.isEmpty() || total <= 0) {
            return null;
        }
        int roll = rng.nextInt(total);
        int acc = 0;
        for (WorldQuestBoardPoolEntryJson entry : eligible) {
            acc += entry.weight();
            if (roll < acc) {
                return entry;
            }
        }
        return eligible.get(eligible.size() - 1);
    }
}
