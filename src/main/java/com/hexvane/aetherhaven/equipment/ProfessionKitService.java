package com.hexvane.aetherhaven.equipment;

import com.hexvane.aetherhaven.townsfolk.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.Objects;

/** Invoked on the world thread. Single item kits exchange in place, even when both inventories are full. */
public final class ProfessionKitService {
    private ProfessionKitService() {}
    public static String equip(TownsfolkProgress progress, ProfessionKitCatalog catalog, ItemContainer source,
                               short slot, ItemStack expected, ItemStack expectedEquipped) {
        if (!Objects.equals(progress.professionKit(), expectedEquipped)) return "Equipment changed. Choose a kit again.";
        if (slot < 0 || slot >= source.getCapacity() || ItemStack.isEmpty(expected) || expected.getQuantity() != 1
            || !expected.equals(source.getItemStack(slot))) return "That kit moved. Choose a kit again.";
        var kit = catalog.find(expected.getItemId());
        if (kit == null) return "This item is not a profession kit.";
        if (progress.skill(kit.profession()) == 0) return "This resident cannot perform that profession.";
        // Serialize before touching either owner so malformed metadata cannot remove a kit.
        String encoded = TownsfolkProgress.encodeProfessionKit(expected);
        if (!source.replaceItemStackInSlot(slot, expected, expectedEquipped).succeeded()) return "That kit moved. Choose a kit again.";
        progress.setProfessionKitData(encoded);
        return null;
    }
    public static double workMultiplier(TownsfolkProgress progress, TownsfolkSkill skill, ProfessionKitCatalog catalog) {
        var stack = progress.professionKit();
        var kit = ItemStack.isEmpty(stack) ? null : catalog.find(stack.getItemId());
        return kit != null && kit.profession() == skill && progress.skill(skill) > 0 ? 1 + kit.workSpeedBonus() : 1;
    }
    public static String unequip(TownsfolkProgress progress, ItemContainer destination, ItemStack expected) {
        if (ItemStack.isEmpty(expected) || !Objects.equals(progress.professionKit(), expected)) return "Equipment changed. Refresh and try again.";
        if (destination == null || !destination.addItemStack(expected,true,false,true).succeeded()) return "Make room in your inventory for the kit.";
        progress.setProfessionKitData(null);
        return null;
    }
    public static int extraStacks(TownsfolkProgress progress, ProfessionKitCatalog catalog) {
        var stack = progress.professionKit();
        var kit = ItemStack.isEmpty(stack) ? null : catalog.find(stack.getItemId());
        return kit != null && kit.profession() == TownsfolkSkill.LOGISTICS && progress.skill(TownsfolkSkill.LOGISTICS) > 0
            ? kit.extraCarryingStacks() : 0;
    }
}
