package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteCatalog;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteDefinition;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteItemMetadata;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteUnlockService;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
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
import javax.annotation.Nonnull;

/** Admin helpers for block palettes. */
public final class AetherhavenPaletteCommand extends AbstractCommandCollection {
    private static final String LANG = "aetherhaven_block_palettes.aetherhaven.blockPalette.command.";

    public AetherhavenPaletteCommand() {
        super("palette", "aetherhaven_commands_help.commands.aetherhaven.palette.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new GiveCommand());
        this.addSubCommand(new UnlockAllCommand());
    }

    private static final class GiveCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> paletteIdArg =
            this.withRequiredArg(
                "paletteId",
                "aetherhaven_commands_help.commands.aetherhaven.palette.give.paletteId.desc",
                AetherhavenArgTypes.BLOCK_PALETTE_ID
            );
        @Nonnull
        private final OptionalArg<String> playerArg =
            this.withOptionalArg(
                "player",
                "aetherhaven_commands_help.commands.aetherhaven.palette.give.player.desc",
                AetherhavenArgTypes.ONLINE_PLAYER_NAME
            );
        @Nonnull
        private final OptionalArg<Integer> amountArg =
            this.withOptionalArg(
                "amount",
                "aetherhaven_commands_help.commands.aetherhaven.palette.give.amount.desc",
                ArgTypes.INTEGER
            );

        GiveCommand() {
            super("give", "aetherhaven_commands_help.commands.aetherhaven.palette.give.desc");
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
            String paletteId = context.get(paletteIdArg).trim();
            BlockPaletteCatalog catalog = plugin.getBlockPaletteCatalog();
            BlockPaletteDefinition def = catalog.get(paletteId);
            if (def == null) {
                playerRef.sendMessage(Message.translation(LANG + "unknown").param("id", paletteId));
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
            ItemStack stack = BlockPaletteItemMetadata.createStack(def, amount);
            Player.giveItem(stack, targetEntityRef, targetStore);
            playerRef.sendMessage(
                Message.translation(LANG + "gave")
                    .param("amount", amount)
                    .param("name", def.getDisplayName())
                    .param("player", targetRef.getUsername())
            );
        }
    }

    private static final class UnlockAllCommand extends AbstractPlayerCommand {
        UnlockAllCommand() {
            super("unlockall", "aetherhaven_commands_help.commands.aetherhaven.palette.unlockall.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            int added = BlockPaletteUnlockService.unlockAllForPlayerTown(ref, store, playerRef);
            if (added < 0) {
                playerRef.sendMessage(Message.translation(LANG + "noTown"));
                return;
            }
            if (added == 0) {
                playerRef.sendMessage(Message.translation(LANG + "unlockall.noneLeft"));
                return;
            }
            playerRef.sendMessage(Message.translation(LANG + "unlockall.success").param("count", added));
        }
    }
}
