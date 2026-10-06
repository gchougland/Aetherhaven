package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.*;
import org.bson.BsonDocument;

/** Shared per-resource limits, with each collected stack still owned by its physical furnace location. */
public final class SmeltingOutputStore {
    public static final int BASE_LIMIT=100, UPGRADE_LIMIT=100, MAX_UPGRADES=8;
    private static final short SLOTS=36;
    private int upgrades;
    private long revision;
    private Map<UUID,Origin> origins=new LinkedHashMap<>();
    private Map<UUID,Map<String,Long>> promises=new LinkedHashMap<>();
    public int limit() { return BASE_LIMIT+UPGRADE_LIMIT*Math.clamp(upgrades,0,MAX_UPGRADES); }
    public int upgrades() { return Math.clamp(upgrades,0,MAX_UPGRADES); }
    public long revision() { return revision; }
    public boolean upgrade(long expected) {
        if(expected!=revision||upgrades()>=MAX_UPGRADES)return false;
        upgrades=upgrades()+1;revision++;return true;
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
    /** Player collection or a courier at this origin. The caller enforces source reservations. */
    public int withdraw(UUID originId,short slot,ItemStack expected,int quantity,ItemContainer recipient,long expectedRevision) {
        var origin=origins.get(originId);
        if(expectedRevision!=revision||origin==null||recipient==null||quantity<1||slot<0||slot>=SLOTS||ItemStack.isEmpty(expected))return 0;
        var inventory=origin.inventory();var current=inventory.getItemStack(slot);
        if(!expected.equals(current))return 0;
        int before=current.getQuantity();inventory.moveItemStackFromSlot(slot,Math.min(quantity,before),recipient,false,true);
        var rest=inventory.getItemStack(slot);int moved=before-(ItemStack.isEmpty(rest)?0:rest.getQuantity());
        if(moved>0){origin.save();revision++;}return moved;
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
                if(inventory==null||inventory.getCapacity()!=SLOTS)throw new IllegalStateException("Invalid smelting output inventory");
            }
            return inventory;
        }
        private void save(){data=SimpleItemContainer.CODEC.encode(inventory(),new ExtraInfo()).asDocument().toJson();}
    }
}
