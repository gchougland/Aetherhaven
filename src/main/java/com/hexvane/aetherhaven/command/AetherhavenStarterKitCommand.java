package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Grants core town-building items for testing or creative playthroughs. */
public final class AetherhavenStarterKitCommand extends AbstractPlayerCommand {
    private static final String TOWN_PLANNING_DESK_ITEM_ID = "Aetherhaven_Town_Planning_Desk";

    public AetherhavenStarterKitCommand() {
        super("starterkit", "aetherhaven_commands_root.commands.aetherhaven.starterkit.desc");
        this.addAliases("starter");
        this.setPermissionGroup(GameMode.Creative);
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
        ItemStack[] stacks = {
            new ItemStack(AetherhavenConstants.PLOT_PLACEMENT_TOOL_ITEM_ID, 1),
            new ItemStack(AetherhavenConstants.CHARTER_ITEM_ID, 1),
            new ItemStack(TOWN_PLANNING_DESK_ITEM_ID, 1),
            new ItemStack(AetherhavenConstants.BUILDING_STAFF_ITEM_ID, 1),
        };
        int failed = 0;
        for (ItemStack stack : stacks) {
            ItemStackTransaction tx = player.giveItem(stack, ref, store);
            if (!tx.succeeded()) {
                failed++;
            }
        }
        if (failed == 0) {
            playerRef.sendMessage(Message.translation("aetherhaven_commands_root.commands.aetherhaven.starterkit.success"));
        } else {
            playerRef.sendMessage(
                Message.translation("aetherhaven_commands_root.commands.aetherhaven.starterkit.partial").param("failed", String.valueOf(failed))
            );
        }
    }
}
