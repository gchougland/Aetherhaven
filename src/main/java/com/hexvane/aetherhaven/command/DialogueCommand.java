package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.DialoguePage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class DialogueCommand extends AbstractPlayerCommand {
    @Nonnull
    private final RequiredArg<String> treeArg =
        this.withRequiredArg("treeId", "server.commands.aetherhaven.dialogue.treeId.desc", ArgTypes.STRING);
    @Nonnull
    private final OptionalArg<String> entryArg =
        this.withOptionalArg("entryNode", "server.commands.aetherhaven.dialogue.entry.desc", ArgTypes.STRING);

    public DialogueCommand() {
        super("dialogue", "server.commands.aetherhaven.dialogue.desc");
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
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        String treeId = context.get(this.treeArg);
        String entry = "root";
        if (context.provided(this.entryArg)) {
            String e = context.get(this.entryArg);
            if (e != null && !e.isBlank()) {
                entry = e.trim();
            }
        }
        player
            .getPageManager()
            .openCustomPage(
                ref,
                store,
                new DialoguePage(
                    playerRef,
                    plugin.getDialogueCatalog(),
                    plugin.getDialogueWorldView(),
                    treeId,
                    entry,
                    null
                )
            );
    }
}
