package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.patrol.GuardCombatClock;
import com.hexvane.aetherhaven.patrol.GuardNpcAttackerMemory;
import com.hexvane.aetherhaven.patrol.GuardPlayerProvokedTargets;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector2fc;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class RtsHostileQuery {
    private static final String AGGRESSIVE_GROUP = "Aggressive";
    private static final String TOWNSFOLK_GROUP = "Aetherhaven_Townsfolk";
    private static final String RAID_GROUP = "Aetherhaven_Raid";
    /**
     * Vanilla NPC groups for factions that attack players but are omitted from {@code LivingWorld/Aggressive.json}
     * (Outlander, Scarak). Guards check membership only in Java; we do not patch vanilla Aggressive assets.
     */
    private static final String[] EXTRA_AMBIENT_HOSTILE_GROUPS = {
        "Outlander",
        "Scarak",
    };

    /**
     * Vanilla role ids for player-hostile NPCs that are not in the Aggressive group closure. Matched against
     * {@link NPCEntity#getRoleName()}. Regenerate via {@code tools/audit_ambient_hostile_groups.py}.
     */
    private static final String[] EXTRA_AMBIENT_HOSTILE_ROLE_PATTERNS = {
        "Golem_*",
        "Spirit_*",
        "Ghoul",
        "Wraith",
        "Wraith_*",
        "Werewolf",
        "Shadow_Knight",
        "Hedera",
        "Snapdragon",
        "Molerat",
        "Hound_Bleached",
        "Larva_Void",
        "Larva_Silk",
        "Slug_Magma",
        "Chicken_Undead",
        "Cow_Undead",
        "Pig_Undead",
        "Dungeon_Scarak_*",
    };

    /** Radius around a ground click to pick a hostile under the RTS cursor. */
    private static final double FOCUS_PICK_RADIUS = 6.0;
    /** Max normalized screen distance (0..1) to match cursor to a hostile icon. */
    private static final float HOSTILE_SCREEN_PICK_RADIUS = 0.035f;
    private static final float HOSTILE_SCREEN_PICK_RADIUS_SQ =
        HOSTILE_SCREEN_PICK_RADIUS * HOSTILE_SCREEN_PICK_RADIUS;

    private RtsHostileQuery() {}

    /**
     * Resolves the focus target for a command-sword click: direct entity hit first, then screen-space
     * pick (tracks camera pan), then nearest attackable NPC near the ground pick.
     */
    @Nullable
    public static Ref<EntityStore> resolveFocusTarget(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull RtsCommandPlayerComponent session,
        @Nullable Vector3i targetBlock,
        @Nullable Vector2fc screen,
        @Nullable Ref<EntityStore> directTarget
    ) {
        if (directTarget != null && directTarget.isValid() && isGuardAttackableTarget(directTarget, store)) {
            return directTarget;
        }
        if (screen != null) {
            Ref<EntityStore> atScreen = pickAttackableAtScreen(store, session, screen.x(), screen.y());
            if (atScreen != null) {
                return atScreen;
            }
        }
        RtsScreenPickUtil.GroundPick pick = RtsScreenPickUtil.resolveCommandGroundPick(
            playerRef,
            store,
            session,
            targetBlock,
            screen
        );
        if (pick == null) {
            return null;
        }
        Ref<EntityStore> atPick = nearestAttackableTarget(store, pick.x(), pick.y(), pick.z(), FOCUS_PICK_RADIUS);
        if (atPick != null) {
            return atPick;
        }
        return nearestAttackableTarget(store, pick.x(), pick.y() + 2.0, pick.z(), FOCUS_PICK_RADIUS + 2.0);
    }

    @Nullable
    public static Ref<EntityStore> pickAttackableAtScreen(
        @Nonnull Store<EntityStore> store,
        @Nonnull RtsCommandPlayerComponent session,
        float rawScreenX,
        float rawScreenY
    ) {
        float cursorNx = RtsScreenPickUtil.cameraRawToNormalizedX(rawScreenX);
        float cursorNy = RtsScreenPickUtil.cameraRawToNormalizedY(rawScreenY);
        double cx = session.getFocusX();
        double cz = session.getFocusZ();
        double viewRadius = Math.max(64.0, session.getSavedViewRadiusBlocks() + RtsScreenPickUtil.viewHeightAboveGround(session) + 16.0);
        List<Ref<EntityStore>> attackable = new ArrayList<>();
        collectAttackableInBox(store, cx - viewRadius, cx + viewRadius, cz - viewRadius, cz + viewRadius, attackable);

        Ref<EntityStore> best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Ref<EntityStore> ref : attackable) {
            if (!ref.isValid()) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d pos = tc.getPosition();
            float sx = RtsScreenPickUtil.worldToNormalizedScreenX(pos.x, session);
            float sy = RtsScreenPickUtil.worldToNormalizedScreenY(pos.z, session);
            double dx = sx - cursorNx;
            double dy = sy - cursorNy;
            double distSq = dx * dx + dy * dy;
            if (distSq <= HOSTILE_SCREEN_PICK_RADIUS_SQ && distSq < bestDistSq) {
                bestDistSq = distSq;
                best = ref;
            }
        }
        return best;
    }

    /** True when the NPC role is in Hytale's Aggressive group (hostile creatures for auto-engage). */
    public static boolean isAggressiveNpc(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return false;
        }
        int roleIndex = npc.getRole().getRoleIndex();
        return roleIndex >= 0 && npcInAggressiveGroup(roleIndex);
    }

    /**
     * True for any NPC guards may focus-fire except town villagers, tourists, guards, and other town staff.
     * Quest-board raid mobs ({@code Aetherhaven_Raid_*}) are always attackable even though their role id
     * matches the {@code Aetherhaven_*} townsfolk group wildcard.
     */
    public static boolean isGuardAttackableTarget(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!ref.isValid()) {
            return false;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return false;
        }
        if (store.getComponent(ref, TownVillagerBinding.getComponentType()) != null) {
            return false;
        }
        int roleIndex = npc.getRole().getRoleIndex();
        if (roleIndex >= 0 && npcInRaidGroup(roleIndex)) {
            return true;
        }
        return roleIndex < 0 || !npcInTownsfolkGroup(roleIndex);
    }

    /**
     * True when guards should auto engage {@code targetRef}: aggressive or raid creatures, anything fighting the
     * guard or followed player, or anything the followed player recently hit (never random livestock or prey).
     */
    public static boolean isGuardThreatTarget(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull Store<EntityStore> store
    ) {
        return isGuardThreatTarget(guardRef, targetRef, store, null);
    }

    public static boolean isGuardThreatTarget(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> protectedPlayerRef
    ) {
        if (!isValidNpcThreatCandidate(targetRef, guardRef, store)) {
            return false;
        }
        if (isReactiveGuardThreat(guardRef, targetRef, store, protectedPlayerRef)) {
            return true;
        }
        return isAmbientHostileCreature(targetRef, store);
    }

    private static boolean isValidNpcThreatCandidate(
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!targetRef.isValid() || targetRef.equals(guardRef)) {
            return false;
        }
        if (store.getComponent(targetRef, Player.getComponentType()) != null) {
            return false;
        }
        if (store.getComponent(targetRef, TownVillagerBinding.getComponentType()) != null) {
            return false;
        }
        NPCEntity targetNpc = store.getComponent(targetRef, NPCEntity.getComponentType());
        return targetNpc != null && targetNpc.getRole() != null;
    }

    /** Hostile creatures guards should notice without provocation (not livestock, prey, or critters). */
    private static boolean isAmbientHostileCreature(@Nonnull Ref<EntityStore> targetRef, @Nonnull Store<EntityStore> store) {
        NPCEntity targetNpc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (targetNpc == null || targetNpc.getRole() == null) {
            return false;
        }
        int roleIndex = targetNpc.getRole().getRoleIndex();
        if (roleIndex >= 0 && npcInRaidGroup(roleIndex)) {
            return true;
        }
        if (isAggressiveNpc(targetRef, store)) {
            return true;
        }
        if (roleIndex >= 0) {
            for (String group : EXTRA_AMBIENT_HOSTILE_GROUPS) {
                if (npcInGroup(group, roleIndex)) {
                    return true;
                }
            }
        }
        String roleName = targetNpc.getRoleName();
        return roleName != null && !roleName.isEmpty() && matchesAmbientHostileRoleName(roleName);
    }

    static boolean matchesAmbientHostileRoleName(@Nonnull String roleName) {
        for (String pattern : EXTRA_AMBIENT_HOSTILE_ROLE_PATTERNS) {
            if (matchesRolePattern(roleName, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRolePattern(@Nonnull String roleName, @Nonnull String pattern) {
        if (pattern.endsWith("*") && !pattern.startsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return roleName.startsWith(prefix);
        }
        if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() >= 2) {
            return roleName.contains(pattern.substring(1, pattern.length() - 1));
        }
        return roleName.equals(pattern);
    }

    private static boolean isMarkedHostileAttacker(@Nonnull Ref<EntityStore> attackerRef, @Nonnull Store<EntityStore> store) {
        try {
            GuardNpcAttackerMemory memory = store.getResource(GuardNpcAttackerMemory.getResourceType());
            return memory != null && memory.isMarkedAttacker(attackerRef, GuardCombatClock.nowMs(store));
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static boolean isReactiveGuardThreat(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> protectedPlayerRef
    ) {
        if (isMarkedHostileAttacker(targetRef, store)) {
            return true;
        }
        if (isRecentAttackerOnGuard(guardRef, targetRef, store)) {
            return true;
        }
        if (protectedPlayerRef != null && isRecentAttackerOnPlayer(protectedPlayerRef, targetRef, store)) {
            return true;
        }
        if (protectedPlayerRef != null
            && isRecentVictimOfPlayer(protectedPlayerRef, targetRef, store)
            && isGuardAttackableTarget(targetRef, store)) {
            return true;
        }
        // Neutral livestock sets LockedTarget to the player while fleeing; that is not combat.
        return protectedPlayerRef != null
            && isNpcLockedOn(protectedPlayerRef, targetRef, store)
            && isAmbientHostileCreature(targetRef, store);
    }

    /** True when {@code npcRef} recently dealt combat damage to {@code playerRef}. */
    public static boolean isRecentAttackerOnPlayer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!npcRef.isValid() || store.getComponent(playerRef, Player.getComponentType()) == null) {
            return false;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return false;
        }
        Ref<EntityStore> victim = npc.getDamageData().getMostDamagedVictim();
        if (victim == null) {
            return false;
        }
        return playerRef.equals(victim);
    }

    /** True when {@code playerRef} recently dealt combat damage to {@code npcRef}. */
    public static boolean isRecentVictimOfPlayer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!npcRef.isValid() || store.getComponent(playerRef, Player.getComponentType()) == null) {
            return false;
        }
        UUIDComponent playerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (playerUuid != null) {
            try {
                GuardPlayerProvokedTargets provoked = store.getResource(GuardPlayerProvokedTargets.getResourceType());
                if (provoked != null
                    && provoked.isProvokedByPlayer(
                        playerUuid.getUuid(),
                        npcRef,
                        GuardCombatClock.nowMs(store)
                    )) {
                    return true;
                }
            } catch (IllegalStateException ignored) {
                // Resource not registered in this store context.
            }
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return false;
        }
        Ref<EntityStore> attacker = npc.getDamageData().getMostDamagingAttacker();
        if (attacker == null) {
            attacker = npc.getDamageData().getAnyAttacker();
        }
        return playerRef.equals(attacker);
    }

    /** True when {@code threatRef} recently damaged {@code guardRef} (combat damage this tick window). */
    public static boolean isRecentAttackerOnGuard(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> threatRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!threatRef.isValid()) {
            return false;
        }
        if (isMarkedHostileAttacker(threatRef, store)) {
            return true;
        }
        NPCEntity guardNpc = store.getComponent(guardRef, NPCEntity.getComponentType());
        if (guardNpc == null) {
            return false;
        }
        Ref<EntityStore> attacker = guardNpc.getDamageData().getMostDamagingAttacker();
        if (attacker == null) {
            attacker = guardNpc.getDamageData().getAnyAttacker();
        }
        return threatRef.equals(attacker);
    }

    /** True when {@code npcRef}'s locked combat target is {@code targetRef}. */
    public static boolean isNpcLockedOn(
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return false;
        }
        Ref<EntityStore> locked = NpcSupportUtil.markedEntitySupport(npcRef, store).getMarkedEntityRef(RtsGuardCombatSupport.LOCKED_TARGET_SLOT);
        return targetRef.equals(locked);
    }

    @Nullable
    public static Ref<EntityStore> resolveGuardDamageAttacker(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store
    ) {
        NPCEntity guardNpc = store.getComponent(guardRef, NPCEntity.getComponentType());
        if (guardNpc == null) {
            return null;
        }
        Ref<EntityStore> attacker = guardNpc.getDamageData().getMostDamagingAttacker();
        if (attacker == null) {
            attacker = guardNpc.getDamageData().getAnyAttacker();
        }
        if (attacker == null || !attacker.isValid()) {
            return null;
        }
        if (store.getComponent(attacker, Player.getComponentType()) != null) {
            return null;
        }
        return attacker;
    }

    /**
     * Priority threat pick while a guard follows a player: guard's attacker, mob targeting the player,
     * then nearest threat near the player, then near the guard.
     */
    @Nullable
    public static Ref<EntityStore> resolveFollowPlayerThreat(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        double radius
    ) {
        Ref<EntityStore> guardAttacker = resolveGuardDamageAttacker(guardRef, store);
        if (guardAttacker != null && isGuardThreatTarget(guardRef, guardAttacker, store, playerRef)) {
            return guardAttacker;
        }
        Ref<EntityStore> playerFight = nearestNpcRecentVictimOfPlayer(guardRef, playerRef, store, radius);
        if (playerFight != null) {
            return playerFight;
        }
        Ref<EntityStore> targetingPlayer = nearestNpcLockedOnPlayer(guardRef, playerRef, store, radius);
        if (targetingPlayer != null) {
            return targetingPlayer;
        }
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTc != null) {
            Vector3d p = playerTc.getPosition();
            Ref<EntityStore> nearPlayer = nearestGuardThreat(
                store,
                guardRef,
                p.x,
                p.y,
                p.z,
                radius,
                guardRef,
                playerRef
            );
            if (nearPlayer != null) {
                return nearPlayer;
            }
        }
        TransformComponent guardTc = store.getComponent(guardRef, TransformComponent.getComponentType());
        if (guardTc == null) {
            return null;
        }
        Vector3d g = guardTc.getPosition();
        return nearestGuardThreat(store, guardRef, g.x, g.y, g.z, radius, guardRef, playerRef);
    }

    @Nullable
    public static Ref<EntityStore> resolveAutonomousGuardThreat(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store,
        double radius
    ) {
        Ref<EntityStore> guardAttacker = resolveGuardDamageAttacker(guardRef, store);
        if (guardAttacker != null && isGuardThreatTarget(guardRef, guardAttacker, store)) {
            return guardAttacker;
        }
        TransformComponent guardTc = store.getComponent(guardRef, TransformComponent.getComponentType());
        if (guardTc == null) {
            return null;
        }
        Vector3d g = guardTc.getPosition();
        return nearestGuardThreat(store, guardRef, g.x, g.y, g.z, radius, guardRef, null);
    }

    /**
     * Engage without line of sight when the threat is actively fighting the guard or followed player.
     */
    public static boolean canEngageWithoutLineOfSight(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> threatRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> protectedPlayerRef
    ) {
        return isRecentAttackerOnGuard(guardRef, threatRef, store)
            || (protectedPlayerRef != null && isRecentAttackerOnPlayer(protectedPlayerRef, threatRef, store))
            || isMarkedHostileAttacker(threatRef, store)
            || (protectedPlayerRef != null
                && isRecentVictimOfPlayer(protectedPlayerRef, threatRef, store)
                && isGuardAttackableTarget(threatRef, store))
            || (protectedPlayerRef != null
                && isNpcLockedOn(protectedPlayerRef, threatRef, store)
                && isAmbientHostileCreature(threatRef, store));
    }

    @Nullable
    public static Ref<EntityStore> nearestGuardThreat(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> guardRef,
        double centerX,
        double centerY,
        double centerZ,
        double radius,
        @Nullable Ref<EntityStore> lineOfSightObserverRef,
        @Nullable Ref<EntityStore> protectedPlayerRef
    ) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            store.getResource(EntityModule.get().getEntitySpatialResourceType());
        List<Ref<EntityStore>> hits = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(new Vector3d(centerX, centerY, centerZ), radius, hits);
        Ref<EntityStore> best = null;
        double bestSq = Double.MAX_VALUE;
        for (Ref<EntityStore> ref : hits) {
            if (!ref.isValid() || !isGuardThreatTarget(guardRef, ref, store, protectedPlayerRef)) {
                continue;
            }
            if (lineOfSightObserverRef != null
                && !canEngageWithoutLineOfSight(guardRef, ref, store, protectedPlayerRef)
                && !hasLineOfSight(lineOfSightObserverRef, ref, store)) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            double dx = p.x - centerX;
            double dy = p.y - centerY;
            double dz = p.z - centerZ;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= radius * radius && sq < bestSq) {
                bestSq = sq;
                best = ref;
            }
        }
        return best;
    }

    /** True when {@code observerRef} has unobstructed line of sight to {@code targetRef}. */
    public static boolean hasLineOfSight(
        @Nonnull Ref<EntityStore> observerRef,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!observerRef.isValid() || !targetRef.isValid()) {
            return false;
        }
        NPCEntity observer = store.getComponent(observerRef, NPCEntity.getComponentType());
        if (observer == null || observer.getRole() == null) {
            return false;
        }
        return NpcSupportUtil.positionCache(observerRef, store).hasLineOfSight(observerRef, targetRef, store);
    }

    @Nullable
    public static Ref<EntityStore> nearestHostile(
        @Nonnull Store<EntityStore> store,
        double centerX,
        double centerY,
        double centerZ,
        double radius
    ) {
        return nearestHostile(store, centerX, centerY, centerZ, radius, null);
    }

    @Nullable
    public static Ref<EntityStore> nearestHostile(
        @Nonnull Store<EntityStore> store,
        double centerX,
        double centerY,
        double centerZ,
        double radius,
        @Nullable Ref<EntityStore> observerRef
    ) {
        if (observerRef == null || !observerRef.isValid()) {
            return nearestAggressiveOnly(store, centerX, centerY, centerZ, radius, null);
        }
        return nearestGuardThreat(store, observerRef, centerX, centerY, centerZ, radius, observerRef, null);
    }

    @Nullable
    public static Ref<EntityStore> nearestAttackableTarget(
        @Nonnull Store<EntityStore> store,
        double centerX,
        double centerY,
        double centerZ,
        double radius
    ) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            store.getResource(EntityModule.get().getEntitySpatialResourceType());
        List<Ref<EntityStore>> hits = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(new Vector3d(centerX, centerY, centerZ), radius, hits);
        Ref<EntityStore> best = null;
        double bestSq = Double.MAX_VALUE;
        for (Ref<EntityStore> ref : hits) {
            if (!ref.isValid() || !isGuardAttackableTarget(ref, store)) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            double dx = p.x - centerX;
            double dy = p.y - centerY;
            double dz = p.z - centerZ;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= radius * radius && sq < bestSq) {
                bestSq = sq;
                best = ref;
            }
        }
        return best;
    }

    @Nonnull
    public static List<Ref<EntityStore>> collectNpcRefsNear(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> centerRef,
        double radius
    ) {
        TransformComponent centerTc = store.getComponent(centerRef, TransformComponent.getComponentType());
        if (centerTc == null) {
            return List.of();
        }
        Vector3d center = centerTc.getPosition();
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            store.getResource(EntityModule.get().getEntitySpatialResourceType());
        List<Ref<EntityStore>> hits = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(center, radius, hits);
        List<Ref<EntityStore>> npcs = new ArrayList<>(hits.size());
        for (Ref<EntityStore> ref : hits) {
            if (ref.isValid() && !ref.equals(centerRef) && store.getComponent(ref, NPCEntity.getComponentType()) != null) {
                npcs.add(ref);
            }
        }
        return npcs;
    }

    public static double horizontalDistance(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Nullable
    public static UUID entityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    public static void collectHostilesInBox(
        @Nonnull Store<EntityStore> store,
        double minX,
        double maxX,
        double minZ,
        double maxZ,
        @Nonnull List<Ref<EntityStore>> out
    ) {
        collectAttackableInBox(store, minX, maxX, minZ, maxZ, out);
    }

    public static void collectAttackableInBox(
        @Nonnull Store<EntityStore> store,
        double minX,
        double maxX,
        double minZ,
        double maxZ,
        @Nonnull List<Ref<EntityStore>> out
    ) {
        double cx = (minX + maxX) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        double radius = Math.max(Math.abs(maxX - minX), Math.abs(maxZ - minZ)) * 0.5 + 2.0;
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            store.getResource(EntityModule.get().getEntitySpatialResourceType());
        List<Ref<EntityStore>> hits = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(new Vector3d(cx, 0, cz), radius + ParticleUtil.DEFAULT_PARTICLE_DISTANCE, hits);
        hits.sort(Comparator.comparingInt(Ref::getIndex));
        for (Ref<EntityStore> ref : hits) {
            if (!ref.isValid() || !isGuardAttackableTarget(ref, store)) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            if (p.x >= minX && p.x <= maxX && p.z >= minZ && p.z <= maxZ) {
                out.add(ref);
            }
        }
    }

    @Nullable
    private static Ref<EntityStore> nearestNpcRecentVictimOfPlayer(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        double radius
    ) {
        List<Ref<EntityStore>> nearPlayer = collectNpcRefsNear(store, playerRef, radius);
        Ref<EntityStore> best = null;
        double bestSq = Double.MAX_VALUE;
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTc == null) {
            return null;
        }
        Vector3d playerPos = playerTc.getPosition();
        for (Ref<EntityStore> ref : nearPlayer) {
            if (!isRecentVictimOfPlayer(playerRef, ref, store)) {
                continue;
            }
            if (!isGuardThreatTarget(guardRef, ref, store, playerRef)) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            double dx = p.x - playerPos.x;
            double dy = p.y - playerPos.y;
            double dz = p.z - playerPos.z;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = ref;
            }
        }
        return best;
    }

    @Nullable
    private static Ref<EntityStore> nearestNpcLockedOnPlayer(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        double radius
    ) {
        List<Ref<EntityStore>> nearPlayer = collectNpcRefsNear(store, playerRef, radius);
        Ref<EntityStore> best = null;
        double bestSq = Double.MAX_VALUE;
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTc == null) {
            return null;
        }
        Vector3d playerPos = playerTc.getPosition();
        for (Ref<EntityStore> ref : nearPlayer) {
            if (!isNpcLockedOn(playerRef, ref, store) || !isAmbientHostileCreature(ref, store)) {
                continue;
            }
            if (!isGuardThreatTarget(guardRef, ref, store, playerRef)) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            double dx = p.x - playerPos.x;
            double dy = p.y - playerPos.y;
            double dz = p.z - playerPos.z;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = ref;
            }
        }
        return best;
    }

    @Nullable
    private static Ref<EntityStore> nearestAggressiveOnly(
        @Nonnull Store<EntityStore> store,
        double centerX,
        double centerY,
        double centerZ,
        double radius,
        @Nullable Ref<EntityStore> observerRef
    ) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            store.getResource(EntityModule.get().getEntitySpatialResourceType());
        List<Ref<EntityStore>> hits = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(new Vector3d(centerX, centerY, centerZ), radius, hits);
        Ref<EntityStore> best = null;
        double bestSq = Double.MAX_VALUE;
        for (Ref<EntityStore> ref : hits) {
            if (!ref.isValid() || !isAggressiveNpc(ref, store)) {
                continue;
            }
            if (observerRef != null && !hasLineOfSight(observerRef, ref, store)) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            double dx = p.x - centerX;
            double dy = p.y - centerY;
            double dz = p.z - centerZ;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= radius * radius && sq < bestSq) {
                bestSq = sq;
                best = ref;
            }
        }
        return best;
    }

    private static boolean npcInAggressiveGroup(int roleIndex) {
        return npcInGroup(AGGRESSIVE_GROUP, roleIndex);
    }

    private static boolean npcInTownsfolkGroup(int roleIndex) {
        return npcInGroup(TOWNSFOLK_GROUP, roleIndex);
    }

    private static boolean npcInRaidGroup(int roleIndex) {
        return npcInGroup(RAID_GROUP, roleIndex);
    }

    private static boolean npcInGroup(@Nonnull String groupName, int roleIndex) {
        try {
            int g = NPCGroup.getAssetMap().getIndex(groupName);
            if (g == Integer.MIN_VALUE) {
                return false;
            }
            return WorldSupport.hasTagInGroup(g, roleIndex);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
