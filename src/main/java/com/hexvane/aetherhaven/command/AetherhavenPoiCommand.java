package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class AetherhavenPoiCommand extends AbstractCommandCollection {
    public AetherhavenPoiCommand() {
        super("poi", "server.commands.aetherhaven.poi.desc");
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new DumpCommand());
    }

    private static final class ListCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townFilterArg =
            this.withOptionalArg("town", "server.commands.aetherhaven.poi.town.desc", ArgTypes.STRING);

        ListCommand() {
            super("list", "server.commands.aetherhaven.poi.list.desc");
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
            var reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            UUID townUuid = null;
            if (context.provided(townFilterArg)) {
                String raw = context.get(townFilterArg);
                if (raw != null && !raw.isBlank() && !raw.equalsIgnoreCase("me")) {
                    try {
                        townUuid = UUID.fromString(raw.trim());
                    } catch (IllegalArgumentException e) {
                        playerRef.sendMessage(Message.raw("Invalid town UUID: " + raw));
                        return;
                    }
                }
            }
            if (townUuid == null) {
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc == null) {
                    return;
                }
                TownRecord tr =
                    AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
                if (tr == null) {
                    playerRef.sendMessage(Message.raw("You have no town in this world (use a town UUID argument)."));
                    return;
                }
                townUuid = tr.getTownId();
            }
            List<PoiEntry> list = reg.listByTown(townUuid);
            playerRef.sendMessage(Message.raw("POIs for town " + townUuid + " (" + list.size() + "):"));
            if (list.isEmpty()) {
                playerRef.sendMessage(Message.raw("None registered for this town (complete a building or check pois.json)."));
                return;
            }
            for (PoiEntry e : list) {
                playerRef.sendMessage(
                    Message.raw(
                        "  "
                            + e.getX()
                            + ","
                            + e.getY()
                            + ","
                            + e.getZ()
                            + " kind="
                            + e.getInteractionKind()
                            + " blockTypeId="
                            + e.getBlockTypeId()
                            + " tags="
                            + e.getTags()
                            + " cap="
                            + e.getCapacity()
                            + " plot="
                            + e.getPlotId()
                    )
                );
            }
        }
    }

    private static final class DumpCommand extends AbstractPlayerCommand {
        DumpCommand() {
            super("dump", "server.commands.aetherhaven.poi.dump.desc");
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
            List<PoiEntry> all = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin).allEntries();
            playerRef.sendMessage(Message.raw("All POIs in world registry (" + all.size() + "):"));
            for (PoiEntry e : all) {
                playerRef.sendMessage(
                    Message.raw(
                        "  town="
                            + e.getTownId()
                            + " "
                            + e.getX()
                            + ","
                            + e.getY()
                            + ","
                            + e.getZ()
                            + " kind="
                            + e.getInteractionKind()
                            + " blockTypeId="
                            + e.getBlockTypeId()
                            + " tags="
                            + e.getTags()
                            + " plot="
                            + e.getPlotId()
                    )
                );
            }
        }
    }
}
