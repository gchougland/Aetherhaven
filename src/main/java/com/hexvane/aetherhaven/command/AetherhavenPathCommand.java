package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.pathtool.PathCommitRecord;
import com.hexvane.aetherhaven.pathtool.PathToolRegistry;
import com.hexvane.aetherhaven.pathtool.PathToolPersistence;
import com.hexvane.aetherhaven.pathtool.PathToolRestoreService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.pathtool.PathNavViz;

/**
 * Reverts a cemented path by id (restores the undo snapshot and removes the record on disk) and path-nav debug
 * helpers.
 */
public final class AetherhavenPathCommand extends AbstractCommandCollection {
    public AetherhavenPathCommand() {
        super("path", "aetherhaven_items.commands.aetherhaven.path.root.desc");
        this.addSubCommand(new RevertCommand());
        this.addSubCommand(new NavVizCommand());
    }

    private static final class RevertCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "aetherhaven_items.commands.aetherhaven.path.revert.id", ArgTypes.STRING);

        RevertCommand() {
            super("revert", "aetherhaven_items.commands.aetherhaven.path.revert.desc");
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
            Player pl = store.getComponent(ref, Player.getComponentType());
            if (pl == null) {
                return;
            }
            if (!pl.hasPermission(AetherhavenConstants.PERMISSION_PATH_REVERT)) {
                playerRef.sendMessage(Message.translation("aetherhaven_items.aetherhaven.pathTool.revertNoPerm"));
                return;
            }
            @Nonnull
            String s = context.get(idArg).trim();
            UUID u;
            try {
                u = UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_items.aetherhaven.pathTool.badUuid")
                        .param("raw", s)
                );
                return;
            }
            PathToolRegistry reg = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
            PathCommitRecord r = reg.remove(u);
            if (r == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_items.aetherhaven.pathTool.unknownId"));
                return;
            }
            int n = PathToolRestoreService.restoreAndRemove(world, r);
            AetherhavenWorldRegistries.getOrCreatePathNavGraphService(world).rebuildAll(reg, plugin.getConfig().get());
            PathToolPersistence.save(world, plugin, reg);
            playerRef.sendMessage(
                Message.translation("aetherhaven_items.aetherhaven.pathTool.reverted")
                    .param("id", s)
                    .param("cells", String.valueOf(n))
            );
        }
    }

    private static final class NavVizCommand extends AbstractPlayerCommand {
        NavVizCommand() {
            super("navviz", "aetherhaven_items.commands.aetherhaven.path.navviz.desc");
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
            Player pl = store.getComponent(ref, Player.getComponentType());
            if (pl == null) {
                return;
            }
            if (!pl.hasPermission(AetherhavenConstants.PERMISSION_PATH_TOOL)) {
                playerRef.sendMessage(Message.translation("aetherhaven_items.aetherhaven.pathTool.navvizNoPerm"));
                return;
            }
            @Nullable
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            UUID id = uc.getUuid();
            PathNavViz.toggle(plugin, world, store, ref, pl, playerRef);
            if (PathNavViz.isOn(id)) {
                playerRef.sendMessage(Message.translation("aetherhaven_items.aetherhaven.pathTool.navvizOn"));
            } else {
                playerRef.sendMessage(Message.translation("aetherhaven_items.aetherhaven.pathTool.navvizOff"));
            }
        }
    }
}
