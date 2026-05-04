package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Pays gold for a full durability restore to the item definition max (clears repair-kit max-durability loss). */
public final class BlacksmithRepairInteraction extends ChoiceInteraction {
    private static final double EPS = 1e-6;
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
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TownRecord town = uc != null ? tm.findTownForPlayerInWorld(uc.getUuid()) : null;
        boolean allowTreasury = uc != null && town != null && town.playerCanSpendTreasuryGold(uc.getUuid());
        if (!GoldCoinPayment.canAfford(town, inv, cost, allowTreasury)) {
            playerRef.sendMessage(Message.translation("aetherhaven_misc.aetherhaven.blacksmith.repair.insufficientGold").color("#ff5555"));
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        SpendBreakdown paid = GoldCoinPayment.trySpendReturningBreakdown(town, inv, cost, allowTreasury);
        if (paid == null) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        ItemStack restored = itemStack.withRestoredDurability(baseMax);
        ItemStackSlotTransaction replace =
            this.itemContext.getContainer().replaceItemStackInSlot(this.itemContext.getSlot(), itemStack, restored);
        if (!replace.succeeded()) {
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
        Message nameMsg = Message.translation(restored.getItem().getTranslationKey());
        playerRef.sendMessage(Message.translation("aetherhaven_misc.aetherhaven.blacksmith.repair.success").param("itemName", nameMsg));
        pageManager.setPage(ref, store, Page.None);
        UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_WEAPON_BENCH_CRAFT);
    }
}
