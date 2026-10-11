package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.workplace.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** Queues one preparation per site in task order. Native furnaces can continue processing independently. Stock leaves its real source only on courier pickup. */
public final class WorkplaceSupplyPlanner {
    private WorkplaceSupplyPlanner() {}
    private static final Map<World,Map<UUID,Long>> NEXT=Collections.synchronizedMap(new WeakHashMap<>());
    public static void tick(World world,TownManager manager,TownRecord town,AetherhavenPlugin plugin,long now) {
        Map<UUID,Long> next;
        synchronized(NEXT) { next=NEXT.computeIfAbsent(world,w->new HashMap<>()); }
        if(now<next.getOrDefault(town.getTownId(),0L)) return;
        next.put(town.getTownId(),now+2000);
        var stock=town.getWarehouse();boolean changed=false;
        WorkplaceBatchTimings.observe(town.getTownId(),town.getDisplayName(),stock.batches(),now);
        // A building that was emptied, moved or removed can no longer supply what was reserved from it. Drop such a
        // delivery now, so no courier keeps walking there, and let it be prepared again from wherever the stock is.
        var live=ProductionStockSources.loaded(world,town,plugin,now);
        for(var batch:List.copyOf(stock.batches())) {
            if(batch.cancelled()) continue;
            if(stock.sourceLost(batch.id(),live,id->town.findPlotById(id)!=null||town.getApprenticeWorkplaces().find(id)!=null)) {
                changed|=stock.cancel(batch.id());continue;
            }
            if(batch.herdFeed()) {
                if(HerdFeedSupplyPlanner.invalid(world,town,plugin,batch))changed|=stock.cancel(batch.id());
                continue;
            }
            var site=town.getApprenticeWorkplaces().find(batch.workplaceId());
            var order=site==null?null:site.orders().find(batch.orderId());
            var recipe=plugin.getApprenticeRecipes().find(batch.recipeId());
            var point=batch.destination();
            if(site==null||order==null||recipe==null||recipe.type()!=site.type()||(recipe.type()==TownsfolkWorkType.SMELTING&&!batch.smelting())
                ||(recipe.type()==TownsfolkWorkType.CULTIVATION)!=batch.cultivation()||point==null
                ||!site.area().contains(point.x()+.5,point.y()+.5,point.z()+.5)
                ||batch.cultivation()&&NativeCultivationService.plantMissing(world,batch)) changed|=stock.cancel(batch.id());
        }
        if(!WarehouseAccess.hasManager(town)) {
            if(changed) manager.updateTown(town);return;
        }
        var sources=live;
        int courierStacks=-1;
        for(var site:town.getApprenticeWorkplaces().sites()) {
            // Reserved garden work may finish or return supplies. Never reserve new garden batches.
            if(site.type()==null||!site.type().availableForNewWork())continue;
            // Furnaces retain native fuel, slots and processing. Their service jobs are separate.
            if(!stock.canPrepare(site.id(),site.type()==TownsfolkWorkType.SMELTING,site.type()==TownsfolkWorkType.CULTIVATION)) continue;
            WorkplaceEquipment.Snapshot equipment=null;
            for(var order:site.orders().list()) {
                if(order.paused()) continue;
                var recipe=plugin.getApprenticeRecipes().find(order.recipeId());
                if(recipe==null||recipe.type()!=site.type()||!recipe.unlocked(town)||!hasWorker(town,recipe)) continue;
                var source=recipe.source();if(source==null||recipe.nativeUnlockIssue(town,world)!=null||source.getOutputs()==null) continue;
                List<ItemStack> outputs=new ArrayList<>();boolean supported=true;
                for(var material:source.getOutputs()) {
                    if(material==null||material.getItemId()==null) {supported=false;break;}
                    var item=material.toItemStack();if(ItemStack.isEmpty(item)){supported=false;break;}outputs.add(item);
                }
                if(!supported||outputs.isEmpty()) continue;
                var primary=source.getPrimaryOutput();if(primary==null)primary=source.getOutputs()[0];
                String itemId=primary.getItemId();if(itemId==null)continue;
                int need=order.batchesNeeded(recipe.stored(stock,itemId),recipe.incoming(stock,itemId),primary.getQuantity(),stock.queuedForOrder(order.id()));
                if(need==0) continue;
                boolean forever=order.mode()==WorkplaceOrders.Mode.FOREVER;
                if(equipment==null)equipment=WorkplaceEquipment.scan(world,site.area());
                if(recipe.equipmentIssue(site,equipment)!=null) continue;
                if(site.type()==TownsfolkWorkType.CULTIVATION) {
                    var point=NativeCultivationService.findPlantPoint(world,site,stock);
                    if(point==null)continue;
                    var batch=stock.reserveCultivation(site.id(),order.id(),recipe.id(),AlchemicalCrop.fromBlock(recipe.output()),sources);
                    if(batch!=null){stock.setDestination(batch.id(),point);changed=true;break;}
                    continue;
                }
                boolean prepared=false;
                for(var bench:equipment.benches()) {
                    if(!recipe.acceptsBench(bench))continue;
                    var point=new WarehouseBatch.Point(bench.x(),bench.y(),bench.z());
                    WarehouseBatch batch;
                    if(site.type()==TownsfolkWorkType.SMELTING)batch=NativeFurnaceService.queue(world,town,site,order,recipe,point,sources,need,forever);
                    else {
                        // One trip brings what several crafts need. Try fewer when stock or carrying room falls short.
                        if(courierStacks<0)courierStacks=bestCourierStacks(town,plugin);
                        var inputs=source.getInput()==null?List.<com.hypixel.hytale.server.core.inventory.MaterialQuantity>of():Arrays.asList(source.getInput());
                        batch=null;
                        for(int crafts=WorkplaceBatchSize.crafts(need,forever);crafts>=1&&batch==null;crafts=WorkplaceBatchSize.smaller(crafts))
                            batch=stock.reserveCrafts(site.id(),order.id(),recipe.id(),inputs,outputs,sources,crafts,Math.max(courierStacks,inputs.size()));
                    }
                    if(batch!=null) {
                        stock.setDestination(batch.id(),point);changed=true;prepared=true;break;
                    }
                }
                if(prepared)break;
            }
        }
        changed|=HerdFeedSupplyPlanner.queue(world,town,plugin,sources,now);
        if(changed) manager.updateTown(town);
    }
    /** The most stacks any of the town's couriers can carry in one trip. One when nobody is a courier yet. */
    static int bestCourierStacks(TownRecord town,AetherhavenPlugin plugin) {
        int best=1;
        for(var row:town.getResidentNpcRecords()) {
            var identity=row.getTownsfolkIdentity();
            if(identity==null||!TownAdmission.isMember(town,row.getLastEntityUuid()))continue;
            String id=identity.getCharacterId();var progress=town.getTownsfolkProgress().get(id.toLowerCase(Locale.ROOT));
            if(progress==null||!progress.canPerform(TownsfolkWorkType.LOGISTICS,TownVillagerBinding.KIND_GUARD.equals(row.getKind()),town.getPayroll().overdue(id),!row.isPendingDawnRevival()))continue;
            best=Math.max(best,CourierAssignments.carryingStacks(progress,plugin.getProfessionKits()));
        }
        return best;
    }
    private static boolean hasWorker(TownRecord town,WorkplaceRecipe recipe) {
        for(var row:town.getResidentNpcRecords()) {
            var identity=row.getTownsfolkIdentity();
            if(identity==null||!TownAdmission.isMember(town,row.getLastEntityUuid()))continue;
            String id=identity.getCharacterId();var progress=town.getTownsfolkProgress().get(id.toLowerCase(Locale.ROOT));
            // A shop clerk stands at a counter all day. Preparing a job only they can do would hold the bench for nothing.
            if(com.hexvane.aetherhaven.autonomy.ApprenticeWorkService.staffsShop(town,id,progress,recipe.type()))continue;
            if(progress!=null&&progress.skill(recipe.skill())>=recipe.skillLevel()
                &&progress.canPerform(recipe.type(),TownVillagerBinding.KIND_GUARD.equals(row.getKind()),town.getPayroll().overdue(id),!row.isPendingDawnRevival()))return true;
        }
        return false;
    }
}
