package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryRarity;
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
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Appraise jewelry stacks in combined inventory; merchant charges gold, appraisal bench does not. */
public final class JewelryAppraisalPage extends InteractiveCustomUIPage<JewelryAppraisalPage.PageData> {
    private static final String ROWS = "#Content #ListScroll #Rows";
    private static final int MAX_ROWS = 48;

    private final boolean chargeGold;
    private boolean templateAppended;
    private short selectedSlot = -1;

    public JewelryAppraisalPage(@Nonnull PlayerRef playerRef, boolean chargeGold) {
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
            commandBuilder.append("Aetherhaven/JewelryAppraisal.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyJewelryAppraisal(commandBuilder);
        commandBuilder.set("#DetailPick.Visible", true);
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        if (chargeGold) {
            commandBuilder.set(
                "#Hint.TextSpans",
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.hintPaid")
                    .param("cost", AetherhavenConstants.JEWELRY_APPRAISAL_GOLD_COST)
            );
        } else {
            commandBuilder.set("#Hint.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.hintFree"));
        }

        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        commandBuilder.clear(ROWS);
        if (inv == null) {
            commandBuilder.set("#DetailPick.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.empty"));
            commandBuilder.set("#DetailRarity.Visible", false);
            commandBuilder.set("#DetailTraits.Visible", false);
            commandBuilder.set("#Appraise.Disabled", true);
            return;
        }

        List<Short> slots = new ObjectArrayList<>();
        for (short s = 0; s < inv.getCapacity(); s++) {
            ItemStack st = inv.getItemStack(s);
            if (ItemStack.isEmpty(st)) {
                continue;
            }
            if (JewelryItemIds.isJewelry(st.getItemId())) {
                slots.add(s);
            }
        }

        if (slots.isEmpty()) {
            commandBuilder.set("#DetailPick.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.empty"));
            commandBuilder.set("#DetailRarity.Visible", false);
            commandBuilder.set("#DetailTraits.Visible", false);
            commandBuilder.set("#Appraise.Disabled", true);
            selectedSlot = -1;
            return;
        }

        if (selectedSlot >= 0) {
            ItemStack sel = inv.getItemStack(selectedSlot);
            if (ItemStack.isEmpty(sel) || !JewelryItemIds.isJewelry(sel.getItemId())) {
                selectedSlot = -1;
            }
        }

        int n = Math.min(slots.size(), MAX_ROWS);
        for (int i = 0; i < n; i++) {
            short slot = slots.get(i);
            ItemStack stack = inv.getItemStack(slot);
            commandBuilder.append(ROWS, "Aetherhaven/JewelryAppraisalRow.ui");
            String row = ROWS + "[" + i + "]";
            Item it = stack.getItem();
            Message itemName =
                it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()
                    ? Message.translation(it.getTranslationKey())
                    : Message.raw(stack.getItemId());
            Message statusMsg =
                JewelryMetadata.isAppraised(stack)
                    ? Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.statusAppraised")
                    : Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.statusUnidentified");
            ItemStack forRow = JewelryMetadata.syncInstanceDescriptionForTooltip(JewelryMetadata.ensureRolled(stack));
            commandBuilder.set(
                row + " #Icon.Slots", new ItemGridSlot[] {new ItemGridSlot(forRow)});
            commandBuilder.set(
                row + " #Line.TextSpans",
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.row").param("itemName", itemName).param("status", statusMsg)
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row,
                new EventData().append("Action", "Select").append("Slot", String.valueOf(slot)),
                false
            );
        }

        if (selectedSlot < 0) {
            commandBuilder.set("#DetailPick.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.pick"));
            commandBuilder.set("#DetailRarity.Visible", false);
            commandBuilder.set("#DetailTraits.Visible", false);
            commandBuilder.set("#Appraise.Disabled", true);
            return;
        }

        ItemStack cur = inv.getItemStack(selectedSlot);
        if (ItemStack.isEmpty(cur) || !JewelryItemIds.isJewelry(cur.getItemId())) {
            selectedSlot = -1;
            commandBuilder.set("#DetailPick.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.pick"));
            commandBuilder.set("#DetailRarity.Visible", false);
            commandBuilder.set("#DetailTraits.Visible", false);
            commandBuilder.set("#Appraise.Disabled", true);
            return;
        }

        Item itCur = cur.getItem();
        Message selName =
            itCur != null && itCur.getTranslationKey() != null && !itCur.getTranslationKey().isBlank()
                ? Message.translation(itCur.getTranslationKey())
                : Message.raw(cur.getItemId());
        commandBuilder.set(
            "#DetailPick.TextSpans",
            Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.selectedHeader").param("itemName", selName)
        );

        boolean appraised = JewelryMetadata.isAppraised(cur);
        if (!appraised) {
            commandBuilder.set("#DetailRarity.Visible", true);
            commandBuilder.set("#DetailRarity.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.unidentified"));
            commandBuilder.set("#DetailTraits.Visible", false);
            commandBuilder.set("#Appraise.Disabled", false);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#Appraise",
                new EventData().append("Action", "Appraise").append("Slot", String.valueOf(selectedSlot)),
                false
            );
            return;
        }

        ItemStack rolledView = JewelryMetadata.ensureRolled(cur);
        JewelryRarity rarity = JewelryMetadata.readRarity(rolledView);
        String rarityKey = rarity != null ? rarity.wireName() : "COMMON";
        commandBuilder.set("#DetailRarity.Visible", true);
        commandBuilder.set(
            "#DetailRarity.TextSpans",
            Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.rarityLine").param("rarity", Message.translation("aetherhaven_jewelry_geode.aetherhaven.jewelry.rarity." + rarityKey))
        );
        Message traitsMsg = null;
        for (JewelryMetadata.RolledTrait rt : JewelryMetadata.readTraits(rolledView)) {
            Message line =
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.jewelry.traitLine")
                    .param("stat", Message.translation("aetherhaven_jewelry_geode.aetherhaven.jewelry.stat." + rt.statId()))
                    .param("amount", (double) rt.amount());
            traitsMsg = traitsMsg == null ? line : traitsMsg.insert(Message.raw("\n")).insert(line);
        }
        commandBuilder.set("#DetailTraits.Visible", true);
        commandBuilder.set("#DetailTraits.TextSpans", traitsMsg != null ? traitsMsg : Message.raw(""));
        commandBuilder.set("#Appraise.Disabled", true);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        if ("Select".equalsIgnoreCase(data.action)) {
            if (data.slot == null || data.slot.isBlank()) {
                return;
            }
            try {
                selectedSlot = Short.parseShort(data.slot.trim());
            } catch (NumberFormatException e) {
                selectedSlot = -1;
            }
            CombinedItemContainer invSel = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
            if (invSel != null && selectedSlot >= 0) {
                ItemStack v = invSel.getItemStack(selectedSlot);
                if (!ItemStack.isEmpty(v) && JewelryItemIds.isJewelry(v.getItemId())) {
                    ItemStack r = JewelryMetadata.ensureRolled(v);
                    if (r != v) {
                        invSel.replaceItemStackInSlot(selectedSlot, v, r);
                    }
                }
            }
            refresh(ref, store);
            return;
        }
        if (!"Appraise".equalsIgnoreCase(data.action)) {
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
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        if (inv == null) {
            refresh(ref, store);
            return;
        }
        ItemStack cur = inv.getItemStack(slot);
        if (ItemStack.isEmpty(cur) || !JewelryItemIds.isJewelry(cur.getItemId())) {
            refresh(ref, store);
            return;
        }
        ItemStack rolled = JewelryMetadata.ensureRolled(cur);
        if (JewelryMetadata.isAppraised(rolled)) {
            refresh(ref, store);
            return;
        }
        int goldCost = chargeGold ? AetherhavenConstants.JEWELRY_APPRAISAL_GOLD_COST : 0;
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        TownManager tm =
            plugin != null ? AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin) : null;
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        TownRecord town = plugin != null && uc != null ? tm.findTownForPlayerInWorld(uc.getUuid()) : null;
        boolean allowTreasury = uc != null && town != null && town.playerCanSpendTreasuryGold(uc.getUuid());
        SpendBreakdown paid = GoldCoinPayment.trySpendReturningBreakdown(town, inv, goldCost, allowTreasury);
        if (paid == null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.insufficientGold"),
                NotificationStyle.Danger
            );
            refresh(ref, store);
            return;
        }
        if (rolled != cur) {
            ItemStackSlotTransaction r0 = inv.replaceItemStackInSlot(slot, cur, rolled);
            if (!r0.succeeded()) {
                GoldCoinPayment.refund(town, player, ref, store, paid);
                if (town != null && plugin != null) {
                    tm.updateTown(town);
                }
                refresh(ref, store);
                return;
            }
        }
        ItemStack now = inv.getItemStack(slot);
        if (ItemStack.isEmpty(now)) {
            GoldCoinPayment.refund(town, player, ref, store, paid);
            if (town != null && plugin != null) {
                tm.updateTown(town);
            }
            refresh(ref, store);
            return;
        }
        ItemStack appraised = JewelryMetadata.setAppraised(now, true);
        ItemStackSlotTransaction r1 = inv.replaceItemStackInSlot(slot, now, appraised);
        if (!r1.succeeded()) {
            GoldCoinPayment.refund(town, player, ref, store, paid);
            if (town != null && plugin != null) {
                tm.updateTown(town);
            }
            refresh(ref, store);
            return;
        }
        if (town != null && plugin != null && paid.fromTreasury() > 0L) {
            tm.updateTown(town);
        }
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.jewelryAppraisal.done"),
            NotificationStyle.Success
        );
        UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_ARCANE_WORKBENCH_CRAFT);
        selectedSlot = slot;
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
