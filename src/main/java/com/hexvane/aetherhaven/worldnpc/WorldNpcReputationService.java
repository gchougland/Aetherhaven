package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WorldNpcReputationService {
    private WorldNpcReputationService() {}

    @Nonnull
    public static VillagerReputationEntry getOrCreate(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        VillagerReputationEntry entry = progress.reputationForPlacement(placementId);
        entry.migrateIfNeeded();
        registry.markPlayerDirty();
        return entry;
    }

    public static int applyDailyTalkBonus(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId
    ) {
        long day = VillagerReputationService.currentGameEpochDay(store);
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        VillagerReputationEntry entry = progress.reputationForPlacement(placementId);
        entry.migrateIfNeeded();
        Long last = entry.getLastTalkGameEpochDay();
        if (last != null && last == day) {
            return 0;
        }
        int before = entry.getReputation();
        int after = Math.min(VillagerReputationService.MAX_REPUTATION, before + VillagerReputationService.DAILY_TALK_BONUS);
        entry.setReputation(after);
        entry.setLastTalkGameEpochDay(day);
        enqueueMilestones(plugin, world, progress, placementId, entry, before, after);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
        return after - before;
    }

    public static void setReputation(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId,
        int reputation
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        VillagerReputationEntry entry = progress.reputationForPlacement(placementId);
        int before = entry.getReputation();
        int after = Math.max(0, Math.min(VillagerReputationService.MAX_REPUTATION, reputation));
        entry.setReputation(after);
        enqueueMilestones(plugin, world, progress, placementId, entry, before, after);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
    }

    @Nullable
    public static String peekPendingRewardEntryNode(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.findPlayerProgress(playerUuid);
        if (progress == null) {
            return null;
        }
        return progress.peekPendingRewardNode(placementId);
    }

    public static void clearPendingRewardNode(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        progress.setPendingRewardNode(placementId, null);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
    }

    public static void setPendingMainHubBody(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId,
        @Nonnull String langKey
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        progress.setPendingMainHubBody(placementId, langKey);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
    }

    public static boolean claimPendingReward(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId,
        @Nonnull String rewardId
    ) {
        String rid = rewardId.trim();
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        VillagerReputationEntry e = progress.reputationForPlacement(placementId);
        e.migrateIfNeeded();
        if (e.getClaimedRewardIds().contains(rid)) {
            e.getPendingRewardIds().remove(rid);
            progress.setPendingRewardNode(placementId, null);
            registry.markPlayerDirty();
            WorldNpcPersistence.save(world, plugin, registry);
            return false;
        }
        java.util.List<String> pending = e.getPendingRewardIds();
        if (pending.isEmpty() || !rid.equals(pending.get(0))) {
            return false;
        }
        pending.remove(0);
        e.getClaimedRewardIds().add(rid);
        progress.setPendingRewardNode(placementId, null);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
        return true;
    }

    public static boolean tryGift(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId,
        int deltaReputation
    ) {
        long week = VillagerReputationService.currentGameEpochDay(store) / 7L;
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        if (progress.giftLastWeek(placementId) != week) {
            progress.setGiftLastWeek(placementId, week);
            progress.setGiftCountThisWeek(placementId, 0);
        }
        if (progress.giftCountThisWeek(placementId) >= 3) {
            return false;
        }
        VillagerReputationEntry entry = progress.reputationForPlacement(placementId);
        int before = entry.getReputation();
        int after = Math.max(0, Math.min(VillagerReputationService.MAX_REPUTATION, before + deltaReputation));
        entry.setReputation(after);
        progress.setGiftCountThisWeek(placementId, progress.giftCountThisWeek(placementId) + 1);
        enqueueMilestones(plugin, world, progress, placementId, entry, before, after);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
        return true;
    }

    /**
     * Adds reputation without dialogue gift weekly limits (e.g. Heartberry).
     *
     * @return reputation actually gained, or 0 if none
     */
    public static int addReputationDelta(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String placementId,
        int deltaReputation
    ) {
        if (deltaReputation <= 0) {
            return 0;
        }
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        VillagerReputationEntry entry = progress.reputationForPlacement(placementId);
        entry.migrateIfNeeded();
        int before = entry.getReputation();
        int after = Math.max(0, Math.min(VillagerReputationService.MAX_REPUTATION, before + deltaReputation));
        if (after == before) {
            return 0;
        }
        entry.setReputation(after);
        enqueueMilestones(plugin, world, progress, placementId, entry, before, after);
        registry.markPlayerDirty();
        WorldNpcPersistence.save(world, plugin, registry);
        return after - before;
    }

    private static void enqueueMilestones(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull WorldNpcPlayerProgress progress,
        @Nonnull String placementId,
        @Nonnull VillagerReputationEntry entry,
        int before,
        int after
    ) {
        WorldNpcPlacementRecord placement =
            AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin).findPlacement(placementId);
        if (placement == null) {
            return;
        }
        String roleId = placement.npcRoleIdOrEmpty();
        if (roleId.isEmpty()) {
            return;
        }
        for (ReputationRewardCatalog.ReputationRewardDefinition def : ReputationRewardCatalog.forRoleSorted(roleId)) {
            int t = def.minReputation();
            if (before < t && after >= t) {
                String rid = def.rewardId();
                if (!entry.claimedSet().contains(rid) && !entry.getPendingRewardIds().contains(rid)) {
                    entry.getPendingRewardIds().add(rid);
                }
                String node = def.dialogueNodeId();
                if (node != null && !node.isBlank()) {
                    progress.setPendingRewardNode(placementId, node.trim());
                }
            }
        }
    }
}
