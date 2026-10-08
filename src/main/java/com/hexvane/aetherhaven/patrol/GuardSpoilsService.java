package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.rts.RtsHostileQuery;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import org.bson.BsonDocument;
import org.joml.Vector3d;

public final class GuardSpoilsService {
    private GuardSpoilsService(){}
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store){
        var state=store.getComponent(ref,GuardSpoilsState.getComponentType());
        if(state!=null){state.safeAfter=System.nanoTime()/1_000_000L+3000;state.active=false;state.target=null;state.destination=null;}
    }
    /** Called only after the patrol system has excluded combat, escorts and manual orders. */
    public static boolean tick(Ref<EntityStore> ref,NPCEntity npc,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var state=store.getComponent(ref,GuardSpoilsState.getComponentType());
        if(state==null){buffer.addComponent(ref,GuardSpoilsState.getComponentType(),new GuardSpoilsState());return false;}
        long now=System.nanoTime()/1_000_000L;
        var town=GuardSpoils.town(ref,store);var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        var guard=town==null||uuid==null?null:GuardSpoils.guard(town,uuid.getUuid());
        if(guard==null||!guard.collectsMobDrops()){state.active=false;state.target=null;state.destination=null;return false;}
        if(now<state.safeAfter||now<state.retryAfter)return false;
        if(now<state.nextScan)return state.active;state.nextScan=now+250;
        var tc=store.getComponent(ref,TransformComponent.getComponentType());if(tc==null)return false;var pos=tc.getPosition();
        if(RtsHostileQuery.nearestGuardThreat(store,ref,pos.x,pos.y,pos.z,16,null,null)!=null){interrupt(ref,store);return false;}
        var world=store.getExternalData().getWorld();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get());
        if(state.active&&now-state.started>20000){state.active=false;state.target=null;state.destination=null;state.retryAfter=now+10000;return false;}
        if(!guard.spoils().isEmpty()){
            var desk=WarehouseDeskBlock.loaded(world,manager,town).stream().min(Comparator.comparingDouble(p->pos.distanceSquared(p.x()+.5,p.y(),p.z()+.5))).orElse(null);
            if(desk==null){state.active=false;return false;}
            if(Math.abs(pos.y-desk.y())<=2&&pos.distanceSquared(desk.x()+.5,pos.y,desk.z()+.5)<=9){
                var stack=ItemStack.CODEC.decode(BsonDocument.parse(guard.spoils().getFirst()),new ExtraInfo());
                if(!town.getWarehouse().acceptGuardSpoils(stack)){state.active=false;state.retryAfter=now+10000;return false;}
                guard.spoils().removeFirst();manager.updateTown(town);state.active=false;state.target=null;state.destination=null;return true;
            }
            if(state.destination==null||!state.active)state.destination=com.hexvane.aetherhaven.autonomy.CourierWorkService.approach(world,desk,pos,0);
            if(state.destination==null)return false;
            return walk(ref,npc,state,state.destination,now,buffer);
        }
        ItemComponent item=null;
        if(state.target!=null&&state.target.isValid())item=buffer.getComponent(state.target,ItemComponent.getComponentType());
        String owner=GuardSpoils.owner(town,guard);
        if(item==null||!owner.equals(GuardSpoils.owner(item.getItemStack()))){
            state.target=null;state.active=false;state.destination=null;
            List<Ref<EntityStore>> nearby=new ArrayList<>();store.getResource(EntityModule.get().getNetworkSendableSpatialResourceType()).getSpatialStructure().collect(pos,20,nearby);
            double best=Double.MAX_VALUE;
            for(var candidate:nearby){if(!candidate.isValid())continue;var drop=buffer.getComponent(candidate,ItemComponent.getComponentType());var pt=buffer.getComponent(candidate,TransformComponent.getComponentType());
                if(drop==null||pt==null||!drop.canPickUp()||!owner.equals(GuardSpoils.owner(drop.getItemStack())))continue;
                double distance=pos.distanceSquared(pt.getPosition());if(distance<best){best=distance;state.target=candidate;item=drop;}
            }
        }
        if(state.target==null||item==null)return false;
        var target=buffer.getComponent(state.target,TransformComponent.getComponentType());if(target==null)return false;
        var point=target.getPosition();
        if(pos.distanceSquared(point)<=4&&item.canPickUp()){
            var clean=GuardSpoils.clean(item.getItemStack());if(ItemStack.isEmpty(clean))return false;
            String serialized=ItemStack.CODEC.encode(clean,new ExtraInfo()).asDocument().toJson();
            guard.spoils().add(serialized);item.setItemStack(null);buffer.removeEntity(state.target,RemoveReason.REMOVE);
            manager.updateTown(town);state.target=null;state.destination=null;state.active=false;return true;
        }
        return walk(ref,npc,state,point,now,buffer);
    }
    private static boolean walk(Ref<EntityStore> ref,NPCEntity npc,GuardSpoilsState state,Vector3d point,long now,CommandBuffer<EntityStore> buffer){
        if(!state.active)state.started=now;state.active=true;npc.setLeashPoint(new Vector3d(point));
        if(!NpcSupportUtil.stateName(buffer.getStore(),ref).contains("Patrol"))NpcSupportUtil.setState(ref,AetherhavenConstants.NPC_STATE_GUARD_PATROL,null,buffer);buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
    }
}
