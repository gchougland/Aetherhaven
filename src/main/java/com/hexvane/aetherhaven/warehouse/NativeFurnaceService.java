package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.*;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
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
        return queue(world,town,site,order,recipe,point,sources,1,false);
    }
    /** The most crafts whose input and output each still fit one furnace slot. */
    static int stackLimit(List<MaterialQuantity> inputs,List<ItemStack> outputs) {
        int limit=WorkplaceBatchSize.MAX_FURNACE_CRAFTS;
        for(var input:inputs) {
            if(input==null||input.getQuantity()<=0)continue;
            var item=input.getItemId()==null?null:Item.getAssetMap().getAsset(input.getItemId());
            // An ingredient named by kind may be met by items with different stack sizes. Stay with the smallest sensible load.
            int stack=item==null?input.getQuantity():Math.max(1,item.getMaxStack());
            limit=Math.min(limit,Math.max(1,stack/input.getQuantity()));
        }
        for(var output:outputs)if(!ItemStack.isEmpty(output))limit=Math.min(limit,Math.max(1,Math.max(1,output.getItem().getMaxStack())/output.getQuantity()));
        return Math.max(1,limit);
    }
    /** Fuel already in a furnace's slot that a load can burn: how many pieces, and how long they last. */
    record SlotFuel(int pieces,double burnSeconds) {}
    /**
     * Reads a furnace's fuel slot for a load that burns {@code id}.
     *
     * @param setAside true when the Apprentice may take a different fuel out of the slot before loading
     * @return null when the slot holds a different fuel that has to stay where it is
     */
    static SlotFuel slotFuel(ItemContainer fuel,String id,boolean setAside) {
        int pieces=0;double seconds=0;
        for(short slot=0;slot<fuel.getCapacity();slot++) {
            var held=fuel.getItemStack(slot);if(ItemStack.isEmpty(held))continue;
            if(!id.equals(held.getItemId())) {
                // A slot holds one kind of fuel. The old kind is taken out when the load goes in, so it is not counted.
                if(!setAside)return null;
                continue;
            }
            pieces+=held.getQuantity();seconds+=held.getQuantity()*held.getItem().getFuelQuality();
        }
        return new SlotFuel(pieces,seconds);
    }
    /**
     * @param need crafts the task still wants
     * @param forever true for a task with no end
     */
    public static WarehouseBatch queue(World world,TownRecord town,ApprenticeWorkplace site,WorkplaceOrders.Order order,WorkplaceRecipe recipe,WarehouseBatch.Point point,List<TownWarehouse.ProductionStock> sources,int need,boolean forever) {
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
        // First a fuel that fits what the slot already holds. Failing that, any chosen fuel: a furnace last fed wood keeps
        // a spare log, and one last fed charcoal keeps charcoal, which used to stop it taking the other fuel for good.
        // The Apprentice sets the old fuel with the furnace's goods when loading (TownWarehouse.loadFurnace).
        for(boolean setAside:new boolean[]{false,true})for(String id:candidates) {
            var item=Item.getAssetMap().getAsset(id);if(item==null||item.getFuelQuality()<=0)continue;
            boolean selected=!recipe.nativeCharcoal()&&site.charcoalFuel()?"Ingredient_Charcoal".equals(id):CraftingManager.matches(new MaterialQuantity(null,"Wood_Trunk",null,1,null),new ItemStack(id,1));
            if(!selected)continue;
            var inSlot=slotFuel(fuel,id,setAside);
            // The second pass is only for fuels the first one could not try.
            if(inSlot==null||setAside&&slotFuel(fuel,id,false)!=null)continue;
            double available=furnace.processing.getFuelTime()+inSlot.burnSeconds();boolean hasFuel=inSlot.pieces()>0;int existingFuel=inSlot.pieces();
            // Two logs produce one native charcoal byproduct. Do not add them to recipe input as well.
            if(recipe.nativeCharcoal()&&existingFuel>2)continue;
            var materials=recipe.nativeCharcoal()||source.getInput()==null?List.<MaterialQuantity>of():Arrays.asList(source.getInput());
            // A furnace takes whole stacks. Load as many crafts as the task wants and one slot holds, with fuel for all of
            // them, and try fewer when ore, fuel or storage room falls short.
            int most=recipe.nativeCharcoal()?1:WorkplaceBatchSize.furnaceCrafts(need,forever,stackLimit(materials,outputs));
            for(int crafts=most;crafts>=1;crafts=WorkplaceBatchSize.smaller(crafts)) {
                int quantity=recipe.nativeCharcoal()?2-existingFuel:Math.max(hasFuel?0:1,(int)Math.ceil(Math.max(0,crafts*source.getTimeSeconds()-available)/item.getFuelQuality()));
                // A long load must not run dry part way: a furnace that stops with ore still in it would wait forever.
                // One spare piece of fuel covers rounding, and whatever is left over serves the next load.
                if(crafts>1&&!recipe.nativeCharcoal()&&existingFuel+quantity+1<=Math.max(1,item.getMaxStack()))quantity++;
                // The fuel slot holds one stack.
                if(existingFuel+quantity>Math.max(1,item.getMaxStack()))continue;
                var batch=stock.reserveSmeltingCrafts(site.id(),order.id(),recipe.id(),materials,outputs,sources,id,quantity,crafts,existingFuel+quantity);
                if(batch!=null){stock.setDestination(batch.id(),point);return batch;}
            }
        }
        return null;
    }
    /** A worker can leave during processing and return once native output is ready. */
    public static boolean needsService(World world,WarehouseBatch batch) {
        var furnace=at(world,batch.destination());if(furnace==null)return false;
        if(!batch.nativeLoaded())return batch.state()==WarehouseBatch.State.READY;
        // A load is many items now. Come back when the furnace has stopped, not for every ingot. A full output would
        // stall it, so that also brings the worker.
        if(!furnace.processing.isActive())return true;
        var output=furnace.processing.getOutputContainer();
        for(short slot=0;slot<output.getCapacity();slot++)if(ItemStack.isEmpty(output.getItemStack(slot)))return false;
        return true;
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
        UUID origin=SmeltingOutputStore.originId(batch.workplaceId(),batch.destination());
        int collected=stock.smelting().collect(batch.id(),origin,batch.destination(),nativeBench.getOutputContainer());
        if(collected>0){furnace.info.markNeedsSaving();stock.markProductionWithdrawal();collectedWork.run();}
        if(nativeBench.getInputContainer().isEmpty()&&!nativeBench.isActive()&&nativeBench.getOutputContainer().isEmpty()) {
            var site=town.getApprenticeWorkplaces().find(batch.workplaceId());
            stock.finishFurnace(batch.id(),site==null?null:site.orders());return true;
        }
        return collected>0;
    }
}
