package com.hexvane.aetherhaven.backpack;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.bard.BardEnvironmentMusic;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

public final class BackpackTickSystem extends EntityTickingSystem<EntityStore> {
    @Override public Query<EntityStore> getQuery(){return Query.and(Player.getComponentType(),TransformComponent.getComponentType());}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var ref=chunk.getReferenceTo(index);var state=chunk.getComponent(index,BackpackRuntime.getComponentType());
        if(state==null){buffer.addComponent(ref,BackpackRuntime.getComponentType(),new BackpackRuntime());return;}
        long now=System.nanoTime()/1_000_000L;if(now<state.nextScan)return;state.nextScan=now+500;state.music=0;
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);if(inv==null)return;
        List<Short> magnets=new ArrayList<>();
        for(short i=0;i<inv.getCapacity();i++){
            var stack=inv.getItemStack(i);
            if(com.hexvane.aetherhaven.patrol.GuardSpoils.owner(stack)!=null){
                var original=stack;stack=com.hexvane.aetherhaven.patrol.GuardSpoils.clean(stack);var clean=stack;short slot=i;
                inv.replaceAll((at,held)->at==slot&&Objects.equals(held,original)?clean:held);
            }
            if(!Backpack.isBackpack(stack))continue;var pack=Backpack.read(stack);if(pack==null)continue;
            if(pack.jukebox&&!pack.song.isBlank()&&state.music==0){var song=AetherhavenPlugin.get().getBardSongCatalog().byId(pack.song);if(song!=null)state.music=BardEnvironmentMusic.resolveMusicContainerIndex(song);}
            if(pack.magnet&&pack.magnetOn)magnets.add(i);
        }
        if(magnets.isEmpty())return;
        var position=chunk.getComponent(index,TransformComponent.getComponentType()).getPosition();
        List<Ref<EntityStore>> nearby=new ArrayList<>();
        store.getResource(EntityModule.get().getNetworkSendableSpatialResourceType()).getSpatialStructure().collect(position,5,nearby);
        int inspected=0;
        for(var itemRef:nearby){
            if(!itemRef.isValid())continue;if(++inspected>50)break;var drop=buffer.getComponent(itemRef,ItemComponent.getComponentType());
            if(drop==null||!drop.canPickUp()||ItemStack.isEmpty(drop.getItemStack())||Backpack.isBackpack(drop.getItemStack()))continue;
            for(short slot:magnets){
                var incoming=drop.getItemStack();int accepted=BackpackExchange.collect(inv,slot,inv.getItemStack(slot),com.hexvane.aetherhaven.patrol.GuardSpoils.clean(incoming));
                if(accepted==0)continue;
                if(accepted==incoming.getQuantity()){drop.setItemStack(null);buffer.removeEntity(itemRef,RemoveReason.REMOVE);break;}
                drop.setItemStack(incoming.withQuantity(incoming.getQuantity()-accepted));buffer.putComponent(itemRef,ItemComponent.getComponentType(),drop);
            }
        }
    }
}
