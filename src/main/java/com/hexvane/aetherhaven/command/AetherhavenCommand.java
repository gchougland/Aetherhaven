package com.hexvane.aetherhaven.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public final class AetherhavenCommand extends AbstractCommandCollection {
    public AetherhavenCommand() {
        super("aetherhaven", "server.commands.aetherhaven.root.desc");
        this.addAliases("ah");
        this.addSubCommand(new PlotSignAdminCommand());
        this.addSubCommand(new ExportAvatarSkinCommand());
        this.addSubCommand(new DialogueCommand());
    }
}
