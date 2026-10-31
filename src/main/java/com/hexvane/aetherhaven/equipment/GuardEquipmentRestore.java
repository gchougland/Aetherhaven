package com.hexvane.aetherhaven.equipment;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerLifeProps;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileCatalog;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;

/** Rebuilds missing NPC equipment from the saved guard loadout, never from a free kit item. */
public final class GuardEquipmentRestore {
    private GuardEquipmentRestore() {}

    /** Called after the ECS tick; retries while the NPC, its role or its inventory is still loading. */
    public static void refresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        var plugin = AetherhavenPlugin.get();
        var binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        var uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        var npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (plugin == null || binding == null || uuid == null || npc == null || npc.getRole() == null
                || npc.getRole().isRoleChangeRequested()
                || store.getComponent(ref, DeathComponent.getComponentType()) != null) return;
        if (!TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) return;
        var town = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin)
            .getTown(binding.getTownId());
        var profile = savedProfile(town, uuid.getUuid(), binding.getKind(), npc.getRoleName(),
            plugin.getEquipmentProfileCatalog());
        if (profile == null) return;
        // Native NPC initialization supplies these. Wait rather than racing its inventory setup.
        var armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        var hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        var utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        if (armor == null || hotbar == null || utility == null) return;
        restore(profile, armor, hotbar, utility, ref, store);
    }

    static EquipmentProfileDefinition savedProfile(TownRecord town, UUID uuid, String kind, String role,
            EquipmentProfileCatalog catalog) {
        if (town == null || uuid == null || !TownVillagerBinding.KIND_GUARD.equals(kind)) return null;
        var guard = town.getHiredGuardRecords().stream().filter(g -> uuid.equals(g.getEntityUuid())).findFirst().orElse(null);
        var profile = guard == null ? null : catalog.byId(guard.getEquipmentProfileId());
        // Never turn an old/stale entity back into a guard, or downgrade an unavailable addon kit.
        return profile != null && role != null && role.equals(profile.getGuardNpcRole()) ? profile : null;
    }

    /** Repairs individual slots only; matching stacks retain their durability and metadata. No health changes. */
    static void restore(EquipmentProfileDefinition profile, InventoryComponent.Armor armor,
            InventoryComponent.Hotbar hotbar, InventoryComponent.Utility utility,
            Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        for (String id : profile.getArmorItemIds()) {
            var item = Item.getAssetMap().getAsset(id);
            if (item != null && item.getArmor() != null
                    && restoreSlot(armor.getInventory(), (short) item.getArmor().getArmorSlot().ordinal(), id)) {
                armor.setOutdatedEquipment(true);
            }
        }
        // Eating, reading and other gestures borrow a slot. Let them finish before selecting a weapon/shield.
        if (!hasTemporaryItem(hotbar.getInventory())) {
            byte active = -1;
            boolean changed = false;
            for (var slot : profile.getHotbarSlots()) {
                if (slot.getSlot() < 0 || slot.getSlot() >= hotbar.getInventory().getCapacity()
                        || slot.getSlot() > Byte.MAX_VALUE || slot.getItemId().isBlank()) continue;
                changed |= restoreSlot(hotbar.getInventory(), (short) slot.getSlot(), slot.getItemId());
                var equipped = hotbar.getInventory().getItemStack((short) slot.getSlot());
                if (!ItemStack.isEmpty(equipped) && slot.getItemId().equals(equipped.getItemId())) {
                    active = (byte) slot.getSlot();
                }
            }
            if (active >= 0 && (changed || ItemStack.isEmpty(hotbar.getActiveItem()))) {
                VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, active, ref, accessor);
            }
        }
        String offhand = profile.getOffhandItemId();
        if (offhand != null && !offhand.isBlank() && Item.getAssetMap().getAsset(offhand) != null
                && !hasTemporaryItem(utility.getInventory())) {
            VillagerEquipmentService.prepareOffhandInventory(utility);
            boolean changed = restoreSlot(utility.getInventory(), (short) 0, offhand);
            if (changed || utility.getActiveSlot() != 0) {
                VillagerEquipmentService.markOffhandEquipmentDirty(utility, ref, accessor);
            }
        }
    }

    private static boolean restoreSlot(ItemContainer inventory, short slot, String id) {
        if (slot < 0 || slot >= inventory.getCapacity() || Item.getAssetMap().getAsset(id) == null) return false;
        var current = inventory.getItemStack(slot);
        if (!ItemStack.isEmpty(current) && id.equals(current.getItemId())) return false;
        return inventory.setItemStackForSlot(slot, new ItemStack(id, 1)).succeeded();
    }

    private static boolean hasTemporaryItem(ItemContainer inventory) {
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            if (VillagerLifeProps.isTemporary(inventory.getItemStack(slot))) return true;
        }
        return false;
    }
}
