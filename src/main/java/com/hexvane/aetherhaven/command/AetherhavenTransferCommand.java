package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.transfer.SaveContentTransfer;
import com.hexvane.aetherhaven.ui.SaveContentTransferPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import joptsimple.OptionSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AetherhavenTransferCommand extends AbstractPlayerCommand {
    public AetherhavenTransferCommand() {
        super("transfer", "aetherhaven_transfer.aetherhaven.transfer.command");
        setPermissionGroups("hytale:Adventurer");
    }

    public static boolean isLocalOwner(PlayerRef player) {
        return isLocalOwner(Options.getOptionSet(), player != null ? player.getUuid() : null);
    }

    /** Dedicated servers never qualify, even if an owner UUID was supplied or the caller is an administrator. */
    static boolean isLocalOwner(@Nullable OptionSet options, @Nullable UUID playerUuid) {
        return options != null && options.has(Options.SINGLEPLAYER) && playerUuid != null
            && playerUuid.equals(options.valueOf(Options.OWNER_UUID));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!isLocalOwner(playerRef)) {
            playerRef.sendMessage(Message.translation("aetherhaven_transfer.aetherhaven.transfer.localOnly"));
            return;
        }
        var plugin = AetherhavenPlugin.get();
        if (plugin == null) return;
        playerRef.sendMessage(Message.translation("aetherhaven_transfer.aetherhaven.transfer.scanning"));
        CompletableFuture.runAsync(() -> {
            try {
                if (!isLocalOwner(playerRef)) return;
                var source = plugin.getDataDirectory().toAbsolutePath().normalize();
                var targets = SaveContentTransfer.discoverTargets(source);
                var inventory = SaveContentTransfer.inventory(source);
                world.execute(() -> {
                    if (!ref.isValid() || !isLocalOwner(playerRef)) return;
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) player.getPageManager().openCustomPage(ref, store,
                        new SaveContentTransferPage(playerRef, source, targets, inventory));
                });
            } catch (Exception e) {
                playerRef.sendMessage(Message.translation("aetherhaven_transfer.aetherhaven.transfer.failed").param("reason", e.getMessage()));
            }
        });
    }
}
