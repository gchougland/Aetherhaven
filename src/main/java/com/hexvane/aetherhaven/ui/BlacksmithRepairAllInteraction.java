package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import javax.annotation.Nonnull;

/** Pays gold once to fully restore every damaged item in the repair list. */
public final class BlacksmithRepairAllInteraction extends ChoiceInteraction {
    private final ItemContainer itemContainer;

    public BlacksmithRepairAllInteraction(@Nonnull ItemContainer itemContainer) {
        this.itemContainer = itemContainer;
    }

    @Override
    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PageManager pageManager = player.getPageManager();
        ShortArrayList slots = new ShortArrayList();
        int cost = 0;
        int fullCost = AetherhavenConstants.BLACKSMITH_REPAIR_COST_FULL;
        for (short slot = 0; slot < this.itemContainer.getCapacity(); slot++) {
            ItemStack stack = this.itemContainer.getItemStack(slot);
            if (!BlacksmithRepairInteraction.needsBlacksmithRepair(stack)) {
                continue;
            }
            slots.add(slot);
            cost += BlacksmithRepairInteraction.goldCost(stack, fullCost);
        }
        if (slots.isEmpty() || cost <= 0) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TownRecord town = uc != null ? TownPlayerResolution.resolveActiveTown(world, store, ref, tm) : null;
        boolean allowTreasury = uc != null && town != null && town.playerCanSpendTreasuryGold(uc.getUuid());
        if (!GoldCoinPayment.canAfford(town, inv, cost, allowTreasury)) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_misc.aetherhaven.blacksmith.repair.insufficientGold").color("#ff5555")
            );
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        SpendBreakdown paid = GoldCoinPayment.trySpendReturningBreakdown(town, inv, cost, allowTreasury);
        if (paid == null) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        int repaired = 0;
        for (int i = 0; i < slots.size(); i++) {
            short slot = slots.getShort(i);
            ItemStack stack = this.itemContainer.getItemStack(slot);
            if (!BlacksmithRepairInteraction.needsBlacksmithRepair(stack)) {
                continue;
            }
            double baseMax = stack.getItem().getMaxDurability();
            ItemStack restored = stack.withRestoredDurability(baseMax);
            ItemStackSlotTransaction replace = this.itemContainer.replaceItemStackInSlot(slot, stack, restored);
            if (replace.succeeded()) {
                repaired++;
            }
        }
        if (repaired == 0) {
            GoldCoinPayment.refund(town, player, ref, store, paid);
            if (town != null) {
                tm.updateTown(town);
            }
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        if (town != null && paid.fromTreasury() > 0L) {
            tm.updateTown(town);
        }
        playerRef.sendMessage(
            Message.translation("aetherhaven_misc.aetherhaven.blacksmith.repair.fixAll.success")
                .param("count", repaired)
                .param("cost", cost)
        );
        pageManager.setPage(ref, store, Page.None);
        UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_WEAPON_BENCH_CRAFT);
    }
}
