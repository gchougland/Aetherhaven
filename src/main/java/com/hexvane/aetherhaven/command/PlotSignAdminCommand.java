package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.ui.PlotSignAdminPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class PlotSignAdminCommand extends AbstractPlayerCommand {
    public PlotSignAdminCommand() {
        super("plot", "server.commands.aetherhaven.plot.desc");
        this.addAliases("plotsign");
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
        PageManager pm = player.getPageManager();
        if (pm.getCustomPage() != null) {
            return;
        }
        pm.openCustomPage(ref, store, new PlotSignAdminPage(playerRef));
    }
}
