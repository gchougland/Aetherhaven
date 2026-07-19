package com.hexvane.aetherhaven.equipment;

import com.hexvane.aetherhaven.equipment.data.EquipmentProfileCatalog;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies data driven armor, weapons, and held items to NPC entities. */
public final class VillagerEquipmentService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private VillagerEquipmentService() {}

    public static void applyProfile(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull EquipmentProfileCatalog catalog,
        @Nonnull String profileId
    ) {
        EquipmentProfileDefinition def = catalog.byId(profileId);
        if (def == null) {
            LOGGER.atWarning().log("Unknown equipment profile %s", profileId);
            return;
        }
        applyProfile(npcRef, store, commandBuffer, def);
    }

    public static void applyProfile(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull EquipmentProfileDefinition def
    ) {
        applyArmor(npcRef, store, commandBuffer, def.getArmorItemIds());
        applyHotbar(npcRef, store, commandBuffer, def);
        applyOffhand(npcRef, store, commandBuffer, def.getOffhandItemId());
    }

    /** Clears all hotbar slots (drops held work tools after leaving a work POI). */
    public static void clearHotbar(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        InventoryComponent.Hotbar hb = store.getComponent(npcRef, InventoryComponent.Hotbar.getComponentType());
        if (hb == null) {
            return;
        }
        try {
            short capacity = hb.getInventory().getCapacity();
            for (short s = 0; s < capacity; s++) {
                hb.getInventory().setItemStackForSlot(s, ItemStack.EMPTY);
            }
            hb.setActiveSlot((byte) 0, npcRef, commandBuffer);
            markHotbarEquipmentDirty(hb, (byte) 0, npcRef, commandBuffer);
            putHotbar(npcRef, store, commandBuffer, hb);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not clear NPC hotbar");
        }
    }

    private static void applyArmor(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull java.util.List<String> armorIds
    ) {
        if (armorIds.isEmpty()) {
            return;
        }
        InventoryComponent.Armor armor = store.getComponent(npcRef, InventoryComponent.Armor.getComponentType());
        if (armor == null) {
            return;
        }
        try {
            for (String itemId : armorIds) {
                if (!itemId.isBlank()) {
                    InventoryHelper.useArmor(armor.getInventory(), itemId);
                }
            }
            putArmor(npcRef, store, commandBuffer, armor);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not equip armor on NPC");
        }
    }

    private static void applyHotbar(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull EquipmentProfileDefinition def
    ) {
        InventoryComponent.Hotbar hb = store.getComponent(npcRef, InventoryComponent.Hotbar.getComponentType());
        if (hb == null) {
            return;
        }
        try {
            if (def.getHotbarSlots().isEmpty()) {
                short capacity = hb.getInventory().getCapacity();
                for (short s = 0; s < capacity; s++) {
                    hb.getInventory().setItemStackForSlot(s, ItemStack.EMPTY);
                }
                hb.setActiveSlot((byte) 0, npcRef, commandBuffer);
                markHotbarEquipmentDirty(hb, (byte) 0, npcRef, commandBuffer);
                putHotbar(npcRef, store, commandBuffer, hb);
                return;
            }
            byte active = 0;
            for (EquipmentProfileDefinition.HotbarSlot slot : def.getHotbarSlots()) {
                String itemId = slot.getItemId();
                if (itemId.isEmpty()) {
                    continue;
                }
                short s = (short) Math.max(0, slot.getSlot());
                hb.getInventory().setItemStackForSlot(s, new ItemStack(itemId, 1));
                active = (byte) s;
            }
            hb.setActiveSlot(active, npcRef, commandBuffer);
            markHotbarEquipmentDirty(hb, active, npcRef, commandBuffer);
            putHotbar(npcRef, store, commandBuffer, hb);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not equip hotbar on NPC");
        }
    }

    private static void applyOffhand(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nullable String offhandItemId
    ) {
        if (offhandItemId == null || offhandItemId.isBlank()) {
            return;
        }
        InventoryComponent.Utility util = store.getComponent(npcRef, InventoryComponent.Utility.getComponentType());
        if (util == null) {
            return;
        }
        try {
            util.getInventory().setItemStackForSlot((short) 0, new ItemStack(offhandItemId, 1));
            putUtility(npcRef, store, commandBuffer, util);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not equip offhand on NPC");
        }
    }

    private static void putArmor(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InventoryComponent.Armor armor
    ) {
        if (commandBuffer != null) {
            commandBuffer.putComponent(npcRef, InventoryComponent.Armor.getComponentType(), armor);
        } else {
            store.putComponent(npcRef, InventoryComponent.Armor.getComponentType(), armor);
        }
    }

    /**
     * NPC held-item visuals only refresh on active-slot changes or {@link InventoryComponent.Hotbar#setOutdatedEquipment}.
     * Replacing the item in the already-active slot needs both.
     */
    public static void markHotbarEquipmentDirty(
        @Nonnull InventoryComponent.Hotbar hb,
        byte targetSlot,
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        byte current = hb.getActiveSlot();
        if (current == targetSlot) {
            byte alt = alternateHotbarSlot(hb, targetSlot);
            hb.setActiveSlot(alt, npcRef, commandBuffer);
        }
        hb.setActiveSlot(targetSlot, npcRef, commandBuffer);
        hb.setOutdatedEquipment(true);
    }

    private static byte alternateHotbarSlot(@Nonnull InventoryComponent.Hotbar hb, byte avoid) {
        short capacity = hb.getInventory().getCapacity();
        for (short s = 0; s < capacity; s++) {
            if ((byte) s != avoid) {
                return (byte) s;
            }
        }
        return -1;
    }

    private static void putHotbar(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InventoryComponent.Hotbar hb
    ) {
        if (commandBuffer != null) {
            commandBuffer.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(), hb);
        } else {
            store.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(), hb);
        }
    }

    private static void putUtility(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InventoryComponent.Utility util
    ) {
        if (commandBuffer != null) {
            commandBuffer.putComponent(npcRef, InventoryComponent.Utility.getComponentType(), util);
        } else {
            store.putComponent(npcRef, InventoryComponent.Utility.getComponentType(), util);
        }
    }
}
