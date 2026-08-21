package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteApplyService;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteCatalog;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteDefinition;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.pathtool.PathToolWidthPreviewHelper;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Town records shelf paint tab: pick unlocked block palettes for this plot. */
public final class BlockPalettePage extends AetherhavenInteractiveCustomUIPage<BlockPalettePage.PageData> {
    private static final String CATEGORY_ROWS = "#CategoryRows";

    private final UUID townId;
    @Nullable
    private final Ref<ChunkStore> managementBlockRef;
    @Nullable
    private final Vector3i managementBlockPos;
    private boolean templateAppended;
    /** Draft selections for this session (category → palette id, empty string = default). */
    private final LinkedHashMap<String, String> draftSelections = new LinkedHashMap<>();
    private boolean draftsLoaded;

    public BlockPalettePage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nullable Ref<ChunkStore> managementBlockRef,
        @Nullable Vector3i managementBlockPos
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.managementBlockRef = managementBlockRef;
        this.managementBlockPos = managementBlockPos != null ? new Vector3i(managementBlockPos) : null;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/BlockPalettePage.ui");
            templateAppended = true;
            AetherhavenUiLocalization.applyBlockPalettePage(commandBuilder);
        }

        boolean showMgmtTabs = managementBlockRef != null && managementBlockPos != null;
        commandBuilder.set("#ManagementTabStrip.Visible", showMgmtTabs);
        if (showMgmtTabs) {
            boolean needsOk = computeNeedsMoveTabsOk(store);
            commandBuilder.set("#TabPlotButton.Disabled", false);
            commandBuilder.set("#TabPlayersButton.Disabled", false);
            commandBuilder.set("#TabNeedsButton.Disabled", !needsOk);
            commandBuilder.set("#TabLogButton.Disabled", !needsOk);
            commandBuilder.set("#TabPaintButton.Disabled", true);
            commandBuilder.set("#TabMoveButton.Disabled", !needsOk);
            bindManagementReturnNav(eventBuilder, needsOk);
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating, "#SaveButton", new EventData().append("Action", "Save"), false);

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        commandBuilder.clear(CATEGORY_ROWS);
        if (plugin == null) {
            showStatusOnly(
                commandBuilder,
                Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded")
            );
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        PlotInstance plot = resolvePlot(store, town);
        if (town == null || plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            showStatusOnly(
                commandBuilder,
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.blockPalette.needComplete")
            );
            return;
        }
        if (!draftsLoaded) {
            draftSelections.clear();
            draftSelections.putAll(plot.getBlockPaletteSelections());
            draftsLoaded = true;
        }

        BlockPaletteCatalog catalog = plugin.getBlockPaletteCatalog();
        List<String> categories = catalog.categoryOrder();
        if (categories.isEmpty()) {
            showStatusOnly(
                commandBuilder,
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.blockPalette.empty")
            );
            return;
        }

        commandBuilder.set("#StatusHint.Visible", false);
        commandBuilder.set("#CategoryScroll.Visible", true);

        int rowIndex = 0;
        for (String category : categories) {
            commandBuilder.append(CATEGORY_ROWS, "Aetherhaven/BlockPaletteCategoryRow.ui");
            String row = CATEGORY_ROWS + "[" + rowIndex + "]";
            commandBuilder.set(row + " #CategoryName.TextSpans", Message.raw(categoryDisplayName(category)));

            List<BlockPaletteDefinition> cycle = buildCycleList(catalog, town, category);
            int paletteIndex = indexForDraft(cycle, category);
            boolean canCycle = cycle.size() > 1;
            commandBuilder.set(row + " #PalettePrevButton.Disabled", !canCycle);
            commandBuilder.set(row + " #PaletteNextButton.Disabled", !canCycle);

            if (paletteIndex <= 0 || !canCycle) {
                commandBuilder.set(row + " #IconBox.Visible", false);
                commandBuilder.set(row + " #DefaultLabel.Visible", true);
                commandBuilder.set(
                    row + " #DefaultLabel.TextSpans",
                    Message.translation("aetherhaven_ui_town.aetherhaven.ui.blockPalette.default")
                );
            } else {
                BlockPaletteDefinition def = cycle.get(paletteIndex);
                commandBuilder.set(row + " #DefaultLabel.Visible", false);
                commandBuilder.set(row + " #IconBox.Visible", true);
                String iconPath = PathToolWidthPreviewHelper.assetPathForBlockId(def.getIconBlockId());
                if (iconPath != null && !iconPath.isBlank()) {
                    commandBuilder.set(row + " #IconBox #Icon.AssetPath", iconPath);
                }
            }

            if (canCycle) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #PalettePrevButton",
                    new EventData().append("Action", "PalettePrev").append("Category", category),
                    false);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #PaletteNextButton",
                    new EventData().append("Action", "PaletteNext").append("Category", category),
                    false);
            }
            rowIndex++;
        }
    }

    private static void showStatusOnly(@Nonnull UICommandBuilder commandBuilder, @Nonnull Message status) {
        commandBuilder.set("#CategoryScroll.Visible", false);
        commandBuilder.set("#StatusHint.Visible", true);
        commandBuilder.set("#StatusHint.TextSpans", status);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.action == null) {
            return;
        }
        if (data.action.equalsIgnoreCase("SwitchTabPlot")) {
            openPlotManagement(ref, store, 0, false);
            return;
        }
        if (data.action.equalsIgnoreCase("SwitchTabPlayers")) {
            openPlotManagement(ref, store, 1, false);
            return;
        }
        if (data.action.equalsIgnoreCase("OpenTownNeeds")) {
            openTownNeeds(ref, store);
            return;
        }
        if (data.action.equalsIgnoreCase("OpenTownLog")) {
            openTownLog(ref, store);
            return;
        }
        if (data.action.equalsIgnoreCase("BeginMoveBuilding")) {
            openPlotManagement(ref, store, 0, true);
            return;
        }
        if (data.action.equalsIgnoreCase("PalettePrev")) {
            shiftPalette(store, data.category, -1);
            refresh(ref, store);
            return;
        }
        if (data.action.equalsIgnoreCase("PaletteNext")) {
            shiftPalette(store, data.category, 1);
            refresh(ref, store);
            return;
        }
        if (data.action.equalsIgnoreCase("Save")) {
            saveAndApply(ref, store);
        }
    }

    private void bindManagementReturnNav(@Nonnull UIEventBuilder eventBuilder, boolean needsMoveTabsEnabled) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabPlotButton",
            new EventData().append("Action", "SwitchTabPlot"),
            false);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabPlayersButton",
            new EventData().append("Action", "SwitchTabPlayers"),
            false);
        if (needsMoveTabsEnabled) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabNeedsButton",
                new EventData().append("Action", "OpenTownNeeds"),
                false);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabLogButton",
                new EventData().append("Action", "OpenTownLog"),
                false);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabMoveButton",
                new EventData().append("Action", "BeginMoveBuilding"),
                false);
        }
    }

    private void shiftPalette(@Nonnull Store<EntityStore> store, @Nullable String category, int delta) {
        if (category == null || category.isBlank()) {
            return;
        }
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
        String cat = category.trim();
        List<BlockPaletteDefinition> cycle = buildCycleList(plugin.getBlockPaletteCatalog(), town, cat);
        int idx = indexForDraft(cycle, cat);
        int next = Math.floorMod(idx + delta, cycle.size());
        if (next == 0) {
            draftSelections.remove(cat);
        } else {
            draftSelections.put(cat, cycle.get(next).getId());
        }
    }

    private void saveAndApply(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        PlotInstance plot = resolvePlot(store, town);
        if (town == null || plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            return;
        }
        plot.clearBlockPaletteSelections();
        for (Map.Entry<String, String> e : draftSelections.entrySet()) {
            if (e.getValue() != null && !e.getValue().isBlank()) {
                plot.setBlockPaletteSelection(e.getKey(), e.getValue());
            }
        }
        tm.updateTown(town);
        BlockPaletteApplyService.applyToPlot(world, plugin, town, plot, def);
        playerRef.sendMessage(Message.translation("aetherhaven_ui_town.aetherhaven.ui.blockPalette.saved"));
        refresh(ref, store);
    }

    @Nonnull
    private static List<BlockPaletteDefinition> buildCycleList(
        @Nonnull BlockPaletteCatalog catalog,
        @Nonnull TownRecord town,
        @Nonnull String category
    ) {
        List<BlockPaletteDefinition> out = new ArrayList<>();
        out.add(null); // index 0 = Default
        Set<String> unlocked = town.getUnlockedBlockPaletteIds();
        for (BlockPaletteDefinition def : catalog.forCategory(category)) {
            if (unlocked.contains(def.getId())) {
                out.add(def);
            }
        }
        return out;
    }

    private int indexForDraft(@Nonnull List<BlockPaletteDefinition> cycle, @Nonnull String category) {
        String selected = draftSelections.get(category);
        if (selected == null || selected.isBlank()) {
            return 0;
        }
        for (int i = 1; i < cycle.size(); i++) {
            BlockPaletteDefinition def = cycle.get(i);
            if (def != null && selected.equals(def.getId())) {
                return i;
            }
        }
        return 0;
    }

    @Nonnull
    private static String categoryDisplayName(@Nonnull String category) {
        return switch (category) {
            case "walls" -> "Walls";
            case "trunks" -> "Logs";
            case "planks" -> "Planks";
            case "cobble" -> "Cobble";
            case "bricks" -> "Bricks";
            case "cloth" -> "Cloth";
            case "roofs" -> "Roofs";
            default -> category;
        };
    }

    @Nullable
    private PlotInstance resolvePlot(@Nonnull Store<EntityStore> store, @Nullable TownRecord town) {
        if (town == null || managementBlockRef == null) {
            return null;
        }
        Store<ChunkStore> cs = managementBlockRef.getStore();
        ManagementBlock mb = cs.getComponent(managementBlockRef, ManagementBlock.getComponentType());
        if (mb == null || mb.getPlotId().isBlank()) {
            return null;
        }
        try {
            return town.findPlotById(UUID.fromString(mb.getPlotId().trim()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean computeNeedsMoveTabsOk(@Nonnull Store<EntityStore> store) {
        if (managementBlockRef == null) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Store<ChunkStore> cs = managementBlockRef.getStore();
        ManagementBlock mb = cs.getComponent(managementBlockRef, ManagementBlock.getComponentType());
        if (mb == null || mb.getTownId().isBlank() || mb.getPlotId().isBlank()) {
            return false;
        }
        try {
            TownRecord town = tm.getTown(UUID.fromString(mb.getTownId().trim()));
            if (town == null) {
                return false;
            }
            PlotInstance pi = town.findPlotById(UUID.fromString(mb.getPlotId().trim()));
            return pi != null && pi.getState() == PlotInstanceState.COMPLETE;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void openTownNeeds(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (managementBlockRef == null || managementBlockPos == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager()
            .openCustomPage(ref, store, new VillagerNeedsOverviewPage(playerRef, townId, managementBlockRef, managementBlockPos));
    }

    private void openTownLog(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (managementBlockRef == null || managementBlockPos == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager()
            .openCustomPage(ref, store, new TownLogPage(playerRef, townId, managementBlockRef, managementBlockPos));
    }

    private void openPlotManagement(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        int managementTab,
        boolean openMoveBuildingModalOnFirstBuild
    ) {
        if (managementBlockRef == null || managementBlockPos == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager()
            .openCustomPage(
                ref,
                store,
                new PlotConstructionPage(
                    playerRef,
                    managementBlockRef,
                    managementBlockPos,
                    true,
                    managementTab,
                    openMoveBuildingModalOnFirstBuild
                )
            );
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action)
                .add()
                .append(new KeyedCodec<>("Category", Codec.STRING), (o, v) -> o.category = v, o -> o.category)
                .add()
                .build();

        public String action;
        public String category;
    }
}
