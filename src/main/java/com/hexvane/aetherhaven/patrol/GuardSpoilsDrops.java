package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.NPCDamageSystems;
import java.util.*;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;

/** Same native loot roll and corpse timing, only annotating patrol kills for their killer. */
public final class GuardSpoilsDrops extends EntityTickingSystem<EntityStore> {
    @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),DeathComponent.getComponentType(),TransformComponent.getComponentType(),HeadRotation.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,DeathSystems.TickCorpseRemoval.class),new SystemDependency<>(Order.BEFORE,NPCDamageSystems.DropDeathItems.class));}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var death=chunk.getComponent(index,DeathComponent.getComponentType());var claim=chunk.getComponent(index,GuardKillClaim.type);
        if(death.getItemsLossMode()!=DeathConfig.ItemsLossMode.ALL||claim==null||claim.owner.isBlank())return;
        var role=chunk.getComponent(index,NPCEntity.getComponentType()).getRole();if(role==null||role.hasDroppedDeathItems())return;
        if(!role.isDropDeathItemsInstantly()){var deferred=chunk.getComponent(index,DeferredCorpseRemoval.getComponentType());if(deferred!=null&&!deferred.shouldRemove())return;}
        var items=new ArrayList<ItemStack>();
        if(role.isPickupDropOnDeath()){var storage=chunk.getComponent(index,InventoryComponent.Storage.getComponentType());if(storage!=null)items.addAll(storage.getInventory().dropAllItemStacks());}
        if(role.getDropListId()!=null&&ItemModule.get().isEnabled())items.addAll(ItemModule.get().getRandomItemDrops(role.getDropListId()));
        items.replaceAll(s->GuardSpoils.mark(s,claim.owner));
        role.setDeathItemsDropped();
        if(!items.isEmpty()){
            var position=new Vector3d(chunk.getComponent(index,TransformComponent.getComponentType()).getPosition()).add(0,1,0);
            var rotation=chunk.getComponent(index,HeadRotation.getComponentType()).getRotation();
            buffer.addEntities(ItemComponent.generateItemDrops(store,items,position,new Rotation3f(rotation)),AddReason.SPAWN);
        }
    }
}
