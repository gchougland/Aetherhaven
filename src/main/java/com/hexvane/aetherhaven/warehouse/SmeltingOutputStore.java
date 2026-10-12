package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.*;
import org.bson.BsonDocument;

/** Shared per-resource limits, with each collected stack still owned by its physical furnace location. */
public final class SmeltingOutputStore {
    public static final int BASE_LIMIT=25, UPGRADE_LIMIT=25, MAX_UPGRADES=8;
    private static final short SLOTS=40;
    private int upgrades;
    private long revision;
    private Map<UUID,Origin> origins=new LinkedHashMap<>();
    private Map<UUID,Map<String,Long>> promises=new LinkedHashMap<>();
    public int limit() { return BASE_LIMIT+UPGRADE_LIMIT*Math.clamp(upgrades,0,MAX_UPGRADES); }
    public int upgrades() { return Math.clamp(upgrades,0,MAX_UPGRADES); }
    public long revision() { return revision; }
    /** Buys the tier after {@code ownedTiers}. The press names the tier it saw, so one offer is never bought twice. */
    public boolean upgrade(int ownedTiers) {
        if(ownedTiers!=upgrades()||upgrades()>=MAX_UPGRADES)return false;
        upgrades=upgrades()+1;revision++;return true;
    }
    /** Names the goods kept at one furnace of one site. The same furnace always gets the same name, so it survives a save. */
    public static UUID originId(UUID workplace,WarehouseBatch.Point furnace) {
        return UUID.nameUUIDFromBytes((workplace+":"+furnace).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public List<Origin> origins() { return List.copyOf(origins.values()); }
    public Origin origin(UUID id) { return origins.get(id); }
    public long count(String item) {
        return origins.values().stream().flatMap(o->o.items().stream()).filter(s->item.equals(s.getItemId())).mapToLong(ItemStack::getQuantity).sum();
    }
    public long promised(String item) { return promises.values().stream().mapToLong(p->p.getOrDefault(item,0L)).sum(); }
    public boolean hasPromise(UUID job) { return promises.containsKey(job); }
    public Map<String,Long> remaining(UUID job) { return Map.copyOf(promises.getOrDefault(job,Map.of())); }
    /** Reserve before loading fuel or input. Adding furnaces never increases the town limit. */
    public boolean reserve(UUID job,List<ItemStack> outputs) {
        if(job==null||promises.containsKey(job)||outputs==null||outputs.isEmpty())return false;
        Map<String,Long> amounts=new LinkedHashMap<>();
        for(var stack:outputs) {
            if(ItemStack.isEmpty(stack))return false;
            amounts.merge(stack.getItemId(),(long)stack.getQuantity(),Long::sum);
        }
        for(var entry:amounts.entrySet())if(count(entry.getKey())+promised(entry.getKey())+entry.getValue()>limit())return false;
        promises.put(job,amounts);revision++;return true;
    }
    public boolean release(UUID job) { if(promises.remove(job)==null)return false;revision++;return true; }
    /** Physical servicing only. Transfer actual native output, never synthesize expected recipe results. */
    public int collect(UUID job,UUID originId,WarehouseBatch.Point location,ItemContainer furnaceOutput) {
        var promised=promises.get(job);
        if(promised==null||originId==null||location==null||furnaceOutput==null)return 0;
        var origin=origins.get(originId);
        if(origin!=null&&!location.equals(origin.location()))return 0;
        if(origin==null)origin=new Origin(originId,location);
        int total=0;
        for(short slot=0;slot<furnaceOutput.getCapacity();slot++) {
            var stack=furnaceOutput.getItemStack(slot);if(ItemStack.isEmpty(stack))continue;
            int allowed=(int)Math.min(stack.getQuantity(),promised.getOrDefault(stack.getItemId(),0L));
            if(allowed<=0)continue;
            // Reservations already account for this batch, but reject malformed over-cap saves too.
            allowed=(int)Math.min(allowed,Math.max(0,limit()-count(stack.getItemId())));
            if(allowed<=0)continue;
            int before=stack.getQuantity();
            furnaceOutput.moveItemStackFromSlot(slot,allowed,origin.inventory(),false,true);
            var rest=furnaceOutput.getItemStack(slot);int moved=before-(ItemStack.isEmpty(rest)?0:rest.getQuantity());
            if(moved<=0)continue;
            origins.putIfAbsent(originId,origin);
            promised.put(stack.getItemId(),promised.get(stack.getItemId())-moved);total+=moved;
        }
        if(total>0){origin.save();revision++;}
        return total;
    }
    /**
     * Fuel an Apprentice takes out of a furnace so it can burn the town's other fuel. It joins the goods kept at that
     * furnace, where players and couriers can collect it. The town limit does not apply: leaving the fuel in the slot
     * would stop the furnace for good. All or nothing.
     */
    public boolean setAside(UUID originId,WarehouseBatch.Point location,List<ItemStack> stacks) {
        if(originId==null||location==null||stacks==null||stacks.stream().anyMatch(s->ItemStack.isEmpty(s)))return false;
        if(stacks.isEmpty())return true;
        var origin=origins.get(originId);
        if(origin!=null&&!location.equals(origin.location()))return false;
        if(origin==null)origin=new Origin(originId,location);
        var next=WarehouseBatch.copy(origin.inventory());
        for(var stack:stacks) {
            var added=next.addItemStack(stack,true,false,false);
            if(!added.succeeded()||!ItemStack.isEmpty(added.getRemainder()))return false;
        }
        origin.inventory=next;origins.putIfAbsent(originId,origin);origin.save();revision++;return true;
    }
    /** An all-or-nothing courier pickup after arrival. Allocations retain concrete stack properties. */
    public boolean pickup(UUID originId,WarehouseBatch.Point reached,Map<Short,ItemStack> allocations) {
        var origin=origins.get(originId);
        if(origin==null||!origin.location().equals(reached)||allocations.isEmpty())return false;
        for(var entry:allocations.entrySet()) {
            if(entry.getKey()<0||entry.getKey()>=SLOTS||ItemStack.isEmpty(entry.getValue()))return false;
            var current=origin.stack(entry.getKey());var requested=entry.getValue();
            if(ItemStack.isEmpty(current)||!current.isStackableWith(requested)||current.getQuantity()<requested.getQuantity())return false;
        }
        for(var entry:allocations.entrySet()) {
            var current=origin.stack(entry.getKey());
            origin.inventory().setItemStackForSlot(entry.getKey(),current.withQuantity(current.getQuantity()-entry.getValue().getQuantity()),false);
        }
        origin.save();revision++;return true;
    }
    /**
     * Where the goods a press named are now at this furnace: the slot they were drawn in while it still holds that
     * item, otherwise the first slot that does, otherwise -1. The amount is not compared; smelting adds to it.
     */
    public short locate(UUID originId,short slot,ItemStack expected) {
        var origin=origins.get(originId);
        if(origin==null||ItemStack.isEmpty(expected))return -1;
        if(slot>=0&&slot<SLOTS&&expected.isStackableWith(origin.stack(slot)))return slot;
        for(short i=0;i<SLOTS;i++)if(expected.isStackableWith(origin.stack(i)))return i;
        return -1;
    }
    /**
     * Player collection or a courier at this origin. The caller enforces source reservations. Takes up to
     * {@code quantity} of the named item from where it is now. A furnace finishing another bar since the page was
     * drawn is no reason to refuse.
     */
    public int withdraw(UUID originId,short slot,ItemStack expected,int quantity,ItemContainer recipient) {
        var origin=origins.get(originId);
        if(origin==null||recipient==null||quantity<1)return 0;
        slot=locate(originId,slot,expected);
        if(slot<0)return 0;
        var inventory=origin.inventory();var current=inventory.getItemStack(slot);
        int before=current.getQuantity();inventory.moveItemStackFromSlot(slot,Math.min(quantity,before),recipient,false,true);
        var rest=inventory.getItemStack(slot);int moved=before-(ItemStack.isEmpty(rest)?0:rest.getQuantity());
        if(moved>0){origin.save();revision++;}return moved;
    }
    public boolean consumeForCrafting(UUID originId,short slot,ItemStack expected,int count,int reserved) {
        var origin=origins.get(originId);
        if(origin==null||slot<0||slot>=origin.slots()||count<=0)return false;
        var current=origin.stack(slot);
        if(ItemStack.isEmpty(current)||!current.equals(expected)||count>current.getQuantity()-reserved)return false;
        origin.inventory().setItemStackForSlot(slot,count==current.getQuantity()?null:current.withQuantity(current.getQuantity()-count),false);
        origin.save();revision++;return true;
    }
    public static final class Origin {
        private UUID id;
        private WarehouseBatch.Point location;
        private String data;
        private transient SimpleItemContainer inventory;
        private Origin(UUID id,WarehouseBatch.Point location){this.id=id;this.location=location;}
        public UUID id(){return id;}
        public WarehouseBatch.Point location(){return location;}
        public List<ItemStack> items(){return WarehouseBatch.items(inventory());}
        public short slots(){return SLOTS;}
        public ItemStack stack(short slot){return inventory().getItemStack(slot);}
        private SimpleItemContainer inventory() {
            if(inventory==null) {
                inventory=data==null?new SimpleItemContainer(SLOTS):SimpleItemContainer.CODEC.decode(BsonDocument.parse(data),new ExtraInfo());
                if(inventory==null||inventory.getCapacity()>SLOTS)throw new IllegalStateException("Invalid smelting output inventory");
                if(inventory.getCapacity()<SLOTS){var expanded=new SimpleItemContainer(SLOTS);for(short i=0;i<inventory.getCapacity();i++)expanded.setItemStackForSlot(i,inventory.getItemStack(i),false);inventory=expanded;}
            }
            return inventory;
        }
        private void save(){data=SimpleItemContainer.CODEC.encode(inventory(),new ExtraInfo()).asDocument().toJson();}
    }
}
