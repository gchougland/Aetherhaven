package com.hexvane.aetherhaven.plugin;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import javax.annotation.Nonnull;

/** Stable {@link PluginIdentifier} constants for Aetherhaven subplugins. */
public final class AetherhavenPluginIds {
    public static final String GROUP = "Hexvane";

    public static final PluginIdentifier CORE = id("Aetherhaven");
    public static final PluginIdentifier PATH_DESIGNER = id("PathDesigner");
    public static final PluginIdentifier PATROL_ROUTES = id("PatrolRoutes");
    public static final PluginIdentifier RTS = id("Rts");
    public static final PluginIdentifier FLOATING_GIFTS = id("FloatingGifts");
    public static final PluginIdentifier BARD = id("Bard");
    public static final PluginIdentifier REPUTATION = id("Reputation");
    public static final PluginIdentifier CONSTRUCTION = id("Construction");
    public static final PluginIdentifier PRODUCTION = id("Production");
    public static final PluginIdentifier PLOT_CREATOR = id("PlotCreator");
    public static final PluginIdentifier QUESTS = id("Quests");
    public static final PluginIdentifier DIALOGUE = id("Dialogue");
    public static final PluginIdentifier VILLAGERS = id("Villagers");
    public static final PluginIdentifier ECONOMY = id("Economy");
    public static final PluginIdentifier COMMERCE = id("Commerce");
    public static final PluginIdentifier GUILD = id("Guild");
    public static final PluginIdentifier JEWELRY = id("Jewelry");
    public static final PluginIdentifier REPUTATION_UNLOCKS = id("ReputationUnlocks");
    public static final PluginIdentifier ADMIN_TOOLS = id("AdminTools");
    public static final PluginIdentifier WORLD_NPCS = id("WorldNpcs");
    public static final PluginIdentifier FESTIVALS = id("Festivals");

    private AetherhavenPluginIds() {}

    @Nonnull
    public static PluginIdentifier id(@Nonnull String name) {
        return new PluginIdentifier(GROUP, name);
    }
}
