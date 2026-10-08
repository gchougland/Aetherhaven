package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.*;
import org.bson.BsonDocument;

/** One recipe batch. Pending stock remains at its source; cargo belongs to a named courier. */
public final class WarehouseBatch {
    public enum State { AWAITING_PICKUP, IN_TRANSIT, READY, PROCESSING, RETURNING }
    public record Source(TownWarehouse.Page page, short slot, short batchSlot, UUID plot, Point pickup) {
        public Source(TownWarehouse.Page page,short slot,short batchSlot) { this(page,slot,batchSlot,null,null); }
    }
    public record Point(int x,int y,int z) {}
    public static final short BUFFER_SLOTS = 40, CARGO_SLOTS = 10;
    private UUID id = UUID.randomUUID();
    private UUID workplaceId, orderId;
    private String recipeId, courierId;
    private boolean cancelled;
    private boolean smelting, nativeLoaded;
    private boolean cultivation, cropPlanted;
    private boolean herdFeed;
    public boolean herdFeed() { return herdFeed; }
    void markHerdFeed() { herdFeed = true; }
    public boolean cultivation() { return cultivation; }
    public boolean cropPlanted() { return cropPlanted; }
    void markCultivation() { cultivation = true; }
    void markCropPlanted() { cropPlanted = true; }
    private String fuelItem;
    private int fuelQuantity;
    public int fuelQuantity() { return fuelQuantity; }
    public boolean smelting() { return smelting; }
    public boolean nativeLoaded() { return nativeLoaded; }
    public String fuelItem() { return fuelItem; }
    void smelting(String fuel,int quantity) { smelting=true;fuelItem=fuel;fuelQuantity=quantity; }
    void markNativeLoaded() { nativeLoaded=true;delivered=new SimpleItemContainer(BUFFER_SLOTS); }

    private double workSeconds;
    private Point destination;
    private List<Source> sources;
    private String pendingData, cargoData, deliveredData, outputData;
    private transient SimpleItemContainer pending, cargo, delivered, output;

    WarehouseBatch(UUID workplaceId, UUID orderId, String recipeId, List<Source> sources,
                   SimpleItemContainer pending, SimpleItemContainer output) {
        this.workplaceId = workplaceId;
        this.orderId = orderId;
        this.recipeId = recipeId;
        this.sources = List.copyOf(sources);
        this.pending = pending;
        this.output = output;
        save();
    }
    public UUID id() { return id; }
    public UUID workplaceId() { return workplaceId; }
    public UUID orderId() { return orderId; }
    public String recipeId() { return recipeId; }
    public String courierId() { return courierId; }
    public boolean cancelled() { return cancelled; }
    public double workSeconds() { return Math.max(0,workSeconds); }
    void advanceWork(double seconds) { workSeconds=Math.max(0,workSeconds)+seconds; }
    public Point destination() { return destination; }
    void destination(Point point) { destination=point; }
    public State state() {
        if (nativeLoaded) return State.PROCESSING;
        if (cancelled) return State.RETURNING;
        if (!items(cargo()).isEmpty()) return State.IN_TRANSIT;
        return items(pending()).isEmpty() ? State.READY : State.AWAITING_PICKUP;
    }
    public List<ItemStack> pendingItems() { return items(pending()); }
    public List<ItemStack> cargoItems() { return items(cargo()); }
    public List<ItemStack> deliveredItems() { return items(delivered()); }
    public List<ItemStack> outputItems() { return items(output()); }
    List<Source> sources() { return sources; }
    public List<Source> pendingSources() {
        return sources.stream().filter(s->!ItemStack.isEmpty(pending().getItemStack(s.batchSlot()))).toList();
    }
    SimpleItemContainer pending() { if (pending == null) pending = decode(pendingData, BUFFER_SLOTS); return pending; }
    SimpleItemContainer cargo() {
        if (cargo == null) {
            if (cargoData == null) cargo = new SimpleItemContainer(CARGO_SLOTS);
            else {
                var saved = SimpleItemContainer.CODEC.decode(BsonDocument.parse(cargoData), new ExtraInfo());
                if (saved == null || saved.getCapacity() != 5 && saved.getCapacity() != 8 && saved.getCapacity() != CARGO_SLOTS)
                    throw new IllegalStateException("Invalid saved delivery cargo");
                cargo = new SimpleItemContainer(CARGO_SLOTS);
                for (short i=0;i<saved.getCapacity();i++) cargo.setItemStackForSlot(i,saved.getItemStack(i),false);
            }
        }
        return cargo;
    }
    SimpleItemContainer delivered() { if (delivered == null) delivered = decode(deliveredData, BUFFER_SLOTS); return delivered; }
    SimpleItemContainer output() { if (output == null) output = decode(outputData, BUFFER_SLOTS); return output; }
    void cargo(SimpleItemContainer value, String carrier) { cargo = value; courierId = carrier; }
    void pending(SimpleItemContainer value) { pending = value; }
    void delivered(SimpleItemContainer value) { delivered = value; }
    void cancel() { cancelled = true; pending = new SimpleItemContainer(BUFFER_SLOTS); }
    void save() {
        pendingData = encode(pending()); cargoData = encode(cargo());
        deliveredData = encode(delivered()); outputData = encode(output());
    }
    static List<ItemStack> items(SimpleItemContainer inventory) {
        List<ItemStack> stacks = new ArrayList<>();
        for (short i = 0; i < inventory.getCapacity(); i++) {
            var stack = inventory.getItemStack(i);
            if (!ItemStack.isEmpty(stack)) stacks.add(stack);
        }
        return List.copyOf(stacks);
    }
    static SimpleItemContainer copy(SimpleItemContainer inventory) {
        return decode(encode(inventory), inventory.getCapacity());
    }
    private static String encode(SimpleItemContainer inventory) {
        return SimpleItemContainer.CODEC.encode(inventory, new ExtraInfo()).asDocument().toJson();
    }
    private static SimpleItemContainer decode(String data, short capacity) {
        if (data == null) return new SimpleItemContainer(capacity);
        var inventory = SimpleItemContainer.CODEC.decode(BsonDocument.parse(data), new ExtraInfo());
        if (inventory == null || inventory.getCapacity() > capacity) throw new IllegalStateException("Invalid saved delivery inventory");
        if(inventory.getCapacity()<capacity){var expanded=new SimpleItemContainer(capacity);for(short i=0;i<inventory.getCapacity();i++)expanded.setItemStackForSlot(i,inventory.getItemStack(i),false);return expanded;}
        return inventory;
    }
}
