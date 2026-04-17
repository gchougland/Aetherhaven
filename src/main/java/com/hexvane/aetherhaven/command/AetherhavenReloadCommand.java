package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletionException;
import javax.annotation.Nonnull;

public final class AetherhavenReloadCommand extends AbstractPlayerCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public AetherhavenReloadCommand() {
        super("reload", "server.commands.aetherhaven.reload.desc");
        this.setPermissionGroup(GameMode.Creative);
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
            playerRef.sendMessage(Message.translation("server.commands.aetherhaven.reload.noPlugin"));
            return;
        }
        try {
            plugin.reloadConfigsAndAssetCatalogs();
            playerRef.sendMessage(Message.translation("server.commands.aetherhaven.reload.success"));
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String detail = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            playerRef.sendMessage(Message.translation("server.commands.aetherhaven.reload.failure").param("detail", detail));
            LOGGER.atWarning().withCause(cause).log("Aetherhaven reload failed");
        } catch (RuntimeException e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            playerRef.sendMessage(Message.translation("server.commands.aetherhaven.reload.failure").param("detail", detail));
            LOGGER.atWarning().withCause(e).log("Aetherhaven reload failed");
        }
    }
}
