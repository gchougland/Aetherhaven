package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayService;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.CalendarDate;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class IntroQuestPromptPage extends AetherhavenInteractiveCustomUIPage<IntroQuestPromptPage.PageData> {
    private static final String MSG = "aetherhaven_intro_quest.aetherhaven.introQuest.prompt";
    private static final String BIRTHDAY_MSG = "aetherhaven_intro_quest.aetherhaven.introQuest.birthday";

    private final boolean birthdayOnly;
    private boolean templateAppended;
    @Nullable
    private Season selectedSeason;
    private int selectedDay;

    public IntroQuestPromptPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, false);
    }

    public IntroQuestPromptPage(@Nonnull PlayerRef playerRef, boolean birthdayOnly) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.birthdayOnly = birthdayOnly;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        ensureDefaults(ref, store);
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/IntroQuestPromptPage.ui");
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#BirthdaySeasonDropdown",
                EventData.of("@Season", "#BirthdaySeasonDropdown.Value"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#BirthdayDayDropdown",
                EventData.of("@Day", "#BirthdayDayDropdown.Value"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#IntroPromptAccept",
                new EventData()
                    .append("Action", birthdayOnly ? "SaveBirthday" : "Accept")
                    .append("@Season", "#BirthdaySeasonDropdown.Value")
                    .append("@Day", "#BirthdayDayDropdown.Value"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#IntroPromptDecline",
                new EventData()
                    .append("Action", "Decline")
                    .append("@Season", "#BirthdaySeasonDropdown.Value")
                    .append("@Day", "#BirthdayDayDropdown.Value"),
                false
            );
        }
        if (birthdayOnly) {
            commandBuilder.set("#IntroPromptTitle.TextSpans", Message.translation(BIRTHDAY_MSG + ".title"));
            commandBuilder.set("#IntroPromptText.TextSpans", Message.translation(BIRTHDAY_MSG + ".body"));
            commandBuilder.set("#IntroPromptAccept.TextSpans", Message.translation(BIRTHDAY_MSG + ".confirm"));
            commandBuilder.set("#IntroPromptDecline.Visible", false);
        } else {
            commandBuilder.set("#IntroPromptTitle.TextSpans", Message.translation(MSG + ".title"));
            commandBuilder.set("#IntroPromptText.TextSpans", Message.translation(MSG + ".body"));
            commandBuilder.set("#IntroPromptAccept.TextSpans", Message.translation(MSG + ".accept"));
            commandBuilder.set("#IntroPromptDecline.TextSpans", Message.translation(MSG + ".decline"));
            commandBuilder.set("#IntroPromptDecline.Visible", true);
        }
        commandBuilder.set("#BirthdayHeading.TextSpans", Message.translation(BIRTHDAY_MSG + ".heading"));
        commandBuilder.set("#BirthdaySeasonLabel.TextSpans", Message.translation(BIRTHDAY_MSG + ".season"));
        commandBuilder.set("#BirthdayDayLabel.TextSpans", Message.translation(BIRTHDAY_MSG + ".day"));
        applyBirthdayDropdowns(commandBuilder);
    }

    private void ensureDefaults(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (selectedSeason != null && selectedDay >= 1) {
            return;
        }
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (journal != null && journal.hasBirthday()) {
            selectedSeason = journal.getBirthdaySeason();
            selectedDay = journal.getBirthdayDay();
            return;
        }
        CalendarDate today = today(store);
        selectedSeason = today.season();
        selectedDay = today.dayOfSeason();
    }

    @Nonnull
    private static CalendarDate today(@Nonnull Store<EntityStore> store) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return new CalendarDate(Season.SPRING, 1, 1L);
        }
        return AetherhavenCalendar.from(wtr.getGameDateTime());
    }

    private void applyBirthdayDropdowns(@Nonnull UICommandBuilder commandBuilder) {
        Season season = selectedSeason != null ? selectedSeason : Season.SPRING;
        int day = selectedDay >= 1 ? selectedDay : 1;
        commandBuilder.set("#BirthdaySeasonDropdown.Entries", seasonEntries());
        commandBuilder.set("#BirthdaySeasonDropdown.Value", PlayerBirthdayService.seasonValue(season));
        commandBuilder.set("#BirthdayDayDropdown.Entries", dayEntries());
        commandBuilder.set("#BirthdayDayDropdown.Value", String.valueOf(day));
    }

    @Nonnull
    public static ObjectArrayList<DropdownEntryInfo> seasonEntries() {
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        for (Season season : Season.values()) {
            entries.add(
                new DropdownEntryInfo(
                    LocalizableString.fromString(season.displayName()),
                    PlayerBirthdayService.seasonValue(season)
                )
            );
        }
        return entries;
    }

    @Nonnull
    public static ObjectArrayList<DropdownEntryInfo> dayEntries() {
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        for (int day = 1; day <= AetherhavenCalendar.DAYS_PER_SEASON; day++) {
            String value = String.valueOf(day);
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(value), value));
        }
        return entries;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        applySelection(store, data);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (data.action == null || data.action.isBlank()) {
            return;
        }
        Season season = selectedSeason != null ? selectedSeason : Season.SPRING;
        int day = selectedDay >= 1 ? selectedDay : 1;
        if ("Accept".equals(data.action)) {
            IntroQuestPromptService.accept(AetherhavenPlugin.get(), ref, store, playerRef, season, day);
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if ("SaveBirthday".equals(data.action)) {
            IntroQuestPromptService.saveBirthdayOnly(ref, store, season, day);
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if ("Decline".equals(data.action)) {
            IntroQuestPromptService.decline(ref, store, season, day);
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    private void applySelection(@Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        CalendarDate today = today(store);
        Season fallbackSeason = selectedSeason != null ? selectedSeason : today.season();
        int fallbackDay = selectedDay >= 1 ? selectedDay : today.dayOfSeason();
        Season parsedSeason = PlayerBirthdayService.parseSeason(data.season, fallbackSeason);
        if (parsedSeason != null) {
            selectedSeason = parsedSeason;
        }
        selectedDay = PlayerBirthdayService.parseDay(data.day, fallbackDay);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@Season", Codec.STRING), (d, v) -> d.season = v, d -> d.season)
            .add()
            .append(new KeyedCodec<>("@Day", Codec.STRING), (d, v) -> d.day = v, d -> d.day)
            .add()
            .build();

        @Nullable
        String action;
        @Nullable
        String season;
        @Nullable
        String day;
    }
}
