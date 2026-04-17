package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class AetherhavenTownsCommand extends AbstractPlayerCommand {
    public AetherhavenTownsCommand() {
        super("towns", "server.commands.aetherhaven.towns.desc");
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
            return;
        }
        var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        int n = tm.allTowns().size();
        playerRef.sendMessage(Message.raw("Aetherhaven towns in this world: " + n));
        for (TownRecord t : tm.allTowns()) {
            playerRef.sendMessage(
                Message.raw(
                    "  "
                        + t.getDisplayName()
                        + "  id="
                        + t.getTownId()
                        + " owner="
                        + t.getOwnerUuid()
                        + " charter="
                        + t.getCharterX()
                        + ","
                        + t.getCharterY()
                        + ","
                        + t.getCharterZ()
                        + " plots="
                        + t.getPlotInstances().size()
                )
            );
        }
    }
}
