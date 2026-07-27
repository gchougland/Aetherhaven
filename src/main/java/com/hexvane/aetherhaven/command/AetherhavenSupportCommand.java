package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.SupportUploadConfig;
import com.hexvane.aetherhaven.support.SupportUploadService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Support commands for sending debug data to Aetherhaven. */
public final class AetherhavenSupportCommand extends AbstractCommandCollection {
    public AetherhavenSupportCommand() {
        super("support", "aetherhaven_commands_help.commands.aetherhaven.support.desc");
        this.addSubCommand(new UploadCommand());
    }

    private static void beginUpload(
        @Nonnull PlayerRef playerRef,
        @Nonnull World world,
        @Nullable String note
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

        playerRef.sendMessage(Message.translation("aetherhaven_support.aetherhaven.support.upload.started"));
        SupportUploadService.scheduleUpload(plugin, world, playerRef, note != null ? note : "");
    }

    /** {@code /aetherhaven support upload [note...]} */
    private static final class UploadCommand extends AbstractPlayerCommand {
        UploadCommand() {
            super("upload", "aetherhaven_commands_help.commands.aetherhaven.support.upload.desc");
            this.setAllowsExtraArguments(true);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            beginUpload(playerRef, world, extractOptionalNote(context));
        }

        @Nullable
        private static String extractOptionalNote(@Nonnull CommandContext context) {
            String raw = context.getInputString().trim();
            if (raw.startsWith("/")) {
                raw = raw.substring(1).trim();
            }

            String commandPath = context.getCalledCommand().getFullyQualifiedName();
            if (!raw.regionMatches(true, 0, commandPath, 0, commandPath.length())) {
                return null;
            }

            String note = raw.substring(commandPath.length()).trim();
            if (note.isEmpty()) {
                return null;
            }

            if (note.length() >= 2 && note.startsWith("\"") && note.endsWith("\"")) {
                note = note.substring(1, note.length() - 1).trim();
            }
            return note.isEmpty() ? null : note;
        }
    }
}
