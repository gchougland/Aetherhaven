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
                AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noTownInWorld"));
                return;
            }
            playerRef.sendMessage(
                Message.translation("server.aetherhaven.debug.plots.forTown").param("id", town.getTownId().toString())
            );
            for (PlotInstance p : town.getPlotInstances()) {
                playerRef.sendMessage(
                    Message.translation("server.aetherhaven.debug.plots.line")
                        .param("plotId", p.getPlotId().toString())
                        .param("construction", p.getConstructionId() != null ? p.getConstructionId() : "")
                        .param("state", p.getState() != null ? p.getState().name() : "")
                        .param("x", String.valueOf(p.getSignX()))
                        .param("y", String.valueOf(p.getSignY()))
                        .param("z", String.valueOf(p.getSignZ()))
                );
            }
        }
    }
}
