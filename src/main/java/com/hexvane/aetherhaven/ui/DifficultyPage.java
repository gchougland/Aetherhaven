package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.difficulty.DifficultyPreset;
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

    private final UUID townId;
    @Nullable
    private final UUID openStylePickerTownIdAfterSave;

    private boolean templateAppended;
    private boolean customizeMode;
    private DifficultyPreset selectedPreset = DifficultyPreset.NORMAL;
    private double resourceMult = 1.0;
    private double goldMult = 1.0;
    private boolean requireAllBlocks;

    public DifficultyPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId) {
        this(playerRef, townId, null);
    }

    public DifficultyPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId, @Nullable UUID openStylePickerTownIdAfterSave) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.openStylePickerTownIdAfterSave = openStylePickerTownIdAfterSave;
    }

    /** Opens difficulty for the player's owned town in their current world, or null if unavailable. */
    @Nullable
    public static DifficultyPage tryOpenForOwnedTown(@Nonnull PlayerRef playerRef) {
        com.hypixel.hytale.component.Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        com.hypixel.hytale.server.core.entity.UUIDComponent uc =
            store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
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
            loadFromTown(store);
            // Static labels only when the template is first attached. Later sendUpdate calls must not
            // re-set chrome or the client can log "Selected element ... not found".
            AetherhavenUiLocalization.applyDifficultyPage(commandBuilder);
            wireEvents(eventBuilder);
        }
        applyDynamicState(commandBuilder);
    }

    private void loadFromTown(@Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        TownDifficultySettings state = town.getDifficultySettings();
        selectedPreset = state.getPreset();
        resourceMult = state.getResourceCostMultiplier();
        goldMult = state.getGoldCostMultiplier();
        requireAllBlocks = state.isRequireAllPrefabBlocks();
    }

    private void applyDynamicState(@Nonnull UICommandBuilder b) {
        b.set("#PresetSection.Visible", !customizeMode);
        b.set("#CustomizeSection.Visible", customizeMode);
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
        b.set("#ResourceMultBlocker.Visible", disableResource);
        b.set("#AllBlocksToggle #CheckBox.Value", requireAllBlocks);
        String resourceLabelColor = disableResource ? "#8a8698" : "#d8ccb8";
        b.set("#ResourceMultLabel.Style.TextColor", resourceLabelColor);
        b.set("#ResourceMultValue.Style.TextColor", resourceLabelColor);
        b.set("#ResourceMultValue.TextSpans", Message.raw(formatMult(resourceMult)));
        b.set("#GoldMultSlider.Value", (float) goldMult);
        b.set("#GoldMultValue.TextSpans", Message.raw(formatMult(goldMult)));
    }

    private static String formatMult(double v) {
        return String.format("%.1fx", TownDifficultySettings.clampMultiplier(v));
    }

    private void wireEvents(@Nonnull UIEventBuilder eventBuilder) {
        bind(eventBuilder, "#CardEasy", "PresetEasy");
        bind(eventBuilder, "#CardNormal", "PresetNormal");
        bind(eventBuilder, "#CardHard", "PresetHard");
        bind(eventBuilder, "#CustomizeButton", "Customize");
        bind(eventBuilder, "#BackToPresetsButton", "BackPresets");
        bind(eventBuilder, "#SaveButton", "Save");
        bind(eventBuilder, "#CancelButton", "Cancel");
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ResourceMultSlider",
            EventData.of("@ResourceMult", "#ResourceMultSlider.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#GoldMultSlider",
            EventData.of("@GoldMult", "#GoldMultSlider.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#AllBlocksToggle #CheckBox",
            EventData.of("@AllBlocks", "#AllBlocksToggle #CheckBox.Value"),
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

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.resourceMult != null && !requireAllBlocks) {
            resourceMult = TownDifficultySettings.clampMultiplier(data.resourceMult);
            selectedPreset = DifficultyPreset.CUSTOM;
        }
        if (data.goldMult != null) {
            goldMult = TownDifficultySettings.clampMultiplier(data.goldMult);
            selectedPreset = DifficultyPreset.CUSTOM;
        }
        if (data.allBlocks != null) {
            requireAllBlocks = data.allBlocks;
            selectedPreset = DifficultyPreset.CUSTOM;
            if (requireAllBlocks) {
                resourceMult = 1.0;
            }
        }
        if (data.action == null) {
            if (data.resourceMult != null || data.goldMult != null || data.allBlocks != null) {
                refreshDynamic(ref, store);
            }
            return;
        }
        switch (data.action) {
            case "PresetEasy" -> {
                selectedPreset = DifficultyPreset.EASY;
                resourceMult = 0.5;
                goldMult = 0.5;
                requireAllBlocks = false;
            }
            case "PresetNormal" -> {
                selectedPreset = DifficultyPreset.NORMAL;
                resourceMult = 1.0;
                goldMult = 1.0;
                requireAllBlocks = false;
            }
            case "PresetHard" -> {
                selectedPreset = DifficultyPreset.HARD;
                resourceMult = 1.0;
                goldMult = 1.0;
                requireAllBlocks = true;
            }
            case "Customize" -> customizeMode = true;
            case "BackPresets" -> customizeMode = false;
            case "Cancel" -> {
                close();
                return;
            }
            case "Save" -> {
                saveTown(ref, store);
                return;
            }
            default -> {
                return;
            }
        }
        refreshDynamic(ref, store);
    }

    private void refreshDynamic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        applyDynamicState(cmd);
        sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void saveTown(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + ".townMissing"),
                NotificationStyle.Danger
            );
            close();
            return;
        }
        TownDifficultySettings state = town.getDifficultySettings();
        state.setPreset(selectedPreset);
        state.setResourceCostMultiplier(TownDifficultySettings.clampMultiplier(resourceMult));
        state.setGoldCostMultiplier(TownDifficultySettings.clampMultiplier(goldMult));
        state.setRequireAllPrefabBlocks(requireAllBlocks);
        state.setDifficultyChosen(true);
        town.setDifficultySettings(state);
        tm.updateTown(town);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation(MSG + ".saved"),
            NotificationStyle.Success
        );
        if (openStylePickerTownIdAfterSave != null) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                // openCustomPage replaces this UI; do not call close() or Page.None clears the new page.
                player
                    .getPageManager()
                    .openCustomPage(ref, store, new TownStylePickerPage(pr, openStylePickerTownIdAfterSave));
                return;
            }
        }
        close();
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
                .append(new KeyedCodec<>("@AllBlocks", Codec.BOOLEAN), (d, v) -> d.allBlocks = v, d -> d.allBlocks)
                .add()
                .build();

        private String action;
        private Float resourceMult;
        private Float goldMult;
        private Boolean allBlocks;
    }
}
