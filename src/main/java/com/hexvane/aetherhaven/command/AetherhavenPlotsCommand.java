package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Lists plot instances for the caller's town. Named {@code plots} to avoid clashing with {@code /aetherhaven plot}
 * (plot sign admin UI).
 */
public final class AetherhavenPlotsCommand extends AbstractCommandCollection {
    public AetherhavenPlotsCommand() {
        super("plots", "server.commands.aetherhaven.plots.desc");
        this.addSubCommand(new ListCommand());
    }

    private static final class ListCommand extends AbstractPlayerCommand {
        ListCommand() {
            super("list", "server.commands.aetherhaven.plots.list.desc");
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
            if (!AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town =
                AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForOwnerInWorld(uc.getUuid());
            if (town == null) {
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            playerRef.sendMessage(Message.raw("Plots for town " + town.getTownId() + ":"));
            for (PlotInstance p : town.getPlotInstances()) {
                playerRef.sendMessage(
                    Message.raw(
                        "  "
                            + p.getPlotId()
                            + " "
                            + p.getConstructionId()
                            + " "
                            + p.getState()
                            + " sign@"
                            + p.getSignX()
                            + ","
                            + p.getSignY()
                            + ","
                            + p.getSignZ()
                    )
                );
            }
        }
    }
}
