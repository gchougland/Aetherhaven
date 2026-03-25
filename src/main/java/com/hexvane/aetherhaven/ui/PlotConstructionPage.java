package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
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
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotConstructionPage extends InteractiveCustomUIPage<PlotConstructionPage.PageData> {
    private static final int MATERIAL_ROW_CAP = 10;
    private static final int BREAK_SETTINGS = 10;

    private final Ref<ChunkStore> blockRef;
    @Nonnull
    private final Vector3i blockWorldPos;
    private final boolean managementUi;

    public PlotConstructionPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull Vector3i blockWorldPos,
        boolean managementUi
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.blockRef = blockRef;
        this.blockWorldPos = blockWorldPos.clone();
        this.managementUi = managementUi;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/PlotConstructionPage.ui");
        ConstructionDefinition def = resolveDefinition(store, ref);
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;

        if (def == null) {
            commandBuilder.set("#BuildingTitle.TextSpans", Message.raw(managementUi ? "Building" : "Plot sign"));
            commandBuilder.set("#Description.TextSpans", Message.raw("No construction is configured for this plot."));
            commandBuilder.set("#VillagerRow.Visible", false);
            for (int i = 0; i < MATERIAL_ROW_CAP; i++) {
                commandBuilder.set("#Mat" + i + ".Visible", false);
            }
            commandBuilder.set("#BuildButton.Disabled", true);
            return;
        }

        PlotInstanceState state = resolvePlotState(store, ref);
        boolean completed = state == PlotInstanceState.COMPLETE;

        commandBuilder.set("#BuildingTitle.TextSpans", Message.raw(def.getDisplayName()));
        String desc = def.getDescription() != null ? def.getDescription() : "";
        if (completed) {
            desc = desc.isEmpty() ? "Construction complete." : desc + "\n\nConstruction complete.";
        }
        commandBuilder.set("#Description.TextSpans", Message.raw(desc));

        boolean villagerOk = villagerRequirementMet(def) || completed;
        commandBuilder.set("#VillagerRow.Visible", true);
        commandBuilder.set("#VillagerLabel.TextSpans", Message.raw(completed ? "Villager: n/a (complete)" : villagerLabel(def)));
        commandBuilder.set("#VillagerLabel.Style.TextColor", villagerOk ? "#3d913f" : "#962f2f");

        String lang = this.playerRef.getLanguage();
        int mi = 0;
        for (; mi < def.getMaterials().size() && mi < MATERIAL_ROW_CAP; mi++) {
            var m = def.getMaterials().get(mi);
            String itemId = m.getItemId() != null ? m.getItemId() : "?";
            int need = m.getCount();
            int has = inv != null ? InventoryMaterials.count(inv, itemId) : 0;
            boolean ok = completed || has >= need;
            String itemLabel = itemLabelForUi(lang, itemId);
            commandBuilder.set("#Mat" + mi + ".Visible", true);
            commandBuilder.set("#Mat" + mi + " #Line.TextSpans", Message.raw(itemLabel + " x" + need + " (have " + has + ")"));
            commandBuilder.set("#Mat" + mi + " #Line.Style.TextColor", ok ? "#3d913f" : "#962f2f");
        }
        for (; mi < MATERIAL_ROW_CAP; mi++) {
            commandBuilder.set("#Mat" + mi + ".Visible", false);
        }

        boolean matsOk = completed || (inv != null && InventoryMaterials.hasAll(inv, def.getMaterials()));
        boolean canBuild = !managementUi && !completed && villagerOk && matsOk;
        commandBuilder.set("#BuildButton.Disabled", !canBuild);

        commandBuilder.set("#TownNeedsButton.Visible", managementUi && completed);
        if (managementUi && completed) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TownNeedsButton",
                new EventData().append("Action", "OpenTownNeeds"),
                false
            );
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BuildButton",
            new EventData().append("Action", "Build"),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action != null && data.action.equalsIgnoreCase("OpenTownNeeds")) {
            if (!managementUi) {
                return;
            }
            PlotInstanceState st = resolvePlotState(store, ref);
            if (st != PlotInstanceState.COMPLETE) {
                return;
            }
            Store<ChunkStore> cs = blockRef.getStore();
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank()) {
                return;
            }
            UUID townUuid;
            try {
                townUuid = UUID.fromString(mb.getTownId().trim());
            } catch (IllegalArgumentException e) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                // openCustomPage replaces this UI; do not call close() — that sets Page.None and clears the new page.
                player.getPageManager().openCustomPage(ref, store, new VillagerNeedsOverviewPage(playerRef, townUuid));
            }
            return;
        }
        if (data.action == null || !data.action.equalsIgnoreCase("Build")) {
            return;
        }
        if (managementUi) {
            return;
        }
        ConstructionDefinition def = resolveDefinition(store, ref);
        if (def == null) {
            return;
        }
        PlotInstanceState state = resolvePlotState(store, ref);
        if (state == PlotInstanceState.COMPLETE) {
            return;
        }
        if (!villagerRequirementMet(def)) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (!InventoryMaterials.hasAll(inv, def.getMaterials())) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        Store<ChunkStore> cstore = blockRef.getStore();
        PlotSignBlock plot = cstore.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            sendBuildError(store, ref, "This plot sign has no plot id (legacy); replace the sign.");
            return;
        }
        UUID plotId;
        try {
            plotId = UUID.fromString(plot.getPlotId().trim());
        } catch (IllegalArgumentException e) {
            sendBuildError(store, ref, "Invalid plot id on sign.");
            return;
        }

        InventoryMaterials.removeAll(inv, def.getMaterials());

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i signPos = blockWorldPos;
        int[] o = def.getPlotAnchorOffset();
        Vector3i anchor = new Vector3i(signPos.x + o[0], signPos.y + o[1], signPos.z + o[2]);
        Rotation yaw = PlotBlockRotationUtil.readBlockYaw(world, signPos);
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            sendBuildError(store, ref, "Prefab not found for path: " + def.getPrefabPath());
            return;
        }
        IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPath);
        var cfg = plugin.getConfig().get();
        UUID ownerUuid = uc.getUuid();
        Runnable onComplete =
            () -> {
                world.breakBlock(signPos.x, signPos.y, signPos.z, BREAK_SETTINGS);
                ConstructionCompleter.finishBuild(world, plugin, ownerUuid, plotId, anchor, yaw);
            };
        ConstructionAnimator.start(
            plugin,
            world,
            anchor,
            yaw,
            true,
            buffer,
            store,
            cfg.getConstructionBlocksPerTick(),
            cfg.getConstructionMinIntervalMs(),
            onComplete
        );
        close();
    }

    private void sendBuildError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String text) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw(text));
        }
    }

    @Nullable
    private ConstructionDefinition resolveDefinition(@Nonnull Store<EntityStore> entityStore, @Nonnull Ref<EntityStore> playerRef) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return null;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        World world = entityStore.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);

        if (managementUi) {
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank() || mb.getPlotId().isBlank()) {
                return null;
            }
            try {
                TownRecord town = tm.getTown(UUID.fromString(mb.getTownId().trim()));
                if (town == null) {
                    return null;
                }
                PlotInstance pi = town.findPlotById(UUID.fromString(mb.getPlotId().trim()));
                if (pi == null) {
                    return null;
                }
                return p.getConstructionCatalog().get(pi.getConstructionId());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null) {
            return null;
        }
        return p.getConstructionCatalog().get(plot.getConstructionId());
    }

    @Nonnull
    private PlotInstanceState resolvePlotState(@Nonnull Store<EntityStore> entityStore, @Nonnull Ref<EntityStore> playerRef) {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return PlotInstanceState.BLUEPRINTING;
        }
        World world = entityStore.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
        Store<ChunkStore> cs = blockRef.getStore();

        if (managementUi) {
            ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
            if (mb == null || mb.getTownId().isBlank() || mb.getPlotId().isBlank()) {
                return PlotInstanceState.COMPLETE;
            }
            try {
                TownRecord town = tm.getTown(UUID.fromString(mb.getTownId().trim()));
                if (town == null) {
                    return PlotInstanceState.COMPLETE;
                }
                PlotInstance pi = town.findPlotById(UUID.fromString(mb.getPlotId().trim()));
                return pi != null ? pi.getState() : PlotInstanceState.COMPLETE;
            } catch (IllegalArgumentException e) {
                return PlotInstanceState.COMPLETE;
            }
        }

        PlotSignBlock plot = cs.getComponent(blockRef, PlotSignBlock.getComponentType());
        if (plot == null || plot.getPlotId() == null || plot.getPlotId().isBlank()) {
            return PlotInstanceState.BLUEPRINTING;
        }
        UUIDComponent uc = entityStore.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return PlotInstanceState.BLUEPRINTING;
        }
        TownRecord town = tm.findTownForOwnerInWorld(uc.getUuid());
        if (town == null) {
            return PlotInstanceState.BLUEPRINTING;
        }
        try {
            PlotInstance pi = town.findPlotById(UUID.fromString(plot.getPlotId().trim()));
            return pi != null ? pi.getState() : PlotInstanceState.BLUEPRINTING;
        } catch (IllegalArgumentException e) {
            return PlotInstanceState.BLUEPRINTING;
        }
    }

    @Nonnull
    private static String itemLabelForUi(@Nullable String language, @Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return itemId;
        }
        String trKey = item.getTranslationKey();
        String lang = language != null ? language : "en-US";
        String resolved = I18nModule.get().getMessage(lang, trKey);
        return resolved != null ? resolved : itemId;
    }

    private boolean villagerRequirementMet(ConstructionDefinition def) {
        String vid = def.getRequiredVillagerId();
        if (vid == null || vid.isEmpty()) {
            return true;
        }
        AetherhavenPlugin p = AetherhavenPlugin.get();
        return p != null && p.getConfig().get().isIgnoreVillagerRequirement();
    }

    private static String villagerLabel(ConstructionDefinition def) {
        String vid = def.getRequiredVillagerId();
        if (vid == null || vid.isEmpty()) {
            return "Villager: not required";
        }
        return "Villager required: " + vid + " (not implemented; enable IgnoreVillagerRequirement in config to test)";
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .build();

        private String action;
    }
}
