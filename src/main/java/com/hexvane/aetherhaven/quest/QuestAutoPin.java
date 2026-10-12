package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hud.AetherhavenHudRefreshSystem;
import com.hexvane.aetherhaven.hud.AetherhavenHudSnapshotService;
import com.hexvane.aetherhaven.hud.AetherhavenHudSupport;
import com.hexvane.aetherhaven.hud.HudQuestEntry;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pins a town quest marked {@code autoPin} to the HUD quest tracker of each town member, once. Town quests belong to
 * the town but pins belong to the player, so each player carries a mark for every quest they were given this way: a
 * player who unpins it is left alone, and a member who was away gets it when they next arrive.
 */
public final class QuestAutoPin {
    private static final String REPLACED = "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.autoPinReplaced";

    private QuestAutoPin() {}

    /** One mark per town and quest: the same quest in a second town is pinned for that town too. */
    @Nonnull
    static String key(@Nonnull UUID townId, @Nonnull String questId) {
        return townId + "|" + questId.trim();
    }

    /**
     * @param added  false when the player already had the quest pinned and only the mark was set
     * @param gaveUp the pin that made room for it, or null
     */
    public record Pinned(@Nonnull String questId, boolean added, @Nullable String gaveUp) {}

    /**
     * Pins each of the town's active {@code autoPin} quests this player has not been given yet.
     *
     * @param showing true for a pinned id the HUD is drawing now; a full tracker gives up a pin that is not
     * @return one entry for every quest handled now; empty when nothing changed
     */
    @Nonnull
    public static List<Pinned> apply(
        @Nonnull PlayerTownJournalState state,
        @Nonnull TownRecord town,
        @Nonnull QuestCatalog catalog,
        @Nonnull Predicate<String> showing
    ) {
        List<Pinned> handled = new ArrayList<>();
        for (String questId : town.getActiveQuestIdsSnapshot()) {
            QuestDefinition def = catalog.get(questId);
            if (def == null || !def.autoPin()) {
                continue;
            }
            if (!state.markQuestAutoPinned(key(town.getTownId(), questId))) {
                continue;
            }
            boolean had = state.isQuestPinned(questId);
            // The quest now being pinned counts as showing, so two quests pinned at once do not push each other out.
            Set<String> fresh = new HashSet<>();
            for (Pinned earlier : handled) {
                fresh.add(earlier.questId());
            }
            String gaveUp = state.pinQuestMakingRoom(questId, id -> fresh.contains(id) || showing.test(id));
            handled.add(new Pinned(questId, !had, gaveUp));
        }
        return handled;
    }

    /** True when {@link #apply} would do something, without doing it. */
    static boolean wants(
        @Nonnull PlayerTownJournalState state,
        @Nonnull TownRecord town,
        @Nonnull QuestCatalog catalog
    ) {
        for (String questId : town.getActiveQuestIdsSnapshot()) {
            QuestDefinition def = catalog.get(questId);
            if (def != null && def.autoPin() && !state.wasQuestAutoPinned(key(town.getTownId(), questId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives one player the pins of every town they belong to in this world. Writes the player's journal state, so it
     * must run as a world task and never from inside a tick system.
     *
     * @return true when a pin or a mark was added
     */
    public static boolean applyToPlayer(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        UUID playerUuid = uc.getUuid();
        QuestCatalog catalog = plugin.getQuestCatalog();
        PlayerTownJournalState saved = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        // A player who has never opened the journal has no state yet. It is only written when there is a pin to keep.
        PlayerTownJournalState state = saved != null ? saved : new PlayerTownJournalState();
        List<Pinned> handled = new ArrayList<>();
        Set<String> showing = null;
        for (TownRecord town : tm.findAllTownsForPlayerInWorld(playerUuid)) {
            if (!town.playerHasQuestPermission(playerUuid) || !wants(state, town, catalog)) {
                continue;
            }
            if (showing == null) {
                showing = showingPins(world, plugin, tm, ref, store, state, playerUuid);
            }
            handled.addAll(apply(state, town, catalog, showing::contains));
        }
        if (handled.isEmpty()) {
            return false;
        }
        store.putComponent(ref, PlayerTownJournalState.getComponentType(), state);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef != null) {
            for (Pinned pinned : handled) {
                if (pinned.gaveUp() != null) {
                    playerRef.sendMessage(Message.translation(REPLACED).param("name", catalog.titleMessage(pinned.questId())));
                }
            }
            if (player != null && state.isHudEnabled()) {
                AetherhavenHudSupport.obtain(player, playerRef);
            }
        }
        AetherhavenHudRefreshSystem.requestRefresh(world);
        return true;
    }

    /** The pinned ids the HUD is drawing for this player now, found the way the HUD finds them. */
    @Nonnull
    private static Set<String> showingPins(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerTownJournalState state,
        @Nonnull UUID playerUuid
    ) {
        Set<String> showing = new HashSet<>();
        if (state.getPinnedQuestIds().isEmpty()) {
            return showing;
        }
        TownRecord active = TownPlayerResolution.resolveActiveTown(world, store, ref, tm, state);
        var worldNpcs = AetherhavenWorldRegistries.getWorldNpcRegistry(world);
        var worldProgress = worldNpcs != null ? worldNpcs.findPlayerProgress(playerUuid) : null;
        PlayerQuestProgress playerProgress = store.getComponent(ref, PlayerQuestProgress.getComponentType());
        for (HudQuestEntry entry : new AetherhavenHudSnapshotService(plugin)
            .pinnedQuests(active, worldProgress, playerProgress, store, state.getPinnedQuestIds())) {
            showing.add(entry.id());
        }
        return showing;
    }

    /**
     * Gives every member of the town who is in this world now their pin. Queued as a world task, so it is safe to
     * call from anywhere a quest starts.
     */
    public static void applyToOnlineMembers(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        world.execute(() -> {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (store == null) {
                return;
            }
            for (PlayerRef online : world.getPlayerRefs()) {
                if (!town.hasMemberOrOwner(online.getUuid())) {
                    continue;
                }
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(online.getUuid());
                if (ref != null && ref.isValid()) {
                    applyToPlayer(world, plugin, tm, ref, store);
                }
            }
        });
    }
}
