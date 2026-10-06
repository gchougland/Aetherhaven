package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.*;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native furnaces own fuel consumption and processing. Apprentices only load and collect at arrival. */
public final class NativeFurnaceService {
    private NativeFurnaceService() {}
    private record Furnace(Ref<ChunkStore> ref,ProcessingBenchBlock processing,BenchBlock bench,BlockModule.BlockStateInfo info) {}
    private static Furnace at(World world,WarehouseBatch.Point p) {
        if(p==null||ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunk(ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.z())))==null)return null;
        var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,p.x(),p.y(),p.z());if(ref==null||!ref.isValid())return null;
        var store=world.getChunkStore().getStore();
        var processing=store.getComponent(ref,ProcessingBenchBlock.getComponentType());var bench=store.getComponent(ref,BenchBlock.getComponentType());
        var info=store.getComponent(ref,BlockModule.BlockStateInfo.getComponentType());
        return processing==null||bench==null||info==null||!"Furnace".equals(processing.getProcessingBench().getId())?null:new Furnace(ref,processing,bench,info);
    }
    public static boolean available(World world,WarehouseBatch.Point point) { return at(world,point)!=null; }
    public static WarehouseBatch queue(World world,TownRecord town,ApprenticeWorkplace site,WorkplaceOrders.Order order,WorkplaceRecipe recipe,WarehouseBatch.Point point,List<TownWarehouse.ProductionStock> sources) {
        var furnace=at(world,point);if(town.getWarehouse().destinationReserved(point)||furnace==null||!furnace.processing.getInputContainer().isEmpty()||!furnace.processing.getOutputContainer().isEmpty())return null;
        var source=recipe.source();if(source==null||source.getOutputs()==null||recipe.nativeUnlockIssue(town,world)!=null)return null;
        if(recipe.nativeCharcoal()) {
            var processing=furnace.processing.getProcessingBench();var extra=processing.getExtraOutput();
            if(!processing.shouldAllowNoInputProcessing()||extra==null||extra.getPerFuelItemsConsumed()!=2
                ||extra.getOutputs()==null||extra.getOutputs().length!=1
                ||!"Ingredient_Charcoal".equals(extra.getOutputs()[0].getItemId())||extra.getOutputs()[0].getQuantity()!=1)return null;
        }
        var stock=town.getWarehouse();var candidates=new LinkedHashSet<String>();
        for(var production:sources)candidates.addAll(production.state().getAmounts().keySet());
        for(short slot=0;slot<stock.slots(TownWarehouse.Page.GENERAL);slot++) {
            var held=stock.stack(TownWarehouse.Page.GENERAL,slot);if(!ItemStack.isEmpty(held))candidates.add(held.getItemId());
        }
        for(var origin:stock.smelting().origins())for(var held:origin.items())candidates.add(held.getItemId());
        // Existing selected fuel may cover this batch without another delivery.
        var fuel=furnace.processing.getFuelContainer();
        for(short slot=0;slot<fuel.getCapacity();slot++){var held=fuel.getItemStack(slot);if(!ItemStack.isEmpty(held))candidates.add(held.getItemId());}
        var outputs=Arrays.stream(source.getOutputs()).map(m->m==null?null:m.toItemStack()).toList();
        if(outputs.stream().anyMatch(s->ItemStack.isEmpty(s)))return null;
        for(String id:candidates) {
            var item=Item.getAssetMap().getAsset(id);if(item==null||item.getFuelQuality()<=0)continue;
            boolean selected=!recipe.nativeCharcoal()&&site.charcoalFuel()?"Ingredient_Charcoal".equals(id):CraftingManager.matches(new MaterialQuantity(null,"Wood_Trunk",null,1,null),new ItemStack(id,1));
            if(!selected)continue;
            double available=furnace.processing.getFuelTime();boolean compatible=true;boolean hasFuel=false;int existingFuel=0;
            for(short slot=0;slot<fuel.getCapacity();slot++) {
                var held=fuel.getItemStack(slot);if(ItemStack.isEmpty(held))continue;
                if(!id.equals(held.getItemId())){compatible=false;break;}
                available+=held.getQuantity()*held.getItem().getFuelQuality();hasFuel=true;existingFuel+=held.getQuantity();
            }
            if(!compatible)continue;
            // Two logs produce one native charcoal byproduct. Do not add them to recipe input as well.
            if(recipe.nativeCharcoal()&&existingFuel>2)continue;
            int quantity=recipe.nativeCharcoal()?2-existingFuel:Math.max(hasFuel?0:1,(int)Math.ceil(Math.max(0,source.getTimeSeconds()-available)/item.getFuelQuality()));
            var materials=recipe.nativeCharcoal()||source.getInput()==null?List.<MaterialQuantity>of():Arrays.asList(source.getInput());
            var batch=stock.reserveSmelting(site.id(),order.id(),recipe.id(),materials,outputs,sources,id,quantity);
            if(batch!=null){stock.setDestination(batch.id(),point);return batch;}
        }
        return null;
    }
    /** A worker can leave during processing and return once native output is ready. */
    public static boolean needsService(World world,WarehouseBatch batch) {
        var furnace=at(world,batch.destination());if(furnace==null)return false;
        if(!batch.nativeLoaded())return batch.state()==WarehouseBatch.State.READY;
        return !furnace.processing.getOutputContainer().isEmpty()||!furnace.processing.isActive();
    }
    public static boolean service(World world,TownRecord town,WarehouseBatch batch) {
        return service(world,town,batch,()->{});
    }
    /** Notify only after real furnace items are collected; loading and empty completion earn no XP. */
    public static boolean service(World world,TownRecord town,WarehouseBatch batch,Runnable collectedWork) {
        var furnace=at(world,batch.destination());if(furnace==null||!batch.smelting())return false;
        var stock=town.getWarehouse();var nativeBench=furnace.processing;
        if(!batch.nativeLoaded()) {
            if(!stock.loadFurnace(batch.id(),nativeBench.getInputContainer(),nativeBench.getFuelContainer(),nativeBench.getOutputContainer()))return false;
            nativeBench.setActive(true,furnace.bench,furnace.info);furnace.info.markNeedsSaving();return true;
        }
        UUID origin=UUID.nameUUIDFromBytes((batch.workplaceId()+":"+batch.destination()).getBytes(StandardCharsets.UTF_8));
        int collected=stock.smelting().collect(batch.id(),origin,batch.destination(),nativeBench.getOutputContainer());
        if(collected>0){furnace.info.markNeedsSaving();stock.markProductionWithdrawal();collectedWork.run();}
        if(nativeBench.getInputContainer().isEmpty()&&!nativeBench.isActive()&&nativeBench.getOutputContainer().isEmpty()) {
            var site=town.getApprenticeWorkplaces().find(batch.workplaceId());
            stock.finishFurnace(batch.id(),site==null?null:site.orders());return true;
        }
        return collected>0;
    }
}
