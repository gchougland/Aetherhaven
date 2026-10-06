package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.monument.FounderMonumentSpawnService;
import com.hexvane.aetherhaven.monument.FounderMonumentStatueSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** Read-only native cosmetic resolution and saved-skin round-trip checks. */
final class AetherhavenFounderAssetsTestCommand extends AbstractAsyncCommand {
    AetherhavenFounderAssetsTestCommand() {
        super("founderassets", "Check Founder statue cosmetics against the loaded game assets");
        setPermissionGroups("hytale:WorldEditor");
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var block=com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap().getAsset("Aetherhaven_Founder_Monument");
        ctx.sendMessage(Message.raw("Founder loaded block: model="+(block==null?"missing":block.getCustomModel())
            +" blockEntity="+(block==null?"missing":block.getBlockEntity())));
        var cosmetics=CosmeticsModule.get();
        var random=new Random(4040);
        int count=0;
        for(int i=0;i<24;i++) {
            var skin=cosmetics.generateRandomSkin(random);
            var saved=FounderMonumentStatueSkin.fromProtocol(skin).tryToProtocol();
            var model=saved==null?null:FounderMonumentSpawnService.buildMonumentModel(saved);
            if(model==null||model.getAttachments()==null||model.getAttachments().length==0)
                throw new IllegalStateException("Founder appearance failed for native skin sample "+i);
            if(model.toPacket().attachments==null||model.toPacket().attachments.length!=model.getAttachments().length)
                throw new IllegalStateException("Founder model packet lost attachments for sample "+i);
            count+=model.getAttachments().length;
        }
        String result="Founder asset check PASSED: 24 native skins, saved skin round trips and "+count+" stone attachments retained in model packets.";
        ctx.sendMessage(Message.raw(result));
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
        return CompletableFuture.completedFuture(null);
    }
}
