package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Pays gold for a full durability restore to the item definition max (clears repair-kit max-durability loss). */
public final class BlacksmithRepairInteraction extends ChoiceInteraction {
    private static final double EPS = 1e-6;
    private static final String SOUND_EVENT_ITEM_REPAIR = "SFX_Item_Repair";

    private final ItemContext itemContext;

    public BlacksmithRepairInteraction(@Nonnull ItemContext itemContext) {
        this.itemContext = itemContext;
    }

    static boolean needsBlacksmithRepair(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || stack.isUnbreakable()) {
            return false;
        }
        double baseMax = stack.getItem().getMaxDurability();
        if (baseMax <= EPS) {
            return false;
        }
        double cur = stack.getDurability();
        double stackMax = stack.getMaxDurability();
        return cur < baseMax - EPS || stackMax < baseMax - EPS;
    }

    static int goldCost(@Nonnull ItemStack stack, int fullCost) {
        double baseMax = stack.getItem().getMaxDurability();
        if (baseMax <= EPS) {
            return Math.max(1, fullCost);
        }
        double cur = stack.getDurability();
        double missing = (baseMax - cur) / baseMax;
        missing = MathUtil.clamp(missing, 0.0, 1.0);
        int cost = (int) Math.ceil(fullCost * missing);
        return Math.max(1, cost);
    }

    @Override
    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PageManager pageManager = player.getPageManager();
        ItemStack itemStack = this.itemContext.getItemStack();
        if (!needsBlacksmithRepair(itemStack)) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        double baseMax = itemStack.getItem().getMaxDurability();
        int cost = goldCost(itemStack, AetherhavenConstants.BLACKSMITH_REPAIR_COST_FULL);
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        if (InventoryMaterials.count(inv, AetherhavenConstants.ITEM_GOLD_COIN) < cost) {
            playerRef.sendMessage(Message.translation("server.aetherhaven.blacksmith.repair.insufficientGold").color("#ff5555"));
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        ItemStackTransaction pay = inv.removeItemStack(new ItemStack(AetherhavenConstants.ITEM_GOLD_COIN, cost));
        if (!pay.succeeded()) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        ItemStack restored = itemStack.withRestoredDurability(baseMax);
        ItemStackSlotTransaction replace =
            this.itemContext.getContainer().replaceItemStackInSlot(this.itemContext.getSlot(), itemStack, restored);
        if (!replace.succeeded()) {
            player.giveItem(new ItemStack(AetherhavenConstants.ITEM_GOLD_COIN, cost), ref, store);
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        Message nameMsg = Message.translation(restored.getItem().getTranslationKey());
        playerRef.sendMessage(Message.translation("server.aetherhaven.blacksmith.repair.success").param("itemName", nameMsg));
        pageManager.setPage(ref, store, Page.None);
        SoundUtil.playSoundEvent2d(ref, soundEventIndex(SOUND_EVENT_ITEM_REPAIR), SoundCategory.UI, store);
    }

    private static int soundEventIndex(@Nonnull String soundEventId) {
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        return index == Integer.MIN_VALUE ? SoundEvent.EMPTY_ID : index;
    }
}
