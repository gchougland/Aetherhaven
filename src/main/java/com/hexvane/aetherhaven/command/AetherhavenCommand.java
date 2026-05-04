package com.hexvane.aetherhaven.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public final class AetherhavenCommand extends AbstractCommandCollection {
    public AetherhavenCommand() {
        super("aetherhaven", "aetherhaven_commands_root.commands.aetherhaven.root.desc");
        this.addAliases("ah");
        this.addSubCommand(new PlotSignAdminCommand());
        this.addSubCommand(new AetherhavenStarterKitCommand());
        this.addSubCommand(new AetherhavenTownsCommand());
        this.addSubCommand(new AetherhavenTownCommand());
        this.addSubCommand(new AetherhavenReloadCommand());
        this.addSubCommand(new ExportAvatarSkinCommand());
        this.addSubCommand(new DialogueCommand());
        this.addSubCommand(new AetherhavenPoiCommand());
        this.addSubCommand(new AetherhavenPlotsCommand());
        this.addSubCommand(new AetherhavenNeedsCommand());
        this.addSubCommand(new AetherhavenTaxCommand());
        this.addSubCommand(new AetherhavenQuestDebugCommand());
        this.addSubCommand(new AetherhavenAutonomyDebugCommand());
        this.addSubCommand(new AetherhavenReputationDebugCommand());
        this.addSubCommand(new AetherhavenVillagerCommand());
        this.addSubCommand(new AetherhavenGiftCommand());
        this.addSubCommand(new AetherhavenLootChestDebugCommand());
        this.addSubCommand(new AetherhavenPathCommand());
    }
}
