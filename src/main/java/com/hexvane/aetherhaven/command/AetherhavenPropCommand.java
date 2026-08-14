package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.prop.PropBreakProtection;
import com.hexvane.aetherhaven.prop.PropCatalog;
import com.hexvane.aetherhaven.prop.PropConstants;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.prop.PropItemMetadata;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.ui.PropPrefabBrowserPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Admin helpers for the props system: give items, hand out the packaging wand, browse prefabs, toggle break protection. */
public final class AetherhavenPropCommand extends AbstractCommandCollection {
    private static final String LANG = "aetherhaven_props.aetherhaven.prop.command.";

    public AetherhavenPropCommand() {
        super("prop", "aetherhaven_commands_help.commands.aetherhaven.prop.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new GiveCommand());
        this.addSubCommand(new WandCommand());
        this.addSubCommand(new CreateCommand());
        this.addSubCommand(new BreakCommand());
    }

    private static final class GiveCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> propIdArg =
            this.withRequiredArg("propId", "aetherhaven_commands_help.commands.aetherhaven.prop.give.propId.desc", AetherhavenArgTypes.PROP_ID);
        @Nonnull
        private final OptionalArg<String> playerArg =
            this.withOptionalArg("player", "aetherhaven_commands_help.commands.aetherhaven.prop.give.player.desc", AetherhavenArgTypes.ONLINE_PLAYER_NAME);
        @Nonnull
        private final OptionalArg<Integer> amountArg =
            this.withOptionalArg("amount", "aetherhaven_commands_help.commands.aetherhaven.prop.give.amount.desc", ArgTypes.INTEGER);

        GiveCommand() {
            super("give", "aetherhaven_commands_help.commands.aetherhaven.prop.give.desc");
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
            String propId = context.get(propIdArg).trim();
            PropCatalog catalog = plugin.getPropCatalog();
            PropDefinition def = catalog.get(propId);
            if (def == null) {
                playerRef.sendMessage(Message.translation(LANG + "unknown").param("id", propId));
                return;
            }
            int amount = context.provided(amountArg) ? Math.max(1, Math.min(64, context.get(amountArg))) : 1;

            PlayerRef targetRef = playerRef;
            if (context.provided(playerArg)) {
                String targetName = context.get(playerArg).trim();
                PlayerRef found = TownPlayerLookup.findOnlinePlayerByUsername(world, targetName);
                if (found == null) {
                    playerRef.sendMessage(Message.translation(LANG + "playerNotFound").param("name", targetName));
                    return;
                }
                targetRef = found;
            }
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            if (targetEntityRef == null || !targetEntityRef.isValid()) {
                playerRef.sendMessage(Message.translation(LANG + "playerNotFound").param("name", targetRef.getUsername()));
                return;
            }
            Store<EntityStore> targetStore = targetEntityRef.getStore();
            Player targetPlayer = targetStore.getComponent(targetEntityRef, Player.getComponentType());
            if (targetPlayer == null) {
                return;
            }
            ItemStack stack = PropItemMetadata.createStack(def, amount);
            Player.giveItem(stack, targetEntityRef, targetStore);
            playerRef.sendMessage(
                Message.translation(LANG + "gave").param("amount", amount).param("name", def.getDisplayName()).param("player", targetRef.getUsername())
            );
        }
    }

    private static final class WandCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> playerArg =
            this.withOptionalArg("player", "aetherhaven_commands_help.commands.aetherhaven.prop.wand.player.desc", AetherhavenArgTypes.ONLINE_PLAYER_NAME);

        WandCommand() {
            super("wand", "aetherhaven_commands_help.commands.aetherhaven.prop.wand.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            PlayerRef targetRef = playerRef;
            if (context.provided(playerArg)) {
                String targetName = context.get(playerArg).trim();
                PlayerRef found = TownPlayerLookup.findOnlinePlayerByUsername(world, targetName);
                if (found == null) {
                    playerRef.sendMessage(Message.translation(LANG + "playerNotFound").param("name", targetName));
                    return;
                }
                targetRef = found;
            }
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            if (targetEntityRef == null || !targetEntityRef.isValid()) {
                playerRef.sendMessage(Message.translation(LANG + "playerNotFound").param("name", targetRef.getUsername()));
                return;
            }
            Store<EntityStore> targetStore = targetEntityRef.getStore();
            Player targetPlayer = targetStore.getComponent(targetEntityRef, Player.getComponentType());
            if (targetPlayer == null) {
                return;
            }
            Player.giveItem(new ItemStack(PropConstants.PACKAGING_WAND_ITEM_ID, 1), targetEntityRef, targetStore);
            playerRef.sendMessage(Message.translation(LANG + "gaveWand").param("player", targetRef.getUsername()));
        }
    }

    private static final class CreateCommand extends AbstractPlayerCommand {
        CreateCommand() {
            super("create", "aetherhaven_commands_help.commands.aetherhaven.prop.create.desc");
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
            player.getPageManager().openCustomPage(ref, store, new PropPrefabBrowserPage(playerRef));
        }
    }

    private static final class BreakCommand extends AbstractPlayerCommand {
        BreakCommand() {
            super("break", "aetherhaven_commands_help.commands.aetherhaven.prop.break.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!playerRef.hasPermission(AetherhavenConstants.PERMISSION_PROP_BREAK, false)) {
                playerRef.sendMessage(Message.translation(LANG + "break.noPermission"));
                return;
            }
            UUID uuid = playerRef.getUuid();
            if (uuid == null) {
                return;
            }
            boolean allowed = PropBreakProtection.toggleBreakAllowed(uuid);
            playerRef.sendMessage(Message.translation(allowed ? LANG + "break.enabled" : LANG + "break.disabled"));
        }
    }
}
