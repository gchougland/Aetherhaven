package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.difficulty.DifficultyAccess;
import com.hexvane.aetherhaven.difficulty.DifficultyResolver;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.DifficultyPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class AetherhavenDifficultyCommand extends AbstractPlayerCommand {
    private static final String MSG = "aetherhaven_difficulty.aetherhaven.difficulty";

    public AetherhavenDifficultyCommand() {
        super("difficulty", "aetherhaven_commands_root.commands.aetherhaven.difficulty.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new ServerSubcommand());
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
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (player == null || uc == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        boolean admin = TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
        if (DifficultyResolver.isForced() && admin) {
            if (player.getPageManager().getCustomPage() != null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store, DifficultyPage.forServer(playerRef));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownCommandResolution res = TownCommandResolution.resolveForOwnerAction(tm, uc.getUuid(), null, admin);
        if (!res.isOk()) {
            playerRef.sendMessage(res.error());
            return;
        }
        TownRecord town = res.townOrThrow();
        if (!DifficultyAccess.canChangeDifficulty(tm, uc.getUuid(), town, admin)) {
            playerRef.sendMessage(Message.translation(MSG + ".ownersOnly"));
            return;
        }
        if (DifficultyResolver.isForced()) {
            playerRef.sendMessage(Message.translation(MSG + ".serverLocked"));
            return;
        }
        if (player.getPageManager().getCustomPage() != null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new DifficultyPage(playerRef, town.getTownId()));
    }

    private static final class ServerSubcommand extends AbstractPlayerCommand {
        private ServerSubcommand() {
            super("server", "aetherhaven_commands_root.commands.aetherhaven.difficulty.desc");
            this.setPermissionGroups("hytale:WorldEditor");
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
            if (!TownPermissionUtil.canAdministerForeignTowns(player, playerRef)) {
                playerRef.sendMessage(Message.translation(MSG + ".ownersOnly"));
                return;
            }
            if (player.getPageManager().getCustomPage() != null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store, DifficultyPage.forServer(playerRef));
        }
    }
}
