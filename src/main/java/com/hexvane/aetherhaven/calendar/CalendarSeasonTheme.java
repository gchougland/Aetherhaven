package com.hexvane.aetherhaven.calendar;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import javax.annotation.Nonnull;

/** Season tinted colors and container art for the wall calendar UI. */
public record CalendarSeasonTheme(
    @Nonnull String accentBarColor,
    @Nonnull String headerTextColor,
    @Nonnull String flourishSoftColor,
    @Nonnull String borderColor,
    @Nonnull String cellFillColor,
    @Nonnull String todayFillColor,
    @Nonnull String dayNumberColor,
    @Nonnull String todayDayNumberColor
) {
    private static final String CONTENT_ROOT = " #Content";
    private static final String[] FLOURISH_PRIMARY = {" #FlourishIcon1", " #FlourishIcon5"};
    private static final String[] FLOURISH_SECONDARY = {" #FlourishIcon2", " #FlourishIcon3", " #FlourishIcon4", " #FlourishIcon6"};

    @Nonnull
    public static CalendarSeasonTheme forSeason(@Nonnull Season season) {
        return switch (season) {
            case SPRING ->
                new CalendarSeasonTheme(
                    "#8EC48773",
                    "#DFF2D8",
                    "#DFF2D899",
                    "#7AAB72",
                    "#1E2E24",
                    "#4A8A58B8",
                    "#D0E8C8",
                    "#F2FFF0"
                );
            case SUMMER ->
                new CalendarSeasonTheme(
                    "#E8C86873",
                    "#FFF0C0",
                    "#FFF0C099",
                    "#C9A85A",
                    "#2A2818",
                    "#B89030B8",
                    "#E8DCC0",
                    "#FFF8E0"
                );
            case AUTUMN ->
                new CalendarSeasonTheme(
                    "#E0905073",
                    "#FFD8B0",
                    "#FFD8B099",
                    "#C87840",
                    "#2A2018",
                    "#D08038B8",
                    "#E8C8A8",
                    "#FFF0D8"
                );
            case WINTER ->
                new CalendarSeasonTheme(
                    "#8AB0D073",
                    "#D8E8F8",
                    "#D8E8F899",
                    "#7A9AB8",
                    "#1A2430",
                    "#5A88A8B8",
                    "#C8D4E0",
                    "#EEF6FF"
                );
        };
    }

    /** Seasonal container art is declared in {@code CalendarPage.ui}; only visibility may be toggled at runtime. */
    public static void applyContainerArtVisibility(
        @Nonnull UICommandBuilder cmd,
        @Nonnull String containerRoot,
        @Nonnull Season activeSeason
    ) {
        for (Season season : Season.values()) {
            String suffix = suffixFor(season);
            boolean visible = season == activeSeason;
            cmd.set(containerRoot + " #TitleBg" + suffix + ".Visible", visible);
            cmd.set(containerRoot + " #DecoTop" + suffix + ".Visible", visible);
            cmd.set(containerRoot + " #ContentBg" + suffix + ".Visible", visible);
            cmd.set(containerRoot + " #DecoBottom" + suffix + ".Visible", visible);
            cmd.set(containerRoot + CONTENT_ROOT + " #Flourish" + suffix + ".Visible", visible);
        }
    }

    public static void applyFlourishColors(
        @Nonnull UICommandBuilder cmd,
        @Nonnull String containerRoot,
        @Nonnull Season season,
        @Nonnull CalendarSeasonTheme theme
    ) {
        String flourishRoot = containerRoot + CONTENT_ROOT + " #Flourish" + suffixFor(season);
        for (String icon : FLOURISH_PRIMARY) {
            cmd.set(flourishRoot + icon + ".Background.Color", theme.headerTextColor());
        }
        for (String icon : FLOURISH_SECONDARY) {
            cmd.set(flourishRoot + icon + ".Background.Color", theme.flourishSoftColor());
        }
    }

    @Nonnull
    private static String suffixFor(@Nonnull Season season) {
        return switch (season) {
            case SPRING -> "Spring";
            case SUMMER -> "Summer";
            case AUTUMN -> "Autumn";
            case WINTER -> "Winter";
        };
    }
}
