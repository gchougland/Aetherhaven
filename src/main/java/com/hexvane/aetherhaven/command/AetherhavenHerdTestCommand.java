package com.hexvane.aetherhaven.command;
import com.hexvane.aetherhaven.production.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.concurrent.CompletableFuture;
final class AetherhavenHerdTestCommand extends AbstractCommandCollection {
    AetherhavenHerdTestCommand(){super("herds","Check Barn animal assets");setPermissionGroups("hytale:WorldEditor");addSubCommand(new WorldCheck());}
    private static final class WorldCheck extends AbstractAsyncCommand {
        WorldCheck(){super("worldcheck","Check animal roles, foods and resource assets");setPermissionGroups("hytale:WorldEditor");}
        @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            for(var species:BarnAnimalCatalog.all()) {
                require(NPCPlugin.get().getIndex(species.role())>=0,"Missing role "+species.role());
                require(ModelAsset.getAssetMap().getAsset(species.model())!=null,"Missing model "+species.model());
                require(Item.getAssetMap().getAsset(species.food())!=null,"Missing food "+species.food());
                for(String product:species.products().keySet())require(Item.getAssetMap().getAsset(product)!=null,"Missing product "+product);
                var herd=new HerdProductionState();require(herd.buy(species.id(),10,()->true)==null,"Purchase failed");
                var animal=herd.animals().getFirst();require(herd.assign(0,animal.id(),1),"Assignment failed");
                require(herd.supply(species.food(),()->true)&&herd.feedAnimal(animal.id(),1,1),"Feeding failed");
                require(!herd.feedAnimal(animal.id(),1,1),"Duplicate feeding");
            }
            String result="Barn assets PASSED: all 15 species, foods, products and daily feeding.";
            ctx.sendMessage(Message.raw(result));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
            return CompletableFuture.completedFuture(null);
        }
    }
    private static void require(boolean yes,String issue){if(!yes)throw new IllegalStateException(issue);}
}
