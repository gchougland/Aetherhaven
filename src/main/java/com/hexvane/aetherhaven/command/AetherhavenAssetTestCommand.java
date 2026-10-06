package com.hexvane.aetherhaven.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.npc.config.balancing.BalanceAsset;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/** Read-only checks of assets as decoded by the running server. */
final class AetherhavenAssetTestCommand extends AbstractAsyncCommand {
    AetherhavenAssetTestCommand() {
        super("assets", "Check loaded civilian protection and snowball interaction prompt assets");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        int checked = 0;
        for (var entry : BalanceAsset.getAssetMap().getAssetMap().entrySet()) {
            if (!entry.getKey().startsWith("*Aetherhaven_") || !entry.getKey().endsWith("_CombatConfig")) continue;
            String expected = "Immunity_Environmental";
            if (!Arrays.asList(entry.getValue().getEntityEffects()).contains(expected))
                throw new IllegalStateException("Missing authored protection " + expected + ": " + entry.getKey());
            checked++;
        }
        if (checked == 0) throw new IllegalStateException("No civilian protection assets loaded");
        var snowballs = BlockType.getAssetMap().getAsset("Aetherhaven_Snowball_Pile");
        if (snowballs == null || !"aetherhaven_festivals.aetherhaven.festival.interactionHints.takeSnowballs".equals(snowballs.getInteractionHint()))
            throw new IllegalStateException("Snowball interaction prompt did not load");
        String result = "Loaded asset check passed: " + checked + " civilian protection configs and the snowball interaction prompt.";
        ctx.sendMessage(Message.raw(result));
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
        return CompletableFuture.completedFuture(null);
    }
}
