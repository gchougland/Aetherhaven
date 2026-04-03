package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.plot.TreasuryBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TreasuryPage extends InteractiveCustomUIPage<TreasuryPage.PageData> {
    private final Ref<ChunkStore> treasuryBlockRef;
    private boolean templateAppended;

    public TreasuryPage(@Nonnull PlayerRef playerRef, @Nonnull Ref<ChunkStore> treasuryBlockRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.treasuryBlockRef = treasuryBlockRef;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/TreasuryPage.ui");
            templateAppended = true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            commandBuilder.set("#Balance.TextSpans", Message.raw("Aetherhaven not loaded."));
            return;
        }
        Store<ChunkStore> cs = treasuryBlockRef.getStore();
        TreasuryBlock tb = cs.getComponent(treasuryBlockRef, TreasuryBlock.getComponentType());
        if (tb == null || tb.getTownId().isBlank()) {
            commandBuilder.set("#Balance.TextSpans", Message.raw("Treasury is not linked."));
            return;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(tb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            commandBuilder.set("#Balance.TextSpans", Message.raw("Invalid town link."));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            commandBuilder.set("#Balance.TextSpans", Message.raw("Town not found."));
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null || !town.getOwnerUuid().equals(uc.getUuid())) {
            commandBuilder.set("#Balance.TextSpans", Message.raw("Only the town owner may use the treasury."));
            commandBuilder.set("#DepositButton.Disabled", true);
            commandBuilder.set("#WithdrawButton.Disabled", true);
            return;
        }

        long bal = town.getTreasuryGoldCoinCount();
        commandBuilder.set("#Balance.TextSpans", Message.raw("Gold coins stored: " + bal));
        commandBuilder.set("#DepositButton.Disabled", false);
        commandBuilder.set("#WithdrawButton.Disabled", bal <= 0L);

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DepositButton",
            new EventData().append("Action", "Deposit"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#WithdrawButton",
            new EventData().append("Action", "Withdraw"),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        String action = data.action;
        if (action == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return;
        }
        Store<ChunkStore> cs = treasuryBlockRef.getStore();
        TreasuryBlock tb = cs.getComponent(treasuryBlockRef, TreasuryBlock.getComponentType());
        if (tb == null || tb.getTownId().isBlank()) {
            return;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(tb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (uc == null || player == null || !town.getOwnerUuid().equals(uc.getUuid())) {
            return;
        }

        if (action.equalsIgnoreCase("Deposit")) {
            CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
            int have = InventoryMaterials.count(inv, AetherhavenConstants.ITEM_GOLD_COIN);
            if (have <= 0) {
                if (pr != null) {
                    pr.sendMessage(Message.raw("You have no gold coins to deposit."));
                }
                refresh(ref, store);
                return;
            }
            ItemStackTransaction tx = inv.removeItemStack(new ItemStack(AetherhavenConstants.ITEM_GOLD_COIN, have));
            if (!tx.succeeded()) {
                if (pr != null) {
                    pr.sendMessage(Message.raw("Could not remove coins from inventory."));
                }
                refresh(ref, store);
                return;
            }
            town.addTreasuryGoldCoins(have);
            tm.updateTown(town);
            if (pr != null) {
                pr.sendMessage(Message.raw("Deposited " + have + " gold coins."));
            }
            refresh(ref, store);
            return;
        }

        if (action.equalsIgnoreCase("Withdraw")) {
            long bal = town.getTreasuryGoldCoinCount();
            if (bal <= 0L) {
                refresh(ref, store);
                return;
            }
            int give = (int) Math.min(bal, 9999);
            ItemStack stack = new ItemStack(AetherhavenConstants.ITEM_GOLD_COIN, give);
            CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
            if (inv == null || !inv.canAddItemStack(stack)) {
                if (pr != null) {
                    pr.sendMessage(Message.raw("Make room in your inventory."));
                }
                refresh(ref, store);
                return;
            }
            ItemStackTransaction giveTx = player.giveItem(stack, ref, store);
            if (!giveTx.succeeded()) {
                if (pr != null) {
                    pr.sendMessage(Message.raw("Could not add coins."));
                }
                refresh(ref, store);
                return;
            }
            town.addTreasuryGoldCoins(-(long) give);
            tm.updateTown(town);
            if (pr != null) {
                pr.sendMessage(Message.raw("Withdrew " + give + " gold coins."));
            }
            refresh(ref, store);
        }
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .build();

        @Nullable
        private String action;
    }
}
