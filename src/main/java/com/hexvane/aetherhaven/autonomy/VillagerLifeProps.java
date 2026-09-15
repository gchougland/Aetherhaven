package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Temporary work props. Call only after the ECS tick, through a deferred command. */
public final class VillagerLifeProps {
    private static final String PREFIX = "Aetherhaven_Life_Prop_";
    private VillagerLifeProps() {}

    static String itemFor(String gesture, String kind) {
        return switch (gesture) {
            case "Eat" -> com.hexvane.aetherhaven.AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID;
            case "Read", "ReadLoop" -> PREFIX + "OpenBook";
            case "Sweep" -> PREFIX + "Broom";
            case "Craft" -> PREFIX + (TownVillagerBinding.KIND_CHEF.equals(kind) ? "Spoon" : "Mallet");
            case "Inspect" -> PREFIX + "Stone";
            case "Tend" -> PREFIX + "Plant";
            default -> null;
        };
    }

    static void equip(Ref<EntityStore> ref, String gesture, Store<EntityStore> store) {
        if (!gesture.equals("Read") && !gesture.equals("ReadLoop")) {
            var life = store.getComponent(ref, VillagerLifeState.getComponentType());
            if (life != null) { life.readingLoop = false; life.readingResumeMs = 0; }
        }
        var binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        String item = itemFor(gesture, binding == null ? "" : binding.getKind());
        if (item == null) { clear(ref, store); return; }
        var hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null || hotbar.getInventory().getCapacity() == 0) return;
        ItemStack held = hotbar.getActiveItem();
        if (held != null && item.equals(held.getItemId())) return;
        byte slot = hotbar.getActiveSlot() < 0 ? 0 : hotbar.getActiveSlot();
        hotbar.getInventory().setItemStackForSlot(slot, new ItemStack(item, 1));
        VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, slot, ref, store);
        store.putComponent(ref, InventoryComponent.Hotbar.getComponentType(), hotbar);
    }

    public static void clear(Ref<EntityStore> ref, Store<EntityStore> store) {
        var life = store.getComponent(ref, VillagerLifeState.getComponentType());
        if (life != null) { life.readingLoop = false; life.readingResumeMs = 0; }
        var hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return;
        boolean changed = false;
        for (short slot = 0; slot < hotbar.getInventory().getCapacity(); slot++) {
            ItemStack item = hotbar.getInventory().getItemStack(slot);
            if (item != null && item.getItemId().startsWith(PREFIX)) {
                hotbar.getInventory().setItemStackForSlot(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, hotbar.getActiveSlot(), ref, store);
            store.putComponent(ref, InventoryComponent.Hotbar.getComponentType(), hotbar);
        }
    }
}
