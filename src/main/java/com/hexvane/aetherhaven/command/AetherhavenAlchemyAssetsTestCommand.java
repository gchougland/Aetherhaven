package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.alchemy.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Read-only game asset audit and isolated inventory transaction checks. Does not edit players or towns. */
final class AetherhavenAlchemyAssetsTestCommand extends AbstractAsyncCommand {
    AetherhavenAlchemyAssetsTestCommand(){super("alchemyassets","Check loaded flask, cauldron, ingredients, effects and safe bottling");setPermissionGroups("hytale:WorldEditor");}
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var item=Item.getAssetMap().getAsset(AlchemyFlask.ITEM);
        require(item!=null&&item.getMaxStack()==1&&item.getInteractions().containsKey(InteractionType.Secondary),"Alchemy Flask drink interaction is missing");
        require(BlockType.getAssetMap().getAsset(AlchemyFlask.ITEM)==null,"Alchemy Flask must be an item, so placement cannot strip its upgrades");
        var cauldron=BlockType.getAssetMap().getAsset("Alchemy_Cauldron_Big");
        require(cauldron!=null&&cauldron.getInteractions().containsKey(InteractionType.Use),"Laboratory cauldron Use interaction is missing");
        var effects=new HashSet<String>();
        for(var ingredient:AlchemyIngredients.all()) {
            require(Item.getAssetMap().getAsset(ingredient.item())!=null,"Unknown ingredient "+ingredient.item());
            var effect=EntityEffect.getAssetMap().getAsset(ingredient.effect());
            require(effect!=null,"Missing effect "+ingredient.effect());
            require(!effect.isInfinite()&&effect.getDuration()>0,"Unexpected effect duration "+ingredient.effect());
            // Anything that lasts long enough to be watched shows on the status bar; instant restores do not.
            if(effect.getDuration()>=1)require(effect.getStatusEffectIcon()!=null,"Missing status icon "+ingredient.effect());
            effects.add(ingredient.effect());
        }
        for(int cap=1;cap<=4;cap++)require(Item.getAssetMap().getAsset(AlchemyFlask.upgradeLeather(cap))!=null,"Missing flask upgrade leather");
        var bag=new SimpleItemContainer((short)4);var flask=AlchemyFlask.upgrade(new ItemStack(AlchemyFlask.ITEM,1));
        bag.setItemStackForSlot((short)0,flask,false);bag.setItemStackForSlot((short)1,new ItemStack("Plant_Crop_Health1",3),false);
        var mix=List.of("Plant_Crop_Health1","Plant_Crop_Mana1");var costs=AlchemyInventory.brewCosts(mix,2);var filled=AlchemyFlask.fill(flask,mix);
        require(!AlchemyInventory.exchange(bag,(short)0,flask,filled,costs),"Missing ingredients accepted");
        require(bag.getItemStack((short)1).getQuantity()==3,"Missing ingredient partially consumed resources");
        bag.setItemStackForSlot((short)2,new ItemStack("Plant_Crop_Mana1",2),false);
        require(AlchemyInventory.exchange(bag,(short)0,flask,filled,costs),"Valid bottling failed");
        require(AlchemyFlask.charges(bag.getItemStack((short)0))==2&&bag.getItemStack((short)1).getQuantity()==1,"Bottling quantities differ");
        require(!AlchemyInventory.exchange(bag,(short)0,flask,filled,costs),"Old bottling request duplicated charges");
        checkReservedMixedStock();
        checkFlaskCraft();
        String result="Alchemy asset check PASSED: "+AlchemyIngredients.all().size()+" native ingredients, "+effects.size()+" effects, flask, cauldron crafting, leather, mixed tiers, reservations, late harvests, duplicate requests and reload.";
        ctx.sendMessage(Message.raw(result));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
        return CompletableFuture.completedFuture(null);
    }
    private static void checkReservedMixedStock() {
        String health="Plant_Crop_Health3",mana="Plant_Crop_Mana2",weak="Plant_Crop_Health1";
        var warehouse=new com.hexvane.aetherhaven.warehouse.TownWarehouse();
        var supply=new SimpleItemContainer((short)1);var supplied=new ItemStack(mana,7);
        supply.setItemStackForSlot((short)0,supplied,false);
        require(warehouse.deposit(com.hexvane.aetherhaven.warehouse.TownWarehouse.Page.GENERAL,supply,(short)0,
            supplied,com.hexvane.aetherhaven.warehouse.TownWarehouse.Amount.STACK).moved()==7,
            "Could not seed isolated warehouse");
        var production=new com.hexvane.aetherhaven.production.PlotProductionState();production.addAmount(health,7,100);
        var plot=UUID.randomUUID();var point=new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(0,128,0);
        var physical=new com.hexvane.aetherhaven.warehouse.TownWarehouse.ProductionStock(plot,point,production);
        var reservation=warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"alchemy_native_fixture",
            List.of(new com.hypixel.hytale.server.core.inventory.MaterialQuantity(health,null,null,3,null),
                new com.hypixel.hytale.server.core.inventory.MaterialQuantity(mana,null,null,3,null)),
            List.of(new ItemStack("Ingredient_Bar_Iron",1)),List.of(physical));
        require(reservation!=null,"Could not reserve courier ingredients");
        var sources=List.of(new AlchemyTownStock.Source(plot,production));
        var seen=AlchemyTownStock.snapshot(warehouse,sources);
        require(seen.count(health)==4&&seen.count(mana)==4,"Reserved stock appears available");
        var flask=new ItemStack(AlchemyFlask.ITEM,1);for(int i=1;i<5;i++)flask=AlchemyFlask.upgrade(flask);
        var bag=new SimpleItemContainer((short)4);bag.setItemStackForSlot((short)0,flask,false);
        bag.setItemStackForSlot((short)1,new ItemStack(health,1),false);
        bag.setItemStackForSlot((short)2,new ItemStack(mana,1),false);
        bag.setItemStackForSlot((short)3,new ItemStack(weak,5),false);
        var mix=List.of(health,mana,weak);var filled=AlchemyFlask.fill(flask,mix);var costs=AlchemyInventory.brewCosts(mix,5);
        // A harvest that lands after the page was opened must not refuse the fill. Stock is read when bottling.
        production.addAmount(health,1,100);
        require(AlchemyTownStock.brew(warehouse,()->sources,bag,(short)0,flask,filled,costs),"Reserved mixed-source brew failed after a harvest");
        require(production.getAmount(health)==4&&warehouse.craftingStock().count(mana)==0,"Mixed brew cost differs");
        require(warehouse.reservedProduction(plot,health)==3,"Brew altered a production reservation");
        require(AlchemyFlask.charges(bag.getItemStack((short)0))==5,"Mixed flask has wrong charges");
        require(!AlchemyTownStock.brew(warehouse,()->sources,bag,(short)0,flask,filled,costs),"Duplicate brew accepted");
        var reloaded=com.hypixel.hytale.server.core.inventory.ItemStack.CODEC.decode(
            ItemStack.CODEC.encode(bag.getItemStack((short)0),new com.hypixel.hytale.codec.ExtraInfo()),new com.hypixel.hytale.codec.ExtraInfo());
        require(AlchemyFlask.charges(reloaded)==5&&AlchemyFlask.ingredients(reloaded).equals(mix),"Native item codec lost the mixture");
        var effective=AlchemyIngredients.effects(AlchemyFlask.ingredients(reloaded));
        require(effective.size()==2&&effective.get(0).strength()==3&&effective.get(1).strength()==2,"Weaker duplicate family replaced a stronger effect");
        var gson=new com.google.gson.Gson();var restored=gson.fromJson(gson.toJson(warehouse),com.hexvane.aetherhaven.warehouse.TownWarehouse.class);
        require(restored.reservedProduction(plot,health)==3&&restored.craftingStock().count(mana)==0,"Warehouse reload lost reservations");
        require(restored.pickupAt(reservation.id(),"native_courier",1,physical,point)==3,"Production reservation no longer deliverable");
        require(restored.deliver(reservation.id(),"native_courier"),"Production reservation delivery failed");
        require(restored.pickup(reservation.id(),"native_courier",1)==3,"Warehouse reservation no longer deliverable");
        require(restored.deliver(reservation.id(),"native_courier"),"Warehouse reservation delivery failed");
    }
    /** The flask is made at the cauldron from data, with no recipe to learn and none left at any bench. */
    private static void checkFlaskCraft() {
        for(var recipe:com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe.getAssetMap().getAssetMap().values()) {
            var outputs=new ArrayList<com.hypixel.hytale.server.core.inventory.MaterialQuantity>();
            if(recipe.getPrimaryOutput()!=null)outputs.add(recipe.getPrimaryOutput());
            if(recipe.getOutputs()!=null)outputs.addAll(Arrays.asList(recipe.getOutputs()));
            for(var output:outputs)
                require(output==null||!AlchemyFlask.ITEM.equals(output.getItemId()),"A bench recipe still makes the Alchemy Flask: "+recipe.getId());
        }
        var cost=AlchemyFlaskCraft.cost();
        require(!cost.isEmpty(),"Alchemy Flask craft cost did not load");
        for(var part:cost.entrySet())require(Item.getAssetMap().getAsset(part.getKey())!=null&&part.getValue()>0,"Unknown flask material "+part.getKey());
        var bag=new SimpleItemContainer((short)(cost.size()+1));short slot=0;
        for(var part:cost.entrySet())bag.setItemStackForSlot(slot++,new ItemStack(part.getKey(),part.getValue()),false);
        require(AlchemyFlaskCraft.craft(bag,null).outcome()==AlchemyFlaskCraft.Outcome.CRAFTED,"Flask craft from a full set of materials failed");
        short made=AlchemyBottling.locate(bag,(short)-1,null);
        require(made>=0&&AlchemyFlask.capacity(bag.getItemStack(made))==1&&AlchemyFlask.charges(bag.getItemStack(made))==0,"Crafted flask is not an empty one charge flask");
        for(var part:cost.entrySet())require(com.hexvane.aetherhaven.inventory.InventoryMaterials.count(bag,part.getKey())==0,"Flask craft left materials behind");
        var short1=AlchemyFlaskCraft.craft(bag,null);
        require(short1.outcome()==AlchemyFlaskCraft.Outcome.MISSING&&short1.missing().size()==cost.size(),"Flask craft without materials did not name what is missing");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
