package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hexvane.aetherhaven.hud.AetherhavenHudRefreshSystem;
import com.hexvane.aetherhaven.hud.AetherhavenHudSupport;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One time first join popup offering the intro founding quest and a birthday date. */
public final class IntroQuestPromptService {
    private IntroQuestPromptService() {}

    public static void maybeShow(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getPageManager().getCustomPage() != null) {
            return;
        }
        PlayerTownJournalState journalState = ensureJournalState(ref, store);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        boolean needsIntro = false;
        if (!journalState.isIntroQuestPromptHandled()) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            if (tm.findTownForOwnerInWorld(uc.getUuid()) != null) {
                journalState.setIntroQuestPromptHandled(true);
                store.putComponent(ref, PlayerTownJournalState.getComponentType(), journalState);
            } else {
                PlayerQuestProgress questProgress = ensureQuestProgress(ref, store);
                String questId = AetherhavenConstants.QUEST_INTRO_AETHERHAVEN;
                if (questProgress.hasQuestActive(questId) || questProgress.hasQuestCompleted(questId)) {
                    journalState.setIntroQuestPromptHandled(true);
                    store.putComponent(ref, PlayerTownJournalState.getComponentType(), journalState);
                } else {
                    needsIntro = true;
                }
            }
        }
        boolean needsBirthday = !journalState.hasBirthday();
        if (!needsIntro && !needsBirthday) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new IntroQuestPromptPage(playerRef, !needsIntro));
    }

    public static void accept(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nullable Season birthdaySeason,
        int birthdayDay
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        PlayerTownJournalState journalState = ensureJournalState(ref, store);
        saveBirthday(journalState, birthdaySeason, birthdayDay);
        journalState.setIntroQuestPromptHandled(true);
        PlayerQuestProgress questProgress = ensureQuestProgress(ref, store);
        String questId = AetherhavenConstants.QUEST_INTRO_AETHERHAVEN;
        PlayerQuestProgressionService.startQuest(plugin, world, ref, store, questProgress, questId);
        ItemStack journal = new ItemStack(AetherhavenConstants.ITEM_QUEST_JOURNAL, 1);
        player.giveItem(journal, ref, store);
        journalState.pinQuest(PlayerQuestIds.playerRow(questId));
        store.putComponent(ref, PlayerTownJournalState.getComponentType(), journalState);
        store.putComponent(ref, PlayerQuestProgress.getComponentType(), questProgress);
        if (journalState.isHudEnabled()) {
            AetherhavenHudSupport.obtain(player, playerRef);
        }
        AetherhavenHudRefreshSystem.requestRefresh(world);
    }

    public static void decline(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable Season birthdaySeason,
        int birthdayDay
    ) {
        PlayerTownJournalState journalState = ensureJournalState(ref, store);
        saveBirthday(journalState, birthdaySeason, birthdayDay);
        journalState.setIntroQuestPromptHandled(true);
        store.putComponent(ref, PlayerTownJournalState.getComponentType(), journalState);
    }

    public static void saveBirthdayOnly(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable Season birthdaySeason,
        int birthdayDay
    ) {
        PlayerTownJournalState journalState = ensureJournalState(ref, store);
        saveBirthday(journalState, birthdaySeason, birthdayDay);
        store.putComponent(ref, PlayerTownJournalState.getComponentType(), journalState);
    }

    private static void saveBirthday(
        @Nonnull PlayerTownJournalState journalState,
        @Nullable Season birthdaySeason,
        int birthdayDay
    ) {
        if (birthdaySeason != null) {
            journalState.setBirthday(birthdaySeason, birthdayDay);
        }
    }

    @Nonnull
    private static PlayerTownJournalState ensureJournalState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerTownJournalState state = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (state == null) {
            state = new PlayerTownJournalState();
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), state);
        }
        return state;
    }

    @Nonnull
    private static PlayerQuestProgress ensureQuestProgress(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerQuestProgress progress = store.getComponent(ref, PlayerQuestProgress.getComponentType());
        if (progress == null) {
            progress = new PlayerQuestProgress();
            store.putComponent(ref, PlayerQuestProgress.getComponentType(), progress);
        }
        return progress;
    }
}
