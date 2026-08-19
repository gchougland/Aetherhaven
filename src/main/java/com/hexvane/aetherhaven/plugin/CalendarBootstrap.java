package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayDialogueHandlers;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayGiftSeekState;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayGiftSeekSystem;
import com.hexvane.aetherhaven.command.AetherhavenDateCommand;
import com.hexvane.aetherhaven.ui.CalendarPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import javax.annotation.Nonnull;

public final class CalendarBootstrap {
    private CalendarBootstrap() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        OpenCustomUIInteraction.registerSimple(
            plugin,
            CalendarPage.class,
            AetherhavenConstants.PAGE_CALENDAR,
            CalendarPage::new
        );
        PlayerBirthdayGiftSeekState.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new PlayerBirthdayGiftSeekSystem());
        PlayerBirthdayDialogueHandlers.register(plugin);
    }

    public static void registerCommands(@Nonnull AetherhavenPlugin plugin) {
        plugin.registerAetherhavenSubcommand(new AetherhavenDateCommand());
    }
}
