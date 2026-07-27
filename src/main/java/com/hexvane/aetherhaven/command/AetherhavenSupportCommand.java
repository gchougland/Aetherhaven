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
            String raw = normalizeCommandInput(context.getInputString());
            String note = extractNoteAfterCommandPath(raw, context.getCalledCommand().getFullyQualifiedName());
            if (note == null) {
                note = extractNoteAfterWord(raw, "upload");
            }
            return note;
        }

        @Nullable
        private static String extractNoteAfterCommandPath(@Nonnull String raw, @Nonnull String commandPath) {
            String[] pathParts = commandPath.split("\\s+");
            int pos = 0;
            for (String part : pathParts) {
                pos = skipWhitespace(raw, pos);
                if (pos >= raw.length()) {
                    return null;
                }
                int wordEnd = findWordEnd(raw, pos);
                String word = normalizeCommandInput(raw.substring(pos, wordEnd));
                if (!word.equalsIgnoreCase(part)) {
                    return null;
                }
                pos = wordEnd;
            }
            return trimOptionalNote(raw, pos);
        }

        @Nullable
        private static String extractNoteAfterWord(@Nonnull String raw, @Nonnull String word) {
            int searchFrom = 0;
            while (searchFrom < raw.length()) {
                int pos = skipWhitespace(raw, searchFrom);
                if (pos >= raw.length()) {
                    return null;
                }
                int wordEnd = findWordEnd(raw, pos);
                String token = raw.substring(pos, wordEnd);
                if (token.equalsIgnoreCase(word)) {
                    return trimOptionalNote(raw, wordEnd);
                }
                searchFrom = wordEnd;
            }
            return null;
        }

        @Nullable
        private static String trimOptionalNote(@Nonnull String raw, int pos) {
            pos = skipWhitespace(raw, pos);
            if (pos >= raw.length()) {
                return null;
            }
            String note = raw.substring(pos).trim();
            if (note.length() >= 2 && note.startsWith("\"") && note.endsWith("\"")) {
                note = note.substring(1, note.length() - 1).trim();
            }
            return note.isEmpty() ? null : note;
        }

        @Nonnull
        private static String normalizeCommandInput(@Nonnull String raw) {
            String normalized = raw.trim();
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1).trim();
            }
            return normalized;
        }

        private static int skipWhitespace(@Nonnull String raw, int pos) {
            while (pos < raw.length() && raw.charAt(pos) == ' ') {
                pos++;
            }
            return pos;
        }

        private static int findWordEnd(@Nonnull String raw, int pos) {
            while (pos < raw.length() && raw.charAt(pos) != ' ') {
                pos++;
            }
            return pos;
        }
    }
}
