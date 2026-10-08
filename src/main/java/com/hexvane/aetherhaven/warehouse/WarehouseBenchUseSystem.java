package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchUseSystem;
import com.hypixel.hytale.builtin.crafting.component.*;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

public final class WarehouseBenchUseSystem extends EntityEventSystem<EntityStore,UseBlockEvent.Pre> {
    private final AetherhavenPlugin plugin;
    public WarehouseBenchUseSystem(AetherhavenPlugin plugin){super(UseBlockEvent.Pre.class);this.plugin=plugin;}
    @Override public Query<EntityStore> getQuery(){return Query.and(Player.getComponentType(),PlayerRef.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,WorkplaceBenchUseSystem.class));}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,UseBlockEvent.Pre event) {
        if(event.isCancelled()||event.getInteractionType()!=InteractionType.Use)return;
        var world=store.getExternalData().getWorld();var pos=event.getTargetBlock();var chunks=world.getChunkStore();
        var section=chunks.getChunkSectionReferenceAtBlock(pos.x,pos.y,pos.z);if(section==null||!section.isValid())return;
        var cs=chunks.getStore();var blocks=cs.getComponent(section,BlockSection.getComponentType());if(blocks==null)return;
        var block=BlockType.getAssetMap().getAsset(blocks.get(pos.x,pos.y,pos.z));
        if(block==null||block.getBench()==null)return;
        switch(block.getBench().getType()){case Crafting,DiagramCrafting,StructuralCrafting:break;default:return;}
        var player=chunk.getComponent(index,PlayerRef.getComponentType());int rotation=blocks.getRotationIndex(pos.x,pos.y,pos.z);
        var link=WarehouseSupplyLink.find(plugin,world,player.getUuid(),pos.x,pos.y,pos.z,block,rotation);if(link==null)return;
        var ref=chunk.getReferenceTo(index);var craft=buffer.getComponent(ref,CraftingManager.getComponentType());
        if(craft==null||craft.hasBenchSet())return;
        var entity=BlockModule.getBlockEntity(cs,section,pos.x,pos.y,pos.z);if(entity==null)return;
        var bench=cs.getComponent(entity,BenchBlock.getComponentType());if(bench==null)return;
        event.setCancelled(true);
        BenchBlock.refreshGrantedAugmentTags(cs,entity,pos);
        if(!BenchBlock.tryOpen(ref,buffer,entity,pos))return;
        BenchWindow window=switch(block.getBench().getType()) {
            case Crafting->new WarehouseLinkedWindow.Simple(pos.x,pos.y,pos.z,rotation,block,bench,link);
            case DiagramCrafting->new WarehouseLinkedWindow.Diagram(ref,buffer,pos.x,pos.y,pos.z,rotation,block,bench,link);
            case StructuralCrafting->new WarehouseLinkedWindow.Structural(pos.x,pos.y,pos.z,rotation,block,bench,link);
            default->throw new IllegalStateException();
        };
        if(bench.getWindows().putIfAbsent(player.getUuid(),window)==null)
            window.registerCloseEvent(e->bench.getWindows().remove(player.getUuid(),window));
        chunk.getComponent(index,Player.getComponentType()).getPageManager().setPageWithWindows(ref,store,Page.Bench,true,window);
    }
}
