package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.questboard.FetchQuestPayment;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/** Read the actual merged quest and price catalogs without changing any town's offers or gold. */
final class AetherhavenQuestEconomyTestCommand extends AbstractAsyncCommand {
    AetherhavenQuestEconomyTestCommand() {
        super("questpayments", "Check material payments for all loaded fetch quest choices");
        setPermissionGroups("hytale:WorldEditor");
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var plugin=AetherhavenPlugin.get();
        if(plugin==null)throw new IllegalStateException("Aetherhaven is unavailable");
        var prices=plugin.getShopPriceCatalog();
        int entries=0,choices=0;long lowest=Long.MAX_VALUE,highest=0;
        var fallback=new TreeSet<String>();
        for(var villager:plugin.getQuestBoardCatalog().villagers().entrySet()) {
            for(var entry:villager.getValue().fetchEntriesOrEmpty()) {
                if(entry.materialPaymentBonusGold()==0)continue;
                entries++;
                for(var set:entry.itemSetsOrEmpty()) {
                    for(var item:set.itemsOrEmpty()) {
                        if(Item.getAssetMap().getAsset(item.itemIdOrEmpty())==null)
                            throw new IllegalStateException("Missing quest item: "+item.itemIdOrEmpty());
                        if(!prices.hasExplicitPrice(item.itemIdOrEmpty()))fallback.add(item.itemIdOrEmpty());
                    }
                    var rewards=FetchQuestPayment.quote(entry,set.itemsOrEmpty(),prices)
                        .orElseThrow(()->new IllegalStateException("Unrepresentable quest payment: "+entry.id()));
                    long gold=rewards.stream().filter(r->"item".equalsIgnoreCase(r.kind())&&"Aetherhaven_Gold_Coin".equals(r.itemId()))
                        .mapToLong(r->r.count()).sum();
                    lowest=Math.min(lowest,gold);highest=Math.max(highest,gold);choices++;
                    if("recruit_gear".equals(entry.id())||"stone_haul".equals(entry.id())||"bread_loaves".equals(entry.id()))
                        report(ctx,entry.id()+": quoted "+gold+" gold.");
                }
            }
        }
        if(choices==0)throw new IllegalStateException("No material payment choices loaded");
        report(ctx,"Quest payment check passed: "+entries+" entries, "+choices+" choices, "+lowest+" to "+highest+" gold. Default-price items: "+fallback);
        return CompletableFuture.completedFuture(null);
    }
    private static void report(CommandContext ctx,String message) {
        ctx.sendMessage(Message.raw(message));
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);
    }
}
