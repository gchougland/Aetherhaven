package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PlayerInventoryLayout {
    private PlayerInventoryLayout() {}
    public interface Cell { void render(String row,int column,short inventorySlot); }
    /** How many slots at the start of the combined hotbar, storage and backpack inventory are the hotbar. */
    public static int hotbarSlots(Store<EntityStore> store,Ref<EntityStore> ref){
        var hotbar=store.getComponent(ref,InventoryComponent.Hotbar.getComponentType());
        return hotbar==null?0:hotbar.getInventory().getCapacity();
    }
    public static void render(UICommandBuilder c,Store<EntityStore> store,Ref<EntityStore> ref,ItemContainer inventory,Cell cell){
        var hotbar=store.getComponent(ref,InventoryComponent.Hotbar.getComponentType());
        int count=hotbar==null?0:hotbar.getInventory().getCapacity();
        var storage=store.getComponent(ref,InventoryComponent.Storage.getComponentType());
        int mainEnd=Math.min(inventory.getCapacity(),count+(storage==null?0:storage.getInventory().getCapacity()));
        c.clear("#NativeBackpackItems");
        c.set("#NativeBackpackPanel.Visible",mainEnd<inventory.getCapacity());
        int rows=0;
        for(int start=count;start<mainEnd;start+=9){
            c.append("#PlayerItems","Aetherhaven/WarehouseGridRow.ui");
            for(int col=0;col<9&&start+col<mainEnd;col++)cell.render("#PlayerItems["+rows+"]",col,(short)(start+col));
            rows++;
        }
        for(int start=mainEnd,row=0;start<inventory.getCapacity();start+=3,row++){
            c.append("#NativeBackpackItems","Aetherhaven/WarehouseGridRow.ui");
            for(int col=0;col<3&&start+col<inventory.getCapacity();col++)cell.render("#NativeBackpackItems["+row+"]",col,(short)(start+col));
        }
        for(int start=0;start<count;start+=9){
            c.append("#PlayerItems","Aetherhaven/WarehouseGridRow.ui");
            for(int col=0;col<9&&start+col<count;col++)cell.render("#PlayerItems["+rows+"]",col,(short)(start+col));
            rows++;
        }
    }
}
