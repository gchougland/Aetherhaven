package com.hexvane.aetherhaven.command;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.workplace.WorkplaceOrders;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.hexvane.aetherhaven.townsfolk.TownsfolkTraining.Result.*;

/** Loaded native inventory/codec checks on ephemeral town data. No world, player or resident is modified. */
final class AetherhavenTownsfolkMasteryTestCommand extends AbstractAsyncCommand {
    AetherhavenTownsfolkMasteryTestCommand(){super("townsfolkmastery","Check committed work XP, training payments, points and save replay safety");setPermissionGroups("hytale:WorldEditor");}
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var owner=UUID.randomUUID();var resident=UUID.randomUUID();var town=new TownRecord();town.setOwner(owner,"Fixture");
        var home=new PlotInstance(UUID.randomUUID(),"plot_house",PlotInstanceState.COMPLETE,new PlotFootprintRecord(0,100,0,4,104,4),0,100,0,0);
        home.setHomeResidentAt(0,resident);town.getPlotInstances().add(home);
        var progress=new TownsfolkProgress();town.getTownsfolkProgress().put("fixture",progress);
        progress.earnMasteryXp(TownsfolkProgress.masteryXpForLevel(5)-15);
        var source=new SimpleItemContainer((short)1);var essence=new ItemStack("Ingredient_Life_Essence",2);source.setItemStackForSlot((short)0,essence,false);
        var warehouse=town.getWarehouse();
        require(warehouse.deposit(TownWarehouse.Page.GENERAL,source,(short)0,essence,TownWarehouse.Amount.STACK).moved()==2,"Native supply deposit failed");
        var orders=new WorkplaceOrders();orders.add("fixture",WorkplaceOrders.Mode.COUNT,1);
        var batch=warehouse.reserve(UUID.randomUUID(),orders.list().getFirst().id(),"fixture",
            List.of(new MaterialQuantity("Ingredient_Life_Essence",null,null,2,null)),List.of(new ItemStack("Plant_Seeds_Wheat",1)));
        require(batch!=null,"Native fixture reservation failed");
        require(TownsfolkMastery.completed(progress,TownsfolkMastery.Work.DELIVERY,warehouse.deliver(batch.id(),"fixture"))==0,"Undelivered cargo gave XP");
        require(warehouse.pickup(batch.id(),"fixture",1)>0,"Native pickup failed");
        require(TownsfolkMastery.completed(progress,TownsfolkMastery.Work.DELIVERY,warehouse.deliver(batch.id(),"fixture"))==5,"Completed delivery gave wrong XP");
        require(TownsfolkMastery.completed(progress,TownsfolkMastery.Work.DELIVERY,warehouse.deliver(batch.id(),"fixture"))==0,"Repeated delivery gave XP");
        require(TownsfolkMastery.completed(progress,TownsfolkMastery.Work.CRAFT,warehouse.finishWork(batch.id(),orders,6))==0,"Incomplete work gave XP");
        for(int i=0;i<24;i++)warehouse.advanceWork(batch.id(),.25);
        town=reload(town);progress=town.getTownsfolkProgress().get("fixture");
        require(progress.masteryLevel()==4,"Polling or reload granted a level");
        require(TownsfolkMastery.completed(progress,TownsfolkMastery.Work.CRAFT,town.getWarehouse().finishWork(batch.id(),orders,6))==10,"Completed craft gave wrong XP");
        town=reload(town);progress=town.getTownsfolkProgress().get("fixture");
        require(TownsfolkMastery.completed(progress,TownsfolkMastery.Work.CRAFT,town.getWarehouse().finishWork(batch.id(),orders,6))==0,"Reload repeated craft XP");
        require(progress.masteryLevel()==5&&progress.masterySkillPoints()==4&&progress.masteryAttributePoints()==2,"Crossed level did not grant exact point balances");
        require(town.getWarehouse().count(TownWarehouse.Page.OUTPUT,"Plant_Seeds_Wheat")==1,"Crafted output duplicated or lost");
        var training=new SimpleItemContainer((short)2);
        training.setItemStackForSlot((short)0,new ItemStack(AetherhavenConstants.ITEM_VERDANT_CATALYST,3),false);
        training.setItemStackForSlot((short)1,new ItemStack(AetherhavenConstants.ITEM_SHARD_OF_GAIA,3),false);
        require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.COOKING,1,false,training)==TRAINED,"Native Catalyst training failed");
        require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.COOKING,1,false,training)==STALE,"Old Catalyst click paid twice");
        require(TownsfolkTraining.train(town,owner,resident,progress,TownsfolkAttribute.STRENGTH,null,5,false,training)==TRAINED,"Native Shard training failed");
        require(training.getItemStack((short)0).getQuantity()==2&&training.getItemStack((short)1).getQuantity()==2,"Training did not consume exactly one item");
        require(progress.skill(TownsfolkSkill.COOKING)==2&&progress.attribute(TownsfolkAttribute.STRENGTH)==6&&progress.masterySkillPoints()==4,"Paid training changed the wrong values");
        require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.COOKING,2,true,null)==TRAINED,"Skill point spending failed");
        require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.COOKING,2,true,null)==STALE,"Old point click spent twice");
        require(TownsfolkTraining.train(town,owner,resident,progress,TownsfolkAttribute.STRENGTH,null,6,true,null)==TRAINED,"Attribute point spending failed");
        require(TownsfolkTraining.train(town,UUID.randomUUID(),resident,progress,null,TownsfolkSkill.COOKING,3,true,null)==FORBIDDEN,"Outsider spent town points");
        require(TownsfolkTraining.train(town,owner,UUID.randomUUID(),progress,null,TownsfolkSkill.COOKING,3,true,null)==FORBIDDEN,"Nonresident training accepted");
        require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.COOKING,3,false,new SimpleItemContainer((short)1))==MISSING_MATERIALS,"Missing Catalyst gave training");
        progress.setSkill(TownsfolkSkill.FARMING,0);progress.setSkill(TownsfolkSkill.MINING,10);progress.setAttribute(TownsfolkAttribute.SPEED,20);
        require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.FARMING,0,true,null)==LOCKED,"Incapable profession learned");
        require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.MINING,10,false,training)==LOCKED,"Capped skill trained");
        require(TownsfolkTraining.train(town,owner,resident,progress,TownsfolkAttribute.SPEED,null,20,true,null)==LOCKED,"Capped attribute trained");
        town=reload(town);progress=town.getTownsfolkProgress().get("fixture");
        require(progress.masterySkillPoints()==3&&progress.masteryAttributePoints()==1&&progress.skill(TownsfolkSkill.COOKING)==3&&progress.attribute(TownsfolkAttribute.STRENGTH)==7,"Reload lost spending or awarded extra points");
        for(int i=0;i<3;i++)require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.COOKING,progress.skill(TownsfolkSkill.COOKING),true,null)==TRAINED,"Available skill point refused");
        require(TownsfolkTraining.train(town,owner,resident,progress,null,TownsfolkSkill.COOKING,6,true,null)==NO_POINTS,"Exhausted points trained a skill");
        require(progress.earnMasteryXp(Long.MAX_VALUE)==9750&&progress.masteryLevel()==20&&progress.earnMasteryXp(1)==0,"Mastery cap or overflow failed");
        String result="Townsfolk mastery isolated check PASSED: native inventory and saved work, committed XP, level rewards, exact training payments, points, permissions, stale clicks, incapability, caps and reload. No player or town changed.";
        ctx.sendMessage(Message.raw(result));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
        return CompletableFuture.completedFuture(null);
    }
    private static TownRecord reload(TownRecord town){var gson=new Gson();return gson.fromJson(gson.toJson(town),TownRecord.class);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
