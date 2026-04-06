package com.hexvane.aetherhaven.reputation;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerReputationService {
    public static final int MAX_REPUTATION = 100;
    public static final int DAILY_TALK_BONUS = 3;

    private VillagerReputationService() {}

    @Nullable
    public static TownRecord findTownForPlayer(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull TownManager tm
    ) {
        UUIDComponent uuidComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return null;
        }
        return tm.findTownForOwnerInWorld(uuidComp.getUuid());
    }

    /**
     * Daily-reset id aligned with the in-world day/night cycle: a new id starts at dawn (sunrise), not at calendar
     * midnight. Between midnight and the next sunrise, this still returns the previous calendar day’s id so the
     * “day” matches other dawn-based dailies (see {@link WorldTimeResource#SUNRISE_SECONDS} vs game clock).
     */
    public static long currentGameEpochDay(@Nonnull Store<EntityStore> store) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return 0L;
        }
        return dawnAlignedGameDayId(wtr);
    }

    private static long dawnAlignedGameDayId(@Nonnull WorldTimeResource wtr) {
        LocalDateTime dt = wtr.getGameDateTime();
        LocalDate d = dt.toLocalDate();
        int secOfDay = dt.get(ChronoField.SECOND_OF_DAY);
        if (secOfDay < WorldTimeResource.SUNRISE_SECONDS) {
            return d.minusDays(1).toEpochDay();
        }
        return d.toEpochDay();
    }

    @Nonnull
    public static VillagerReputationEntry getOrCreateEntry(
        @Nonnull TownRecord town, @Nonnull UUID playerUuid, @Nonnull UUID villagerEntityUuid
    ) {
        String pk = playerUuid.toString();
        String vk = villagerEntityUuid.toString();
        return town.getPlayerVillagerReputation().computeIfAbsent(pk, k -> new java.util.LinkedHashMap<>()).computeIfAbsent(
            vk,
            k -> {
                VillagerReputationEntry e = new VillagerReputationEntry();
                e.migrateIfNeeded();
                return e;
            }
        );
    }

    /**
     * First dialogue open after dawn on a new in-world day grants a small bonus (once per dawn-day per villager).
     *
     * @param gameEpochDay value from {@link #currentGameEpochDay} (dawn-aligned, not raw calendar midnight)
     * @return reputation gained from this daily bonus (0 if already received today, capped, or no change)
     */
    public static int applyDailyTalkBonus(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid,
        @Nonnull UUID villagerEntityUuid,
        long gameEpochDay
    ) {
        VillagerReputationEntry e = getOrCreateEntry(town, playerUuid, villagerEntityUuid);
        Long last = e.getLastTalkGameEpochDay();
        // Same-day check must be equality, not >=: if lastTalkGameEpochDay was ever ahead of the
        // current epoch (save migration, time quirks), >= blocks all future daily bonuses until time catches up.
        if (last != null && last == gameEpochDay) {
            return 0;
        }
        int oldRep = e.getReputation();
        e.setLastTalkGameEpochDay(gameEpochDay);
        boolean updated = addReputationInternal(town, world, playerUuid, villagerEntityUuid, e, DAILY_TALK_BONUS, tm);
        if (!updated) {
            tm.updateTown(town);
            return 0;
        }
        return e.getReputation() - oldRep;
    }

    /**
     * Adds reputation after quest completion when beneficiary NPC role matches.
     *
     * @return true if town was updated
     */
    public static boolean addQuestReputation(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid,
        @Nonnull UUID beneficiaryNpcEntityUuid,
        @Nonnull String beneficiaryNpcRoleName,
        @Nonnull String questId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        QuestCatalog.QuestReputationGrant qr = plugin.getQuestCatalog().findQuestBeneficiaryReputation(questId);
        if (qr == null) {
            return false;
        }
        if (!qr.beneficiaryRoleId().equals(beneficiaryNpcRoleName.trim())) {
            return false;
        }
        VillagerReputationEntry e = getOrCreateEntry(town, playerUuid, beneficiaryNpcEntityUuid);
        return addReputationInternal(town, world, playerUuid, beneficiaryNpcEntityUuid, e, qr.amount(), tm);
    }

    public static boolean addReputationInternal(
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull UUID villagerEntityUuid,
        @Nonnull VillagerReputationEntry e,
        int delta,
        @Nonnull TownManager tm
    ) {
        if (delta == 0) {
            return false;
        }
        int old = e.getReputation();
        int next = Math.max(0, Math.min(MAX_REPUTATION, old + delta));
        if (next == old) {
            return false;
        }
        e.setReputation(next);
        String roleId = resolveRoleForVillager(town, world, villagerEntityUuid);
        if (roleId != null) {
            enqueueNewlyCrossedMilestones(e, old, next, roleId);
        }
        tm.updateTown(town);
        return true;
    }

    private static void enqueueNewlyCrossedMilestones(
        @Nonnull VillagerReputationEntry e, int oldRep, int newRep, @Nonnull String npcRoleId
    ) {
        List<ReputationRewardCatalog.ReputationRewardDefinition> defs = ReputationRewardCatalog.forRoleSorted(npcRoleId);
        java.util.Set<String> claimed = e.claimedSet();
        for (ReputationRewardCatalog.ReputationRewardDefinition def : defs) {
            int t = def.minReputation();
            if (oldRep < t && newRep >= t) {
                String rid = def.rewardId();
                if (!claimed.contains(rid) && !e.getPendingRewardIds().contains(rid)) {
                    e.getPendingRewardIds().add(rid);
                }
            }
        }
    }

    @Nullable
    private static String resolveRoleForVillager(@Nonnull TownRecord town, @Nonnull World world, @Nonnull UUID villagerEntityUuid) {
        if (town.getElderEntityUuid() != null && town.getElderEntityUuid().equals(villagerEntityUuid)) {
            return com.hexvane.aetherhaven.AetherhavenConstants.ELDER_NPC_ROLE_ID;
        }
        if (town.getInnkeeperEntityUuid() != null && town.getInnkeeperEntityUuid().equals(villagerEntityUuid)) {
            return com.hexvane.aetherhaven.AetherhavenConstants.INNKEEPER_NPC_ROLE_ID;
        }
        var es = world.getEntityStore();
        if (es == null) {
            return null;
        }
        Store<EntityStore> store = es.getStore();
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(villagerEntityUuid);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        return npc != null && npc.getRoleName() != null ? npc.getRoleName() : null;
    }

    /** @return dialogue entry node id to override root, or null to keep default */
    @Nullable
    public static String peekPendingRewardEntryNode(
        @Nonnull TownRecord town, @Nonnull UUID playerUuid, @Nonnull UUID villagerEntityUuid
    ) {
        VillagerReputationEntry e = getOrCreateEntry(town, playerUuid, villagerEntityUuid);
        List<String> pending = e.getPendingRewardIds();
        if (pending.isEmpty()) {
            return null;
        }
        String first = pending.get(0);
        ReputationRewardCatalog.ReputationRewardDefinition def = ReputationRewardCatalog.byId(first);
        return def != null ? def.dialogueNodeId() : null;
    }

    /**
     * Confirms the pending reward: marks claimed, removes from queue. Call after items are granted.
     *
     * @return true if town was updated
     */
    /**
     * @return true if the reward was newly claimed from the pending queue (caller should grant items). False if
     *     already claimed, queue mismatch, or empty.
     */
    public static boolean claimPendingReward(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid,
        @Nonnull UUID villagerEntityUuid,
        @Nonnull String rewardId
    ) {
        String rid = rewardId.trim();
        VillagerReputationEntry e = getOrCreateEntry(town, playerUuid, villagerEntityUuid);
        if (e.getClaimedRewardIds().contains(rid)) {
            e.getPendingRewardIds().remove(rid);
            tm.updateTown(town);
            return false;
        }
        List<String> pending = e.getPendingRewardIds();
        if (pending.isEmpty() || !rid.equals(pending.get(0))) {
            return false;
        }
        pending.remove(0);
        e.getClaimedRewardIds().add(rid);
        tm.updateTown(town);
        return true;
    }
}
