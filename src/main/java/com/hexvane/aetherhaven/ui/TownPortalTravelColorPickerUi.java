package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import javax.annotation.Nonnull;

/** Preset swatch grid for visitor portal town color (shared by travel page and portal shelf). */
public final class TownPortalTravelColorPickerUi {
    public static final String ACTION_OPEN = "OpenPortalColorPicker";
    public static final String ACTION_CLOSE = "ClosePortalColorPicker";
    public static final String ACTION_PICK = "PickPortalColorPreset";

    private static final String SWATCH_UI = "Aetherhaven/TownPortalColorPresetSwatch.ui";
    private static final String GRID_ROW_UI = "Aetherhaven/TownPortalColorPresetGridRow.ui";
    private static final int SWATCHES_PER_ROW = 8;

    private TownPortalTravelColorPickerUi() {}

    public static void setModalVisible(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String modalSelector,
        boolean visible
    ) {
        commandBuilder.set(modalSelector + ".Visible", visible);
    }

    public static void bindOpenButton(@Nonnull UIEventBuilder eventBuilder, @Nonnull String buttonSelector) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            buttonSelector,
            EventData.of("Action", ACTION_OPEN),
            false
        );
    }

    public static void bindCloseButton(@Nonnull UIEventBuilder eventBuilder, @Nonnull String cancelButtonSelector) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            cancelButtonSelector,
            EventData.of("Action", ACTION_CLOSE),
            false
        );
    }

    /**
     * Fills a preset color grid. Row/cell selectors match {@code ProductionMaterialPickerPage} /
     * {@code ProductionUnlockGridRow.ui} ({@code grid[r] #Strip[c]}).
     */
    public static void buildPresetGrid(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull String gridSelector,
        @Nonnull String selectedHex
    ) {
        String resolved = TownPortalTravelColor.normalizePresetHex(selectedHex);
        commandBuilder.clear(gridSelector);
        String[] presets = TownPortalTravelColor.PRESET_HEX;
        for (int i = 0; i < presets.length; i++) {
            int rowIndex = i / SWATCHES_PER_ROW;
            int col = i % SWATCHES_PER_ROW;
            if (col == 0) {
                commandBuilder.append(gridSelector, GRID_ROW_UI);
            }
            String rowBase = gridSelector + "[" + rowIndex + "]";
            String strip = rowBase + " #Strip";
            commandBuilder.append(strip, SWATCH_UI);
            String cell = strip + "[" + col + "]";
            String hex = presets[i];
            boolean selected = hex.equalsIgnoreCase(resolved);
            commandBuilder.setObject(cell + " #ColorFill.Background", TownPortalTravelColor.solidColorPatch(hex));
            commandBuilder.set(cell + " #SelectedOutline.Visible", selected);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                cell + " #PickButton",
                new EventData().append("Action", ACTION_PICK).append("PresetHex", hex),
                false
            );
        }
    }
}
