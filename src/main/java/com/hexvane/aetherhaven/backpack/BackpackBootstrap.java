package com.hexvane.aetherhaven.backpack;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import java.util.Objects;

public final class BackpackBootstrap {
    private BackpackBootstrap(){}
    public static void register(AetherhavenPlugin plugin){
        BackpackRuntime.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new BackpackTickSystem());
        OpenCustomUIInteraction.registerCustomPageSupplier(plugin,BackpackPage.class,"AetherhavenBackpack",(ref,accessor,player,context)->{
            var inv=InventoryComponent.getCombined(accessor,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            if(inv==null||!Backpack.isBackpack(context.getHeldItem()))return null;
            for(short i=0;i<inv.getCapacity();i++)if(Objects.equals(inv.getItemStack(i),context.getHeldItem()))return new BackpackPage(player,i,null);
            return null;
        });
    }
}
