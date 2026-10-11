package com.hexvane.aetherhaven.crafter;

import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseAccess;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Pays Tobin from the player's pack first, then from unreserved town warehouse stock. World thread only. */
public final class PatternBookPayment {
    private PatternBookPayment() {}

    private record Taken(TownWarehouse.Page page, short slot, ItemStack before, int count) {}

    /** Materials the player can spend: carried plus unreserved warehouse stock. A null warehouse means pack only. */
    public static long available(@Nonnull ItemContainer pack, @Nullable TownWarehouse warehouse, @Nonnull String item) {
        return InventoryMaterials.count(pack, item) + (warehouse == null ? 0 : warehouse.craftingStock().count(item));
    }

    /** Takes the whole cost or nothing. */
    public static boolean pay(@Nonnull ItemContainer pack, @Nullable TownWarehouse warehouse, @Nonnull Map<String, Integer> cost) {
        for (var part : cost.entrySet()) if (available(pack, warehouse, part.getKey()) < part.getValue()) return false;
        var fromPack = new LinkedHashMap<String, Integer>();
        var fromWarehouse = new ArrayList<Taken>();
        for (var part : cost.entrySet()) {
            int carried = Math.min(part.getValue(), InventoryMaterials.count(pack, part.getKey()));
            if (carried > 0) {
                if (!pack.removeItemStack(new ItemStack(part.getKey(), carried)).succeeded()) return refund(pack, warehouse, fromPack, fromWarehouse);
                fromPack.put(part.getKey(), carried);
            }
            int left = part.getValue() - carried;
            if (left > 0 && (warehouse == null || !take(warehouse, part.getKey(), left, fromWarehouse))) return refund(pack, warehouse, fromPack, fromWarehouse);
        }
        return true;
    }

    /** The warehouse a player may pay from, or null when they may not open town stores. */
    @Nullable
    public static TownWarehouse stockFor(@Nullable com.hexvane.aetherhaven.town.TownRecord town, @Nullable java.util.UUID player) {
        return town != null && player != null && WarehouseAccess.canBrowse(town, player) ? town.getWarehouse() : null;
    }

    /**
     * Brings what the pack is short of out of the warehouse, so a payment written for carried items can go ahead.
     * Nothing moves unless pack and warehouse together cover the whole cost and the pack has room for it.
     *
     * @return true when the pack now holds the whole cost
     */
    public static boolean topUp(@Nonnull ItemContainer pack, @Nullable TownWarehouse warehouse, @Nonnull Map<String, Integer> cost) {
        for (var part : cost.entrySet()) if (available(pack, warehouse, part.getKey()) < part.getValue()) return false;
        if (warehouse == null) return true;
        var moved = new ArrayList<Taken>();
        for (var part : cost.entrySet()) {
            int missing = part.getValue() - (int) Math.min(part.getValue(), InventoryMaterials.count(pack, part.getKey()));
            if (missing <= 0) continue;
            var taken = new ArrayList<Taken>();
            boolean ok = take(warehouse, part.getKey(), missing, taken);
            for (var stack : taken) {
                if (ok && pack.addItemStack(stack.before().withQuantity(stack.count())).succeeded()) moved.add(stack);
                else {
                    ok = false;
                    warehouse.acceptCompletedOutput(stack.before().withQuantity(stack.count()));
                }
            }
            if (!ok) {
                // Put back whatever already reached the pack, so a full pack costs nothing.
                for (var stack : moved) {
                    if (pack.removeItemStack(stack.before().withQuantity(stack.count())).succeeded())
                        warehouse.acceptCompletedOutput(stack.before().withQuantity(stack.count()));
                }
                return false;
            }
        }
        return true;
    }

    private static boolean take(TownWarehouse warehouse, String item, int needed, List<Taken> taken) {
        for (var slot : warehouse.craftingStock().slots()) {
            if (needed <= 0) break;
            if (ItemStack.isEmpty(slot.stack()) || !item.equals(slot.stack().getItemId()) || slot.available() <= 0) continue;
            int count = Math.min(needed, slot.available());
            if (!warehouse.consumeForCrafting(slot.page(), slot.slot(), slot.stack(), count)) return false;
            taken.add(new Taken(slot.page(), slot.slot(), slot.stack(), count));
            needed -= count;
        }
        return needed <= 0;
    }

    /** Hand back anything already taken so a failed order never costs a part. */
    private static boolean refund(ItemContainer pack, TownWarehouse warehouse, Map<String, Integer> fromPack, List<Taken> fromWarehouse) {
        for (var part : fromPack.entrySet()) pack.addItemStack(new ItemStack(part.getKey(), part.getValue()));
        for (var part : fromWarehouse) warehouse.acceptCompletedOutput(part.before().withQuantity(part.count()));
        return false;
    }
}
