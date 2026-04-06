package com.hexvane.aetherhaven.quest;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.InnkeeperSpawnService;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestEffectEntry;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Runs {@code lifecycle} effect entries from quest JSON. */
public final class QuestLifecycleEffects {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String EFFECT_LOCK_INN_VISITOR_FOR_NPC = "lock_inn_visitor_for_npc";
    public static final String EFFECT_CLEAR_INN_VISITOR_LOCKS = "clear_inn_visitor_locks";
    public static final String EFFECT_SPAWN_INNKEEPER_IF_INN_READY = "spawn_innkeeper_if_inn_ready";

    private QuestLifecycleEffects() {}

    public static void runOnStart(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull QuestDefinition def,
        @Nullable java.util.UUID talkingNpcUuid
    ) {
        runList(world, plugin, town, tm, def, def.lifecycleOrEmpty().onStartOrEmpty(), talkingNpcUuid);
    }

    public static void runOnComplete(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull QuestDefinition def,
        @Nullable java.util.UUID talkingNpcUuid
    ) {
        runList(world, plugin, town, tm, def, def.lifecycleOrEmpty().onCompleteOrEmpty(), talkingNpcUuid);
    }

    public static void runOnAbandon(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull QuestDefinition def,
        @Nullable java.util.UUID talkingNpcUuid
    ) {
        runList(world, plugin, town, tm, def, def.lifecycleOrEmpty().onAbandonOrEmpty(), talkingNpcUuid);
    }

    private static void runList(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull QuestDefinition def,
        @Nonnull List<QuestEffectEntry> entries,
        @Nullable java.util.UUID talkingNpcUuid
    ) {
        for (QuestEffectEntry e : entries) {
            String name = e.effect();
            if (name == null || name.isBlank()) {
                continue;
            }
            JsonObject params = e.paramsOrEmpty();
            switch (name.trim()) {
                case EFFECT_LOCK_INN_VISITOR_FOR_NPC -> applyLockInnVisitor(town, params, talkingNpcUuid);
                case EFFECT_CLEAR_INN_VISITOR_LOCKS -> town.getInnLockedEntityUuids().clear();
                case EFFECT_SPAWN_INNKEEPER_IF_INN_READY -> InnkeeperSpawnService.trySpawnAfterInnQuestComplete(world, plugin, town);
                default -> LOGGER.atWarning().log("Unknown quest lifecycle effect: %s (quest %s)", name, def.idOrEmpty());
            }
        }
    }

    private static void applyLockInnVisitor(
        @Nonnull TownRecord town,
        @Nonnull JsonObject params,
        @Nullable java.util.UUID talkingNpcUuid
    ) {
        boolean lock = true;
        if (params.has("lockInnVisitor") && params.get("lockInnVisitor").isJsonPrimitive()) {
            lock = params.get("lockInnVisitor").getAsBoolean();
        }
        if (lock && talkingNpcUuid != null) {
            town.addInnLockedEntity(talkingNpcUuid);
        }
    }
}
