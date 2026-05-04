package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.geode.GeodeLootFiles;
import com.hexvane.aetherhaven.geode.GeodeLootTable;
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
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Open geodes from inventory; blacksmith charges gold, geode anvil does not. Stays open until Back. */
public final class GeodeOpenPage extends InteractiveCustomUIPage<GeodeOpenPage.PageData> {
    private static final String ROWS = "#Content #GeodeRows";
    private static final int MAX_ROWS = 48;

    private final boolean chargeGold;
    private boolean templateAppended;

    public GeodeOpenPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, true);
    }

    /**
     * @param chargeGold when true, deducts {@link AetherhavenConstants#GEODE_OPEN_GOLD_COST} per open (blacksmith); when false, only consumes the geode (anvil block).
     */
    public GeodeOpenPage(@Nonnull PlayerRef playerRef, boolean chargeGold) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.chargeGold = chargeGold;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/GeodeOpen.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyGeodeOpen(commandBuilder);
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        if (inv == null) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.geodeopen.empty"));
            commandBuilder.clear(ROWS);
            return;
        }

        List<Short> slots = new ObjectArrayList<>();
        for (short s = 0; s < inv.getCapacity(); s++) {
            ItemStack st = inv.getItemStack(s);
            if (ItemStack.isEmpty(st)) {
                continue;
            }
            if (AetherhavenConstants.ITEM_GEODE.equals(st.getItemId())) {
                slots.add(s);
            }
        }

        if (slots.isEmpty()) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.geodeopen.empty"));
            commandBuilder.clear(ROWS);
            return;
        }

        commandBuilder.set(
            "#Hint.TextSpans",
            Message.translation(
                chargeGold ? "aetherhaven_jewelry_geode.aetherhaven.ui.geodeopen.hint" : "aetherhaven_jewelry_geode.aetherhaven.ui.geodeopen.hintAnvil"
            )
        );
        commandBuilder.clear(ROWS);
        int n = Math.min(slots.size(), MAX_ROWS);
        for (int i = 0; i < n; i++) {
            short slot = slots.get(i);
            ItemStack stack = inv.getItemStack(slot);
            commandBuilder.append(ROWS, "Aetherhaven/GeodeOpenRow.ui");
            String row = ROWS + "[" + i + "]";
            com.hypixel.hytale.server.core.asset.type.item.config.Item it = stack.getItem();
            Message itemName =
                it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()
                    ? Message.translation(it.getTranslationKey())
                    : Message.raw(stack.getItemId());
            commandBuilder.set(row + " #Open #GeodeIcon.ItemId", stack.getItemId());
            commandBuilder.set(row + " #Open #GeodeIcon.Quantity", stack.getQuantity());
            commandBuilder.set(
                row + " #Open #Line.TextSpans",
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.geode.row").param("count", stack.getQuantity()).param("itemName", itemName)
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Open",
                new EventData().append("Action", "Open").append("Slot", String.valueOf(slot)),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || !data.action.equalsIgnoreCase("Open")) {
            return;
        }
        if (data.slot == null || data.slot.isBlank()) {
            return;
        }
        short slot;
        try {
            slot = Short.parseShort(data.slot.trim());
        } catch (NumberFormatException e) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || player == null || pr == null) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        if (inv == null) {
            return;
        }
        ItemStack geodeStack = inv.getItemStack(slot);
        if (ItemStack.isEmpty(geodeStack) || !AetherhavenConstants.ITEM_GEODE.equals(geodeStack.getItemId())) {
            refresh(ref, store);
            return;
        }
        int goldCost = chargeGold ? AetherhavenConstants.GEODE_OPEN_GOLD_COST : 0;
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TownRecord town = uc != null ? tm.findTownForPlayerInWorld(uc.getUuid()) : null;
        boolean allowTreasury = uc != null && town != null && town.playerCanSpendTreasuryGold(uc.getUuid());
        SpendBreakdown paid = GoldCoinPayment.trySpendReturningBreakdown(town, inv, goldCost, allowTreasury);
        if (paid == null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.geode.open.insufficientGold"),
                NotificationStyle.Danger
            );
            refresh(ref, store);
            return;
        }
        ItemStackSlotTransaction takeGeode = inv.removeItemStackFromSlot(slot, 1);
        if (!takeGeode.succeeded()) {
            GoldCoinPayment.refund(town, player, ref, store, paid);
            if (town != null) {
                tm.updateTown(town);
            }
            refresh(ref, store);
            return;
        }

        GeodeLootTable table = GeodeLootFiles.loadTable(plugin);
        ItemStack reward = table.rollStack();
        if (reward == null) {
            GoldCoinPayment.refund(town, player, ref, store, paid);
            if (town != null) {
                tm.updateTown(town);
            }
            player.giveItem(new ItemStack(AetherhavenConstants.ITEM_GEODE, 1), ref, store);
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.geode.open.failed"),
                NotificationStyle.Danger
            );
            refresh(ref, store);
            return;
        }

        ItemStackTransaction giveTx = player.giveItem(reward, ref, store);
        if (!giveTx.succeeded()) {
            GoldCoinPayment.refund(town, player, ref, store, paid);
            if (town != null) {
                tm.updateTown(town);
            }
            player.giveItem(new ItemStack(AetherhavenConstants.ITEM_GEODE, 1), ref, store);
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.geode.open.noRoom"),
                NotificationStyle.Warning
            );
            refresh(ref, store);
            return;
        }

        if (town != null && paid.fromTreasury() > 0L) {
            tm.updateTown(town);
        }

        com.hypixel.hytale.server.core.asset.type.item.config.Item rewardItem = reward.getItem();
        Message itemNameMsg =
            rewardItem != null && rewardItem.getTranslationKey() != null && !rewardItem.getTranslationKey().isBlank()
                ? Message.translation(rewardItem.getTranslationKey())
                : Message.raw(reward.getItemId());
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_jewelry_geode.aetherhaven.geode.open.reward")
                .param("amount", reward.getQuantity())
                .param("itemName", itemNameMsg),
            NotificationStyle.Success
        );
        UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_WEAPON_BENCH_CRAFT);
        refresh(ref, store);
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
            .append(new KeyedCodec<>("Slot", Codec.STRING), (d, v) -> d.slot = v, d -> d.slot)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String slot;
    }
}
