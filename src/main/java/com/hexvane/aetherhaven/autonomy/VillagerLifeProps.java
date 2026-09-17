package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Temporary activity items, using the original item definitions and grips. Call only after the ECS tick, through a deferred command. */
public final class VillagerLifeProps {
    private static final String PREFIX = "Aetherhaven_Life_Prop_";
    static final String TEMPORARY_KEY = "AetherhavenLifeTemporary";
    static final String RESTORE_KEY = "AetherhavenLifePreviousSlot";
    static final String SALAD = "Food_Salad_Caesar";
    static final String SPOON = PREFIX + "Spoon";
    private VillagerLifeProps() {}

    static ItemStack temporary(String id, byte previousSlot) {
        return new ItemStack(id, 1).withMetadata(TEMPORARY_KEY, Codec.BOOLEAN, true)
            .withMetadata(RESTORE_KEY, Codec.BYTE, previousSlot);
    }

    static boolean isTemporary(ItemStack item) {
        return !ItemStack.isEmpty(item) && (item.getItemId().startsWith(PREFIX)
            || Boolean.TRUE.equals(item.getFromMetadataOrNull(TEMPORARY_KEY, Codec.BOOLEAN)));
    }

    static byte previousSlot(ItemStack item, byte fallback) {
        Byte saved = item.getFromMetadataOrNull(RESTORE_KEY, Codec.BYTE);
        return saved == null ? fallback : saved;
    }

    static String itemFor(String gesture, String kind) {
        return switch (gesture) {
            case "Eat" -> com.hexvane.aetherhaven.AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID;
            case "Read", "ReadLoop" -> "Weapon_Spellbook_Grimoire_Brown";
            case "Sweep" -> "Halloween_Broomstick";
            case "Craft" -> "Tool_Hammer_Iron";
            case "Mix" -> SALAD;
            case "Tend" -> "Plant_Flower_Bushy_Blue";
            default -> null;
        };
    }

    static void equip(Ref<EntityStore> ref, String gesture, Store<EntityStore> store) {
        var activeLife = store.getComponent(ref, VillagerLifeState.getComponentType());
        String loop = VillagerLifeVisuals.loopGesture(gesture);
        if (activeLife != null && !java.util.Objects.equals(loop, activeLife.activeLoopGesture)) activeLife.activeLoopGesture = null;
        if (!gesture.equals("Read") && !gesture.equals("ReadLoop")) {
            var life = store.getComponent(ref, VillagerLifeState.getComponentType());
            if (life != null) { life.readingLoop = false; }
        }
        var binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        var life = VillagerLifeVisuals.state(ref, store);
        String item = gesture.equals("ShowItem") && life.conversationItemId != null
            ? life.conversationItemId : itemFor(gesture, binding == null ? "" : binding.getKind());
        if (item == null) { clear(ref, store); return; }
        var hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null || hotbar.getInventory().getCapacity() == 0) return;
        ItemStack held = hotbar.getActiveItem();
        if (held != null && item.equals(held.getItemId())) {
            if (gesture.equals("Mix")) equipSpoon(ref, life, store);
            return;
        }
        clear(ref, store);
        byte slot = hotbar.getActiveSlot() < 0 ? 0 : hotbar.getActiveSlot();
        // Keep real equipment in its inventory slot, including across an unload.
        // A transient life component must never be the only copy of a guard's weapon.
        if (!ItemStack.isEmpty(hotbar.getInventory().getItemStack(slot))) {
            slot = -1;
            for (short s = 0; s < hotbar.getInventory().getCapacity() && s <= Byte.MAX_VALUE; s++) {
                if (ItemStack.isEmpty(hotbar.getInventory().getItemStack(s))) { slot = (byte) s; break; }
            }
            if (slot < 0) return;
        }
        life.previousActiveSlot = hotbar.getActiveSlot();
        life.temporarySlot = slot;
        life.temporaryItemId = item;
        hotbar.getInventory().setItemStackForSlot(slot, temporary(item, life.previousActiveSlot));
        VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, slot, ref, store);
        store.putComponent(ref, InventoryComponent.Hotbar.getComponentType(), hotbar);
        if (gesture.equals("Mix")) equipSpoon(ref, life, store);
    }

    public static void clear(Ref<EntityStore> ref, Store<EntityStore> store) {
        var life = store.getComponent(ref, VillagerLifeState.getComponentType());
        clearOffhand(ref, life, store);
        if (life != null) { life.readingLoop = false; life.activeLoopGesture = null; }
        var hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return;
        boolean changed = false;
        byte restoreSlot = hotbar.getActiveSlot();
        if (life != null && life.temporarySlot >= 0 && life.temporarySlot < hotbar.getInventory().getCapacity()) {
            var item = hotbar.getInventory().getItemStack(life.temporarySlot);
            if (isTemporary(item) && item.getItemId().equals(life.temporaryItemId)) {
                hotbar.getInventory().setItemStackForSlot(life.temporarySlot, ItemStack.EMPTY);
                if (restoreSlot == life.temporarySlot) restoreSlot = life.previousActiveSlot;
                changed = true;
            }
            life.temporaryItemId = null;
            life.temporarySlot = -1;
            life.previousActiveSlot = -1;
        }
        for (short slot = 0; slot < hotbar.getInventory().getCapacity(); slot++) {
            ItemStack item = hotbar.getInventory().getItemStack(slot);
            if (isTemporary(item)) {
                hotbar.getInventory().setItemStackForSlot(slot, ItemStack.EMPTY);
                if (restoreSlot == slot) restoreSlot = previousSlot(item, (byte) -1);
                changed = true;
            }
        }
        if (changed) {
            if (restoreSlot < 0) {
                for (short s = 0; s < hotbar.getInventory().getCapacity() && s <= Byte.MAX_VALUE; s++) {
                    if (!ItemStack.isEmpty(hotbar.getInventory().getItemStack(s))) { restoreSlot = (byte) s; break; }
                }
            }
            VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, restoreSlot, ref, store);
            store.putComponent(ref, InventoryComponent.Hotbar.getComponentType(), hotbar);
        }
    }

    private static void equipSpoon(Ref<EntityStore> ref, VillagerLifeState life, Store<EntityStore> store) {
        var utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        if (utility == null) {
            utility = new InventoryComponent.Utility((short) 2);
            store.putComponent(ref, InventoryComponent.Utility.getComponentType(), utility);
        }
        if (utility.getActiveItem() != null && SPOON.equals(utility.getActiveItem().getItemId())) return;
        short slot = reserveUtilitySlot(utility);
        if (slot >= 0) {
            life.previousUtilitySlot = utility.getActiveSlot();
            life.temporaryUtilitySlot = (byte) slot;
            // The native salad permits an offhand item; the spoon is a usable
            // utility item. Both now follow the client's normal equipment rules.
            var result = utility.getInventory().setItemStackForSlot(slot, temporary(SPOON, life.previousUtilitySlot));
            if (!result.succeeded()) { life.temporaryUtilitySlot = -1; return; }
            utility.setActiveSlot((byte) slot, ref, store);
            utility.setOutdatedEquipment(true);
            store.putComponent(ref, InventoryComponent.Utility.getComponentType(), utility);
            return;
        }
    }

    static short reserveUtilitySlot(InventoryComponent.Utility utility) {
        short capacity = utility.getInventory().getCapacity();
        for (short slot = 0; slot < capacity && slot <= Byte.MAX_VALUE; slot++) {
            if (ItemStack.isEmpty(utility.getInventory().getItemStack(slot))) return slot;
        }
        if (capacity > Byte.MAX_VALUE) return -1;
        // Some NPC roles have a zero-capacity utility inventory or a permanent
        // torch/shield. Add a spare slot instead of silently omitting the bowl.
        utility.ensureCapacity((short) (capacity + 1), new java.util.ArrayList<>());
        return capacity;
    }

    private static void clearOffhand(Ref<EntityStore> ref, VillagerLifeState life, Store<EntityStore> store) {
        var utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        if (utility == null) return;
        byte restore = utility.getActiveSlot();
        boolean changed = false;
        boolean knownPreviousSlot = false;
        for (short slot = 0; slot < utility.getInventory().getCapacity(); slot++) {
            var item = utility.getInventory().getItemStack(slot);
            if (!isTemporary(item)) continue;
            utility.getInventory().setItemStackForSlot(slot, ItemStack.EMPTY, false);
            if (restore == slot) {
                knownPreviousSlot = item.getFromMetadataOrNull(RESTORE_KEY, Codec.BYTE) != null;
                restore = previousSlot(item, life != null && life.temporaryUtilitySlot == slot ? life.previousUtilitySlot : (byte) -1);
            }
            changed = true;
        }
        if (life != null) { life.temporaryUtilitySlot = -1; life.previousUtilitySlot = -1; }
        if (!changed) return;
        if (restore < 0 && !knownPreviousSlot) for (short slot = 0; slot < utility.getInventory().getCapacity() && slot <= Byte.MAX_VALUE; slot++) {
            if (!ItemStack.isEmpty(utility.getInventory().getItemStack(slot))) { restore = (byte) slot; break; }
        }
        utility.setActiveSlot(restore, ref, store);
        utility.setOutdatedEquipment(true);
        store.putComponent(ref, InventoryComponent.Utility.getComponentType(), utility);
    }
}
