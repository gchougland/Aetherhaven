package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.TownPermissionUtil;
import com.hexvane.aetherhaven.difficulty.DifficultyAccess;
import com.hexvane.aetherhaven.difficulty.DifficultyPreset;
import com.hexvane.aetherhaven.difficulty.DifficultyResolver;
import com.hexvane.aetherhaven.difficulty.ServerDifficultyPersistence;
import com.hexvane.aetherhaven.difficulty.ServerDifficultyState;
import com.hexvane.aetherhaven.difficulty.TownDifficultySettings;
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
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DifficultyPage extends AetherhavenInteractiveCustomUIPage<DifficultyPage.PageData> {
    private static final String MSG = "aetherhaven_difficulty.aetherhaven.difficulty";

    private static final int CARD_HEIGHT = 176;
    private static final int CARD_HEIGHT_SELECTED = 202;
    private static final int CARD_FLEX = 10;
    private static final int CARD_FLEX_SELECTED = 16;
    private static final int CARD_ICON = 36;
    private static final int CARD_ICON_SELECTED = 44;

    @Nullable
    private final UUID townId;
    private final boolean serverMode;
    @Nullable
    private final UUID openStylePickerTownIdAfterSave;

    private boolean templateAppended;
    private boolean customizeMode;
    private boolean readOnly;
    private boolean forceAllTowns;
    private DifficultyPreset selectedPreset = DifficultyPreset.NORMAL;
    private double resourceMult = 1.0;
    private double goldMult = 1.0;
    private boolean requireAllBlocks;
    private double buyPriceMult = 1.0;
    private int sellMarginPercent = TownDifficultySettings.DEFAULT_SELL_PROFIT_MARGIN_PERCENT;
    private double taxMult = 1.0;
    private double upgradeGoldMult = 1.0;
    private double upgradeResourceMult = 1.0;
    private double unlockGoldMult = 1.0;
    private double unlockResourceMult = 1.0;
    private boolean buildingStaffDisabled;
    private double goldLootMult = 1.0;
    private double otherLootMult = 1.0;

    public DifficultyPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId) {
        this(playerRef, townId, null);
    }

    public DifficultyPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nullable UUID openStylePickerTownIdAfterSave
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.serverMode = false;
        this.openStylePickerTownIdAfterSave = openStylePickerTownIdAfterSave;
    }

    private DifficultyPage(@Nonnull PlayerRef playerRef, boolean serverMode) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = null;
        this.serverMode = serverMode;
        this.openStylePickerTownIdAfterSave = null;
    }

    @Nonnull
    public static DifficultyPage forServer(@Nonnull PlayerRef playerRef) {
        return new DifficultyPage(playerRef, true);
    }

    /** Opens difficulty for the player's owned town in their current world, or null if unavailable. */
    @Nullable
    public static DifficultyPage tryOpenForOwnedTown(@Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean admin = player != null && TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
        if (DifficultyResolver.isForced()) {
            return admin ? forServer(playerRef) : null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForOwnerInWorld(uc.getUuid());
        if (town == null) {
            return null;
        }
        return new DifficultyPage(playerRef, town.getTownId());
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/DifficultyPage.ui");
            templateAppended = true;
            loadState(store);
            AetherhavenUiLocalization.applyDifficultyPage(commandBuilder);
            wireEvents(eventBuilder);
        }
        applyDynamicState(commandBuilder);
    }

    private void loadState(@Nonnull Store<EntityStore> store) {
        if (serverMode) {
            ServerDifficultyState server = ServerDifficultyPersistence.getOrLoad();
            forceAllTowns = server.isForceAllTowns();
            applyFromSettings(server.getDifficulty());
            readOnly = false;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || townId == null) {
            return;
        }
        if (DifficultyResolver.isForced()) {
            applyFromSettings(DifficultyResolver.serverState().effectiveForcedSettings());
            forceAllTowns = true;
            readOnly = !isAdmin(store);
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        applyFromSettings(town.getDifficultySettings());
        readOnly = false;
        forceAllTowns = false;
    }

    private void applyFromSettings(@Nonnull TownDifficultySettings state) {
        selectedPreset = state.getPreset();
        resourceMult = state.getResourceCostMultiplier();
        goldMult = state.getGoldCostMultiplier();
        requireAllBlocks = state.isRequireAllPrefabBlocks();
        buyPriceMult = state.getBuyPriceMultiplier();
        sellMarginPercent = state.getSellProfitMarginPercent();
        taxMult = state.getTaxMultiplier();
        upgradeGoldMult = state.getBuildingUpgradeGoldMultiplier();
        upgradeResourceMult = state.getBuildingUpgradeResourceMultiplier();
        unlockGoldMult = state.getProductionUnlockGoldMultiplier();
        unlockResourceMult = state.getProductionUnlockResourceMultiplier();
        buildingStaffDisabled = state.isBuildingStaffDisabled();
        goldLootMult = state.getGoldLootRarityMultiplier();
        otherLootMult = state.getOtherLootRarityMultiplier();
    }

    private void applyDynamicState(@Nonnull UICommandBuilder b) {
        if (serverMode) {
            b.set("#DifficultyTitleText.TextSpans", Message.translation(MSG + ".serverTitle"));
        }
        b.set("#PresetSection.Visible", !customizeMode);
        b.set("#CustomizeSection.Visible", customizeMode);
        b.set("#ForceAllTownsRow.Visible", serverMode);
        b.set("#ServerLockedHint.Visible", readOnly);
        if (readOnly) {
            b.set("#ServerLockedHint.TextSpans", Message.translation(MSG + ".serverLocked"));
        }
        b.set("#SaveButton.Visible", !readOnly);
        applyPresetHighlights(b);
        applyCustomizeSliders(b);
    }

    private void applyPresetHighlights(@Nonnull UICommandBuilder b) {
        boolean easy = selectedPreset == DifficultyPreset.EASY;
        boolean normal = selectedPreset == DifficultyPreset.NORMAL;
        boolean hard = selectedPreset == DifficultyPreset.HARD;
        applyPresetCardShell(b, "#CardEasyShell", easy, true);
        applyPresetCardShell(b, "#CardNormalShell", normal, true);
        applyPresetCardShell(b, "#CardHardShell", hard, false);
        applyPresetCardIcon(b, "#CardEasyIcon", easy);
        applyPresetCardIcon(b, "#CardNormalIcon", normal);
        applyPresetCardIcon(b, "#CardHardIcon", hard);
        b.set("#CardEasyTitle.Style.FontSize", easy ? 16 : 14);
        b.set("#CardNormalTitle.Style.FontSize", normal ? 16 : 14);
        b.set("#CardHardTitle.Style.FontSize", hard ? 16 : 14);
    }

    private static void applyPresetCardShell(
        @Nonnull UICommandBuilder b,
        @Nonnull String shell,
        boolean selected,
        boolean marginRight
    ) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(selected ? CARD_HEIGHT_SELECTED : CARD_HEIGHT));
        anchor.setBottom(Value.of(0));
        if (marginRight) {
            anchor.setRight(Value.of(8));
        }
        b.setObject(shell + ".Anchor", anchor);
        b.set(shell + ".FlexWeight", selected ? CARD_FLEX_SELECTED : CARD_FLEX);
    }

    private static void applyPresetCardIcon(@Nonnull UICommandBuilder b, @Nonnull String icon, boolean selected) {
        int px = selected ? CARD_ICON_SELECTED : CARD_ICON;
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(px));
        anchor.setHeight(Value.of(px));
        b.setObject(icon + ".Anchor", anchor);
    }

    private void applyCustomizeSliders(@Nonnull UICommandBuilder b) {
        boolean disableResource = requireAllBlocks;
        b.set("#ResourceMultSlider.Value", (float) resourceMult);
        b.set("#ResourceMultBlocker.Visible", disableResource || readOnly);
        b.set("#AllBlocksToggle #CheckBox.Value", requireAllBlocks);
        String resourceLabelColor = disableResource ? "#8a8698" : "#d8ccb8";
        b.set("#ResourceMultLabel.Style.TextColor", resourceLabelColor);
        b.set("#ResourceMultValue.Style.TextColor", resourceLabelColor);
        b.set("#ResourceMultValue.TextSpans", Message.raw(formatMult(resourceMult, false)));
        b.set("#GoldMultSlider.Value", (float) goldMult);
        b.set("#GoldMultValue.TextSpans", Message.raw(formatMult(goldMult, false)));
        b.set("#BuyPriceMultSlider.Value", (float) buyPriceMult);
        b.set("#BuyPriceMultValue.TextSpans", Message.raw(formatMult(buyPriceMult, true)));
        b.set("#SellMarginSlider.Value", (float) sellMarginPercent);
        b.set("#SellMarginValue.TextSpans", Message.raw(sellMarginPercent + "%"));
        b.set("#TaxMultSlider.Value", (float) taxMult);
        b.set("#TaxMultValue.TextSpans", Message.raw(formatMult(taxMult, true)));
        b.set("#UpgradeGoldMultSlider.Value", (float) upgradeGoldMult);
        b.set("#UpgradeGoldMultValue.TextSpans", Message.raw(formatMult(upgradeGoldMult, true)));
        b.set("#UpgradeResourceMultSlider.Value", (float) upgradeResourceMult);
        b.set("#UpgradeResourceMultValue.TextSpans", Message.raw(formatMult(upgradeResourceMult, true)));
        b.set("#UnlockGoldMultSlider.Value", (float) unlockGoldMult);
        b.set("#UnlockGoldMultValue.TextSpans", Message.raw(formatMult(unlockGoldMult, true)));
        b.set("#UnlockResourceMultSlider.Value", (float) unlockResourceMult);
        b.set("#UnlockResourceMultValue.TextSpans", Message.raw(formatMult(unlockResourceMult, true)));
        b.set("#GoldLootMultSlider.Value", (float) goldLootMult);
        b.set("#GoldLootMultValue.TextSpans", Message.raw(formatMult(goldLootMult, true)));
        b.set("#OtherLootMultSlider.Value", (float) otherLootMult);
        b.set("#OtherLootMultValue.TextSpans", Message.raw(formatMult(otherLootMult, true)));
        b.set("#BuildingStaffToggle #CheckBox.Value", buildingStaffDisabled);
        if (serverMode) {
            b.set("#ForceAllTownsToggle #CheckBox.Value", forceAllTowns);
        }
    }

    private static String formatMult(double v, boolean economy) {
        double clamped =
            economy
                ? TownDifficultySettings.clampEconomyMultiplier(v)
                : TownDifficultySettings.clampMultiplier(v);
        return String.format("%.1fx", clamped);
    }

    private void wireEvents(@Nonnull UIEventBuilder eventBuilder) {
        bind(eventBuilder, "#CardEasy", "PresetEasy");
        bind(eventBuilder, "#CardNormal", "PresetNormal");
        bind(eventBuilder, "#CardHard", "PresetHard");
        bind(eventBuilder, "#CustomizeButton", "Customize");
        bind(eventBuilder, "#BackToPresetsButton", "BackPresets");
        bind(eventBuilder, "#SaveButton", "Save");
        bind(eventBuilder, "#CancelButton", "Cancel");
        bindSlider(eventBuilder, "#ResourceMultSlider", "@ResourceMult");
        bindSlider(eventBuilder, "#GoldMultSlider", "@GoldMult");
        bindSlider(eventBuilder, "#BuyPriceMultSlider", "@BuyPriceMult");
        bindSlider(eventBuilder, "#SellMarginSlider", "@SellMargin");
        bindSlider(eventBuilder, "#TaxMultSlider", "@TaxMult");
        bindSlider(eventBuilder, "#UpgradeGoldMultSlider", "@UpgradeGoldMult");
        bindSlider(eventBuilder, "#UpgradeResourceMultSlider", "@UpgradeResourceMult");
        bindSlider(eventBuilder, "#UnlockGoldMultSlider", "@UnlockGoldMult");
        bindSlider(eventBuilder, "#UnlockResourceMultSlider", "@UnlockResourceMult");
        bindSlider(eventBuilder, "#GoldLootMultSlider", "@GoldLootMult");
        bindSlider(eventBuilder, "#OtherLootMultSlider", "@OtherLootMult");
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#AllBlocksToggle #CheckBox",
            EventData.of("@AllBlocks", "#AllBlocksToggle #CheckBox.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#BuildingStaffToggle #CheckBox",
            EventData.of("@BuildingStaffDisabled", "#BuildingStaffToggle #CheckBox.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ForceAllTownsToggle #CheckBox",
            EventData.of("@ForceAllTowns", "#ForceAllTownsToggle #CheckBox.Value"),
            false
        );
    }

    private static void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            selector,
            EventData.of("Action", action),
            false
        );
    }

    private static void bindSlider(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String key) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            selector,
            EventData.of(key, selector + ".Value"),
            false
        );
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (readOnly && data.action == null) {
            return;
        }
        if (readOnly && data.action != null && !"Cancel".equals(data.action)) {
            if (!"Customize".equals(data.action) && !"BackPresets".equals(data.action)) {
                NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.translation(MSG + ".serverLocked"),
                    NotificationStyle.Warning
                );
                return;
            }
        }
        boolean changed = applySliderUpdates(data);
        if (data.action == null) {
            if (changed) {
                refreshDynamic(ref, store);
            }
            return;
        }
        switch (data.action) {
            case "PresetEasy" -> applyPresetLocal(DifficultyPreset.EASY);
            case "PresetNormal" -> applyPresetLocal(DifficultyPreset.NORMAL);
            case "PresetHard" -> applyPresetLocal(DifficultyPreset.HARD);
            case "Customize" -> customizeMode = true;
            case "BackPresets" -> customizeMode = false;
            case "Cancel" -> {
                close();
                return;
            }
            case "Save" -> {
                save(ref, store);
                return;
            }
            default -> {
                return;
            }
        }
        refreshDynamic(ref, store);
    }

    private boolean applySliderUpdates(@Nonnull PageData data) {
        if (readOnly) {
            return false;
        }
        boolean changed = false;
        if (data.resourceMult != null && !requireAllBlocks) {
            resourceMult = TownDifficultySettings.clampMultiplier(data.resourceMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.goldMult != null) {
            goldMult = TownDifficultySettings.clampMultiplier(data.goldMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.buyPriceMult != null) {
            buyPriceMult = TownDifficultySettings.clampEconomyMultiplier(data.buyPriceMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.sellMargin != null) {
            sellMarginPercent =
                TownDifficultySettings.clampSellProfitMarginPercent(Math.round(data.sellMargin));
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.taxMult != null) {
            taxMult = TownDifficultySettings.clampEconomyMultiplier(data.taxMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.upgradeGoldMult != null) {
            upgradeGoldMult = TownDifficultySettings.clampEconomyMultiplier(data.upgradeGoldMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.upgradeResourceMult != null) {
            upgradeResourceMult = TownDifficultySettings.clampEconomyMultiplier(data.upgradeResourceMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.unlockGoldMult != null) {
            unlockGoldMult = TownDifficultySettings.clampEconomyMultiplier(data.unlockGoldMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.unlockResourceMult != null) {
            unlockResourceMult = TownDifficultySettings.clampEconomyMultiplier(data.unlockResourceMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.goldLootMult != null) {
            goldLootMult = TownDifficultySettings.clampEconomyMultiplier(data.goldLootMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.otherLootMult != null) {
            otherLootMult = TownDifficultySettings.clampEconomyMultiplier(data.otherLootMult);
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (data.allBlocks != null) {
            requireAllBlocks = data.allBlocks;
            selectedPreset = DifficultyPreset.CUSTOM;
            if (requireAllBlocks) {
                resourceMult = 1.0;
            }
            changed = true;
        }
        if (data.buildingStaffDisabled != null) {
            buildingStaffDisabled = data.buildingStaffDisabled;
            selectedPreset = DifficultyPreset.CUSTOM;
            changed = true;
        }
        if (serverMode && data.forceAllTowns != null) {
            forceAllTowns = data.forceAllTowns;
            changed = true;
        }
        return changed;
    }

    private void applyPresetLocal(@Nonnull DifficultyPreset preset) {
        if (readOnly) {
            return;
        }
        TownDifficultySettings tmp = new TownDifficultySettings();
        tmp.applyPreset(preset);
        applyFromSettings(tmp);
        selectedPreset = preset;
    }

    private void refreshDynamic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        applyDynamicState(cmd);
        sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void save(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null || readOnly) {
            return;
        }
        if (serverMode) {
            saveServer(plugin, pr);
            close();
            return;
        }
        saveTown(ref, store, plugin, pr);
    }

    private void saveServer(@Nonnull AetherhavenPlugin plugin, @Nonnull PlayerRef pr) {
        ServerDifficultyState server = ServerDifficultyPersistence.getOrLoad();
        TownDifficultySettings settings = server.getDifficulty();
        writeFieldsTo(settings);
        settings.setDifficultyChosen(true);
        server.setDifficulty(settings);
        server.setForceAllTowns(forceAllTowns);
        ServerDifficultyPersistence.save(plugin, server);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation(MSG + ".serverSaved"),
            NotificationStyle.Success
        );
    }

    private void saveTown(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerRef pr
    ) {
        if (DifficultyResolver.isForced()) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + ".serverLocked"),
                NotificationStyle.Warning
            );
            close();
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townId != null ? tm.getTown(townId) : null;
        if (town == null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + ".townMissing"),
                NotificationStyle.Danger
            );
            close();
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean admin = player != null && TownPermissionUtil.canAdministerForeignTowns(player, pr);
        if (uc == null || !DifficultyAccess.canChangeDifficulty(tm, uc.getUuid(), town, admin)) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + ".ownersOnly"),
                NotificationStyle.Danger
            );
            close();
            return;
        }
        TownDifficultySettings state = town.getDifficultySettings();
        writeFieldsTo(state);
        state.setDifficultyChosen(true);
        town.setDifficultySettings(state);
        tm.updateTown(town);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation(MSG + ".saved"),
            NotificationStyle.Success
        );
        if (openStylePickerTownIdAfterSave != null) {
            if (player != null) {
                player
                    .getPageManager()
                    .openCustomPage(ref, store, new TownStylePickerPage(pr, openStylePickerTownIdAfterSave));
                return;
            }
        }
        close();
    }

    private void writeFieldsTo(@Nonnull TownDifficultySettings state) {
        state.setPreset(selectedPreset);
        state.setResourceCostMultiplier(TownDifficultySettings.clampMultiplier(resourceMult));
        state.setGoldCostMultiplier(TownDifficultySettings.clampMultiplier(goldMult));
        state.setRequireAllPrefabBlocks(requireAllBlocks);
        state.setBuyPriceMultiplier(TownDifficultySettings.clampEconomyMultiplier(buyPriceMult));
        state.setSellProfitMarginPercent(
            TownDifficultySettings.clampSellProfitMarginPercent(sellMarginPercent)
        );
        state.setTaxMultiplier(TownDifficultySettings.clampEconomyMultiplier(taxMult));
        state.setBuildingUpgradeGoldMultiplier(
            TownDifficultySettings.clampEconomyMultiplier(upgradeGoldMult)
        );
        state.setBuildingUpgradeResourceMultiplier(
            TownDifficultySettings.clampEconomyMultiplier(upgradeResourceMult)
        );
        state.setProductionUnlockGoldMultiplier(
            TownDifficultySettings.clampEconomyMultiplier(unlockGoldMult)
        );
        state.setProductionUnlockResourceMultiplier(
            TownDifficultySettings.clampEconomyMultiplier(unlockResourceMult)
        );
        state.setBuildingStaffDisabled(buildingStaffDisabled);
        state.setGoldLootRarityMultiplier(TownDifficultySettings.clampEconomyMultiplier(goldLootMult));
        state.setOtherLootRarityMultiplier(TownDifficultySettings.clampEconomyMultiplier(otherLootMult));
    }

    private boolean isAdmin(@Nonnull Store<EntityStore> store) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        return player != null && TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
    }

    public static final class PageData {
        static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("@ResourceMult", Codec.FLOAT), (d, v) -> d.resourceMult = v, d -> d.resourceMult)
                .add()
                .append(new KeyedCodec<>("@GoldMult", Codec.FLOAT), (d, v) -> d.goldMult = v, d -> d.goldMult)
                .add()
                .append(new KeyedCodec<>("@BuyPriceMult", Codec.FLOAT), (d, v) -> d.buyPriceMult = v, d -> d.buyPriceMult)
                .add()
                .append(new KeyedCodec<>("@SellMargin", Codec.FLOAT), (d, v) -> d.sellMargin = v, d -> d.sellMargin)
                .add()
                .append(new KeyedCodec<>("@TaxMult", Codec.FLOAT), (d, v) -> d.taxMult = v, d -> d.taxMult)
                .add()
                .append(
                    new KeyedCodec<>("@UpgradeGoldMult", Codec.FLOAT),
                    (d, v) -> d.upgradeGoldMult = v,
                    d -> d.upgradeGoldMult
                )
                .add()
                .append(
                    new KeyedCodec<>("@UpgradeResourceMult", Codec.FLOAT),
                    (d, v) -> d.upgradeResourceMult = v,
                    d -> d.upgradeResourceMult
                )
                .add()
                .append(
                    new KeyedCodec<>("@UnlockGoldMult", Codec.FLOAT),
                    (d, v) -> d.unlockGoldMult = v,
                    d -> d.unlockGoldMult
                )
                .add()
                .append(
                    new KeyedCodec<>("@UnlockResourceMult", Codec.FLOAT),
                    (d, v) -> d.unlockResourceMult = v,
                    d -> d.unlockResourceMult
                )
                .add()
                .append(new KeyedCodec<>("@GoldLootMult", Codec.FLOAT), (d, v) -> d.goldLootMult = v, d -> d.goldLootMult)
                .add()
                .append(
                    new KeyedCodec<>("@OtherLootMult", Codec.FLOAT),
                    (d, v) -> d.otherLootMult = v,
                    d -> d.otherLootMult
                )
                .add()
                .append(new KeyedCodec<>("@AllBlocks", Codec.BOOLEAN), (d, v) -> d.allBlocks = v, d -> d.allBlocks)
                .add()
                .append(
                    new KeyedCodec<>("@BuildingStaffDisabled", Codec.BOOLEAN),
                    (d, v) -> d.buildingStaffDisabled = v,
                    d -> d.buildingStaffDisabled
                )
                .add()
                .append(
                    new KeyedCodec<>("@ForceAllTowns", Codec.BOOLEAN),
                    (d, v) -> d.forceAllTowns = v,
                    d -> d.forceAllTowns
                )
                .add()
                .build();

        private String action;
        private Float resourceMult;
        private Float goldMult;
        private Float buyPriceMult;
        private Float sellMargin;
        private Float taxMult;
        private Float upgradeGoldMult;
        private Float upgradeResourceMult;
        private Float unlockGoldMult;
        private Float unlockResourceMult;
        private Float goldLootMult;
        private Float otherLootMult;
        private Boolean allBlocks;
        private Boolean buildingStaffDisabled;
        private Boolean forceAllTowns;
    }
}
