package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.halloween.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.*;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Loaded asset and isolated inventory audit. Never changes a saved town or player. */
final class AetherhavenHalloweenTestCommand extends AbstractAsyncCommand {
    AetherhavenHalloweenTestCommand(){super("halloweenassets","Check Jack's models, native prompt and hide and seek rewards");setPermissionGroups("hytale:WorldEditor");addSubCommand(new WorldCheck("worldcheck",false));addSubCommand(new WorldCheck("coffincheck",true));}
    private static final class WorldCheck extends AbstractAsyncCommand {
        private static final java.util.concurrent.atomic.AtomicBoolean RUNNING = new java.util.concurrent.atomic.AtomicBoolean();
        private final boolean coffinCheck;
        WorldCheck(String name,boolean coffinCheck){super(name,"Check native Halloween behavior in a disposable world");this.coffinCheck=coffinCheck;setPermissionGroups("hytale:WorldEditor");}
        @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            if(!RUNNING.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("A Halloween check is already running"));
            String name="aetherhaven_halloween_check_"+UUID.randomUUID().toString().replace("-","");
            var universe=com.hypixel.hytale.server.core.universe.Universe.get();var path=universe.validateWorldPath(name);
            if(java.nio.file.Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));}
            var config=new com.hypixel.hytale.server.core.universe.world.WorldConfig();
            com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
            config.setWorldGenProvider(new com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider());
            config.setChunkStorageProvider(com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider.INSTANCE);
            config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
            config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
            return universe.makeWorld(name,path,config).thenCompose(world -> (coffinCheck
                ? com.hexvane.aetherhaven.rescue.HalloweenCoffinWorldFixture.run(world) : HalloweenWorldFixture.run(world))
                .orTimeout(60,java.util.concurrent.TimeUnit.SECONDS).whenCompleteAsync((unused,error) -> {
                    universe.removeWorld(name);
                    String label=coffinCheck?"Halloween coffin check":"Halloween isolated world check";
                    String result=error==null ? label+": PASSED. "+(coffinCheck
                        ? "Four native coffin opening chains, delayed rescue, October gating, low voice resolution, duplicate rejection and departure only after quest completion."
                        : "Sealed room rejection, open doorway and native door, head switch, building move cleanup, deleted town cleanup and daily reward retention.")
                        : label+": FAILED: "+error;
                    ctx.sendMessage(Message.raw(result));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
                })).whenComplete((unused,error)->RUNNING.set(false));
        }
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var normalAsset=ModelAsset.getAssetMap().getAsset(HalloweenResident.MODEL);
        var hiddenAsset=ModelAsset.getAssetMap().getAsset(HalloweenResident.HEADLESS_MODEL);
        require(normalAsset!=null&&hiddenAsset!=null,"Missing Jack model variant");
        var normal=Model.createScaledModel(normalAsset,1);var hidden=Model.createScaledModel(hiddenAsset,1);
        require(normal.getAttachments()!=null&&normal.getAttachments().length==1,"Jack must have only the pumpkin attachment");
        require(hidden.getAttachments()==null||hidden.getAttachments().length==0,"Headless Jack inherited the native skull eyes");
        var head=Item.getAssetMap().getAsset(HalloweenGameInventory.HEAD);
        require(head!=null&&head.getMaxStack()==1,"Head item missing or stackable");
        var block=BlockType.getAssetMap().getAsset(HalloweenGameService.BLOCK);
        require(block!=null&&"aetherhaven_halloween.interaction.pickup".equals(block.getInteractionHint()),"Pumpkin native interaction prompt missing");
        var hint=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get().getMessage(
            com.hypixel.hytale.server.core.modules.i18n.I18nModule.DEFAULT_LANGUAGE,block.getInteractionHint());
        require(hint!=null&&hint.contains("[{key}]"),"Pumpkin pickup translation omits the native interaction key");
        require(block.getInteractions()!=null&&block.getInteractions().containsKey(InteractionType.Use),"Pumpkin Use interaction missing");
        var owner=UUID.randomUUID();var town=UUID.randomUUID();var state=new HalloweenGameState();
        var game=state.start(owner,100,new HalloweenGameState.Spot(UUID.randomUUID(),0,10,0,"fixture"));
        var bag=new CombinedItemContainer(new SimpleItemContainer((short)1),new SimpleItemContainer((short)2));
        require(HalloweenGameInventory.pickup(bag,HalloweenGameInventory.head(town,game),()->state.pickup(owner,game.id())),"Empty inventory pickup failed");
        var info=new com.hypixel.hytale.codec.ExtraInfo();
        var saved=ItemStack.CODEC.decode(ItemStack.CODEC.encode(bag.getItemStack((short)0),info),info);
        require(HalloweenGameInventory.matches(saved,town,game),"Native item codec lost the game receipt");
        bag.setItemStackForSlot((short)0,saved,false);
        require(HalloweenGameInventory.reward(bag,town,game,()->state.reward(owner,game.id(),100)),"Native reward exchange failed");
        require(count(bag,"Aetherhaven_Spooky_Candy")==HalloweenGameState.CANDIES&&count(bag,"Aetherhaven_Gold_Coin")==HalloweenGameState.GOLD&&count(bag,HalloweenGameInventory.HEAD)==0,"Native reward quantities wrong");
        require(!HalloweenGameInventory.reward(bag,town,game,()->state.reward(owner,game.id(),100)),"Repeated reward accepted");
        require(!state.canStart(owner,100)&&state.canStart(owner,101),"Daily reward limit failed");
        String result="Halloween assets: PASSED. Both head variants, native pickup prompt, item receipt roundtrip, exact "+HalloweenGameState.CANDIES+" candy / "+HalloweenGameState.GOLD+" gold payout and repeat rejection.";
        ctx.sendMessage(Message.raw(result));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
        return CompletableFuture.completedFuture(null);
    }
    private static int count(ItemContainer bag,String id){int count=0;for(short s=0;s<bag.getCapacity();s++){var item=bag.getItemStack(s);if(!ItemStack.isEmpty(item)&&id.equals(item.getItemId()))count+=item.getQuantity();}return count;}
    private static void require(boolean ok,String error){if(!ok)throw new IllegalStateException(error);}
}
