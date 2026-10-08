package com.hexvane.aetherhaven.command;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.monument.FounderMonumentSpawnService;
import com.hexvane.aetherhaven.monument.FounderMonumentStatueSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** Read-only checks of native skin preservation using loaded cosmetics and the saved-skin round trip. */
final class AetherhavenFounderAssetsTestCommand extends AbstractAsyncCommand {
    AetherhavenFounderAssetsTestCommand() {
        super("founderassets", "Check Founder statue cosmetics against the loaded game assets");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var model = FounderMonumentSpawnService.buildFallbackModel();
        if (model == null || !"Player".equals(model.getModelAssetId())) {
            throw new IllegalStateException("Founder statue must use the native Player model");
        }
        var cosmetics = CosmeticsModule.get();
        var random = new Random(4040);
        var gson = new Gson();
        for (int i = 0; i < 24; i++) {
            var skin = cosmetics.generateRandomSkin(random);
            var saved = FounderMonumentStatueSkin.fromProtocol(skin).tryToProtocol();
            if (saved == null) {
                throw new IllegalStateException("Founder saved skin failed for sample " + i);
            }
            var component = FounderMonumentSpawnService.createStatueSkin(saved);
            if (!gson.toJson(skin).equals(gson.toJson(component.getPlayerSkin())) || !component.consumeNetworkOutdated()) {
                throw new IllegalStateException("Founder native skin changed or cannot be sent for sample " + i);
            }
        }
        String result = "Founder asset check PASSED: 24 native skins preserved through save and restore, ready for native skin updates. No cosmetic conversion.";
        ctx.sendMessage(Message.raw(result));
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
        return CompletableFuture.completedFuture(null);
    }
}
