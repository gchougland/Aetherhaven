package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.PlayerJewelryLoadout;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Custom UI: list player's jewelry in inventory, equip to loadout slots. */
public final class HandMirrorPage extends AetherhavenInteractiveCustomUIPage<HandMirrorPage.PageData> {
    private static final String LEFT = "#Content #LeftColumn";
    private static final String ROWS = LEFT + " #JewelryListScroll #Rows";
    private static final int MAX_ROWS = 48;
    private static final String TRAIT_BODY = "#Content #TraitColumn #TraitBody";

    private static final String R1_ICON = "#Ring1Icon";
    private static final String R1_UNEQUIP = LEFT + " #EquippedRow #Ring1Column #UnequipRing1";
    private static final String R2_ICON = "#Ring2Icon";
    private static final String R2_UNEQUIP = LEFT + " #EquippedRow #Ring2Column #UnequipRing2";
    private static final String NECK_ICON = "#NeckIcon";
    private static final String NECK_UNEQUIP = LEFT + " #EquippedRow #NeckColumn #UnequipNeck";

    private boolean templateAppended;

    public HandMirrorPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/HandMirror.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyHandMirror(commandBuilder);
        commandBuilder.set(LEFT + " #Hint.TextSpans", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.hint"));

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }

        CombinedItemContainer inv = player.getInventory().getCombinedHotbarFirst();
        PlayerJewelryLoadout lw = HandMirrorLoadoutActions.loadoutOrCreate(ref, store);

        applyEquippedSlot(commandBuilder, R1_ICON, R1_UNEQUIP, lw.getRing1());
        applyEquippedSlot(commandBuilder, R2_ICON, R2_UNEQUIP, lw.getRing2());
        applyEquippedSlot(commandBuilder, NECK_ICON, NECK_UNEQUIP, lw.getNecklace());
        commandBuilder.set(TRAIT_BODY + ".TextSpans", HandMirrorLoadoutPanel.forLoadout(lw));

        bindUnequip(eventBuilder, R1_UNEQUIP, 0);
        bindUnequip(eventBuilder, R2_UNEQUIP, 1);
        bindUnequip(eventBuilder, NECK_UNEQUIP, 2);

        commandBuilder.clear(ROWS);
        if (inv == null) {
            return;
        }

        List<Short> jewelrySlots = new ObjectArrayList<>();
        for (short s = 0; s < inv.getCapacity(); s++) {
            ItemStack st = inv.getItemStack(s);
            if (st == null || ItemStack.isEmpty(st)) {
                continue;
            }
            if (JewelryItemIds.isJewelry(st.getItemId())) {
                jewelrySlots.add(s);
            }
        }

        int n = Math.min(jewelrySlots.size(), MAX_ROWS);
        for (int i = 0; i < n; i++) {
            short slot = jewelrySlots.get(i);
            ItemStack stack = inv.getItemStack(slot);
            commandBuilder.append(ROWS, "Aetherhaven/HandMirrorRow.ui");
            String row = ROWS + "[" + i + "]";
            Item it = stack.getItem();
            Message itemName =
                it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()
                    ? Message.translation(it.getTranslationKey())
                    : Message.raw(stack.getItemId());
            AetherhavenUiItemGrids.setSingleSlot(
                commandBuilder, row + " #IconFrame #Icon", AetherhavenUiItemGrids.jewelrySlotForUi(stack));
            commandBuilder.set(
                row + " #Line.TextSpans",
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.listRow").param("itemName", itemName).param("qty", stack.getQuantity()));

            boolean ring = JewelryItemIds.isRing(stack.getItemId());
            commandBuilder.set(row + " #RingActions.Visible", ring);
            commandBuilder.set(row + " #NeckActions.Visible", !ring);
            commandBuilder.set(
                row + " #ToRing1.Text", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.rowRing1"));
            commandBuilder.set(
                row + " #ToRing2.Text", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.rowRing2"));
            commandBuilder.set(
                row + " #ToNeck.Text", Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.rowNeck"));

            if (ring) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #ToRing1",
                    new EventData()
                        .append("Action", "Equip")
                        .append("InvSlot", String.valueOf(slot))
                        .append("Target", "0"),
                    false);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #ToRing2",
                    new EventData()
                        .append("Action", "Equip")
                        .append("InvSlot", String.valueOf(slot))
                        .append("Target", "1"),
                    false);
            } else {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #ToNeck",
                    new EventData()
                        .append("Action", "Equip")
                        .append("InvSlot", String.valueOf(slot))
                        .append("Target", "2"),
                    false);
            }
        }
    }

    private static void applyEquippedSlot(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String iconSelector,
        @Nonnull String unequipSelector,
        @Nullable ItemStack st) {
        if (st == null || ItemStack.isEmpty(st)) {
            AetherhavenUiItemGrids.hide(commandBuilder, iconSelector);
            commandBuilder.set(unequipSelector + ".Visible", false);
            return;
        }
        commandBuilder.set(unequipSelector + ".Visible", true);
        AetherhavenUiItemGrids.setSingleSlot(commandBuilder, iconSelector, AetherhavenUiItemGrids.jewelrySlotForUi(st));
    }

    private static void bindUnequip(@Nonnull UIEventBuilder eventBuilder, @Nonnull String buttonSelector, int target) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            buttonSelector,
            new EventData().append("Action", "Unequip").append("Target", String.valueOf(target)),
            false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        if ("Unequip".equalsIgnoreCase(data.action)) {
            int t = parseTarget(data.target, -1);
            if (t < 0) {
                return;
            }
            if (!HandMirrorLoadoutActions.takeFromLoadout(ref, store, player, t)) {
                NotificationUtil.sendNotification(
                    pr.getPacketHandler(),
                    Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.inventoryFull"),
                    NotificationStyle.Danger);
            }
            refresh(ref, store);
            return;
        }
        if (!"Equip".equalsIgnoreCase(data.action)) {
            return;
        }
        if (data.invSlot == null || data.invSlot.isBlank()) {
            return;
        }
        final short invSlot;
        try {
            invSlot = Short.parseShort(data.invSlot.trim());
        } catch (NumberFormatException e) {
            return;
        }
        int target = parseTarget(data.target, -1);
        if (target < 0) {
            return;
        }
        CombinedItemContainer inv = player.getInventory().getCombinedHotbarFirst();
        if (inv == null) {
            refresh(ref, store);
            return;
        }
        HandMirrorLoadoutActions.EquipFromInventoryResult r =
            HandMirrorLoadoutActions.equipFromInventory(ref, store, player, inv, invSlot, target);
        switch (r) {
            case SUCCESS -> {}
            case COULD_NOT_RETURN_PREVIOUS, INVENTORY_UPDATE_FAILED -> NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.equipFailed"),
                NotificationStyle.Danger);
            case NOT_JEWELRY, INVALID_FOR_SLOT -> NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.cannotEquipThere"),
                NotificationStyle.Danger);
            case SLOT_EMPTY -> {}
        }
        refresh(ref, store);
    }

    private static int parseTarget(@Nullable String s, int dflt) {
        if (s == null || s.isBlank()) {
            return dflt;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
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
