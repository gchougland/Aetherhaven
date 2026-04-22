package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipMessages;
import com.hexvane.aetherhaven.jewelry.PlayerJewelryLoadout;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Jewelry loadout + full combined inventory grid (same scope as geode UI). Pick a jewelry stack, then click a ring or
 * necklace slot to equip; click a worn piece on the loadout to return it to inventory. Non-jewelry cells are disabled.
 */
public final class HandMirrorPage extends InteractiveCustomUIPage<HandMirrorPage.PageData> {
    private static final String INV_GRID = "#Content #InvGrid";
    private static final int GRID_COLS = 9;

    private boolean templateAppended;
    /** Inventory slot index pending equip, or -1. */
    private short pendingInvSlot = -1;
    /** Which inventory slot the hover callout is showing, or -1. */
    private short tipSlot = -1;

    public HandMirrorPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/HandMirror.ui");
            templateAppended = true;
        }
        commandBuilder.set("#JewelryCallout.Visible", false);
        tipSlot = -1;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerJewelryLoadout loadout = store.getComponent(ref, PlayerJewelryLoadout.getComponentType());
        if (loadout == null) {
            loadout = new PlayerJewelryLoadout();
            store.putComponent(ref, PlayerJewelryLoadout.getComponentType(), loadout);
        }

        bindLoadoutSlot(commandBuilder, eventBuilder, "#Ring1Btn", "#Ring1Btn #Ring1Slot", 0, loadout.getRing1());
        bindLoadoutSlot(commandBuilder, eventBuilder, "#Ring2Btn", "#Ring2Btn #Ring2Slot", 1, loadout.getRing2());
        bindLoadoutSlot(commandBuilder, eventBuilder, "#NeckBtn", "#NeckBtn #NeckSlot", 2, loadout.getNecklace());

        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        commandBuilder.clear(INV_GRID);
        if (inv == null) {
            return;
        }
        int cap = inv.getCapacity();
        int rows = (cap + GRID_COLS - 1) / GRID_COLS;
        for (int r = 0; r < rows; r++) {
            commandBuilder.append(INV_GRID, "Aetherhaven/HandMirrorInvRow.ui");
            String rowCells = INV_GRID + "[" + r + "] #Cells";
            commandBuilder.clear(rowCells);
            for (int c = 0; c < GRID_COLS; c++) {
                int idx = r * GRID_COLS + c;
                if (idx >= cap) {
                    break;
                }
                short slot = (short) idx;
                ItemStack st = inv.getItemStack(slot);
                commandBuilder.append(rowCells, "Aetherhaven/HandMirrorInvCell.ui");
                String cell = rowCells + "[" + c + "]";
                String btn = cell + " #CellBtn";
                String isl = cell + " #CellSlot";
                boolean empty = ItemStack.isEmpty(st);
                boolean jewelry = !empty && JewelryItemIds.isJewelry(st.getItemId());
                boolean grey = !empty && !jewelry;
                if (!empty) {
                    commandBuilder.set(isl + ".ItemId", st.getItemId());
                    commandBuilder.set(isl + ".Quantity", st.getQuantity());
                } else {
                    commandBuilder.set(isl + ".ItemId", "");
                    commandBuilder.set(isl + ".Quantity", 0);
                }
                commandBuilder.set(btn + ".Disabled", grey);
                if (jewelry) {
                    Message tip = JewelryTooltipMessages.forStack(st);
                    commandBuilder.set(btn + ".TooltipTextSpans", tip);
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        btn,
                        new EventData().append("Action", "InvPick").append("InvSlot", String.valueOf(slot)),
                        false
                    );
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.MouseEntered,
                        btn,
                        new EventData().append("Action", "TipShow").append("InvSlot", String.valueOf(slot)),
                        false
                    );
                    eventBuilder.addEventBinding(
                        CustomUIEventBindingType.MouseExited,
                        btn,
                        new EventData().append("Action", "TipHide").append("InvSlot", String.valueOf(slot)),
                        false
                    );
                }
                if (grey) {
                    commandBuilder.set(btn + ".TooltipTextSpans", Message.translation("server.aetherhaven.ui.handmirror.tooltipNotJewelry"));
                }
            }
        }
    }

    private static void bindLoadoutSlot(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull String btnSel,
        @Nonnull String slotSel,
        int targetSlot,
        @Nullable ItemStack equipped
    ) {
        if (ItemStack.isEmpty(equipped)) {
            commandBuilder.set(slotSel + ".Visible", true);
            commandBuilder.set(slotSel + ".ItemId", "");
            commandBuilder.set(slotSel + ".Quantity", 0);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                btnSel,
                new EventData().append("Action", "EquipSlot").append("Target", String.valueOf(targetSlot)),
                false
            );
            return;
        }
        commandBuilder.set(slotSel + ".Visible", true);
        commandBuilder.set(slotSel + ".ItemId", equipped.getItemId());
        commandBuilder.set(slotSel + ".Quantity", equipped.getQuantity());
        commandBuilder.set(btnSel + ".TooltipTextSpans", JewelryTooltipMessages.forStack(equipped));
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            btnSel,
            new EventData().append("Action", "EquipSlot").append("Target", String.valueOf(targetSlot)),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        String act = data.action.trim();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        if (inv == null) {
            return;
        }

        if ("TipShow".equalsIgnoreCase(act)) {
            short s = parseShort(data.invSlot, (short) -1);
            if (s < 0) {
                return;
            }
            ItemStack st = inv.getItemStack(s);
            if (ItemStack.isEmpty(st) || !JewelryItemIds.isJewelry(st.getItemId())) {
                return;
            }
            tipSlot = s;
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#JewelryCallout.Visible", true);
            cmd.set("#JewelryCalloutText.TextSpans", JewelryTooltipMessages.forStack(st));
            sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }
        if ("TipHide".equalsIgnoreCase(act)) {
            short s = parseShort(data.invSlot, (short) -1);
            if (tipSlot >= 0 && s == tipSlot) {
                tipSlot = -1;
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#JewelryCallout.Visible", false);
                sendUpdate(cmd, new UIEventBuilder(), false);
            }
            return;
        }

        if ("InvPick".equalsIgnoreCase(act)) {
            short s = parseShort(data.invSlot, (short) -1);
            if (s < 0) {
                return;
            }
            ItemStack st = inv.getItemStack(s);
            if (ItemStack.isEmpty(st) || !JewelryItemIds.isJewelry(st.getItemId())) {
                pendingInvSlot = -1;
                refresh(ref, store);
                return;
            }
            ItemStack rolled = JewelryMetadata.ensureRolled(st);
            if (rolled != st) {
                ItemStackSlotTransaction tx = inv.replaceItemStackInSlot(s, st, rolled);
                if (!tx.succeeded()) {
                    refresh(ref, store);
                    return;
                }
            }
            if (pendingInvSlot == s) {
                pendingInvSlot = -1;
            } else {
                pendingInvSlot = s;
            }
            refresh(ref, store);
            return;
        }

        if ("EquipSlot".equalsIgnoreCase(act)) {
            int target = parseInt(data.target, -1);
            if (target < 0 || target > 2) {
                return;
            }
            PlayerJewelryLoadout lw = store.getComponent(ref, PlayerJewelryLoadout.getComponentType());
            if (lw == null) {
                lw = new PlayerJewelryLoadout();
                store.putComponent(ref, PlayerJewelryLoadout.getComponentType(), lw);
            }
            if (pendingInvSlot >= 0) {
                short from = pendingInvSlot;
                ItemStack incoming = inv.getItemStack(from);
                if (ItemStack.isEmpty(incoming) || !JewelryItemIds.isJewelry(incoming.getItemId())) {
                    pendingInvSlot = -1;
                    refresh(ref, store);
                    return;
                }
                if (target == 2 && !JewelryItemIds.isNecklace(incoming.getItemId())) {
                    refresh(ref, store);
                    return;
                }
                if (target != 2 && !JewelryItemIds.isRing(incoming.getItemId())) {
                    refresh(ref, store);
                    return;
                }
                ItemStackSlotTransaction take = inv.removeItemStackFromSlot(from, 1);
                if (!take.succeeded() || ItemStack.isEmpty(take.getOutput())) {
                    refresh(ref, store);
                    return;
                }
                ItemStack taken = JewelryMetadata.ensureRolled(take.getOutput());
                ItemStack previous = lw.getSlot(target);
                lw.setSlot(target, taken);
                store.putComponent(ref, PlayerJewelryLoadout.getComponentType(), lw);
                if (!ItemStack.isEmpty(previous)) {
                    player.giveItem(previous, ref, store);
                }
                pendingInvSlot = -1;
                refresh(ref, store);
                return;
            }
            ItemStack cur = lw.getSlot(target);
            if (ItemStack.isEmpty(cur)) {
                refresh(ref, store);
                return;
            }
            lw.setSlot(target, null);
            store.putComponent(ref, PlayerJewelryLoadout.getComponentType(), lw);
            player.giveItem(cur, ref, store);
            refresh(ref, store);
            return;
        }
    }

    private static int parseInt(@Nullable String s, int def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static short parseShort(@Nullable String s, short def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Short.parseShort(s.trim());
        } catch (NumberFormatException e) {
            return def;
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
            .append(new KeyedCodec<>("InvSlot", Codec.STRING), (d, v) -> d.invSlot = v, d -> d.invSlot)
            .add()
            .append(new KeyedCodec<>("Target", Codec.STRING), (d, v) -> d.target = v, d -> d.target)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String invSlot;
        @Nullable
        private String target;
    }
}
