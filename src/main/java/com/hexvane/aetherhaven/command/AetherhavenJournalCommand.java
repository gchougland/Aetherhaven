package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.ui.QuestJournalPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Opens the Town Journal UI without requiring the journal item. */
public final class AetherhavenJournalCommand extends AbstractPlayerCommand {
    public AetherhavenJournalCommand() {
        super("journal", "aetherhaven_commands_root.commands.aetherhaven.journal.desc");
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (player.getPageManager().getCustomPage() != null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new QuestJournalPage(playerRef));
    }
}
