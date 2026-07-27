package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.SupportUploadConfig;
import com.hexvane.aetherhaven.support.SupportUploadService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Support commands for sending debug data to Aetherhaven. */
public final class AetherhavenSupportCommand extends AbstractCommandCollection {
    public AetherhavenSupportCommand() {
        super("support", "aetherhaven_commands_help.commands.aetherhaven.support.desc");
        this.addSubCommand(new UploadCommand());
    }

    private static final class UploadCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> noteArg =
            this.withOptionalArg("note", "aetherhaven_commands_help.commands.aetherhaven.support.upload.note.desc", ArgTypes.STRING);

        UploadCommand() {
            super("upload", "aetherhaven_commands_help.commands.aetherhaven.support.upload.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            SupportUploadConfig cfg = plugin.getConfig().get().getSupportUpload();
            if (!cfg.isEnabled()) {
                SupportUploadService.notifyPlayer(playerRef, new SupportUploadService.UploadOutcome("disabled", null));
                return;
            }
            String apiBase = plugin.getConfig().get().getCommunityMarketplace().getApiBaseUrl();
            if (apiBase.isBlank()) {
                SupportUploadService.notifyPlayer(playerRef, new SupportUploadService.UploadOutcome("disabled", null));
                return;
            }
            if (!SupportUploadService.beginUploadIfAllowed(playerRef.getUuid())) {
                SupportUploadService.notifyPlayer(
                    playerRef,
                    new SupportUploadService.UploadOutcome("rate_limited", null)
                );
                return;
            }

            String note = noteArg.provided(context) ? noteArg.get(context) : "";
            playerRef.sendMessage(Message.translation("aetherhaven_support.aetherhaven.support.upload.started"));
            SupportUploadService.scheduleUpload(plugin, world, playerRef, note);
        }
    }
}
