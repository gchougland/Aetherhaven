package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import java.util.*;
import java.util.function.*;

/** Live, output-only native crafting view. No stock is copied into a physical block or player inventory. */
public final class WarehouseCraftingContainer extends SimpleItemContainer {
    public record Slot(Supplier<ItemStack> read, IntPredicate consume) {}
    private final List<Slot> sources;
    private final BooleanSupplier allowed;
    private final Runnable save;

    public WarehouseCraftingContainer(List<Slot> sources,BooleanSupplier allowed,Runnable save) {
        super((short)Math.min(Short.MAX_VALUE,sources.size()));
        this.sources=List.copyOf(sources.subList(0,getCapacity()));this.allowed=allowed;this.save=save;
    }
    public static void addStorage(List<Slot> slots,TownWarehouse stock) {
        for(var page:TownWarehouse.Page.values())for(short i=0;i<stock.slots(page);i++) {
            final short slot=i;
            slots.add(new Slot(()->available(stock.stack(page,slot),stock.reserved(page,slot)),
                count->stock.consumeForCrafting(page,slot,stock.stack(page,slot),count)));
        }
        for(var origin:stock.smelting().origins())for(short i=0;i<origin.slots();i++) {
            final short slot=i;
            slots.add(new Slot(()->stock.smelting().origin(origin.id())==origin?available(origin.stack(slot),stock.reservedSmelting(origin.id(),slot)):null,
                count->{boolean ok=stock.smelting().consumeForCrafting(origin.id(),slot,origin.stack(slot),count,stock.reservedSmelting(origin.id(),slot));
                    if(ok)stock.markProductionWithdrawal();return ok;}));
        }
    }
    public static void addProduction(List<Slot> slots,TownWarehouse stock,UUID id,ProductionInventory source,BooleanSupplier stillPresent) {
        for(String item:source.getAmounts().keySet())if(!com.hexvane.aetherhaven.production.ProductionWithdrawal.isMilkBucketItem(item))slots.add(new Slot(()->{
            long count=stillPresent.getAsBoolean()?Math.max(0,source.getAmount(item)-stock.reservedProduction(id,item)):0;
            return count==0?null:new ItemStack(item,(int)Math.min(Integer.MAX_VALUE,count));
        },count->{
            if(!stillPresent.getAsBoolean()||count>source.getAmount(item)-stock.reservedProduction(id,item))return false;
            if(source.removeAmountUpTo(item,count)!=count)throw new IllegalStateException("Production changed during native crafting");
            stock.markProductionWithdrawal();return true;
        }));
    }
    private static ItemStack available(ItemStack stack,int reserved) {
        return ItemStack.isEmpty(stack)||stack.getQuantity()<=reserved?null:stack.withQuantity(stack.getQuantity()-reserved);
    }
    @Override protected ItemStack internal_getSlot(short slot) { return allowed.getAsBoolean()?sources.get(slot).read().get():null; }
    @Override protected ItemStack internal_setSlot(short slot,ItemStack next) {
        var old=internal_getSlot(slot);
        if(ItemStack.isEmpty(old)) {if(!ItemStack.isEmpty(next))throw new IllegalStateException("Cannot insert into warehouse crafting view");return null;}
        if(!ItemStack.isEmpty(next)&&(!ItemStack.isStackableWith(old,next)||next.getQuantity()>old.getQuantity()))throw new IllegalStateException("Cannot insert into warehouse crafting view");
        int count=old.getQuantity()-(ItemStack.isEmpty(next)?0:next.getQuantity());
        if(count>0) {
            if(!sources.get(slot).consume().test(count))throw new IllegalStateException("Warehouse changed during native crafting");
            save.run();
        }
        return old;
    }
    @Override protected ItemStack internal_removeSlot(short slot) { return internal_setSlot(slot,null); }
    @Override protected boolean cantAddToSlot(short slot,ItemStack item,ItemStack current) {return true;}
    @Override protected boolean cantRemoveFromSlot(short slot) {return !allowed.getAsBoolean();}
    @Override protected boolean cantDropFromSlot(short slot) {return true;}
    @Override protected boolean cantMoveToSlot(ItemContainer from,short slot) {return true;}
    @Override protected ClearTransaction internal_clear() {return new ClearTransaction(false,(short)0,ItemStack.EMPTY_ARRAY);}
    @Override public boolean isEmpty() {
        for(short i=0;i<getCapacity();i++)if(!ItemStack.isEmpty(internal_getSlot(i)))return false;
        return true;
    }
    @Override public SimpleItemContainer clone() {
        var copy=new SimpleItemContainer(getCapacity());
        for(short i=0;i<getCapacity();i++)copy.setItemStackForSlot(i,internal_getSlot(i),false);
        return copy;
    }
}
