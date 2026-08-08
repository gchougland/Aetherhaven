package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.calendar.CalendarSeasonTheme;
import com.hexvane.aetherhaven.calendar.VillagerBirthdayIndex;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.CalendarDate;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.LocalDateTime;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CalendarPage extends AetherhavenInteractiveCustomUIPage<CalendarPage.PageData> {
    private static final String WEEK_ROWS = "#CalendarRoot #Content #ContentForeground #GridCenterWrap #WeekRows";
    private static final String CALENDAR_CONTAINER = "#CalendarRoot";
    private static final int DAYS_PER_WEEK = 7;
    private static final int WEEKS = 4;

    private boolean templateAppended;

    public CalendarPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/CalendarPage.ui");
            commandBuilder.insertBefore(
                "#CalendarRoot #Content #ContentForeground",
                "Aetherhaven/CalendarSeasonFlourish.ui"
            );
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "Close"),
                false
            );
        }
        AetherhavenUiLocalization.applyCalendarPage(commandBuilder);
        commandBuilder.set("#CloseButton.TextSpans", Message.translation("aetherhaven_ui_calendar.aetherhaven.ui.calendar.close"));

        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            commandBuilder.set("#SeasonHeader.TextSpans", Message.translation("aetherhaven_ui_calendar.aetherhaven.ui.calendar.noTime"));
            commandBuilder.clear(WEEK_ROWS);
            return;
        }

        LocalDateTime gameTime = wtr.getGameDateTime();
        CalendarDate today = AetherhavenCalendar.from(gameTime);
        CalendarSeasonTheme theme = CalendarSeasonTheme.forSeason(today.season());
        applySeasonTheme(commandBuilder, theme, today.season());
        commandBuilder.set("#SeasonHeader.TextSpans", Message.raw(AetherhavenCalendar.formatSeasonHeader(today)));

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        VillagerBirthdayIndex index =
            plugin != null
                ? VillagerBirthdayIndex.fromCatalog(plugin.getVillagerDefinitionCatalog())
                : VillagerBirthdayIndex.fromCatalog(VillagerDefinitionCatalog.empty());

        commandBuilder.clear(WEEK_ROWS);
        int rowIndex = 0;
        for (int week = 0; week < WEEKS; week++) {
            commandBuilder.append(WEEK_ROWS, "Aetherhaven/CalendarWeekRow.ui");
            String row = WEEK_ROWS + "[" + rowIndex + "]";
            rowIndex++;
            for (int col = 0; col < DAYS_PER_WEEK; col++) {
                int dayOfSeason = week * DAYS_PER_WEEK + col + 1;
                commandBuilder.append(row, "Aetherhaven/CalendarDayCell.ui");
                String cell = row + "[" + col + "]";
                applyDayCell(commandBuilder, cell, today, index, today.season(), dayOfSeason, theme);
            }
        }
    }

    private static void applySeasonTheme(
        @Nonnull UICommandBuilder cmd,
        @Nonnull CalendarSeasonTheme theme,
        @Nonnull Season season
    ) {
        CalendarSeasonTheme.applyContainerArtVisibility(cmd, CALENDAR_CONTAINER, season);
        CalendarSeasonTheme.applyFlourishColors(cmd, CALENDAR_CONTAINER, season, theme);
        cmd.set("#CalendarTitleText.Style.TextColor", theme.headerTextColor());
        cmd.set("#SeasonHeader.Style.TextColor", theme.headerTextColor());
        cmd.set("#SeasonAccent.Background", theme.accentBarColor());
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (!"Close".equals(data.action)) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    private static void applyDayCell(
        @Nonnull UICommandBuilder cmd,
        @Nonnull String cell,
        @Nonnull CalendarDate today,
        @Nonnull VillagerBirthdayIndex index,
        @Nonnull Season viewedSeason,
        int dayOfSeason,
        @Nonnull CalendarSeasonTheme theme
    ) {
        boolean isToday = viewedSeason == today.season() && dayOfSeason == today.dayOfSeason();
        cmd.set(cell + " #CellFrame.Background", theme.borderColor());
        cmd.set(cell + " #CellBackground.Background", theme.cellFillColor());
        cmd.set(cell + " #TodayHighlight.Background", theme.todayFillColor());
        cmd.set(cell + " #TodayHighlight.Visible", isToday);
        cmd.set(cell + " #DayNumber.TextSpans", Message.raw(String.valueOf(dayOfSeason)));
        cmd.set(cell + " #DayNumber.Style.TextColor", isToday ? theme.todayDayNumberColor() : theme.dayNumberColor());

        List<VillagerDefinition> birthdays = index.birthdaysOn(viewedSeason, dayOfSeason);
        if (birthdays.isEmpty()) {
            cmd.set(cell + " #PortraitArea.Visible", false);
            return;
        }
        VillagerDefinition villager = birthdays.get(0);
        cmd.set(cell + " #PortraitArea.Visible", true);
        cmd.set(cell + " #Portrait.AssetPath", NpcPortraitProvider.portraitPathForRoleId(villager.getNpcRoleId()));
        String name = villager.getDisplayName() != null && !villager.getDisplayName().isBlank()
            ? villager.getDisplayName().trim()
            : villager.getNpcRoleId();
        cmd.set(
            cell + " #PortraitArea.TooltipTextSpans",
            Message.translation("aetherhaven_ui_calendar.aetherhaven.ui.calendar.birthdayTooltip").param("villager", name)
        );
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        @Nullable
        private String action;

        private PageData() {}
    }
}
