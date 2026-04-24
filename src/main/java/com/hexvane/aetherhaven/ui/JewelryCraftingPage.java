package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.jewelry.JewelryCraftingItems;
import com.hexvane.aetherhaven.jewelry.JewelryCraftingRarityTable;
import com.hexvane.aetherhaven.jewelry.JewelryGem;
import com.hexvane.aetherhaven.jewelry.JewelryGemTraits;
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
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class JewelryCraftingPage extends InteractiveCustomUIPage<JewelryCraftingPage.PageData> {
    private static final int ESSENCE_MAX = 999;

    private boolean templateAppended;
    private boolean ring = true;
    private boolean gold = true;
    @Nonnull
    private String gemItemId = JewelryCraftingItems.GEM_ZEPHYR;
    private int essenceReg = 1;
    private int essenceConc = 0;
    @Nullable
    private ItemStack pendingCraftOutput;

    private static final String NO_OWNED_GEM = "__aetherhaven_no_gem__";

    public JewelryCraftingPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/JewelryCraftingPage.ui");
            templateAppended = true;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }

        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        int nGold = inv != null ? InventoryMaterials.count(inv, AetherhavenConstants.INGREDIENT_BAR_GOLD) : 0;
        int nSilver = inv != null ? InventoryMaterials.count(inv, AetherhavenConstants.INGREDIENT_BAR_SILVER) : 0;
        int nEss = inv != null ? InventoryMaterials.count(inv, AetherhavenConstants.ITEM_LIFE_ESSENCE) : 0;
        int nConc = inv != null ? InventoryMaterials.count(inv, AetherhavenConstants.ITEM_LIFE_ESSENCE_CONCENTRATED) : 0;

        int barsNeed = ingotsRequired();
        commandBuilder.set(
            "#IngotCost.TextSpans",
            Message.translation("server.aetherhaven.ui.jewelryCrafting.ingotCost")
                .param("n", Message.raw(String.valueOf(barsNeed)))
                .param("metal", metalNameMessage())
        );
        commandBuilder.set(
            "#InvLine.TextSpans",
            Message.translation("server.aetherhaven.ui.jewelryCrafting.invLine")
                .param("gold", Message.raw(String.valueOf(nGold)))
                .param("silver", Message.raw(String.valueOf(nSilver)))
                .param("ess", Message.raw(String.valueOf(nEss)))
                .param("conc", Message.raw(String.valueOf(nConc)))
        );

        ObjectArrayList<String> ownedGems = new ObjectArrayList<>();
        for (String id : JewelryCraftingItems.ALL_GEM_ITEM_IDS) {
            if (inv != null && InventoryMaterials.count(inv, id) >= 1) {
                ownedGems.add(id);
            }
        }
        if (ownedGems.isEmpty()) {
            gemItemId = NO_OWNED_GEM;
        } else {
            if (NO_OWNED_GEM.equals(gemItemId) || !ownedGems.contains(gemItemId)) {
                gemItemId = ownedGems.get(0);
            }
        }
        int nGem =
            inv != null && gemItemId != null && !NO_OWNED_GEM.equals(gemItemId)
                ? InventoryMaterials.count(inv, gemItemId)
                : 0;

        List<DropdownEntryInfo> gemEntries = new ObjectArrayList<>();
        if (ownedGems.isEmpty()) {
            String lang = pr.getLanguage() != null ? pr.getLanguage() : "en-US";
            String noGems =
                I18nModule.get().getMessage(lang, "server.aetherhaven.ui.jewelryCrafting.noGemsDropdown");
            if (noGems == null || noGems.isEmpty()) {
                noGems = "No cut gems in your bags (find rock gems in the world first)";
            }
            gemEntries.add(new DropdownEntryInfo(LocalizableString.fromString(noGems), NO_OWNED_GEM));
        } else {
            for (String id : ownedGems) {
                JewelryGem g = JewelryCraftingItems.gemFromRockItemId(id);
                String label = g != null ? prettyGemName(g) : id;
                gemEntries.add(new DropdownEntryInfo(LocalizableString.fromString(label), id));
            }
        }
        commandBuilder.set("#GemDropdown.Entries", gemEntries);
        commandBuilder.set("#GemDropdown.Value", gemItemId);
        String gemIconId = NO_OWNED_GEM.equals(gemItemId) ? JewelryCraftingItems.GEM_ZEPHYR : gemItemId;
        commandBuilder.set("#GemIcon.ItemId", gemIconId);
        commandBuilder.set("#GemIcon.Quantity", 1);

        commandBuilder.set("#InvGem.TextSpans", Message.translation("server.aetherhaven.ui.jewelryCrafting.invGem").param("n", Message.raw(String.valueOf(nGem))));

        commandBuilder.set("#ErSlot.ItemId", AetherhavenConstants.ITEM_LIFE_ESSENCE);
        commandBuilder.set("#ErSlot.Quantity", 1);
        commandBuilder.set("#EcSlot.ItemId", AetherhavenConstants.ITEM_LIFE_ESSENCE_CONCENTRATED);
        commandBuilder.set("#EcSlot.Quantity", 1);

        commandBuilder.set("#ErVal.TextSpans", Message.raw(String.valueOf(essenceReg)));
        commandBuilder.set("#EcVal.TextSpans", Message.raw(String.valueOf(essenceConc)));
        commandBuilder.set("#ErMinus10.Disabled", essenceReg < 10);
        commandBuilder.set("#ErMinus.Disabled", essenceReg <= 0);
        commandBuilder.set("#ErPlus.Disabled", essenceReg >= ESSENCE_MAX);
        commandBuilder.set("#ErPlus10.Disabled", essenceReg > ESSENCE_MAX - 10);
        commandBuilder.set("#EcMinus10.Disabled", essenceConc < 10);
        commandBuilder.set("#EcMinus.Disabled", essenceConc <= 0);
        commandBuilder.set("#EcPlus.Disabled", essenceConc >= ESSENCE_MAX);
        commandBuilder.set("#EcPlus10.Disabled", essenceConc > ESSENCE_MAX - 10);

        commandBuilder.set(
            "#ShapePick.TextSpans",
            ring
                ? Message.translation("server.aetherhaven.ui.jewelryCrafting.shapeRing")
                : Message.translation("server.aetherhaven.ui.jewelryCrafting.shapeNeck")
        );

        JewelryRarity r = JewelryCraftingRarityTable.resolve(essenceReg, essenceConc);
        if (r == null) {
            commandBuilder.set(
                "#PreviewRarity.TextSpans",
                Message.translation("server.aetherhaven.ui.jewelryCrafting.rarityNone")
            );
        } else {
            commandBuilder.set(
                "#PreviewRarity.TextSpans",
                Message.translation("server.aetherhaven.ui.jewelryCrafting.rarityLine")
                    .param("rarity", Message.translation("server.aetherhaven.jewelry.rarity." + r.name()))
            );
        }

        JewelryGem selGem = JewelryCraftingItems.gemFromRockItemId(gemItemId);
        if (selGem != null) {
            String[] stats = JewelryGemTraits.statIdsFor(selGem);
            String joined = String.join(", ", stats);
            commandBuilder.set(
                "#PreviewTraits.TextSpans",
                Message.translation("server.aetherhaven.ui.jewelryCrafting.traitsLine").param("stats", Message.raw(joined))
            );
        } else {
            commandBuilder.set("#PreviewTraits.TextSpans", Message.raw(""));
        }

        commandBuilder.set("#GoldIngot.ItemId", AetherhavenConstants.INGREDIENT_BAR_GOLD);
        commandBuilder.set("#GoldIngot.Quantity", 1);
        commandBuilder.set("#SilverIngot.ItemId", AetherhavenConstants.INGREDIENT_BAR_SILVER);
        commandBuilder.set("#SilverIngot.Quantity", 1);
        commandBuilder.set("#GoldHilite.Visible", gold);
        commandBuilder.set("#SilverHilite.Visible", !gold);

        boolean hasPending = hasPendingOutput();
        boolean canAddTake =
            inv != null
                && pendingCraftOutput != null
                && !ItemStack.isEmpty(pendingCraftOutput)
                && inv.canAddItemStack(pendingCraftOutput);
        applyPendingOutputItemGrid(commandBuilder);
        commandBuilder.set("#TakeButton.Disabled", !canAddTake);

        boolean canCraft =
            !hasPending
                && r != null
                && selGem != null
                && hasMaterials(selGem, inv, nGold, nSilver, nGem, nEss, nConc);
        commandBuilder.set("#CraftButton.Disabled", !canCraft);

        EventData gemEvent = new EventData().append("Action", "SelectGem").append("@Gem", "#GemDropdown.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#GemDropdown", gemEvent, false);

        bindA(eventBuilder, "#RingButton", "SelectRing");
        bindA(eventBuilder, "#NecklaceButton", "SelectNecklace");
        bindA(eventBuilder, "#GoldIngotButton", "SelectGold");
        bindA(eventBuilder, "#SilverIngotButton", "SelectSilver");
        bindA(eventBuilder, "#ErMinus10", "ErMinus10");
        bindA(eventBuilder, "#ErMinus", "ErMinus");
        bindA(eventBuilder, "#ErPlus", "ErPlus");
        bindA(eventBuilder, "#ErPlus10", "ErPlus10");
        bindA(eventBuilder, "#EcMinus10", "EcMinus10");
        bindA(eventBuilder, "#EcMinus", "EcMinus");
        bindA(eventBuilder, "#EcPlus", "EcPlus");
        bindA(eventBuilder, "#EcPlus10", "EcPlus10");
        bindA(eventBuilder, "#TakeButton", "Take");
        bindA(eventBuilder, "#CraftButton", "Craft");
    }

    private boolean hasPendingOutput() {
        return pendingCraftOutput != null && !ItemStack.isEmpty(pendingCraftOutput);
    }

    private void applyPendingOutputItemGrid(@Nonnull UICommandBuilder commandBuilder) {
        if (!hasPendingOutput()) {
            commandBuilder.set("#OutputItem.Slots", new ItemGridSlot[0]);
            return;
        }
        ItemStack display =
            JewelryMetadata.syncInstanceDescriptionForTooltip(
                JewelryMetadata.ensureRolled(pendingCraftOutput));
        commandBuilder.set(
            "#OutputItem.Slots",
            new ItemGridSlot[] {new ItemGridSlot(display)}
        );
    }

    private static void bindA(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, new EventData().append("Action", action), false);
    }

    private int ingotsRequired() {
        return ring ? AetherhavenConstants.JEWELRY_CRAFT_BARS_PER_RING : AetherhavenConstants.JEWELRY_CRAFT_BARS_PER_NECKLACE;
    }

    @Nonnull
    private Message metalNameMessage() {
        return gold
            ? Message.translation("server.aetherhaven.ui.jewelryCrafting.metalGold")
            : Message.translation("server.aetherhaven.ui.jewelryCrafting.metalSilver");
    }

    private boolean hasMaterials(
        @Nonnull JewelryGem gem,
        @Nullable CombinedItemContainer inv,
        int nGold,
        int nSilver,
        int nGem,
        int nEss,
        int nConc
    ) {
        String outId = JewelryCraftingItems.outputItemId(!ring, gold, gem);
        com.hypixel.hytale.server.core.asset.type.item.config.Item a =
            com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(outId);
        if (a == null) {
            return false;
        }
        if (inv == null) {
            return false;
        }
        int needBar = ingotsRequired();
        int haveBar = gold ? nGold : nSilver;
        if (haveBar < needBar) {
            return false;
        }
        if (nGem < 1) {
            return false;
        }
        if (nEss < essenceReg) {
            return false;
        }
        return nConc >= essenceConc;
    }

    private void applyGemFromData(@Nullable PageData data) {
        if (data != null && data.gem != null && !data.gem.isBlank()) {
            gemItemId = data.gem.trim();
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (pendingCraftOutput == null || ItemStack.isEmpty(pendingCraftOutput)) {
            return;
        }
        if (!ref.isValid()) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.giveItem(pendingCraftOutput, ref, store);
        pendingCraftOutput = null;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        String action = data.action != null ? data.action.trim() : "";
        applyGemFromData(data);
        switch (action) {
            case "SelectRing" -> ring = true;
            case "SelectNecklace" -> ring = false;
            case "SelectGold" -> gold = true;
            case "SelectSilver" -> gold = false;
            case "SelectGem" -> { /* gemItemId from applyGemFromData */ }
            case "ErMinus10" -> essenceReg = Math.max(0, essenceReg - 10);
            case "ErMinus" -> essenceReg = Math.max(0, essenceReg - 1);
            case "ErPlus" -> essenceReg = Math.min(ESSENCE_MAX, essenceReg + 1);
            case "ErPlus10" -> essenceReg = Math.min(ESSENCE_MAX, essenceReg + 10);
            case "EcMinus10" -> essenceConc = Math.max(0, essenceConc - 10);
            case "EcMinus" -> essenceConc = Math.max(0, essenceConc - 1);
            case "EcPlus" -> essenceConc = Math.min(ESSENCE_MAX, essenceConc + 1);
            case "EcPlus10" -> essenceConc = Math.min(ESSENCE_MAX, essenceConc + 10);
            case "Take" -> {
                tryTakeOutput(ref, store);
                return;
            }
            case "Craft" -> {
                tryCraft(ref, store);
                return;
            }
            default -> {}
        }
        if (!"Craft".equalsIgnoreCase(action) && !"Take".equalsIgnoreCase(action)) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
        }
    }

    private void tryTakeOutput(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        if (!hasPendingOutput()) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        if (inv == null) {
            return;
        }
        if (!inv.canAddItemStack(pendingCraftOutput)) {
            pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.noSpace"));
            return;
        }
        ItemStack stack = pendingCraftOutput;
        pendingCraftOutput = null;
        if (!player.giveItem(stack, ref, store).succeeded()) {
            pendingCraftOutput = stack;
            pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.noSpace"));
            return;
        }
        pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.taken"));
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("server.aetherhaven.ui.jewelryCrafting.taken"),
            NotificationStyle.Success
        );
        refresh(ref, store);
    }

    private void tryCraft(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        if (hasPendingOutput()) {
            pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.takeFirst"));
            return;
        }
        int er = essenceReg;
        int ec = essenceConc;
        JewelryRarity rarity = JewelryCraftingRarityTable.resolve(er, ec);
        JewelryGem gem = JewelryCraftingItems.gemFromRockItemId(gemItemId);
        if (rarity == null || gem == null) {
            pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.cannotResolve"));
            refresh(ref, store);
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
        if (inv == null) {
            return;
        }
        int nGold = InventoryMaterials.count(inv, AetherhavenConstants.INGREDIENT_BAR_GOLD);
        int nSilver = InventoryMaterials.count(inv, AetherhavenConstants.INGREDIENT_BAR_SILVER);
        int nGem = NO_OWNED_GEM.equals(gemItemId) ? 0 : InventoryMaterials.count(inv, gemItemId);
        int nEss = InventoryMaterials.count(inv, AetherhavenConstants.ITEM_LIFE_ESSENCE);
        int nConc = InventoryMaterials.count(inv, AetherhavenConstants.ITEM_LIFE_ESSENCE_CONCENTRATED);
        if (!hasMaterials(gem, inv, nGold, nSilver, nGem, nEss, nConc)) {
            pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.missingMats"));
            refresh(ref, store);
            return;
        }
        int needBar = ingotsRequired();
        String bar = gold ? AetherhavenConstants.INGREDIENT_BAR_GOLD : AetherhavenConstants.INGREDIENT_BAR_SILVER;
        if (!inv.removeItemStack(new ItemStack(bar, needBar)).succeeded()) {
            pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.missingMats"));
            refresh(ref, store);
            return;
        }
        if (!inv.removeItemStack(new ItemStack(gemItemId, 1)).succeeded()) {
            player.giveItem(new ItemStack(bar, needBar), ref, store);
            pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.missingMats"));
            refresh(ref, store);
            return;
        }
        if (er > 0) {
            ItemStackTransaction tr = inv.removeItemStack(new ItemStack(AetherhavenConstants.ITEM_LIFE_ESSENCE, er));
            if (!tr.succeeded()) {
                player.giveItem(new ItemStack(gemItemId, 1), ref, store);
                player.giveItem(new ItemStack(bar, needBar), ref, store);
                pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.missingMats"));
                refresh(ref, store);
                return;
            }
        }
        if (ec > 0) {
            ItemStackTransaction tr = inv.removeItemStack(new ItemStack(AetherhavenConstants.ITEM_LIFE_ESSENCE_CONCENTRATED, ec));
            if (!tr.succeeded()) {
                if (er > 0) {
                    player.giveItem(new ItemStack(AetherhavenConstants.ITEM_LIFE_ESSENCE, er), ref, store);
                }
                player.giveItem(new ItemStack(gemItemId, 1), ref, store);
                player.giveItem(new ItemStack(bar, needBar), ref, store);
                pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.missingMats"));
                refresh(ref, store);
                return;
            }
        }
        String outId = JewelryCraftingItems.outputItemId(!ring, gold, gem);
        ItemStack rolled = JewelryMetadata.rollCraftedAppraised(outId, rarity, ThreadLocalRandom.current());
        pendingCraftOutput = rolled;
        pr.sendMessage(Message.translation("server.aetherhaven.ui.jewelryCrafting.craftedToSlot"));
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("server.aetherhaven.ui.jewelryCrafting.craftedToSlot"),
            NotificationStyle.Success
        );
        refresh(ref, store);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    @Nonnull
    private static String prettyGemName(@Nonnull JewelryGem g) {
        return g.name().charAt(0) + g.name().substring(1).toLowerCase();
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@Gem", Codec.STRING), (d, v) -> d.gem = v, d -> d.gem)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String gem;
    }
}
