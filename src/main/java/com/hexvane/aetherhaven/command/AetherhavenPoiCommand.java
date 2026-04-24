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
                        playerRef.sendMessage(
                            Message.translation("server.aetherhaven.debug.poi.invalidTownUuid").param("raw", raw)
                        );
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
                    playerRef.sendMessage(Message.translation("server.aetherhaven.debug.poi.noTownArg"));
                    return;
                }
                townUuid = tr.getTownId();
            }
            List<PoiEntry> list = reg.listByTown(townUuid);
            playerRef.sendMessage(
                Message.translation("server.aetherhaven.debug.poi.listHeader")
                    .param("town", townUuid.toString())
                    .param("count", String.valueOf(list.size()))
            );
            if (list.isEmpty()) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.debug.poi.listEmpty"));
                return;
            }
            for (PoiEntry e : list) {
                playerRef.sendMessage(
                    Message.translation("server.aetherhaven.debug.poi.listLine")
                        .param("x", String.valueOf(e.getX()))
                        .param("y", String.valueOf(e.getY()))
                        .param("z", String.valueOf(e.getZ()))
                        .param("kind", String.valueOf(e.getInteractionKind()))
                        .param("block", e.getBlockTypeId() != null ? e.getBlockTypeId() : "")
                        .param("tags", e.getTags() != null ? e.getTags().toString() : "")
                        .param("cap", String.valueOf(e.getCapacity()))
                        .param("plot", e.getPlotId() != null ? e.getPlotId().toString() : "")
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
            playerRef.sendMessage(
                Message.translation("server.aetherhaven.debug.poi.dumpHeader")
                    .param("count", String.valueOf(all.size()))
            );
            for (PoiEntry e : all) {
                playerRef.sendMessage(
                    Message.translation("server.aetherhaven.debug.poi.dumpLine")
                        .param("town", e.getTownId() != null ? e.getTownId().toString() : "")
                        .param("x", String.valueOf(e.getX()))
                        .param("y", String.valueOf(e.getY()))
                        .param("z", String.valueOf(e.getZ()))
                        .param("kind", String.valueOf(e.getInteractionKind()))
                        .param("block", e.getBlockTypeId() != null ? e.getBlockTypeId() : "")
                        .param("tags", e.getTags() != null ? e.getTags().toString() : "")
                        .param("plot", e.getPlotId() != null ? e.getPlotId().toString() : "")
                );
            }
        }
    }
}
