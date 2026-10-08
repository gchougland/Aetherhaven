package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hexvane.aetherhaven.inventory.AtomicMaterialExchange;
import org.bson.BsonDocument;
import java.util.*;

/** Town-owned stock. Access desks are views, never new inventories or extra capacity. */
public final class TownWarehouse {
    public record ProductionStock(UUID plot,WarehouseBatch.Point pickup,ProductionInventory state) {}
    public enum Page { GENERAL, OUTPUT }
    public enum Amount { STACK, ONE, ALL_MATCHING }
    public record Transfer(int moved, String error) {}
    public record CraftingSlot(Page page,short slot,ItemStack stack,int reserved) {
        public int available(){return ItemStack.isEmpty(stack)?0:Math.max(0,stack.getQuantity()-reserved);}
    }
    public record CraftingStock(long revision,List<CraftingSlot> slots) {
        public CraftingStock { slots=List.copyOf(slots); }
        public long count(String item) {
            return slots.stream().filter(s->!ItemStack.isEmpty(s.stack())&&item.equals(s.stack().getItemId()))
                .mapToLong(CraftingSlot::available).sum();
        }
    }
    private SmeltingOutputStore smelting;
    public SmeltingOutputStore smelting() { if(smelting==null)smelting=new SmeltingOutputStore();return smelting; }
    private int storageUpgrades;
    private long revision;
    private String generalData, outputData;
    private transient SimpleItemContainer general, output;
    private transient Map<UUID,Boolean> outputRoomCache=new HashMap<>();
    private transient long outputRoomRevision=-1;
    private List<WarehouseBatch> batches = new ArrayList<>();
    public static final int BASE_GENERAL_SLOTS = 40, UPGRADE_SLOTS = 20, MAX_UPGRADES = 8;
    public static final int OUTPUT_SLOTS = 40;
    /** Natural growth can overlap; seed delivery and attended planting remain serial. */
    public static final int CULTIVATION_BEDS = 6;

    public int upgrades() { return Math.clamp(storageUpgrades,0,MAX_UPGRADES); }
    public int capacity() { return BASE_GENERAL_SLOTS + UPGRADE_SLOTS * upgrades(); }
    public long revision() { return revision; }
    public long upgradeCost() { return 150L * (upgrades()+1); }
    public int upgradeTownLevel() { return Math.min(6, 2+upgrades()); }
    private SimpleItemContainer inventory(Page page) {
        if(page==Page.GENERAL) {
            if(general==null) general=decode(generalData,capacity());
            return general;
        }
        if(output==null) output=decode(outputData,OUTPUT_SLOTS);
        return output;
    }
    public int slots(Page page) { return inventory(page).getCapacity(); }
    public ItemStack stack(Page page, short slot) {
        var inv=inventory(page); return slot>=0 && slot<inv.getCapacity()?inv.getItemStack(slot):null;
    }
    public int used(Page page) {
        var inv=inventory(page); int used=0;
        for(short i=0;i<inv.getCapacity();i++) if(!ItemStack.isEmpty(inv.getItemStack(i))) used++;
        return used;
    }
    public long count(Page page,String itemId) {
        var inv=inventory(page); long total=0;
        for(short i=0;i<inv.getCapacity();i++) { var item=inv.getItemStack(i); if(!ItemStack.isEmpty(item)&&itemId.equals(item.getItemId())) total+=item.getQuantity(); }
        return total;
    }
    /** Available general and finished stock only. Courier reservations remain untouched. World thread only. */
    public CraftingStock craftingStock() {
        var slots=new ArrayList<CraftingSlot>();
        for(var page:Page.values())for(short slot=0;slot<slots(page);slot++)
            slots.add(new CraftingSlot(page,slot,stack(page,slot),reserved(page,slot)));
        return new CraftingStock(revision,slots);
    }
    /** Native bench adapter: consume only the unreserved portion, retaining original metadata. */
    public boolean consumeForCrafting(Page page, short slot, ItemStack expected, int count) {
        var current=stack(page,slot);
        if(count<=0||ItemStack.isEmpty(current)||!current.equals(expected)||count>current.getQuantity()-reserved(page,slot))return false;
        inventory(page).setItemStackForSlot(slot,count==current.getQuantity()?null:current.withQuantity(current.getQuantity()-count),false);
        changed();return true;
    }
    /** Worker eligibility needs one available item, without allocating a full inventory snapshot. */
    public boolean hasUnreservedStock(String item) {
        if(item==null||item.isBlank())return false;
        for(var page:Page.values())for(short slot=0;slot<slots(page);slot++) {
            var held=stack(page,slot);
            if(!ItemStack.isEmpty(held)&&item.equals(held.getItemId())&&held.getQuantity()>reserved(page,slot))return true;
        }
        return false;
    }
    /** World thread only. Stage one unreserved fitting, place it, then commit the exact material cost. */
    public boolean placeFromStock(String item,java.util.function.BooleanSupplier place) {
        if(item==null||item.isBlank()||place==null)return false;
        for(var page:Page.values())for(short slot=0;slot<slots(page);slot++) {
            var stack=stack(page,slot);
            if(ItemStack.isEmpty(stack)||!item.equals(stack.getItemId())||stack.getQuantity()<=reserved(page,slot))continue;
            var next=WarehouseBatch.copy(inventory(page));
            next.setItemStackForSlot(slot,stack.getQuantity()==1?null:stack.withQuantity(stack.getQuantity()-1),false);
            // Encode before changing terrain so a codec failure cannot leave a free placement.
            String encoded=SimpleItemContainer.CODEC.encode(next,new ExtraInfo()).asDocument().toJson();
            if(!place.getAsBoolean())return false;
            if(page==Page.GENERAL){general=next;generalData=encoded;}else{output=next;outputData=encoded;}
            revision++;return true;
        }
        return false;
    }
    /** Caller must check container permissions. Bag costs are spent first, then town stock, in one native transaction. */
    public boolean exchangeForCrafting(ItemContainer player,short outputSlot,ItemStack expected,ItemStack replacement,
                                      Map<String,Integer> costs,CraftingStock snapshot) {
        return exchangeForCrafting(player,outputSlot,expected,replacement,costs,snapshot,()->true,Map::isEmpty);
    }
    /** Additional production sources must be snapshotted and spent atomically on this same owning world thread. */
    public boolean exchangeForCrafting(ItemContainer player,short outputSlot,ItemStack expected,ItemStack replacement,
                                      Map<String,Integer> costs,CraftingStock snapshot,java.util.function.BooleanSupplier additionalValid,
                                      java.util.function.Predicate<Map<String,Integer>> spendAdditional) {
        if(player==null||outputSlot<0||outputSlot>=player.getCapacity()||snapshot==null)return false;
        int generalOffset=player.getCapacity(),outputOffset=generalOffset+slots(Page.GENERAL);
        var reservations=new HashMap<Integer,Integer>();
        for(var slot:snapshot.slots())reservations.put((slot.page()==Page.GENERAL?generalOffset:outputOffset)+slot.slot(),slot.reserved());
        var combined=new CombinedItemContainer(player,inventory(Page.GENERAL),inventory(Page.OUTPUT));
        boolean accepted=AtomicMaterialExchange.exchange(combined,outputSlot,expected,replacement,costs,
            slot->reservations.getOrDefault(slot,0),()->snapshot.equals(craftingStock())&&additionalValid.getAsBoolean(),spendAdditional);
        if(accepted&&!snapshot.equals(craftingStock()))changed();
        return accepted;
    }
    public Transfer deposit(Page page,ItemContainer player,short slot,ItemStack expected,Amount amount,long seenRevision) {
        if(page!=Page.GENERAL) return new Transfer(0,"Finished output only accepts items made by the town.");
        if(seenRevision!=revision) return stale();
        return transfer(player,inventory(page),slot,expected,amount);
    }
    public Transfer withdraw(Page page,ItemContainer player,short slot,ItemStack expected,Amount amount,long seenRevision) {
        if(seenRevision!=revision) return stale();
        var from=inventory(page);
        if(slot<0||slot>=from.getCapacity()||ItemStack.isEmpty(expected)||!expected.equals(from.getItemStack(slot))) return stale();
        int moved=0;
        boolean held=false;
        for(short i=0;i<from.getCapacity();i++) {
            var current=from.getItemStack(i);
            if(ItemStack.isEmpty(current)||(amount==Amount.ALL_MATCHING?!expected.isStackableWith(current):i!=slot)) continue;
            int reserved=reserved(page,i);
            held|=reserved>0;
            int available=current.getQuantity()-reserved;
            if(available>0) moved+=move(from,player,i,amount==Amount.ONE?1:available);
        }
        if(moved>0) changed();
        return new Transfer(moved,moved>0?null:held?"Those supplies are reserved for a workplace.":"There is no room for that item.");
    }
    private Transfer transfer(ItemContainer from,ItemContainer to,short slot,ItemStack expected,Amount amount) {
        if(slot<0||slot>=from.getCapacity()||ItemStack.isEmpty(expected)||!expected.equals(from.getItemStack(slot))) return stale();
        int moved=0;
        if(amount==Amount.ALL_MATCHING) {
            for(short i=0;i<from.getCapacity();i++) {
                var stack=from.getItemStack(i);
                if(!ItemStack.isEmpty(stack)&&expected.isStackableWith(stack)) moved+=move(from,to,i,stack.getQuantity());
            }
        } else moved=move(from,to,slot,amount==Amount.ONE?1:expected.getQuantity());
        if(moved>0) changed();
        return new Transfer(moved,moved==0?"There is no room for that item.":null);
    }
    private static int move(ItemContainer from,ItemContainer to,short slot,int count) {
        int before=from.getItemStack(slot).getQuantity();
        from.moveItemStackFromSlot(slot,count,to,false,true);
        var remaining=from.getItemStack(slot);
        return before-(ItemStack.isEmpty(remaining)?0:remaining.getQuantity());
    }
    public boolean upgrade(long seenRevision) {
        if(seenRevision!=revision||upgrades()>=MAX_UPGRADES) return false;
        var old=inventory(Page.GENERAL);
        var expanded=new SimpleItemContainer((short)(capacity()+UPGRADE_SLOTS));
        for(short i=0;i<old.getCapacity();i++) {
            var stack=old.getItemStack(i);
            if(!ItemStack.isEmpty(stack)) expanded.setItemStackForSlot(i,stack,false);
        }
        storageUpgrades=upgrades()+1; general=expanded; changed(); return true;
    }
    /** Called by completed work, never by player deposit handlers. No spill or fallback. */
    public boolean acceptCompletedOutput(ItemStack stack) {
        if(ItemStack.isEmpty(stack)) return false;
        if(!roomForOutputs(List.of(stack))) return false;
        var tx=inventory(Page.OUTPUT).addItemStack(stack,true,false,true);
        if(!tx.succeeded()) return false;
        changed(); return true;
    }
    public void markProductionWithdrawal() { revision++; }
    /** Patrol guards deliver only after physically reaching a town storage desk. */
    public boolean acceptGuardSpoils(ItemStack stack) {
        if(ItemStack.isEmpty(stack))return false;
        var next=WarehouseBatch.copy(inventory(Page.GENERAL));
        if(!next.addItemStack(stack,true,false,true).succeeded())return false;
        general=next;changed();return true;
    }
    /** Only exchanges validated, single item equipment. This is not a general output deposit path. */
    public String equipProfessionKit(Page page, short slot, ItemStack expected, long seenRevision,
            com.hexvane.aetherhaven.townsfolk.TownsfolkProgress progress,
            com.hexvane.aetherhaven.equipment.ProfessionKitCatalog catalog, ItemStack expectedEquipped) {
        if (seenRevision != revision) return "Storage changed. Choose a kit again.";
        if (reserved(page, slot) > 0) return "That kit is reserved for a workplace.";
        String error = com.hexvane.aetherhaven.equipment.ProfessionKitService.equip(progress, catalog, inventory(page), slot, expected, expectedEquipped);
        if (error == null) changed();
        return error;
    }
    public String equipGuardKit(Page page,short slot,ItemStack expected,long seenRevision,
            com.hexvane.aetherhaven.townsfolk.TownsfolkProgress progress,
            com.hexvane.aetherhaven.equipment.GuardKitCatalog catalog,String guardClass,ItemStack previous) {
        if(seenRevision!=revision)return "Storage changed. Choose a kit again.";
        if(reserved(page,slot)>0)return "That kit is reserved for a workplace.";
        String error=com.hexvane.aetherhaven.equipment.GuardKitService.equip(progress,catalog,guardClass,inventory(page),slot,expected,previous);
        if(error==null)changed();
        return error;
    }
    private static Transfer stale() { return new Transfer(0,"Storage changed. The view has been refreshed. Try again."); }
    private void changed() {
        // Encode before the owning TownRecord is saved. Gson never touches native container locks.
        if(general!=null) generalData=SimpleItemContainer.CODEC.encode(general,new ExtraInfo()).asDocument().toJson();
        if(output!=null) outputData=SimpleItemContainer.CODEC.encode(output,new ExtraInfo()).asDocument().toJson();
        revision++;
    }

    public List<WarehouseBatch> batches() { return Collections.unmodifiableList(batches); }
    public WarehouseBatch batch(UUID id) { return batches.stream().filter(b->b.id().equals(id)).findFirst().orElse(null); }
    /** Native processing frees the apprentice to prepare another furnace at this site. */
    public boolean canPrepare(UUID workplace,boolean furnace) {
        return canPrepare(workplace,furnace,false);
    }
    public boolean canPrepare(UUID workplace,boolean furnace,boolean cultivation) {
        long active=batches.stream().filter(b->b.workplaceId().equals(workplace)).count();
        if(cultivation&&active>=CULTIVATION_BEDS)return false;
        return batches.stream().filter(b->b.workplaceId().equals(workplace))
            .allMatch(b->furnace&&b.smelting()&&b.nativeLoaded()
                ||cultivation&&b.cultivation()&&b.cropPlanted()&&!b.cancelled());
    }
    public int queuedForOrder(UUID order) {
        return (int)batches.stream().filter(b->b.orderId().equals(order)&&!b.cancelled()).count();
    }
    public boolean destinationReserved(WarehouseBatch.Point point) {
        return point!=null&&batches.stream().anyMatch(b->point.equals(b.destination()));
    }
    public WarehouseBatch workplaceBatch(UUID workplace) { return batches.stream().filter(b->b.workplaceId().equals(workplace)).findFirst().orElse(null); }
    /** Pin the physical buffer location before any courier departs. Resizing cannot teleport supplies. */
    public boolean setDestination(UUID id,WarehouseBatch.Point point) {
        var batch=batch(id);
        if(batch==null||point==null||batch.destination()!=null||!batch.cargoItems().isEmpty()||!batch.deliveredItems().isEmpty()) return false;
        batch.destination(point); changed(); return true;
    }
    public int reserved(Page page,short slot) {
        int count=0;
        for(var batch:batches) for(var source:batch.sources()) if(source.page()==page&&source.slot()==slot) {
            var stack=batch.pending().getItemStack(source.batchSlot());
            if(!ItemStack.isEmpty(stack)) count=Math.addExact(count,stack.getQuantity());
        }
        return count;
    }
    public long reservedProduction(UUID plot,String item) {
        long count=0;
        for(var batch:batches)for(var source:batch.sources())if(plot.equals(source.plot())) {
            var stack=batch.pending().getItemStack(source.batchSlot());
            if(!ItemStack.isEmpty(stack)&&item.equals(stack.getItemId()))count+=stack.getQuantity();
        }
        return count;
    }
    public int reservedSmelting(UUID origin,short slot) {
        int total=0;
        for(var batch:batches)for(var source:batch.sources())if(origin.equals(source.plot())&&slot==source.slot()) {
            var held=batch.pending().getItemStack(source.batchSlot());if(!ItemStack.isEmpty(held))total=Math.addExact(total,held.getQuantity());
        }
        return total;
    }
    public long incoming(String itemId) {
        long count=0;
        for(var batch:batches) if(!batch.cancelled()) for(var stack:batch.outputItems())
            if(stack.getItemId().equals(itemId)) count+=stack.getQuantity();
        return count;
    }
    /** World thread only. Uses native recipe matching, including resource types, tags and exclusions. */
    public WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results) {
        return reserve(workplace,order,recipe,materials,results,List.of());
    }
    public WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production) {
        return reserve(workplace,order,recipe,materials,results,production,null,0);
    }
    /** A cultivation promise represents one real seed and one eventual native harvest. */
    public WarehouseBatch reserveCultivation(UUID workplace,UUID order,String recipe,
            com.hexvane.aetherhaven.workplace.AlchemicalCrop crop,List<ProductionStock> production) {
        if(crop==null)return null;
        var batch=reserve(workplace,order,recipe,List.of(new MaterialQuantity(crop.seed(),null,null,1,null)),
            List.of(new ItemStack(crop.item(),1)),production,null,0,true);
        if(batch!=null){batch.markCultivation();changed();}
        return batch;
    }
    /** Fuel is a concrete selected item so the service job cannot silently change the player's choice. */
    public WarehouseBatch reserveSmelting(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production,String fuelItem,int fuelQuantity) {
        if(fuelItem==null||fuelItem.isBlank()||fuelQuantity<0||fuelQuantity>100)return null;
        var withFuel=new ArrayList<>(materials);if(fuelQuantity>0)withFuel.add(new MaterialQuantity(fuelItem,null,null,fuelQuantity,null));
        return reserve(workplace,order,recipe,withFuel,results,production,fuelItem,fuelQuantity);
    }
    /** A physical supply trip, with no recipe output or phantom reservation in the finished goods inventory. */
    public WarehouseBatch reserveHerdFeed(UUID plot,WarehouseBatch.Point destination,String food,int quantity,List<ProductionStock> sources) {
        if(destination==null||quantity<1||quantity>com.hexvane.aetherhaven.production.HerdProductionState.FEED_CAPACITY)return null;
        if(!com.hexvane.aetherhaven.production.BarnAnimalCatalog.food(food))return null;
        var batch=reserve(plot,plot,"aetherhaven:barn_feed/"+food,List.of(new MaterialQuantity(
            food,null,null,quantity,null)),List.of(),sources,null,0,false,true);
        if(batch!=null){batch.markHerdFeed();batch.destination(destination);batch.save();changed();}
        return batch;
    }
    private WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production,String fuelItem,int fuelQuantity) {
        return reserve(workplace,order,recipe,materials,results,production,fuelItem,fuelQuantity,false);
    }
    private WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production,String fuelItem,int fuelQuantity,boolean cultivation) {
        return reserve(workplace,order,recipe,materials,results,production,fuelItem,fuelQuantity,cultivation,false);
    }
    private WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production,String fuelItem,int fuelQuantity,boolean cultivation,boolean herdFeed) {
        if(workplace==null||order==null||recipe==null||recipe.isBlank()||materials==null||results==null||!herdFeed&&results.isEmpty()
            ||!canPrepare(workplace,fuelItem!=null,cultivation)||batches.size()>=256) return null;
        if(materials.stream().anyMatch(m->m==null||m.getQuantity()<=0)||results.stream().anyMatch(s->ItemStack.isEmpty(s))) return null;
        if(!herdFeed&&fuelItem==null&&!roomForOutputs(results)) return null;
        var promised=new SimpleItemContainer(WarehouseBatch.BUFFER_SLOTS);
        if(!addAll(promised,results)) return null;
        // Source addresses accompany the scratch slots. A town total is never a pickup location.
        List<WarehouseBatch.Source> addresses=new ArrayList<>();List<ItemStack> stocks=new ArrayList<>();
        long demand=materials.stream().mapToLong(MaterialQuantity::getQuantity).sum();
        Set<UUID> seenPlots=new HashSet<>();
        for(var source:production) {
            if(source.plot()==null||source.pickup()==null||source.state()==null||!seenPlots.add(source.plot()))continue;
            for(var held:source.state().getAmounts().entrySet()) {
                if(com.hexvane.aetherhaven.production.ProductionWithdrawal.isMilkBucketItem(held.getKey()))continue;
                long availableCount=Math.max(0,held.getValue()-reservedProduction(source.plot(),held.getKey()));
                int count=(int)Math.min(Integer.MAX_VALUE,Math.min(demand,availableCount));
                if(count==0)continue;
                addresses.add(new WarehouseBatch.Source(null,(short)-1,(short)-1,source.plot(),source.pickup()));
                stocks.add(new ItemStack(held.getKey(),count));
            }
        }
        for(var origin:smelting().origins())for(short slot=0;slot<origin.slots();slot++) {
            var held=origin.stack(slot);if(ItemStack.isEmpty(held))continue;
            int remaining=held.getQuantity()-reservedSmelting(origin.id(),slot);
            if(remaining<0)throw new IllegalStateException("Reserved smelting stock is missing");
            if(remaining==0)continue;
            addresses.add(new WarehouseBatch.Source(null,slot,(short)-1,origin.id(),origin.location()));stocks.add(held.withQuantity(remaining));
        }
        // Specific crafting output is player collected. It is not a remote ingredient depot.
        for(short slot=0;slot<inventory(Page.GENERAL).getCapacity();slot++) {
            var stack=inventory(Page.GENERAL).getItemStack(slot);
            if(ItemStack.isEmpty(stack)) continue;
            int remaining=stack.getQuantity()-reserved(Page.GENERAL,slot);
            if(remaining<0) throw new IllegalStateException("Reserved stock is missing");
            if(remaining>0) {addresses.add(new WarehouseBatch.Source(Page.GENERAL,slot,(short)-1));stocks.add(stack.withQuantity(remaining));}
        }
        if(stocks.size()>Short.MAX_VALUE)return null;
        var available=new SimpleItemContainer((short)Math.max(1,stocks.size()));
        for(short slot=0;slot<stocks.size();slot++)available.setItemStackForSlot(slot,stocks.get(slot),false);
        var before=WarehouseBatch.copy(available);
        // Check each removal on the scratch inventory. Native bulk transactions can report
        // success even when overlapping ingredient queries exhaust one another's stock.
        for(var material:materials) {
            var removed=available.removeMaterial(material,true,true,false);
            // The installed server retains the original quantity in the aggregate remainder;
            // the last successful slot transaction carries the actual amount still needed.
            if(!removed.succeeded()||removed.getList().isEmpty()||removed.getList().getLast().getRemainder()!=0) return null;
        }
        var pending=new SimpleItemContainer(WarehouseBatch.BUFFER_SLOTS);
        List<WarehouseBatch.Source> sources=new ArrayList<>();
        for(short slot=0;slot<before.getCapacity();slot++) {
            var original=before.getItemStack(slot); if(ItemStack.isEmpty(original)) continue;
            var remaining=available.getItemStack(slot);
            int taken=original.getQuantity()-(ItemStack.isEmpty(remaining)?0:remaining.getQuantity());
            if(taken==0) continue;
            if(sources.size()>=WarehouseBatch.BUFFER_SLOTS) return null;
            short batchSlot=(short)sources.size();
            pending.setItemStackForSlot(batchSlot,original.withQuantity(taken),false);
            var address=addresses.get(slot);
            sources.add(new WarehouseBatch.Source(address.page(),address.slot(),batchSlot,address.plot(),address.pickup()));
        }
        var batch=new WarehouseBatch(workplace,order,recipe,sources,pending,promised);
        if(fuelItem!=null) {
            var promisedOutput=new ArrayList<>(results);
            if(!"Ingredient_Charcoal".equals(fuelItem)&&results.stream().noneMatch(s->"Ingredient_Charcoal".equals(s.getItemId())))promisedOutput.add(new ItemStack("Ingredient_Charcoal",Math.max(1,(fuelQuantity+1)/2)));
            if(!smelting().reserve(batch.id(),promisedOutput))return null;
            batch.smelting(fuelItem,fuelQuantity);
        }
        batches.add(batch); changed(); return batch;
    }
    /** Only mutate disposable staging inventories here, since a later stack may not fit. */
    private static boolean addAll(SimpleItemContainer staging,List<ItemStack> stacks) {
        for(var stack:stacks) {
            var added=staging.addItemStack(stack,true,false,false);
            if(!added.succeeded()||!ItemStack.isEmpty(added.getRemainder())) return false;
        }
        return true;
    }
    private boolean roomForOutputs(List<ItemStack> extra) {
        var shadow=WarehouseBatch.copy(inventory(Page.OUTPUT));
        for(var batch:batches) if(!batch.cancelled()&&!batch.smelting()&&!addAll(shadow,batch.outputItems())) return false;
        return extra.isEmpty()||addAll(shadow,extra);
    }
    /** Recheck prepared output after inventory changes without cloning an inventory on every work tick. */
    public boolean hasOutputRoom(UUID id) {
        var batch=batch(id);
        if(batch==null||batch.cancelled()||batch.smelting()||batch.herdFeed())return false;
        if(outputRoomRevision!=revision) {outputRoomCache.clear();outputRoomRevision=revision;}
        return outputRoomCache.computeIfAbsent(id,unused->addAll(WarehouseBatch.copy(inventory(Page.OUTPUT)),batch.outputItems()));
    }
    private boolean carrierAvailable(String courier) {
        return courier!=null&&!courier.isBlank()&&batches.stream().noneMatch(b->courier.equals(b.courierId()));
    }
    /** Only call after the courier reaches the pickup location. At most the courier's stack allowance leaves storage. */
    public int pickup(UUID id,String courier,int stackLimit) {
        return pickupAt(id,courier,stackLimit,null,null);
    }
    /** A null production source means General storage only. A pickup never sweeps other buildings. */
    public int pickupAt(UUID id,String courier,int stackLimit,ProductionStock production,WarehouseBatch.Point reached) {
        var batch=batch(id);
        if(batch==null||batch.cancelled()||!carrierAvailable(courier)||!batch.cargoItems().isEmpty()||stackLimit<1||stackLimit>WarehouseBatch.CARGO_SLOTS) return 0;
        var pending=WarehouseBatch.copy(batch.pending());
        var nextGeneral=WarehouseBatch.copy(inventory(Page.GENERAL));
        if(production!=null&&!Objects.equals(production.pickup(),reached))return 0;
        Map<String,Long> removedProduction=new HashMap<>();
        var cargo=new SimpleItemContainer((short)stackLimit);
        int total=0;
        for(var source:batch.sources()) {
            if(production==null?(source.page()!=Page.GENERAL||source.plot()!=null):(!production.plot().equals(source.plot())||!Objects.equals(source.pickup(),reached)))continue;
            var held=pending.getItemStack(source.batchSlot()); if(ItemStack.isEmpty(held)) continue;
            var current=production==null?nextGeneral.getItemStack(source.slot()):held.withQuantity((int)Math.min(Integer.MAX_VALUE,production.state().getAmount(held.getItemId())-removedProduction.getOrDefault(held.getItemId(),0L)));
            if(ItemStack.isEmpty(current)||!held.isStackableWith(current)||current.getQuantity()<held.getQuantity())
                return 0;
            int moved=move(pending,cargo,source.batchSlot(),held.getQuantity());
            if(moved>0) {
                if(production==null)nextGeneral.setItemStackForSlot(source.slot(),current.withQuantity(current.getQuantity()-moved),false);
                else removedProduction.merge(held.getItemId(),(long)moved,Long::sum);
                total+=moved;
            }
        }
        if(total==0) return 0;
        var savedCargo=new SimpleItemContainer(WarehouseBatch.CARGO_SLOTS);
        if(!addAll(savedCargo,WarehouseBatch.items(cargo))) throw new IllegalStateException("Delivery cargo does not fit");
        if(production!=null&&!production.state().withdrawAll(removedProduction))return 0;
        general=nextGeneral; batch.pending(pending); batch.cargo(savedCargo,courier); batch.save(); changed(); return total;
    }
    public int pickupSmeltingAt(UUID id,String courier,int stackLimit,UUID originId,WarehouseBatch.Point reached) {
        var batch=batch(id);var origin=smelting().origin(originId);
        if(batch==null||batch.cancelled()||origin==null||!origin.location().equals(reached)||!carrierAvailable(courier)||stackLimit<1||stackLimit>WarehouseBatch.CARGO_SLOTS)return 0;
        var pending=WarehouseBatch.copy(batch.pending());var cargo=new SimpleItemContainer((short)stackLimit);
        Map<Short,ItemStack> allocations=new LinkedHashMap<>();int total=0;
        for(var source:batch.sources()) {
            if(!originId.equals(source.plot())||!reached.equals(source.pickup()))continue;
            var held=pending.getItemStack(source.batchSlot());if(ItemStack.isEmpty(held))continue;
            int moved=move(pending,cargo,source.batchSlot(),held.getQuantity());
            if(moved>0){allocations.put(source.slot(),held.withQuantity(moved));total+=moved;}
        }
        if(total==0)return 0;
        var savedCargo=new SimpleItemContainer(WarehouseBatch.CARGO_SLOTS);
        if(!addAll(savedCargo,WarehouseBatch.items(cargo)))return 0;
        if(!smelting().pickup(originId,reached,allocations))return 0;
        batch.pending(pending);batch.cargo(savedCargo,courier);batch.save();changed();return total;
    }
    /** Only call at the workplace. A stale arrival or another courier cannot deliver the same cargo twice. */
    public boolean deliver(UUID id,String courier) {
        var batch=batch(id);
        if(batch==null||batch.herdFeed()||batch.cancelled()||courier==null||!courier.equals(batch.courierId())||batch.cargoItems().isEmpty()) return false;
        var delivered=WarehouseBatch.copy(batch.delivered());
        if(!addAll(delivered,batch.cargoItems())) return false;
        batch.delivered(delivered); batch.cargo(new SimpleItemContainer(WarehouseBatch.CARGO_SLOTS),null); batch.save(); changed(); return true;
    }
    /** Only after arrival at the pinned barn storage. Excess cargo is physically returned, never discarded. */
    public boolean deliverHerdFeed(UUID id,String courier,com.hexvane.aetherhaven.production.HerdProductionState herd,WarehouseBatch.Point reached) {
        var batch=batch(id);
        if(batch==null||!batch.herdFeed()||batch.cancelled()||herd==null||reached==null||!reached.equals(batch.destination())
            ||courier==null||!courier.equals(batch.courierId())||batch.cargoItems().isEmpty())return false;
        String food=com.hexvane.aetherhaven.warehouse.HerdFeedSupplyPlanner.food(batch);
        if(food==null)return cancel(id);
        if(batch.cargoItems().stream().anyMatch(s->!food.equals(s.getItemId())))return false;
        int quantity=batch.cargoItems().stream().mapToInt(ItemStack::getQuantity).sum();
        int accepted=Math.min(quantity,herd.courierFeedNeeded(food));
        if(accepted==0)return cancel(id);
        var remainder=WarehouseBatch.copy(batch.cargo());int left=accepted;
        for(short slot=0;slot<remainder.getCapacity()&&left>0;slot++) {
            var stack=remainder.getItemStack(slot);if(ItemStack.isEmpty(stack))continue;
            int take=Math.min(left,stack.getQuantity());
            remainder.setItemStackForSlot(slot,stack.withQuantity(stack.getQuantity()-take),false);left-=take;
        }
        if(left!=0||!herd.receiveCourierFeed(food,accepted))return false;
        batch.cargo(remainder,remainder.isEmpty()?null:courier);
        if(herd.courierFeedNeeded(food)==0){batch.cancel();removeReturned(batch);}
        else if(batch.pendingItems().isEmpty()&&batch.cargoItems().isEmpty())batches.remove(batch);
        batch.save();changed();return true;
    }
    /** Called only by the physical work executor when the batch finishes. Consumption and output commit together. */
    public boolean finish(UUID id) {
        return finish(id,java.util.function.UnaryOperator.identity());
    }
    private boolean finish(UUID id,java.util.function.UnaryOperator<ItemStack> decorate) {
        var batch=batch(id);
        if(batch==null||batch.smelting()||batch.cultivation()||batch.herdFeed()||batch.state()!=WarehouseBatch.State.READY) return false;
        // Do not roll or appraise anything until ordinary output space is available.
        if(!addAll(WarehouseBatch.copy(inventory(Page.OUTPUT)),batch.outputItems()))return false;
        var results=new ArrayList<ItemStack>();
        for(var planned:batch.outputItems()) {
            var result=decorate.apply(planned);
            if(ItemStack.isEmpty(result)||!planned.getItemId().equals(result.getItemId())||planned.getQuantity()!=result.getQuantity())return false;
            results.add(result);
        }
        var nextOutput=WarehouseBatch.copy(inventory(Page.OUTPUT));
        if(!addAll(nextOutput,results)) return false;
        output=nextOutput; batches.remove(batch); changed(); return true;
    }
    /** Only the physically present worker supplies tick time. Never accepts elapsed wall-clock catch-up. */
    public boolean advanceWork(UUID id,double seconds) {
        return advanceWork(id,seconds,1);
    }
    /** Validate attended tick time before applying an equipment bonus, so slow ticks cannot discard boosted work. */
    public boolean advanceWork(UUID id,double seconds,double workMultiplier) {
        var batch=batch(id);
        if(batch==null||batch.smelting()||batch.state()!=WarehouseBatch.State.READY||!Double.isFinite(seconds)||seconds<=0||seconds>.25
            ||!Double.isFinite(workMultiplier)||workMultiplier<1||workMultiplier>2||!hasOutputRoom(id))return false;
        batch.advanceWork(seconds*workMultiplier);return true;
    }
    public boolean finishWork(UUID id,com.hexvane.aetherhaven.workplace.WorkplaceOrders orders,double requiredSeconds) {
        return finishWork(id,orders,requiredSeconds,java.util.function.UnaryOperator.identity());
    }
    public boolean finishWork(UUID id,com.hexvane.aetherhaven.workplace.WorkplaceOrders orders,double requiredSeconds,
            java.util.function.UnaryOperator<ItemStack> decorate) {
        var batch=batch(id);
        if(batch==null||!Double.isFinite(requiredSeconds)||requiredSeconds<=0||batch.workSeconds()<requiredSeconds
            ||!orders.canComplete(batch.orderId(),batch.recipeId()))return false;
        UUID order=batch.orderId();
        if(!finish(id,decorate))return false;
        orders.recordCompletion(order);return true;
    }
    /** World thread only. Commit the seed after placing its native growth block successfully. */
    public boolean plantCrop(UUID id,java.util.function.BooleanSupplier plant) {
        var batch=batch(id);
        if(batch==null||!batch.cultivation()||batch.cropPlanted()||batch.state()!=WarehouseBatch.State.READY
            ||batch.workSeconds()<6||batch.outputItems().size()!=1)return false;
        var crop=com.hexvane.aetherhaven.workplace.AlchemicalCrop.fromBlock(batch.outputItems().getFirst().getItemId());
        var delivered=batch.deliveredItems();
        if(crop==null||delivered.size()!=1||!crop.seed().equals(delivered.getFirst().getItemId())
            ||delivered.getFirst().getQuantity()!=1||!plant.getAsBoolean())return false;
        batch.delivered(new SimpleItemContainer(WarehouseBatch.BUFFER_SLOTS));
        batch.markCropPlanted();batch.save();changed();return true;
    }
    /** Check output room before touching the plant. Harvesting never creates drops as well as stored output. */
    public boolean finishCultivation(UUID id,com.hexvane.aetherhaven.workplace.WorkplaceOrders orders,
            double requiredSeconds,java.util.function.BooleanSupplier harvest) {
        var batch=batch(id);
        if(batch==null||!batch.cultivation()||!batch.cropPlanted()||batch.state()!=WarehouseBatch.State.READY
            ||!Double.isFinite(requiredSeconds)||requiredSeconds<12||batch.workSeconds()<requiredSeconds
            ||!orders.canComplete(batch.orderId(),batch.recipeId()))return false;
        var nextOutput=WarehouseBatch.copy(inventory(Page.OUTPUT));
        if(!addAll(nextOutput,batch.outputItems())||!harvest.getAsBoolean())return false;
        output=nextOutput;batches.remove(batch);orders.recordCompletion(batch.orderId());changed();return true;
    }
    /** Load native containers only after the Apprentice arrives. Validation happens on isolated clones. */
    public boolean loadFurnace(UUID id,ItemContainer input,ItemContainer fuel,ItemContainer output) {
        var batch=batch(id);
        if(batch==null||!batch.smelting()||batch.state()!=WarehouseBatch.State.READY||!smelting().hasPromise(id)
            ||input==null||fuel==null||output==null||!input.isEmpty()||!output.isEmpty())return false;
        var supplies=WarehouseBatch.copy(batch.delivered());
        if(batch.fuelQuantity()>0) {
            var removed=supplies.removeMaterial(new MaterialQuantity(batch.fuelItem(),null,null,batch.fuelQuantity(),null),true,true,false);
            if(!removed.succeeded()||removed.getList().isEmpty()||removed.getList().getLast().getRemainder()!=0)return false;
        }
        var nextInput=input.clone();var nextFuel=fuel.clone();
        for(var stack:WarehouseBatch.items(supplies)) {
            var added=nextInput.addItemStack(stack,true,false,true);
            if(!added.succeeded()||!ItemStack.isEmpty(added.getRemainder()))return false;
        }
        // Keep the concrete fuel's metadata rather than rebuilding it from the item ID.
        for(short slot=0;slot<batch.delivered().getCapacity();slot++) {
            var original=batch.delivered().getItemStack(slot);var rest=supplies.getItemStack(slot);
            int quantity=ItemStack.isEmpty(original)?0:original.getQuantity()-(ItemStack.isEmpty(rest)?0:rest.getQuantity());
            if(quantity>0) {
                var added=nextFuel.addItemStack(original.withQuantity(quantity),true,false,true);
                if(!added.succeeded()||!ItemStack.isEmpty(added.getRemainder()))return false;
            }
        }
        for(short slot=0;slot<input.getCapacity();slot++)input.setItemStackForSlot(slot,nextInput.getItemStack(slot),false);
        for(short slot=0;slot<fuel.getCapacity();slot++)fuel.setItemStackForSlot(slot,nextFuel.getItemStack(slot),false);
        batch.markNativeLoaded();batch.save();changed();return true;
    }
    /** Native processing supplied the actual items. Finishing a service job never creates recipe output. */
    public boolean finishFurnace(UUID id,com.hexvane.aetherhaven.workplace.WorkplaceOrders orders) {
        var batch=batch(id);if(batch==null||!batch.nativeLoaded())return false;
        var order=orders==null?null:orders.find(batch.orderId());
        // Pausing prevents new loading; it cannot undo ore already processing in a native furnace.
        if(!batch.cancelled()&&order!=null&&batch.recipeId().equals(order.recipeId()))orders.recordCompletion(batch.orderId());
        smelting().release(id);batches.remove(batch);changed();return true;
    }
    /** Release untouched reservations immediately. Picked up supplies still require a physical return trip. */
    public boolean cancel(UUID id) {
        var batch=batch(id); if(batch==null||batch.cancelled()) return false;
        batch.cancel();
        if(batch.smelting()&&!batch.nativeLoaded())smelting().release(batch.id());
        removeReturned(batch); batch.save(); changed(); return true;
    }
    /** Only call at the cancelled workplace to collect supplies already delivered there. */
    public int collectReturn(UUID id,String courier,int stackLimit) {
        var batch=batch(id);
        if(batch==null||!batch.cancelled()||!carrierAvailable(courier)||!batch.cargoItems().isEmpty()||stackLimit<1||stackLimit>WarehouseBatch.CARGO_SLOTS) return 0;
        var remaining=WarehouseBatch.copy(batch.delivered());
        var cargo=new SimpleItemContainer((short)stackLimit); int moved=0;
        for(short slot=0;slot<remaining.getCapacity();slot++) {
            var stack=remaining.getItemStack(slot);
            if(!ItemStack.isEmpty(stack)) moved+=move(remaining,cargo,slot,stack.getQuantity());
        }
        if(moved==0) return 0;
        var savedCargo=new SimpleItemContainer(WarehouseBatch.CARGO_SLOTS);
        if(!addAll(savedCargo,WarehouseBatch.items(cargo))) throw new IllegalStateException("Return cargo does not fit");
        batch.delivered(remaining); batch.cargo(savedCargo,courier); batch.save(); changed(); return moved;
    }
    /** Only call at the warehouse. Full general storage leaves every item with the courier. */
    public boolean returnCargo(UUID id,String courier) {
        var batch=batch(id);
        if(batch==null||!batch.cancelled()||courier==null||!courier.equals(batch.courierId())||batch.cargoItems().isEmpty()) return false;
        var nextGeneral=WarehouseBatch.copy(inventory(Page.GENERAL));
        if(!addAll(nextGeneral,batch.cargoItems())) return false;
        general=nextGeneral; batch.cargo(new SimpleItemContainer(WarehouseBatch.CARGO_SLOTS),null);
        removeReturned(batch); batch.save(); changed(); return true;
    }
    private void removeReturned(WarehouseBatch batch) {
        if(!batch.nativeLoaded()&&batch.cancelled()&&batch.cargoItems().isEmpty()&&batch.deliveredItems().isEmpty()) batches.remove(batch);
    }
    private static SimpleItemContainer decode(String data,int capacity) {
        if(data==null||data.isBlank()) return new SimpleItemContainer((short)capacity);
        // Fail closed on corrupt saves rather than replacing stored belongings with an empty bag.
        var inv=SimpleItemContainer.CODEC.decode(BsonDocument.parse(data),new ExtraInfo());
        if(inv==null||inv.getCapacity()>capacity) throw new IllegalStateException("Warehouse inventory capacity does not match its saved upgrade level");
        if(inv.getCapacity()<capacity){var expanded=new SimpleItemContainer((short)capacity);for(short i=0;i<inv.getCapacity();i++)expanded.setItemStackForSlot(i,inv.getItemStack(i),false);return expanded;}
        return inv;
    }
}
