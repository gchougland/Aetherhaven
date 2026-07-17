package com.hexvane.aetherhaven.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/** Core {@code /aetherhaven} tree; feature subplugins attach subcommands via {@link com.hexvane.aetherhaven.AetherhavenPlugin#registerAetherhavenSubcommand}. */
public final class AetherhavenCommand extends AbstractCommandCollection {
    public AetherhavenCommand() {
        super("aetherhaven", "aetherhaven_commands_root.commands.aetherhaven.root.desc");
        this.setPermissionGroups("hytale:Adventurer");
        this.addAliases("ah");
        this.addSubCommand(new AetherhavenStarterKitCommand());
        this.addSubCommand(new AetherhavenStarterTownCommand());
        this.addSubCommand(new AetherhavenTownsCommand());
        this.addSubCommand(new AetherhavenReplaceCharterCommand());
        this.addSubCommand(new AetherhavenTownCommand());
        this.addSubCommand(new AetherhavenReloadCommand());
        this.addSubCommand(new ExportAvatarSkinCommand());
        this.addSubCommand(new AetherhavenPlotsCommand());
        this.addSubCommand(new AetherhavenPlotTokenCommand());
    }
}
