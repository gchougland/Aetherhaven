package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Optional INFO logging for raid march diagnostics (see {@code RaidMarchDebugLog} in config.json). */
public final class RaidQuestMarchDebugLog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long STATUS_INTERVAL_MS = 15_000L;
    private static final ConcurrentHashMap<String, Long> LAST_LOG_MS = new ConcurrentHashMap<>();

    private RaidQuestMarchDebugLog() {}

    public static boolean enabled(@Nullable AetherhavenPlugin plugin) {
        return plugin != null && plugin.getConfig().get().isRaidMarchDebugLog();
    }

    public static void logSpawn(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull String questInstanceId,
        @Nonnull String rosterRoleId,
        @Nonnull String spawnRoleId,
        @Nonnull UUID mobUuid,
        @Nonnull Vector3d spawnPos,
        @Nonnull Vector3d charterPos,
        @Nonnull Vector3d leashPos,
        @Nonnull NPCEntity npc
    ) {
        if (!enabled(plugin)) {
            return;
        }
        LOGGER.atInfo().log(
            "[Aetherhaven raid march] spawn quest=%s rosterRole=%s spawnRole=%s uuid=%s pos=%s charter=%s leash=%s %s",
            questInstanceId,
            rosterRoleId,
            spawnRoleId,
            mobUuid,
            fmt(spawnPos),
            fmt(charterPos),
            fmt(leashPos),
            describeNpcMarch(npc)
        );
    }

    public static void logBootstrap(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull UUID mobUuid,
        @Nonnull Vector3d mobPos,
        @Nonnull Vector3d charterPos,
        @Nonnull Vector3d leashPos,
        @Nonnull NPCEntity npc
    ) {
        if (!enabled(plugin)) {
            return;
        }
        LOGGER.atInfo().log(
            "[Aetherhaven raid march] bootstrap uuid=%s pos=%s charter=%s leash=%s distCharter=%.1f %s",
            mobUuid,
            fmt(mobPos),
            fmt(charterPos),
            fmt(leashPos),
            horizDist(mobPos, charterPos),
            describeNpcMarch(npc)
        );
    }

    public static void logMarchStatus(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull UUID mobUuid,
        @Nonnull Vector3d mobPos,
        @Nonnull Vector3d leashPos,
        @Nonnull Vector3d charterPos,
        @Nonnull RaidQuestMobBinding binding,
        @Nonnull NPCEntity npc,
        @Nonnull String stateName,
        boolean inCombat
    ) {
        if (!enabled(plugin) || !shouldLog("status:" + mobUuid)) {
            return;
        }
        LOGGER.atInfo().log(
            "[Aetherhaven raid march] tick uuid=%s role=%s state=%s pos=%s leash=%s distLeash=%.1f distCharter=%.1f "
                + "initialized=%s inCombat=%s stallTicks=%d %s",
            mobUuid,
            roleName(npc),
            stateName,
            fmt(mobPos),
            fmt(leashPos),
            horizDist(mobPos, leashPos),
            horizDist(mobPos, charterPos),
            binding.isMarchInitialized(),
            inCombat,
            binding.getAutonomyStallTicks(),
            describeNpcMarch(npc)
        );
    }

    public static void logCombatPause(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull UUID mobUuid,
        @Nonnull String stateName
    ) {
        if (!enabled(plugin) || !shouldLog("combat:" + mobUuid)) {
            return;
        }
        LOGGER.atInfo().log(
            "[Aetherhaven raid march] paused for combat uuid=%s state=%s",
            mobUuid,
            stateName
        );
    }

    public static void logAdvance(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull UUID mobUuid,
        @Nonnull Vector3d leashPos,
        @Nonnull Vector3d charterPos
    ) {
        if (!enabled(plugin)) {
            return;
        }
        LOGGER.atInfo().log(
            "[Aetherhaven raid march] advance uuid=%s leash=%s distCharter=%.1f",
            mobUuid,
            fmt(leashPos),
            horizDist(leashPos, charterPos)
        );
    }

    public static void logStuckTeleport(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull UUID mobUuid,
        @Nonnull Vector3d fromPos,
        @Nonnull Vector3d toPos,
        @Nonnull Vector3d leashPos,
        int stallTicks
    ) {
        if (!enabled(plugin)) {
            return;
        }
        LOGGER.atInfo().log(
            "[Aetherhaven raid march] stuck teleport uuid=%s from=%s to=%s leash=%s stallTicks=%d",
            mobUuid,
            fmt(fromPos),
            fmt(toPos),
            fmt(leashPos),
            stallTicks
        );
    }

    public static void logReconcileDrop(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull String questInstanceId,
        @Nonnull UUID mobUuid,
        @Nonnull EntityPresence presence,
        int killRequired,
        int killProgress
    ) {
        if (!enabled(plugin)) {
            return;
        }
        LOGGER.atInfo().log(
            "[Aetherhaven raid march] reconcile drop uuid=%s quest=%s presence=%s kill=%d/%d",
            mobUuid,
            questInstanceId,
            presence,
            killProgress,
            killRequired
        );
    }

    public static void logReconcileSkipUnloaded(
        @Nullable AetherhavenPlugin plugin,
        @Nonnull String questInstanceId,
        @Nonnull UUID mobUuid
    ) {
        if (!enabled(plugin) || !shouldLog("unloaded:" + questInstanceId)) {
            return;
        }
        LOGGER.atInfo().log(
            "[Aetherhaven raid march] reconcile skip unloaded uuid=%s quest=%s (chunk not loaded)",
            mobUuid,
            questInstanceId
        );
    }

    @Nonnull
    private static String describeNpcMarch(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return "marchState=none hasRaidMarchState=false";
        }
        String marchState = RaidQuestMarchUtil.resolveMarchState(npc);
        int raidMarchIdx =
            npc.getRole().getStateSupport().getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_RAID_MARCH);
        return String.format(
            Locale.ROOT,
            "marchState=%s hasRaidMarchState=%s activeState=%s",
            marchState != null ? marchState : "none",
            raidMarchIdx >= 0,
            npc.getRole().getStateSupport().getStateName()
        );
    }

    @Nonnull
    private static String roleName(@Nonnull NPCEntity npc) {
        return npc.getRoleName() != null ? npc.getRoleName() : "?";
    }

    @Nonnull
    private static String fmt(@Nonnull Vector3d v) {
        return String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)", v.x, v.y, v.z);
    }

    private static double horizDist(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean shouldLog(@Nonnull String key) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOG_MS.get(key);
        if (last != null && now - last < STATUS_INTERVAL_MS) {
            return false;
        }
        LAST_LOG_MS.put(key, now);
        return true;
    }
}
