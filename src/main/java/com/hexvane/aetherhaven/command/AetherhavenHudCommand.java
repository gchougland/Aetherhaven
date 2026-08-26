package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.hud.AetherhavenHudSupport;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Toggles the complete personal Aetherhaven HUD without changing its detailed settings. */
public final class AetherhavenHudCommand extends AbstractPlayerCommand {
    public AetherhavenHudCommand() {
        super("hud", "aetherhaven_commands_help.commands.aetherhaven.hud.desc");
        requireNoPermission();
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        PlayerTownJournalState state =
            store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (state == null) {
            state = new PlayerTownJournalState();
        }
        boolean enabled = !state.isHudEnabled();
        state.setHudEnabled(enabled);
        store.putComponent(ref, PlayerTownJournalState.getComponentType(), state);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            if (enabled) {
                AetherhavenHudSupport.obtain(player, playerRef);
            } else if (AetherhavenHudSupport.isActive(player)) {
                AetherhavenHudSupport.remove(player, playerRef);
            }
        }
        playerRef.sendMessage(
            Message.translation(
                enabled
                    ? "aetherhaven_commands_help.aetherhaven.hud.enabled"
                    : "aetherhaven_commands_help.aetherhaven.hud.disabled"
            )
        );
    }
}
