package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.hud.AetherhavenHudRefreshSystem;
import com.hexvane.aetherhaven.quest.data.QuestAutoStart;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.workplace.WorkplaceGuide;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Gives a town the quests that begin by themselves ({@code autoStart} in quest JSON), the first time their condition
 * holds. Starting one means what a dialogue's {@code start_quest} means: the quest joins the town's active quests, its
 * steps are read against the town as it stands, its start effects run and the members in the world see the banner.
 *
 * <p>It is asked at the moments that can make a condition true (a building is finished, a quest is turned in, a
 * member arrives) and every few game minutes while a member is in the world. So a town loaded from an older save gets
 * its quest when its first member arrives, with nobody doing anything.
 */
public final class QuestAutoStartService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** The town has a finished building whose villager lives there and can supervise a workplace type. */
    public static final String WHEN_WORKPLACE_SUPERVISOR_READY = "workplace_supervisor_ready";

    /** The slow check runs when the game clock crosses a multiple of this many minutes. */
    private static final long CHECK_EVERY_GAME_MINUTES = 5;

    private static final String BANNER_SOUND_EVENT_ID = "SFX_Discovery_Z1_Short";

    private QuestAutoStartService() {}

    public static boolean isKnownCondition(@Nonnull String when) {
        return WHEN_WORKPLACE_SUPERVISOR_READY.equals(when) || "farmer_ready".equals(when);
    }

    static boolean holds(@Nonnull String when, @Nonnull TownRecord town, @Nonnull ConstructionCatalog catalog) {
        return switch (when) {
            case WHEN_WORKPLACE_SUPERVISOR_READY -> WorkplaceGuide.supervisorReady(town, catalog);
            case "farmer_ready" -> town.hasCompletePlotWithConstruction(catalog,"plot_farm")
                && town.getResidentNpcRecords().stream().anyMatch(r -> "farmer".equals(r.getKind()));
            default -> false;
        };
    }

    public enum Decision {
        /** Not this quest, not this town, or not yet. */
        NOTHING,
        /** Everything that can be read is already done, and the rest stands in chunks nobody has loaded. Ask later. */
        WAIT,
        START,
        /** The town already does all the quest would teach. It is marked done with no banner and no reward. */
        COMPLETE_SILENTLY
    }

    /**
     * Whether the quest could be handed to this town now: its condition holds and the town has neither it nor its
     * completion. Used to start it, and by a villager who offers it again after it was abandoned.
     */
    public static boolean offerable(
        @Nonnull QuestDefinition def,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog
    ) {
        QuestAutoStart auto = def.autoStart();
        String id = def.idOrEmpty();
        return auto != null
            && !id.isEmpty()
            && !town.hasQuestActive(id)
            && !town.hasQuestCompleted(id)
            && def.prerequisitesOrEmpty().satisfiedBy(town)
            && holds(auto.whenOrEmpty(), town, catalog);
    }

    /**
     * What to do about one quest for one town. Nothing here changes the town.
     *
     * @param world answers for objectives whose proof stands in the world
     */
    @Nonnull
    static Decision decide(
        @Nonnull QuestDefinition def,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull Function<QuestObjective, WorldProof> world
    ) {
        // Given once: a quest the town abandoned is not pushed on it again.
        if (!offerable(def, town, catalog) || town.wasQuestAutoStarted(def.idOrEmpty())) {
            return Decision.NOTHING;
        }
        QuestAutoStart auto = def.autoStart();
        if (auto == null || !auto.skipWhenDone()) {
            return Decision.START;
        }
        boolean left = false;
        boolean unseen = false;
        for (QuestObjective objective : def.objectivesOrEmpty()) {
            // The closing talk is not something a town can have done already.
            if (QuestProgressionService.isJournalObjective(objective) || QuestProgressionService.isDialogueTurnIn(objective)) {
                continue;
            }
            if (QuestProgressionService.isStateComplete(catalog, town, def, objective)) {
                continue;
            }
            if (QuestProgressionService.needsWorld(objective)) {
                WorldProof proof = world.apply(objective);
                if (proof == WorldProof.YES) {
                    continue;
                }
                if (proof == WorldProof.UNKNOWN) {
                    unseen = true;
                    continue;
                }
            }
            left = true;
        }
        if (left) {
            return Decision.START;
        }
        return unseen ? Decision.WAIT : Decision.COMPLETE_SILENTLY;
    }

    /**
     * Reads one town: ticks steps only the world can show for quests already running, then starts what should start.
     * It reads loaded chunks and writes player state, so it must run as a world task, never inside a tick system.
     *
     * @return the quests started now
     */
    @Nonnull
    public static List<String> evaluate(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town
    ) {
        QuestCatalog quests = plugin.getQuestCatalog();
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        Function<QuestObjective, WorldProof> proofs = QuestProgressionService.worldProofs(world, plugin, town);
        boolean changed = false;
        if (QuestProgressionService.awaitsWorld(quests, town)) {
            changed |= QuestProgressionService.observeWorld(plugin, town, proofs);
        }
        List<String> started = new ArrayList<>();
        for (QuestDefinition def : quests.all().values()) {
            if (def.autoStart() == null || !QuestAvailability.isEnabled(def)) {
                continue;
            }
            String id = def.idOrEmpty();
            switch (decide(def, town, catalog, proofs)) {
                case START -> {
                    town.addActiveQuest(id);
                    town.markQuestAutoStarted(id);
                    QuestProgressionService.initialize(plugin, town, id);
                    QuestProgressionService.observeWorld(def, town, id, proofs);
                    QuestLifecycleEffects.runOnStart(world, plugin, town, tm, def, null);
                    started.add(id);
                    changed = true;
                    LOGGER.atInfo().log("Quest %s started by itself for town %s", id, town.getTownId());
                }
                case COMPLETE_SILENTLY -> {
                    town.markQuestAutoStarted(id);
                    town.completeQuest(id);
                    changed = true;
                    LOGGER.atInfo().log("Quest %s marked done for town %s, which already does what it teaches", id, town.getTownId());
                }
                default -> { }
            }
        }
        if (changed) {
            tm.updateTown(town);
            AetherhavenHudRefreshSystem.requestRefresh(world);
        }
        if (!started.isEmpty()) {
            announce(world, plugin, tm, town, started);
        }
        return started;
    }

    /** The banner other quests show when they are accepted, and the pin, for each member in the world now. */
    private static void announce(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull List<String> started
    ) {
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store == null) {
            return;
        }
        QuestCatalog quests = plugin.getQuestCatalog();
        int sound = SoundEvent.getAssetMap().getIndex(BANNER_SOUND_EVENT_ID);
        for (PlayerRef online : world.getPlayerRefs()) {
            UUID playerUuid = online.getUuid();
            if (!town.hasMemberOrOwner(playerUuid) || !town.playerHasQuestPermission(playerUuid)) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(playerUuid);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            for (String id : started) {
                EventTitleUtil.showEventTitleToPlayer(
                    online,
                    Message.translation("aetherhaven_misc.aetherhaven.banner.quest.started.secondary")
                        .param("name", quests.displayName(id)),
                    Message.translation("aetherhaven_misc.aetherhaven.banner.quest.started.primary"),
                    EventTitleStyle.Major,
                    null,
                    4.0F,
                    0.7F,
                    0.9F
                );
                QuestDefinition def = quests.get(id);
                String words = def == null || def.autoStart() == null ? null : def.autoStart().announceLangKey();
                if (words != null) {
                    online.sendMessage(Message.translation(words));
                }
            }
            if (sound != Integer.MIN_VALUE) {
                SoundUtil.playSoundEvent2d(ref, sound, SoundCategory.UI, store);
            }
            QuestAutoPin.applyToPlayer(world, plugin, tm, ref, store);
        }
    }

    /** Reads one town soon, as a world task. Safe to call from a tick system or an interaction. */
    public static void evaluateSoon(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull UUID townId) {
        world.execute(() -> {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.getTown(townId);
            if (town != null) {
                evaluate(world, plugin, tm, town);
            }
        });
    }

    /**
     * The slow look: reads every town of a world that has a member in it. A town nobody is in waits, so that a quest
     * never starts with no one there to see it start; a member who arrives is read at once by
     * {@link #onTownMemberPlayerReady}. A world task, like {@link #evaluate}.
     */
    public static void evaluateWorld(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (PersistentWorldSupport.isTemporaryInstance(world)) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getTownManagerIfLoaded(world);
        if (tm == null) {
            return;
        }
        var present = world.getPlayerRefs();
        if (present.isEmpty()) {
            return;
        }
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            for (PlayerRef online : present) {
                if (town.hasMemberOrOwner(online.getUuid())) {
                    evaluate(world, plugin, tm, town);
                    break;
                }
            }
        }
    }

    /** Called as the game clock moves. Queues the slow check every few game minutes, and after a jump in time. */
    public static void onGameMinute(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, long previousMinute, long minute) {
        if (previousMinute / CHECK_EVERY_GAME_MINUTES == minute / CHECK_EVERY_GAME_MINUTES) {
            return;
        }
        world.execute(() -> evaluateWorld(world, plugin));
    }

    /**
     * A town member arrived. Their towns are read now, so an older save gets its quest without waiting for the clock,
     * and the player is given the pins of quests that started while they were away. Already a world task.
     */
    public static void onTownMemberPlayerReady(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        if (PersistentWorldSupport.isTemporaryInstance(world)) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        // This runs while a player is joining. Whatever goes wrong here must not stop them from getting in.
        try {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            for (TownRecord town : tm.findAllTownsForPlayerInWorld(uc.getUuid())) {
                evaluate(world, plugin, tm, town);
            }
            QuestAutoPin.applyToPlayer(world, plugin, tm, ref, store);
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Quests that start by themselves were not read for arriving player %s", uc.getUuid());
        }
    }
}
