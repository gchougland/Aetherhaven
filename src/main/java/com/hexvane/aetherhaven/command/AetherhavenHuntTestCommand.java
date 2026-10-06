package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hunter.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;

/** Operator fixtures never complete waves or grant run rewards. */
final class AetherhavenHuntTestCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> action=withRequiredArg("action","status, open, hunter, supplies or lighting",ArgTypes.STRING);
    private final DebugTownTargetArgs target;
    AetherhavenHuntTestCommand(){super("hunt","Inspect survival hunts and prepare labeled test fixtures");setPermissionGroups("hytale:WorldEditor");target=DebugTownTargetArgs.registerOn(this);}
    @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> player,PlayerRef pr,World world) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;var resolved=target.resolve(ctx,world,store,player,pr,true);
        if(!resolved.isOk()){ctx.sendMessage(resolved.error());return;}var town=resolved.townOrThrow();
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);var hunters=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(),NPCEntity.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++){var b=chunk.getComponent(i,TownVillagerBinding.getComponentType());if(town.getTownId().equals(b.getTownId())&&HuntService.KIND.equals(b.getKind()))hunters.add(chunk.getReferenceTo(i));}
        });
        switch(ctx.get(action)) {
            case "lighting" -> {
                var plot=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),HuntService.CONSTRUCTION);
                if(plot==null||!"plot_monster_hunter".equals(plot.getConstructionId())) {
                    report(ctx,"Complete a built in Hunter's Lodge first.");return;
                }
                if(town.getHunts().run()!=null){report(ctx,"Finish the current run first.");return;}
                var def=plugin.getConstructionCatalog().get(plot.getConstructionId());
                var anchor=plot.resolvePrefabAnchorWorld(def);
                var position=store.getComponent(player,TransformComponent.getComponentType());
                if(position==null||position.getPosition().distanceSquared(anchor.x,anchor.y,anchor.z)>64*64) {
                    report(ctx,"Move closer to the lodge first.");return;
                }
                // The imported Core arena supplies its own lights. Audit those without changing the world.
                int checked=0;
                for(var local:new int[][]{{-8,4,-2},{-8,4,2},{-4,2,-2},{-4,2,2},{0,2,-6},{0,2,6},{8,2,-6},{8,2,6},{10,2,-4},{10,2,4}}) {
                    var point=new org.joml.Vector3i(anchor).add(com.hexvane.aetherhaven.construction.PrefabLocalOffset.rotate(plot.resolvePrefabYaw(),local[0],local[1],local[2]));
                    var block=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world,point.x,point.y,point.z);
                    String expected=local[1]==4?"Deco_Lantern_Ceiling":"Furniture_Jungle_Brazier";
                    if(block==null||!expected.equals(block.getId())) {
                        report(ctx,"Lighting check stopped: an authored light is missing or unloaded. No blocks changed.");return;
                    }
                    checked++;
                }
                report(ctx,"Lodge lighting check found all "+checked+" authored lights. No blocks changed.");
            }
            case "supplies" -> {
                var inv=InventoryComponent.getCombined(store,player,InventoryComponent.EVERYTHING);
                if(inv!=null)for(var family:HuntFamily.all())inv.addItemStack(new ItemStack(family.icon(),family.unlockItems()),true,false,true);
                report(ctx,"Test unlock samples granted where inventory had room. Unlock creatures through the normal screen.");
            }
            case "hunter" -> {
                if(!hunters.isEmpty()||town.getResidentNpcRecords().stream().anyMatch(r->"Aetherhaven_Monster_Hunter".equals(r.getNpcRoleId()))) {report(ctx,"The Hunter already has a resident record.");return;}
                var plot=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),HuntService.CONSTRUCTION);
                if(plot==null){report(ctx,"Complete the Hunter's Lodge first.");return;}
                var blocked=TownAdmission.blocked(plugin,town,null,"Aetherhaven_Monster_Hunter");if(blocked!=null){ctx.sendMessage(blocked);return;}
                var pos=store.getComponent(player,TransformComponent.getComponentType()).getPosition();
                if(!plot.containsWorldBlock((int)Math.floor(pos.x),(int)Math.floor(pos.y),(int)Math.floor(pos.z))||!com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,(int)Math.floor(pos.x),(int)Math.floor(pos.y),(int)Math.floor(pos.z))) {report(ctx,"Stand on a clear floor inside the completed lodge.");return;}
                var npc=TownVillagerSpawner.spawn(store,"Aetherhaven_Monster_Hunter",new org.joml.Vector3d(pos),new TownVillagerBinding(town.getTownId(),HuntService.KIND,plot.getPlotId(),plot.getPlotId()),"hunt_test","Explicit Hunter test fixture");
                if(npc==null){report(ctx,"The Hunter fixture could not spawn.");return;}
                ResidentRegistryService.upsert(town,manager,"Aetherhaven_Monster_Hunter",HuntService.KIND,plot.getPlotId(),store.getComponent(npc,UUIDComponent.getComponentType()).getUuid());manager.updateTown(town);
                report(ctx,"Created one test Hunter resident. No unlocks, training or runs granted.");
            }
            case "open" -> {if(!hunters.isEmpty())store.getComponent(player,Player.getComponentType()).getPageManager().openCustomPage(player,store,new HuntPage(pr,town.getTownId(),hunters.getFirst()));}
            case "status" -> {
                var state=town.getHunts();report(ctx,"Hunts: run="+state.run()+", wave="+(state.defeated()+1)+", best="+state.best()+", stock="+state.stock()+", treasury="+town.getTreasuryGoldCoinCount()+", status="+HuntService.status(store,town.getTownId()));
                for(var hunter:hunters){var hp=store.getComponent(hunter,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType()).get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());report(ctx,"Hunter: "+store.getComponent(hunter,TransformComponent.getComponentType()).getPosition()+" health="+hp.get()+"/"+hp.getMax()+" state="+com.hexvane.aetherhaven.npc.NpcSupportUtil.stateName(store,hunter));}
                store.forEachChunk(HuntOpponent.getComponentType(),(chunk,buffer)->{for(int i=0;i<chunk.size();i++){var ref=chunk.getReferenceTo(i);var npc=chunk.getComponent(i,NPCEntity.getComponentType());var hp=store.getComponent(ref,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType()).get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());report(ctx,"Summon: "+(npc==null?"?":npc.getRoleName())+" at "+store.getComponent(ref,TransformComponent.getComponentType()).getPosition()+" health="+hp.get()+"/"+hp.getMax());}});
            }
            default -> report(ctx,"Use status, open, hunter, supplies or lighting.");
        }
    }
    private static void report(CommandContext ctx,String text){ctx.sendMessage(Message.raw(text));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("%s",text);}
}
