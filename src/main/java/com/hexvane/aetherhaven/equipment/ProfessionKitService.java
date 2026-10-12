package com.hexvane.aetherhaven.equipment;

import com.hexvane.aetherhaven.townsfolk.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/** Invoked on the world thread. Single item kits exchange in place, even when both inventories are full. */
public final class ProfessionKitService {
    private ProfessionKitService() {}
    /** The one true reason a chosen kit cannot be equipped from where it was: it is not there any more. */
    public static final String TAKEN = "That kit is no longer there. Nothing was changed.";
    public static final String NONE_WORN = "This resident is not wearing a kit now.";

    /**
     * Where the exact item a press named is now: the slot it was drawn in while it is still there, otherwise another
     * slot holding the identical item, otherwise -1. A kit is one item with its own wear, so it is matched exactly.
     */
    public static short locate(ItemContainer source, short slot, ItemStack expected) {
        if (source == null || ItemStack.isEmpty(expected) || expected.getQuantity() != 1) return -1;
        if (slot >= 0 && slot < source.getCapacity() && expected.equals(source.getItemStack(slot))) return slot;
        for (short i = 0; i < source.getCapacity(); i++) if (expected.equals(source.getItemStack(i))) return i;
        return -1;
    }

    /**
     * Swaps the chosen kit with whatever the resident wears at this moment. The page may have drawn an older kit on
     * the resident; that is no reason to refuse, because the swap is the same either way and nothing is lost: the
     * kit worn now goes into the slot the new one leaves.
     */
    public static String equip(TownsfolkProgress progress, ProfessionKitCatalog catalog, ItemContainer source,
                               short slot, ItemStack expected) {
        short at = locate(source, slot, expected);
        if (at < 0) return TAKEN;
        var kit = catalog.find(expected.getItemId());
        if (kit == null) return "This item is not a profession kit.";
        if (progress.skill(kit.profession()) == 0) return "This resident cannot perform that profession.";
        // Serialize before touching either owner so malformed metadata cannot remove a kit.
        String encoded = TownsfolkProgress.encodeProfessionKit(expected);
        if (!source.replaceItemStackInSlot(at, expected, progress.professionKit()).succeeded()) return TAKEN;
        progress.setProfessionKitData(encoded);
        return null;
    }
    public static double workMultiplier(TownsfolkProgress progress, TownsfolkSkill skill, ProfessionKitCatalog catalog) {
        var stack = progress.professionKit();
        var kit = ItemStack.isEmpty(stack) ? null : catalog.find(stack.getItemId());
        return kit != null && kit.profession() == skill && progress.skill(skill) > 0 ? 1 + kit.workSpeedBonus() : 1;
    }
    /** Returns the kit worn at this moment, whichever it is. */
    public static String unequip(TownsfolkProgress progress, ItemContainer destination) {
        var worn = progress.professionKit();
        if (ItemStack.isEmpty(worn)) return NONE_WORN;
        if (destination == null || !destination.addItemStack(worn,true,false,true).succeeded()) return "Make room in your inventory for the kit.";
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
