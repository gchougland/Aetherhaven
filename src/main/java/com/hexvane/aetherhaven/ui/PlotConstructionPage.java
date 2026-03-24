package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotConstructionPage extends InteractiveCustomUIPage<PlotConstructionPage.PageData> {
    private static final int MATERIAL_ROW_CAP = 10;
    private static final int BREAK_SETTINGS = 10;

    private final Ref<ChunkStore> plotBlockRef;
    @Nonnull
    private final Vector3i interactionSignWorldPos;

    public PlotConstructionPage(
        @Nonnull PlayerRef playerRef, @Nonnull Ref<ChunkStore> plotBlockRef, @Nonnull Vector3i interactionSignWorldPos
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.plotBlockRef = plotBlockRef;
        this.interactionSignWorldPos = interactionSignWorldPos.clone();
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/PlotConstructionPage.ui");
        ConstructionDefinition def = resolveDefinition();
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;

        if (def == null) {
            commandBuilder.set("#BuildingTitle.TextSpans", Message.raw("Plot sign"));
            commandBuilder.set("#Description.TextSpans", Message.raw("No construction is configured for this plot."));
            commandBuilder.set("#VillagerRow.Visible", false);
            for (int i = 0; i < MATERIAL_ROW_CAP; i++) {
                commandBuilder.set("#Mat" + i + ".Visible", false);
            }
            commandBuilder.set("#BuildButton.Disabled", true);
            return;
        }

        commandBuilder.set("#BuildingTitle.TextSpans", Message.raw(def.getDisplayName()));
        String desc = def.getDescription() != null ? def.getDescription() : "";
        commandBuilder.set("#Description.TextSpans", Message.raw(desc));

        boolean villagerOk = villagerRequirementMet(def);
        commandBuilder.set("#VillagerRow.Visible", true);
        commandBuilder.set("#VillagerLabel.TextSpans", Message.raw(villagerLabel(def)));
        commandBuilder.set("#VillagerLabel.Style.TextColor", villagerOk ? "#3d913f" : "#962f2f");

        String lang = this.playerRef.getLanguage();
        int mi = 0;
        for (; mi < def.getMaterials().size() && mi < MATERIAL_ROW_CAP; mi++) {
            var m = def.getMaterials().get(mi);
            String itemId = m.getItemId() != null ? m.getItemId() : "?";
            int need = m.getCount();
            int has = inv != null ? InventoryMaterials.count(inv, itemId) : 0;
            boolean ok = has >= need;
            String itemLabel = itemLabelForUi(lang, itemId);
            commandBuilder.set("#Mat" + mi + ".Visible", true);
            commandBuilder.set("#Mat" + mi + " #Line.TextSpans", Message.raw(itemLabel + " x" + need + " (have " + has + ")"));
            commandBuilder.set("#Mat" + mi + " #Line.Style.TextColor", ok ? "#3d913f" : "#962f2f");
        }
        for (; mi < MATERIAL_ROW_CAP; mi++) {
            commandBuilder.set("#Mat" + mi + ".Visible", false);
        }

        boolean matsOk = inv != null && InventoryMaterials.hasAll(inv, def.getMaterials());
        boolean canBuild = villagerOk && matsOk;
        commandBuilder.set("#BuildButton.Disabled", !canBuild);

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BuildButton",
            new EventData().append("Action", "Build"),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || !data.action.equalsIgnoreCase("Build")) {
            return;
        }
        ConstructionDefinition def = resolveDefinition();
        if (def == null) {
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
        InventoryMaterials.removeAll(inv, def.getMaterials());

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i signPos = interactionSignWorldPos;
        int[] o = def.getPlotAnchorOffset();
        Vector3i anchor = new Vector3i(signPos.x + o[0], signPos.y + o[1], signPos.z + o[2]);
        // Prefab yaw follows the placed plot sign block rotation (NESW), not only JSON defaults.
        Rotation yaw = PlotBlockRotationUtil.readBlockYaw(world, signPos);
        Path prefabPath = resolvePrefabAssetPath(def.getPrefabPath());
        if (prefabPath == null) {
            sendBuildError(store, ref, "Prefab not found for path: " + def.getPrefabPath());
            return;
        }
        IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPath);
        var cfg = plugin.getConfig().get();
        Runnable removeSign = () -> world.breakBlock(signPos.x, signPos.y, signPos.z, BREAK_SETTINGS);
        // force: use setBlock so solid prefab voxels replace terrain; air voxels are never queued (blockId==0).
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
            removeSign
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
    private static Path resolvePrefabAssetPath(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.trim();
        PrefabStore ps = PrefabStore.get();
        Path p = ps.findAssetPrefabPath(k);
        if (p != null) {
            return p;
        }
        if (!k.endsWith(".prefab.json")) {
            p = ps.findAssetPrefabPath(k + ".prefab.json");
            if (p != null) {
                return p;
            }
        }
        String dotted = k.replace('.', '/');
        if (!dotted.equals(k)) {
            p = ps.findAssetPrefabPath(dotted);
            if (p != null) {
                return p;
            }
            if (!dotted.endsWith(".prefab.json")) {
                p = ps.findAssetPrefabPath(dotted + ".prefab.json");
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
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

    private ConstructionDefinition resolveDefinition() {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return null;
        }
        Store<ChunkStore> store = plotBlockRef.getStore();
        PlotSignBlock plot = store.getComponent(plotBlockRef, PlotSignBlock.getComponentType());
        if (plot == null) {
            return null;
        }
        return p.getConstructionCatalog().get(plot.getConstructionId());
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
        return "Villager required: " + vid + " (not implemented — enable IgnoreVillagerRequirement in config to test)";
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .build();

        private String action;
    }
}
