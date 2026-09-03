package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.jewelry.JewelryCraftingItems;
import com.hexvane.aetherhaven.jewelry.JewelryGem;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryRarity;
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
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Admin helpers for jewelry. */
public final class AetherhavenJewelryCommand extends AbstractCommandCollection {
    private static final String LANG = "aetherhaven_jewelry_geode.aetherhaven.jewelry.command.";

    public AetherhavenJewelryCommand() {
        super("jewelry", "aetherhaven_commands_help.commands.aetherhaven.jewelry.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new GiveCommand());
    }

    private static final class GiveCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> pieceArg =
            this.withRequiredArg(
                "piece",
                "aetherhaven_commands_help.commands.aetherhaven.jewelry.give.piece.desc",
                AetherhavenArgTypes.JEWELRY_PIECE
            );
        @Nonnull
        private final RequiredArg<String> metalArg =
            this.withRequiredArg(
                "metal",
                "aetherhaven_commands_help.commands.aetherhaven.jewelry.give.metal.desc",
                AetherhavenArgTypes.JEWELRY_METAL
            );
        @Nonnull
        private final RequiredArg<String> gemArg =
            this.withRequiredArg(
                "gem",
                "aetherhaven_commands_help.commands.aetherhaven.jewelry.give.gem.desc",
                AetherhavenArgTypes.JEWELRY_GEM
            );
        @Nonnull
        private final RequiredArg<String> rarityArg =
            this.withRequiredArg(
                "rarity",
                "aetherhaven_commands_help.commands.aetherhaven.jewelry.give.rarity.desc",
                AetherhavenArgTypes.JEWELRY_RARITY
            );
        @Nonnull
        private final OptionalArg<String> playerArg =
            this.withOptionalArg(
                "player",
                "aetherhaven_commands_help.commands.aetherhaven.jewelry.give.player.desc",
                AetherhavenArgTypes.ONLINE_PLAYER_NAME
            );
        @Nonnull
        private final OptionalArg<Integer> amountArg =
            this.withOptionalArg(
                "amount",
                "aetherhaven_commands_help.commands.aetherhaven.jewelry.give.amount.desc",
                ArgTypes.INTEGER
            );

        GiveCommand() {
            super("give", "aetherhaven_commands_help.commands.aetherhaven.jewelry.give.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            String pieceRaw = context.get(pieceArg).trim();
            String metalRaw = context.get(metalArg).trim();
            String gemRaw = context.get(gemArg).trim();
            String rarityRaw = context.get(rarityArg).trim();

            boolean necklace = pieceRaw.equalsIgnoreCase("necklace");
            if (!necklace && !pieceRaw.equalsIgnoreCase("ring")) {
                playerRef.sendMessage(Message.translation(LANG + "badPiece"));
                return;
            }
            boolean gold = metalRaw.equalsIgnoreCase("gold");
            if (!gold && !metalRaw.equalsIgnoreCase("silver")) {
                playerRef.sendMessage(Message.translation(LANG + "badMetal"));
                return;
            }
            JewelryGem gem = parseGem(gemRaw);
            if (gem == null) {
                playerRef.sendMessage(Message.translation(LANG + "badGem").param("gem", gemRaw));
                return;
            }
            JewelryRarity rarity = parseRarity(rarityRaw);
            if (rarity == null) {
                playerRef.sendMessage(Message.translation(LANG + "badRarity").param("rarity", rarityRaw));
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

            String itemId = JewelryCraftingItems.outputItemId(necklace, gold, gem);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < amount; i++) {
                ItemStack stack = JewelryMetadata.rollCraftedAppraised(itemId, rarity, rnd);
                Player.giveItem(stack, targetEntityRef, targetStore);
            }
            playerRef.sendMessage(
                Message.translation(LANG + "gave")
                    .param("amount", amount)
                    .param("rarity", Message.translation("aetherhaven_jewelry_geode.aetherhaven.jewelry.rarity." + rarity.wireName()))
                    .param("piece", pieceLabel(necklace, gold, gem))
                    .param("player", targetRef.getUsername())
            );
        }

        @Nullable
        private static JewelryGem parseGem(@Nonnull String raw) {
            try {
                return JewelryGem.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Nullable
        private static JewelryRarity parseRarity(@Nonnull String raw) {
            String key = raw.trim().toUpperCase();
            if ("EPIC".equals(key)) {
                return JewelryRarity.MYTHIC;
            }
            try {
                return JewelryRarity.valueOf(key);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Nonnull
        private static String pieceLabel(boolean necklace, boolean gold, @Nonnull JewelryGem gem) {
            String metal = gold ? "Gold" : "Silver";
            String kind = necklace ? "Necklace" : "Ring";
            String gemName = gem.name().charAt(0) + gem.name().substring(1).toLowerCase();
            return metal + " " + kind + " (" + gemName + ")";
        }
    }
}
