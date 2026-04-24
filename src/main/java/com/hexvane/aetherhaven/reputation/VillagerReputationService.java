package com.hexvane.aetherhaven.reputation;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
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
        return tm.findTownForPlayerInWorld(uuidComp.getUuid());
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

    /**
     * Innkeeper reputation is stored per villager entity UUID. Uses {@link TownRecord#getInnkeeperEntityUuid()} when
     * present; otherwise the first loaded NPC with {@link TownVillagerBinding} kind innkeeper for this town.
     */
    @Nullable
    public static UUID resolveInnkeeperEntityUuidForReputation(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        UUID registered = town.getInnkeeperEntityUuid();
        if (registered != null) {
            return registered;
        }
        UUID tid = town.getTownId();
        final UUID[] found = {null};
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (found[0] != null) {
                    return;
                }
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || !TownVillagerBinding.KIND_INNKEEPER.equals(b.getKind())) {
                        continue;
                    }
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null) {
                        continue;
                    }
                    found[0] = uc.getUuid();
                    return;
                }
            }
        );
        return found[0];
    }

    /** True if this player has claimed the given milestone reward id with any villager row stored on the town. */
    public static boolean hasPlayerClaimedRewardId(
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull String rewardId
    ) {
        String rid = rewardId.trim();
        if (rid.isEmpty()) {
            return false;
        }
        Map<String, VillagerReputationEntry> byVillager = town.getPlayerVillagerReputation().get(playerUuid.toString());
        if (byVillager == null || byVillager.isEmpty()) {
            return false;
        }
        for (VillagerReputationEntry e : byVillager.values()) {
            if (e != null && e.getClaimedRewardIds().contains(rid)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public static VillagerReputationEntry getOrCreateEntry(
        @Nonnull TownRecord town, @Nonnull UUID playerUuid, @Nonnull UUID villagerEntityUuid
    ) {
        String pk = playerUuid.toString();
        String vk = villagerEntityUuid.toString();
        VillagerReputationEntry e = town
            .getPlayerVillagerReputation()
            .computeIfAbsent(pk, k -> new java.util.LinkedHashMap<>())
            .computeIfAbsent(
                vk,
                k -> {
                    VillagerReputationEntry n = new VillagerReputationEntry();
                    n.migrateIfNeeded();
                    return n;
                }
            );
        e.migrateIfNeeded();
        return e;
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

    /**
     * Sets reputation and enqueues milestone rewards for every threshold crossed between {@code old} and {@code newRep}
     * (same rules as incremental gains), so jumping to max still queues lower tiers that were skipped.
     *
     * @return true if the stored reputation value changed
     */
    public static boolean setReputationCrossingMilestones(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid,
        @Nonnull UUID villagerEntityUuid,
        int newReputation
    ) {
        int clamped = Math.max(0, Math.min(MAX_REPUTATION, newReputation));
        VillagerReputationEntry e = getOrCreateEntry(town, playerUuid, villagerEntityUuid);
        int old = e.getReputation();
        if (old == clamped) {
            return false;
        }
        e.setReputation(clamped);
        String roleId = resolveRoleForVillager(town, world, villagerEntityUuid);
        if (roleId != null) {
            enqueueNewlyCrossedMilestones(e, old, clamped, roleId);
        }
        tm.updateTown(town);
        return true;
    }

    /**
     * Grants one reputation milestone immediately (items/recipe), marks it claimed, and strips it from the pending queue.
     * Does not require the reward to be at the front of the queue (unlike dialogue claim flow). Also raises stored
     * {@link VillagerReputationEntry#getReputation()} to at least this reward's {@code minReputation} so UI (e.g. feasts)
     * matches the unlocked tier (normal play reaches that rep before the reward is offered).
     *
     * @return null on success, or a short error reason
     */
    @Nullable
    public static String grantReputationRewardDirect(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid,
        @Nonnull UUID villagerEntityUuid,
        @Nonnull String rewardId,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        ReputationRewardCatalog.ReputationRewardDefinition def = ReputationRewardCatalog.byId(rewardId);
        if (def == null) {
            return "Unknown reward id.";
        }
        String roleId = resolveRoleForVillager(town, world, villagerEntityUuid);
        if (roleId == null) {
            return "Could not resolve villager role (is the NPC loaded?).";
        }
        if (!def.roleId().equals(roleId)) {
            return "This reward does not match this villager's role.";
        }
        VillagerReputationEntry e = getOrCreateEntry(town, playerUuid, villagerEntityUuid);
        String rid = def.rewardId().trim();
        if (e.getClaimedRewardIds().contains(rid)) {
            int floor = def.minReputation();
            if (e.getReputation() < floor) {
                e.setReputation(Math.min(MAX_REPUTATION, floor));
                tm.updateTown(town);
                return null;
            }
            return "That reward was already claimed.";
        }
        e.getPendingRewardIds().removeIf(rid::equals);
        e.getClaimedRewardIds().add(rid);
        int floor = def.minReputation();
        if (e.getReputation() < floor) {
            e.setReputation(Math.min(MAX_REPUTATION, floor));
        }
        tm.updateTown(town);

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return "Player component missing.";
        }
        String learnId = def.learnRecipeItemId();
        if (learnId != null && !learnId.isBlank()) {
            CraftingPlugin.learnRecipe(playerRef, learnId.trim(), store);
            return null;
        }
        String itemId = def.itemId() != null ? def.itemId().trim() : "";
        if (itemId.isBlank() || def.itemCount() <= 0) {
            return null;
        }
        int count = Math.max(1, Math.min(def.itemCount(), 9999));
        player.giveItem(new ItemStack(itemId, count), playerRef, store);
        return null;
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

    /**
     * Rebuilds the pending milestone list from current reputation, claimed set, and role. Fixes saves where rep crossed a
     * threshold but the queue was never enqueued (e.g. before role could be resolved) or was cleared incorrectly.
     */
    private static void reconcilePendingMilestonesForCurrentReputation(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull VillagerReputationEntry e,
        @Nonnull UUID villagerEntityUuid
    ) {
        String roleId = resolveRoleForVillager(town, world, villagerEntityUuid);
        if (roleId == null) {
            return;
        }
        int rep = e.getReputation();
        java.util.Set<String> claimed = e.claimedSet();
        java.util.ArrayList<String> rebuilt = new java.util.ArrayList<>();
        for (ReputationRewardCatalog.ReputationRewardDefinition def : ReputationRewardCatalog.forRoleSorted(roleId)) {
            if (rep < def.minReputation()) {
                continue;
            }
            String rid = def.rewardId();
            if (claimed.contains(rid) || rebuilt.contains(rid)) {
                continue;
            }
            rebuilt.add(rid);
        }
        if (rebuilt.equals(e.getPendingRewardIds())) {
            return;
        }
        e.getPendingRewardIds().clear();
        e.getPendingRewardIds().addAll(rebuilt);
        tm.updateTown(town);
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
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull UUID villagerEntityUuid
    ) {
        VillagerReputationEntry e = getOrCreateEntry(town, playerUuid, villagerEntityUuid);
        reconcilePendingMilestonesForCurrentReputation(world, town, tm, e, villagerEntityUuid);
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
    /**
     * Moves per-player reputation rows from {@code oldUuid} to {@code newUuid} (e.g. after reviving a missing NPC).
     * Merges into an existing {@code newUuid} entry if present.
     */
    public static void migrateVillagerEntityUuid(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID oldUuid,
        @Nonnull UUID newUuid
    ) {
        String oldS = oldUuid.toString();
        String newS = newUuid.toString();
        if (oldS.equals(newS)) {
            return;
        }
        boolean changed = false;
        for (Map<String, VillagerReputationEntry> inner : town.getPlayerVillagerReputation().values()) {
            if (inner == null || !inner.containsKey(oldS)) {
                continue;
            }
            VillagerReputationEntry from = inner.remove(oldS);
            if (from == null) {
                continue;
            }
            VillagerReputationEntry to = inner.get(newS);
            if (to == null) {
                inner.put(newS, from);
            } else {
                mergeReputationEntries(to, from);
            }
            changed = true;
        }
        if (changed) {
            tm.updateTown(town);
        }
    }

    private static void mergeReputationEntries(@Nonnull VillagerReputationEntry into, @Nonnull VillagerReputationEntry from) {
        into.setReputation(Math.max(into.getReputation(), from.getReputation()));
        Long a = into.getLastTalkGameEpochDay();
        Long b = from.getLastTalkGameEpochDay();
        if (b != null && (a == null || b > a)) {
            into.setLastTalkGameEpochDay(b);
        }
        for (String id : from.getClaimedRewardIds()) {
            if (id != null && !id.isBlank() && !into.getClaimedRewardIds().contains(id)) {
                into.getClaimedRewardIds().add(id);
            }
        }
        java.util.LinkedHashSet<String> pending = new java.util.LinkedHashSet<>(into.getPendingRewardIds());
        for (String id : from.getPendingRewardIds()) {
            if (id != null && !id.isBlank()) {
                pending.add(id);
            }
        }
        into.getPendingRewardIds().clear();
        into.getPendingRewardIds().addAll(pending);
    }

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
