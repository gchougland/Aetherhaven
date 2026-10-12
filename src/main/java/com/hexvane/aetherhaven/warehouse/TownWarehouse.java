package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hexvane.aetherhaven.inventory.AtomicMaterialExchange;
import com.hexvane.aetherhaven.inventory.StackedSlots;
import org.bson.BsonDocument;
import java.util.*;

/** Town-owned stock. Access desks are views, never new inventories or extra capacity. */
public final class TownWarehouse {
    public record ProductionStock(UUID plot,WarehouseBatch.Point pickup,ProductionInventory state) {}
    public enum Page { GENERAL, OUTPUT }
    public enum Amount { STACK, ONE, ALL_MATCHING }
    /** Why nothing moved. Each one is something truly missing or in the way at the moment of the press. */
    public enum Refusal {
        /** The item the press named is not there any more. */
        GONE,
        /** Every one of them is set aside for a workplace delivery. */
        RESERVED,
        /** The side receiving the items has no room for even one. */
        NO_ROOM,
        /** Crafted Goods only takes what the town makes. */
        OUTPUT_ONLY
    }
    /** {@code error} is a plain sentence for callers that do not name the item themselves. */
    public record Transfer(int moved, String error, Refusal refusal) {
        static Transfer moved(int count) { return new Transfer(count,null,null); }
        static Transfer refused(Refusal why,String error) { return new Transfer(0,error,why); }
    }
    /** What a quick stack did. {@code matched} is false when nothing carried is already stored here. */
    public record Matching(int moved, boolean matched) {}
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
    /** Bought tiers of deeper shelves. Saves from before the upgrade have none and keep one stack per slot. */
    private int stackUpgrades;
    private long revision;
    private String generalData, outputData;
    private transient SimpleItemContainer general, output;
    private transient Map<UUID,Boolean> outputRoomCache=new HashMap<>();
    private transient long outputRoomRevision=-1;
    private List<WarehouseBatch> batches = new ArrayList<>();
    public static final int BASE_GENERAL_SLOTS = 40, UPGRADE_SLOTS = 20, MAX_UPGRADES = 8;
    public static final int OUTPUT_SLOTS = 40;
    /** Each tier lets every general storage slot hold one more native stack of its item. */
    public static final int MAX_STACK_UPGRADES = 4;
    public static final long STACK_UPGRADE_GOLD = 200;
    /** Natural growth can overlap; seed delivery and attended planting remain serial. */
    public static final int CULTIVATION_BEDS = 6;

    public int upgrades() { return Math.clamp(storageUpgrades,0,MAX_UPGRADES); }
    public int capacity() { return BASE_GENERAL_SLOTS + UPGRADE_SLOTS * upgrades(); }
    public long revision() { return revision; }
    public long upgradeCost() { return 150L * (upgrades()+1); }
    public int upgradeTownLevel() { return Math.min(6, 2+upgrades()); }
    public int stackUpgrades() { return Math.clamp(stackUpgrades,0,MAX_STACK_UPGRADES); }
    /** Native stacks of one item a general storage slot holds. Crafted goods always hold a single stack. */
    public int stacksPerSlot() { return 1+stackUpgrades(); }
    public long stackUpgradeCost() { return STACK_UPGRADE_GOLD * (stackUpgrades()+1); }
    /** Most of this item one slot of the page holds. */
    public int slotLimit(Page page,ItemStack stack) { return StackedSlots.limit(stack,page==Page.GENERAL?stacksPerSlot():1); }
    /**
     * Buys the tier after {@code ownedTiers}. The press names the tier it saw, so a second press on the same offer
     * buys nothing, while couriers and crafts moving stock in the meantime change nothing about the offer.
     */
    public boolean upgradeStacks(int ownedTiers) {
        if(ownedTiers!=stackUpgrades()||stackUpgrades()>=MAX_STACK_UPGRADES) return false;
        stackUpgrades=stackUpgrades()+1; changed(); return true;
    }
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
    /**
     * Stores what the player pointed at: the item {@code expected} in inventory slot {@code slot}.
     *
     * <p>Nothing here asks whether storage changed since the page was drawn. Couriers and crafts change it every few
     * seconds, and that is no reason to refuse. The press is honoured when that slot still holds that item, however
     * many of it there are now, and there is room for at least one. A different item in the slot is a different
     * request and is refused, so a list that moved can never store the wrong thing.
     */
    public Transfer deposit(Page page,ItemContainer player,short slot,ItemStack expected,Amount amount) {
        if(page!=Page.GENERAL) return Transfer.refused(Refusal.OUTPUT_ONLY,"Finished output only accepts items made by the town.");
        var carried=player==null||slot<0||slot>=player.getCapacity()?null:player.getItemStack(slot);
        if(ItemStack.isEmpty(expected)||ItemStack.isEmpty(carried)||!expected.isStackableWith(carried))
            return Transfer.refused(Refusal.GONE,"That item is no longer in that inventory slot. Nothing was moved.");
        int moved=0;
        if(amount==Amount.ALL_MATCHING) {
            for(short i=0;i<player.getCapacity();i++) {
                var stack=player.getItemStack(i);
                if(!ItemStack.isEmpty(stack)&&expected.isStackableWith(stack)) moved+=store(player,i,stack.getQuantity());
            }
        } else moved=store(player,slot,amount==Amount.ONE?1:carried.getQuantity());
        if(moved>0) changed();
        return moved>0?Transfer.moved(moved):Transfer.refused(Refusal.NO_ROOM,"General storage has no room for that item. Nothing was moved.");
    }
    /**
     * Quick stack. Moves every stack in player slots {@code from} (inclusive) to {@code to} (exclusive) that general
     * storage already holds some of. What does not fit stays with the player and nothing is ever discarded.
     * Courier reservations only limit what leaves storage, so adding to a reserved slot is safe.
     */
    public Matching depositMatching(ItemContainer player,int from,int to) {
        if(player==null) return new Matching(0,false);
        int moved=0;boolean matched=false;
        for(int i=Math.max(0,from);i<Math.min(to,player.getCapacity());i++) {
            var stack=player.getItemStack((short)i);
            var stored=generalSlots();
            if(ItemStack.isEmpty(stack)||!StackedSlots.holds(stored,stored.length,stack)) continue;
            matched=true;moved+=store(player,(short)i,stack.getQuantity());
        }
        if(moved>0) changed();
        return new Matching(moved,matched);
    }
    /**
     * Where the item a press named is now: the slot it was drawn in while that still holds it, otherwise the first
     * slot of the page that does, otherwise -1. Stock is compared without its amount, which moves all the time.
     */
    public short locate(Page page,short slot,ItemStack expected) {
        if(ItemStack.isEmpty(expected)) return -1;
        var held=inventory(page);
        if(slot>=0&&slot<held.getCapacity()&&expected.isStackableWith(held.getItemStack(slot))) return slot;
        for(short i=0;i<held.getCapacity();i++) if(expected.isStackableWith(held.getItemStack(i))) return i;
        return -1;
    }
    /**
     * Hands over what the player pointed at: the item {@code expected}, drawn in slot {@code slot}.
     *
     * <p>Nothing here asks whether storage changed since the page was drawn. If the item is still in storage the
     * press is honoured from wherever it sits now, with however many are there now. It is refused only when none is
     * left, when every one is set aside for a workplace, or when the player has no room, and the refusal says which.
     */
    public Transfer withdraw(Page page,ItemContainer player,short slot,ItemStack expected,Amount amount) {
        var from=inventory(page);
        short at=player==null?-1:locate(page,slot,expected);
        if(at<0) return Transfer.refused(Refusal.GONE,"None of that item is left in storage. Nothing was moved.");
        int moved=0;
        boolean held=false,free=false;
        for(short i=0;i<from.getCapacity();i++) {
            var current=from.getItemStack(i);
            if(ItemStack.isEmpty(current)||(amount==Amount.ALL_MATCHING?!expected.isStackableWith(current):i!=at)) continue;
            int reserved=reserved(page,i);
            held|=reserved>0;
            int available=current.getQuantity()-reserved;
            free|=available>0;
            // A click takes one native stack at most, however deep the shelf is.
            if(available>0) moved+=give(from,player,i,amount==Amount.ONE?1:amount==Amount.STACK?Math.min(available,StackedSlots.oneStack(current)):available);
        }
        if(moved>0) changed();
        return moved>0?Transfer.moved(moved):held&&!free?Transfer.refused(Refusal.RESERVED,"Those supplies are reserved for a workplace.")
            :Transfer.refused(Refusal.NO_ROOM,"Your inventory has no room for that item. Nothing was moved.");
    }
    /** General storage as plain slots, for planning an insert before anything changes. */
    private ItemStack[] generalSlots() {
        var inv=inventory(Page.GENERAL);var slots=new ItemStack[inv.getCapacity()];
        for(short i=0;i<slots.length;i++) {var held=inv.getItemStack(i);slots[i]=ItemStack.isEmpty(held)?null:held;}
        return slots;
    }
    private void applyGeneral(ItemStack[] plan) {
        var inv=inventory(Page.GENERAL);
        for(short i=0;i<plan.length;i++) if(!Objects.equals(inv.getItemStack(i),plan[i])) inv.setItemStackForSlot(i,plan[i],false);
    }
    /** Fits every stack into general storage at its slots' real limits, or changes nothing. */
    private boolean storeAll(List<ItemStack> stacks) {
        var plan=generalSlots();
        for(var stack:stacks) if(StackedSlots.insert(plan,plan.length,stack,stacksPerSlot())!=stack.getQuantity()) return false;
        applyGeneral(plan);return true;
    }
    /** Moves up to {@code count} of a player's stack into general storage, filling slots to their real limit. */
    private int store(ItemContainer from,short slot,int count) {
        var stack=from.getItemStack(slot);
        if(ItemStack.isEmpty(stack)||count<=0) return 0;
        var plan=generalSlots();
        int accepted=StackedSlots.insert(plan,plan.length,stack.withQuantity(Math.min(count,stack.getQuantity())),stacksPerSlot());
        if(accepted==0) return 0;
        // Take the items first. A refused removal leaves both sides exactly as they were.
        if(!from.removeItemStackFromSlot(slot,stack,accepted,true,true).succeeded()) return 0;
        applyGeneral(plan);return accepted;
    }
    /**
     * Hands up to {@code count} of a stored stack to the player. The player's inventory decides how much fits and
     * only that much leaves storage: a native move would discard the overflow of a slot holding several stacks.
     */
    private static int give(ItemContainer from,ItemContainer to,short slot,int count) {
        var stack=from.getItemStack(slot);
        if(ItemStack.isEmpty(stack)||count<=0) return 0;
        int offered=Math.min(count,stack.getQuantity());
        var rest=to.addItemStack(stack.withQuantity(offered),false,false,true).getRemainder();
        int moved=offered-(ItemStack.isEmpty(rest)?0:rest.getQuantity());
        if(moved>0) from.setItemStackForSlot(slot,stack.withQuantity(stack.getQuantity()-moved),false);
        return moved;
    }
    private static int move(ItemContainer from,ItemContainer to,short slot,int count) {
        int before=from.getItemStack(slot).getQuantity();
        from.moveItemStackFromSlot(slot,count,to,false,true);
        var remaining=from.getItemStack(slot);
        return before-(ItemStack.isEmpty(remaining)?0:remaining.getQuantity());
    }
    /** Buys the tier after {@code ownedTiers}; see {@link #upgradeStacks}. */
    public boolean upgrade(int ownedTiers) {
        if(ownedTiers!=upgrades()||upgrades()>=MAX_UPGRADES) return false;
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
        if(!storeAll(List.of(stack)))return false;
        changed();return true;
    }
    /** Only exchanges validated, single item equipment. This is not a general output deposit path. */
    /**
     * The kit is the exact item the press named. Stock moving elsewhere in storage is no reason to refuse; the kit
     * itself having been taken is, and so is a courier having set it aside.
     */
    public String equipProfessionKit(Page page, short slot, ItemStack expected,
            com.hexvane.aetherhaven.townsfolk.TownsfolkProgress progress,
            com.hexvane.aetherhaven.equipment.ProfessionKitCatalog catalog) {
        short at = com.hexvane.aetherhaven.equipment.ProfessionKitService.locate(inventory(page), slot, expected);
        if (at < 0) return com.hexvane.aetherhaven.equipment.ProfessionKitService.TAKEN;
        if (reserved(page, at) > 0) return "That kit is reserved for a workplace.";
        String error = com.hexvane.aetherhaven.equipment.ProfessionKitService.equip(progress, catalog, inventory(page), at, expected);
        if (error == null) changed();
        return error;
    }
    public String equipGuardKit(Page page,short slot,ItemStack expected,
            com.hexvane.aetherhaven.townsfolk.TownsfolkProgress progress,
            com.hexvane.aetherhaven.equipment.GuardKitCatalog catalog,String guardClass) {
        short at=com.hexvane.aetherhaven.equipment.ProfessionKitService.locate(inventory(page),slot,expected);
        if(at<0)return com.hexvane.aetherhaven.equipment.ProfessionKitService.TAKEN;
        if(reserved(page,at)>0)return "That kit is reserved for a workplace.";
        String error=com.hexvane.aetherhaven.equipment.GuardKitService.equip(progress,catalog,guardClass,inventory(page),at,expected);
        if(error==null)changed();
        return error;
    }
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
    /** Crafts already on their way for a task. One delivery may cover several. */
    public int queuedForOrder(UUID order) {
        return batches.stream().filter(b->b.orderId().equals(order)&&!b.cancelled()).mapToInt(WarehouseBatch::crafts).sum();
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
    /**
     * One delivery covering several crafts of a recipe. Materials and results are given for a single craft.
     *
     * @param maxStacks the delivery is refused when its materials would need more stacks than this to carry, so the
     *                  caller can try a smaller number of crafts
     */
    public WarehouseBatch reserveCrafts(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,
            List<ProductionStock> production,int crafts,int maxStacks) {
        if(crafts<1||crafts>WorkplaceBatchSize.MAX_CRAFTS||materials==null||results==null)return null;
        if(crafts==1)return reserve(workplace,order,recipe,materials,results,production);
        var batch=reserve(workplace,order,recipe,scale(materials,crafts),repeat(results,crafts),production,null,0,false,false,Math.max(1,maxStacks),0);
        if(batch!=null){batch.crafts(crafts);changed();}
        return batch;
    }
    /**
     * A furnace load covering several crafts. Materials and results are given for a single craft; fuel is the total.
     *
     * @param fuelBurned all the fuel the furnace will hold, counting what is already in it. Every two logs leave one
     *                   charcoal, and room must be promised for all of it or the furnace could never be emptied.
     */
    public WarehouseBatch reserveSmeltingCrafts(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,
            List<ProductionStock> production,String fuelItem,int fuelQuantity,int crafts,int fuelBurned) {
        if(fuelItem==null||fuelItem.isBlank()||fuelQuantity<0||fuelQuantity>100||crafts<1||crafts>WorkplaceBatchSize.MAX_FURNACE_CRAFTS
            ||materials==null||results==null)return null;
        var withFuel=new ArrayList<>(scale(materials,crafts));if(fuelQuantity>0)withFuel.add(new MaterialQuantity(fuelItem,null,null,fuelQuantity,null));
        var batch=reserve(workplace,order,recipe,withFuel,repeat(results,crafts),production,fuelItem,fuelQuantity,false,false,0,Math.max(fuelQuantity,fuelBurned));
        if(batch!=null){batch.crafts(crafts);changed();}
        return batch;
    }
    private static List<MaterialQuantity> scale(List<MaterialQuantity> materials,int crafts) {
        var scaled=new ArrayList<MaterialQuantity>();
        for(var material:materials)scaled.add(material==null?null:material.clone(Math.multiplyExact(material.getQuantity(),crafts)));
        return scaled;
    }
    /** Results are repeated, not multiplied, so each keeps a legal stack size when it is added to a container. */
    private static List<ItemStack> repeat(List<ItemStack> results,int crafts) {
        var repeated=new ArrayList<ItemStack>();
        for(int i=0;i<crafts;i++)repeated.addAll(results);
        return repeated;
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
    public static final String SITE_SUPPLY_RECIPE="aetherhaven:site_supply";
    /**
     * A physical supply trip to a planned building: one material, taken only from stock nothing else has reserved.
     * One trip at a time per building.
     *
     * @param maxStacks refused when the material needs more stacks than one courier carries, so the caller can ask for less
     */
    public WarehouseBatch reserveSiteSupply(UUID plot,WarehouseBatch.Point destination,MaterialQuantity material,List<ProductionStock> sources,int maxStacks) {
        if(plot==null||destination==null||material==null||material.getQuantity()<1||sources==null)return null;
        var batch=reserve(plot,plot,SITE_SUPPLY_RECIPE,List.of(material),List.of(),sources,null,0,false,true,Math.max(1,maxStacks),0);
        if(batch!=null){batch.markSiteSupply();batch.destination(destination);batch.save();changed();}
        return batch;
    }
    private WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production,String fuelItem,int fuelQuantity) {
        return reserve(workplace,order,recipe,materials,results,production,fuelItem,fuelQuantity,false);
    }
    private WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production,String fuelItem,int fuelQuantity,boolean cultivation) {
        return reserve(workplace,order,recipe,materials,results,production,fuelItem,fuelQuantity,cultivation,false);
    }
    private WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production,String fuelItem,int fuelQuantity,boolean cultivation,boolean herdFeed) {
        return reserve(workplace,order,recipe,materials,results,production,fuelItem,fuelQuantity,cultivation,herdFeed,0,fuelQuantity);
    }
    /**
     * @param maxStacks when positive, refuse a delivery whose materials need more stacks than this to carry
     * @param fuelBurned fuel counted towards the charcoal a wood fire leaves behind
     */
    private WarehouseBatch reserve(UUID workplace,UUID order,String recipe,List<MaterialQuantity> materials,List<ItemStack> results,List<ProductionStock> production,String fuelItem,int fuelQuantity,boolean cultivation,boolean herdFeed,int maxStacks,int fuelBurned) {
        if(workplace==null||order==null||recipe==null||recipe.isBlank()||materials==null||results==null||!herdFeed&&results.isEmpty()
            ||!canPrepare(workplace,fuelItem!=null,cultivation)||batches.size()>=256) return null;
        if(materials.stream().anyMatch(m->m==null||m.getQuantity()<=0)||results.stream().anyMatch(s->ItemStack.isEmpty(s))) return null;
        if(!herdFeed&&fuelItem==null&&!roomForOutputs(results)) return null;
        var promised=new SimpleItemContainer(WarehouseBatch.BUFFER_SLOTS);
        if(!addAll(promised,results)) return null;
        // Source addresses accompany the scratch slots. A town total is never a pickup location.
        List<WarehouseBatch.Source> addresses=new ArrayList<>();List<ItemStack> stocks=new ArrayList<>();
        // Where each candidate stack is: a building, a furnace, or the warehouse itself.
        List<String> places=new ArrayList<>();
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
                stocks.add(new ItemStack(held.getKey(),count));places.add("building:"+source.plot());
            }
        }
        for(var origin:smelting().origins())for(short slot=0;slot<origin.slots();slot++) {
            var held=origin.stack(slot);if(ItemStack.isEmpty(held))continue;
            int remaining=held.getQuantity()-reservedSmelting(origin.id(),slot);
            if(remaining<0)throw new IllegalStateException("Reserved smelting stock is missing");
            if(remaining==0)continue;
            addresses.add(new WarehouseBatch.Source(null,slot,(short)-1,origin.id(),origin.location()));stocks.add(held.withQuantity(remaining));places.add("furnace:"+origin.id());
        }
        // Specific crafting output is player collected. It is not a remote ingredient depot.
        for(short slot=0;slot<inventory(Page.GENERAL).getCapacity();slot++) {
            var stack=inventory(Page.GENERAL).getItemStack(slot);
            if(ItemStack.isEmpty(stack)) continue;
            int remaining=stack.getQuantity()-reserved(Page.GENERAL,slot);
            if(remaining<0) throw new IllegalStateException("Reserved stock is missing");
            if(remaining>0) {addresses.add(new WarehouseBatch.Source(Page.GENERAL,slot,(short)-1));stocks.add(stack.withQuantity(remaining));places.add("warehouse");}
        }
        if(stocks.size()>Short.MAX_VALUE)return null;
        var available=new SimpleItemContainer((short)Math.max(1,stocks.size()));
        for(short slot=0;slot<stocks.size();slot++)available.setItemStackForSlot(slot,stocks.get(slot),false);
        var before=WarehouseBatch.copy(available);
        // Check each removal on the scratch inventory. Native bulk transactions can report
        // success even when overlapping ingredient queries exhaust one another's stock.
        for(var material:materials) {
            // A courier collects from one place per trip. Take the whole of a material from a single place that holds
            // enough of it: a building that makes it, a furnace, or the warehouse. Only when no one place has enough
            // is it gathered from several.
            if(takeFromOnePlace(available,places,material))continue;
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
        if(maxStacks>0) {
            int stacks=0;
            for(var stack:WarehouseBatch.items(pending))stacks+=WorkplaceBatchSize.stacks(stack.getQuantity(),stack.getItem().getMaxStack());
            if(stacks>maxStacks)return null;
        }
        var batch=new WarehouseBatch(workplace,order,recipe,sources,pending,promised);
        if(fuelItem!=null) {
            var promisedOutput=new ArrayList<>(results);
            if(!"Ingredient_Charcoal".equals(fuelItem)&&results.stream().noneMatch(s->"Ingredient_Charcoal".equals(s.getItemId())))promisedOutput.add(new ItemStack("Ingredient_Charcoal",Math.max(1,(fuelBurned+1)/2)));
            if(!smelting().reserve(batch.id(),promisedOutput))return null;
            batch.smelting(fuelItem,fuelQuantity);
        }
        batches.add(batch); changed(); return batch;
    }
    /**
     * Tries to take a whole material from the stacks of one place, in the order the places are listed.
     *
     * @return true when one place covered it and the scratch inventory has been reduced
     */
    private static boolean takeFromOnePlace(SimpleItemContainer available,List<String> places,MaterialQuantity material) {
        for(String place:new LinkedHashSet<>(places)) {
            var alone=new SimpleItemContainer(available.getCapacity());
            for(short slot=0;slot<places.size();slot++)if(place.equals(places.get(slot)))alone.setItemStackForSlot(slot,available.getItemStack(slot),false);
            if(alone.isEmpty())continue;
            var removed=alone.removeMaterial(material,true,true,false);
            if(!removed.succeeded()||removed.getList().isEmpty()||removed.getList().getLast().getRemainder()!=0)continue;
            for(short slot=0;slot<places.size();slot++)if(place.equals(places.get(slot)))available.setItemStackForSlot(slot,alone.getItemStack(slot),false);
            return true;
        }
        return false;
    }
    /**
     * True when a delivery that is still waiting to be collected can no longer be collected in full: the stock it
     * reserved is gone or short. Stock in unloaded terrain is not judged.
     *
     * @param loaded production buildings whose stores are in live terrain right now
     * @param exists whether a building or mineshaft base with that id is still part of the town
     */
    public boolean sourceLost(UUID id,List<ProductionStock> loaded,java.util.function.Predicate<UUID> exists) {
        var batch=batch(id);
        if(batch==null||batch.cancelled()||batch.nativeLoaded())return false;
        for(var source:batch.pendingSources()) {
            var wanted=batch.pending().getItemStack(source.batchSlot());
            if(ItemStack.isEmpty(wanted))continue;
            if(source.page()==Page.GENERAL) {
                var held=inventory(Page.GENERAL).getItemStack(source.slot());
                if(ItemStack.isEmpty(held)||!held.isStackableWith(wanted)||held.getQuantity()<wanted.getQuantity())return true;
            } else if(source.plot()!=null&&source.slot()>=0) {
                var origin=smelting().origin(source.plot());
                if(origin==null)return true;
                var held=origin.stack(source.slot());
                if(ItemStack.isEmpty(held)||!held.isStackableWith(wanted)||held.getQuantity()<wanted.getQuantity())return true;
            } else if(source.plot()!=null) {
                if(!exists.test(source.plot()))return true;
                for(var stock:loaded) {
                    if(!source.plot().equals(stock.plot()))continue;
                    if(!Objects.equals(stock.pickup(),source.pickup())||stock.state().getAmount(wanted.getItemId())<wanted.getQuantity())return true;
                }
            }
        }
        return false;
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
        if(batch==null||batch.cancelled()||batch.smelting()||batch.herdFeed()||batch.siteSupply())return false;
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
        if(batch==null||batch.herdFeed()||batch.siteSupply()||batch.cancelled()||courier==null||!courier.equals(batch.courierId())||batch.cargoItems().isEmpty()) return false;
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
    /**
     * Only after arrival at the building's plot sign. The site takes what it still needs from each carried stack;
     * anything it no longer needs stays with the courier and is walked back to storage, never discarded.
     *
     * @param accept takes one carried stack and returns how many of it the site kept
     */
    public boolean deliverSiteSupply(UUID id,String courier,WarehouseBatch.Point reached,java.util.function.ToIntFunction<ItemStack> accept) {
        var batch=batch(id);
        if(batch==null||!batch.siteSupply()||batch.cancelled()||reached==null||!reached.equals(batch.destination())||accept==null
            ||courier==null||!courier.equals(batch.courierId())||batch.cargoItems().isEmpty())return false;
        var remainder=WarehouseBatch.copy(batch.cargo());int kept=0;
        for(short slot=0;slot<remainder.getCapacity();slot++) {
            var stack=remainder.getItemStack(slot);if(ItemStack.isEmpty(stack))continue;
            int taken=Math.clamp(accept.applyAsInt(stack),0,stack.getQuantity());
            if(taken==0)continue;
            remainder.setItemStackForSlot(slot,taken==stack.getQuantity()?null:stack.withQuantity(stack.getQuantity()-taken),false);kept+=taken;
        }
        if(kept==0)return cancel(id);
        batch.cargo(remainder,remainder.isEmpty()?null:courier);
        if(!remainder.isEmpty()){batch.cancel();removeReturned(batch);}
        else if(batch.pendingItems().isEmpty())batches.remove(batch);
        batch.save();changed();return true;
    }
    /** Called only by the physical work executor when the batch finishes. Consumption and output commit together. */
    public boolean finish(UUID id) {
        return finish(id,java.util.function.UnaryOperator.identity());
    }
    private boolean finish(UUID id,java.util.function.UnaryOperator<ItemStack> decorate) {
        var batch=batch(id);
        if(batch==null||batch.smelting()||batch.cultivation()||batch.herdFeed()||batch.siteSupply()||batch.state()!=WarehouseBatch.State.READY) return false;
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
        // A delivery covering several crafts needs the work of each, and all of them are committed together.
        if(batch==null||!Double.isFinite(requiredSeconds)||requiredSeconds<=0||batch.workSeconds()<requiredSeconds*batch.crafts()
            ||!orders.canComplete(batch.orderId(),batch.recipeId()))return false;
        UUID order=batch.orderId();int crafts=batch.crafts();
        if(!finish(id,decorate))return false;
        orders.recordCompletion(order,crafts);return true;
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
        // The fuel slot holds one kind of fuel. A furnace last fed the other kind still has some, so the Apprentice sets
        // it with the goods kept at this furnace and loads what the player chose. Nothing is thrown away.
        var aside=new ArrayList<ItemStack>();
        if(batch.fuelQuantity()>0)for(short slot=0;slot<nextFuel.getCapacity();slot++) {
            var held=nextFuel.getItemStack(slot);
            if(ItemStack.isEmpty(held)||batch.fuelItem().equals(held.getItemId()))continue;
            aside.add(held);nextFuel.setItemStackForSlot(slot,null,false);
        }
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
        // Last check, and the only one that changes anything before the furnace itself: a full pile keeps the load waiting.
        if(!aside.isEmpty()&&(batch.destination()==null||!smelting().setAside(
            SmeltingOutputStore.originId(batch.workplaceId(),batch.destination()),batch.destination(),aside)))return false;
        for(short slot=0;slot<input.getCapacity();slot++)input.setItemStackForSlot(slot,nextInput.getItemStack(slot),false);
        for(short slot=0;slot<fuel.getCapacity();slot++)fuel.setItemStackForSlot(slot,nextFuel.getItemStack(slot),false);
        batch.markNativeLoaded();batch.save();changed();return true;
    }
    /** Native processing supplied the actual items. Finishing a service job never creates recipe output. */
    public boolean finishFurnace(UUID id,com.hexvane.aetherhaven.workplace.WorkplaceOrders orders) {
        var batch=batch(id);if(batch==null||!batch.nativeLoaded())return false;
        var order=orders==null?null:orders.find(batch.orderId());
        // Pausing prevents new loading; it cannot undo ore already processing in a native furnace.
        if(!batch.cancelled()&&order!=null&&batch.recipeId().equals(order.recipeId()))orders.recordCompletion(batch.orderId(),batch.crafts());
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
        // Returned supplies use the same shelf limits as a player's deposit, all or nothing.
        if(!storeAll(batch.cargoItems())) return false;
        batch.cargo(new SimpleItemContainer(WarehouseBatch.CARGO_SLOTS),null);
        removeReturned(batch); batch.save(); changed(); return true;
    }
    public static final String SALVAGE_RECIPE="aetherhaven:site_salvage";
    /**
     * What one worker could still carry home as site salvage: room in their hands, and room in general storage that
     * nothing already walking back will need. A dry run; nothing changes until {@link #carrySalvage}.
     */
    public final class SalvageRoom {
        private final ItemStack[] storage,hands;private final int handSlots;
        private SalvageRoom(ItemStack[] storage,ItemStack[] hands,int handSlots){this.storage=storage;this.hands=hands;this.handSlots=handSlots;}
        /** How many of this stack would be taken: never more than the hands hold or storage has promised. */
        public int accepts(ItemStack stack) {
            if(ItemStack.isEmpty(stack))return 0;
            return Math.min(StackedSlots.insert(storage.clone(),storage.length,stack,stacksPerSlot()),StackedSlots.insert(hands.clone(),handSlots,stack,1));
        }
        private int take(ItemStack stack) {
            int count=accepts(stack);if(count==0)return 0;
            var part=stack.withQuantity(count);
            StackedSlots.insert(storage,storage.length,part,stacksPerSlot());StackedSlots.insert(hands,handSlots,part,1);return count;
        }
        public boolean carrying(){for(var held:hands)if(held!=null)return true;return false;}
        /** Every stack this worker may carry is in use. */
        public boolean handsFull(){for(int slot=0;slot<handSlots;slot++)if(hands[slot]==null)return false;return true;}
    }
    /** Null when this worker cannot carry salvage: no name, no allowance, or their hands hold a delivery. */
    public SalvageRoom salvageRoom(String courier,int stackLimit) {
        if(courier==null||courier.isBlank()||stackLimit<1||stackLimit>WarehouseBatch.CARGO_SLOTS)return null;
        var storage=generalSlots();var hands=new ItemStack[WarehouseBatch.CARGO_SLOTS];
        for(var batch:batches) {
            if(courier.equals(batch.courierId())) {
                if(!batch.salvage())return null;
                for(short slot=0;slot<hands.length;slot++){var held=batch.cargo().getItemStack(slot);hands[slot]=ItemStack.isEmpty(held)?null:held;}
            }
            // Everything already on its way back keeps its room: other salvage and ordinary returned supplies.
            if(batch.cancelled()) {
                for(var stack:batch.cargoItems())StackedSlots.insert(storage,storage.length,stack,stacksPerSlot());
                for(var stack:batch.deliveredItems())StackedSlots.insert(storage,storage.length,stack,stacksPerSlot());
            }
        }
        return new SalvageRoom(storage,hands,stackLimit);
    }
    /**
     * A builder takes loose items from a building site into their hands, to be stored by the ordinary return trip.
     * Each stack is taken only as far as the worker's hands and the room promised in general storage allow. Storage
     * itself and every reservation are untouched. The caller removes exactly the returned amounts from the ground.
     *
     * @return how many of each offered stack were taken, in the same order
     */
    public int[] carrySalvage(String courier,List<ItemStack> offered,int stackLimit) {
        var taken=new int[offered==null?0:offered.size()];
        var room=offered==null?null:salvageRoom(courier,stackLimit);
        var batch=room==null?null:batches.stream().filter(b->courier.equals(b.courierId())).findFirst().orElse(null);
        if(room==null||batch==null&&batches.size()>=256)return taken;
        boolean any=false;
        for(int i=0;i<taken.length;i++){taken[i]=room.take(offered.get(i));any|=taken[i]>0;}
        if(!any)return taken;
        if(batch==null) {
            batch=new WarehouseBatch(UUID.randomUUID(),UUID.randomUUID(),SALVAGE_RECIPE,List.of(),
                new SimpleItemContainer(WarehouseBatch.BUFFER_SLOTS),new SimpleItemContainer(WarehouseBatch.BUFFER_SLOTS));
            batch.cancel();batches.add(batch);
        }
        var cargo=new SimpleItemContainer(WarehouseBatch.CARGO_SLOTS);
        for(short slot=0;slot<room.hands.length;slot++)if(room.hands[slot]!=null)cargo.setItemStackForSlot(slot,room.hands[slot],false);
        batch.cargo(cargo,courier);batch.save();changed();return taken;
    }
    /**
     * Only call at the warehouse. Stores what fits in general storage and empties the worker's hands. Whatever is
     * returned did not fit and is in no inventory any more: the caller must put it on the ground.
     *
     * @return null when this worker carries no such load
     */
    public List<ItemStack> unloadSalvage(UUID id,String courier) {
        var batch=batch(id);
        if(batch==null||!batch.salvage()||courier==null||!courier.equals(batch.courierId()))return null;
        var plan=generalSlots();var left=new ArrayList<ItemStack>();
        for(var stack:batch.cargoItems()) {
            int stored=StackedSlots.insert(plan,plan.length,stack,stacksPerSlot());
            if(stored<stack.getQuantity())left.add(stack.withQuantity(stack.getQuantity()-stored));
        }
        applyGeneral(plan);batch.cargo(new SimpleItemContainer(WarehouseBatch.CARGO_SLOTS),null);
        removeReturned(batch);batch.save();changed();return left;
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
