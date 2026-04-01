package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementCommit;
import com.hexvane.aetherhaven.placement.PlotPlacementSession;
import com.hexvane.aetherhaven.placement.PlotPlacementSessions;
import com.hexvane.aetherhaven.placement.PlotPlacementValidator;
import com.hexvane.aetherhaven.placement.PlotPreviewSpawner;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
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
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotPlacementPage extends InteractiveCustomUIPage<PlotPlacementPage.PageData> {
    @Nonnull
    private final PlotPlacementSession session;

    public PlotPlacementPage(@Nonnull PlayerRef playerRef, @Nonnull PlotPlacementSession session) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.session = session;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/PlotPlacementPage.ui");
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        ConstructionDefinition def =
            plugin != null ? plugin.getConstructionCatalog().get(session.getConstructionId()) : null;
        String name = def != null ? def.getDisplayName() : session.getConstructionId();
        Vector3i sign = session.getAnchor();
        int[] o = def != null ? def.getPlotAnchorOffset() : new int[] {0, 0, 0};
        Vector3i prefabO = prefabOriginForSign(sign, o);
        commandBuilder.set(
            "#Info.TextSpans",
            Message.raw(
                name
                    + "\nPlot sign "
                    + sign.x
                    + ", "
                    + sign.y
                    + ", "
                    + sign.z
                    + " · Prefab origin "
                    + prefabO.x
                    + ", "
                    + prefabO.y
                    + ", "
                    + prefabO.z
                    + "\nYaw step "
                    + session.getRotationSteps()
                    + " / 4 (90° each)\nEscape: close panel only · Cancel: remove preview · Air right-click reopens while preview is active."
            )
        );
        commandBuilder.set("#Error.Visible", false);

        if (plugin != null && inv != null) {
            List<String> validIds = listConstructionIdsWithPlotTokens(plugin, inv);
            if (!validIds.isEmpty() && !validIds.contains(session.getConstructionId())) {
                session.setConstructionId(validIds.get(0));
            }
            List<DropdownEntryInfo> entries = collectPlotDropdownEntries(plugin, inv);
            commandBuilder.set("#PlotTypeDropdown.Entries", entries);
            commandBuilder.set("#PlotTypeDropdown.Visible", !entries.isEmpty());
            commandBuilder.set("#PlotTypeLabel.Visible", !entries.isEmpty());
            if (!entries.isEmpty()) {
                commandBuilder.set("#PlotTypeDropdown.Value", session.getConstructionId());
            }
        } else {
            commandBuilder.set("#PlotTypeDropdown.Visible", false);
            commandBuilder.set("#PlotTypeLabel.Visible", false);
        }

        bind(eventBuilder, "#BtnXm", "MoveXm");
        bind(eventBuilder, "#BtnXp", "MoveXp");
        bind(eventBuilder, "#BtnZm", "MoveZm");
        bind(eventBuilder, "#BtnZp", "MoveZp");
        bind(eventBuilder, "#BtnYm", "MoveYm");
        bind(eventBuilder, "#BtnYp", "MoveYp");
        bind(eventBuilder, "#BtnRotate", "Rotate");
        bind(eventBuilder, "#PlaceButton", "Place");
        bind(eventBuilder, "#CancelButton", "Cancel");

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#PlotTypeDropdown",
            EventData.of("@ConstructionId", "#PlotTypeDropdown.Value"),
            false
        );

        scheduleRefreshPreview(ref, store);
    }

    private static void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, new EventData().append("Action", action), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.constructionId != null && !data.constructionId.isBlank()) {
            String id = data.constructionId.trim();
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            ConstructionDefinition def = plugin != null ? plugin.getConstructionCatalog().get(id) : null;
            Player player = store.getComponent(ref, Player.getComponentType());
            CombinedItemContainer inv =
                player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
            if (def == null || inv == null || !hasPlotToken(inv, def)) {
                sendError(store, ref, "You need the matching plot token in your inventory for that building.");
            } else {
                session.setConstructionId(id);
            }
            scheduleRebuild(ref, store);
            return;
        }
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "MoveXm" -> session.nudge(-1, 0, 0);
            case "MoveXp" -> session.nudge(1, 0, 0);
            case "MoveZm" -> session.nudge(0, 0, -1);
            case "MoveZp" -> session.nudge(0, 0, 1);
            case "MoveYm" -> session.nudge(0, -1, 0);
            case "MoveYp" -> session.nudge(0, 1, 0);
            case "Rotate" -> session.rotateClockwise90();
            case "Cancel" -> {
                scheduleCancel(ref, store);
                return;
            }
            case "Place" -> {
                schedulePlace(ref, store);
                return;
            }
            default -> {
                return;
            }
        }
        scheduleRebuild(ref, store);
    }

    private boolean tryPlace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForOwnerInWorld(uc.getUuid());
        if (town == null) {
            sendError(store, ref, "You need a town (place a charter) first.");
            return false;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            sendError(store, ref, "Unknown construction: " + session.getConstructionId());
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        if (inv == null || !hasPlotToken(inv, def)) {
            sendError(store, ref, "You need the plot token for this building in your inventory to place it.");
            return false;
        }
        Vector3i signPos = session.getAnchor();
        String err = PlotPlacementValidator.validate(world, tm, town, uc.getUuid(), signPos, session.getPrefabYaw(), def, plugin);
        if (err != null) {
            sendError(store, ref, err);
            return false;
        }
        String tokenId = def.getPlotTokenItemId();
        if (tokenId == null || tokenId.isBlank()) {
            sendError(store, ref, "This construction has no plot token configured.");
            return false;
        }
        ItemStackTransaction tokenTx = inv.removeItemStack(new ItemStack(tokenId, 1));
        if (!tokenTx.succeeded()) {
            sendError(store, ref, "Could not consume plot token (inventory changed?).");
            return false;
        }
        UUID plotId = UUID.randomUUID();
        boolean placed =
            PlotPlacementCommit.placePlotSign(
                world,
                signPos.x,
                signPos.y,
                signPos.z,
                session.getPrefabYaw(),
                session.getConstructionId(),
                plotId,
                store
            );
        if (!placed) {
            inv.addItemStack(new ItemStack(tokenId, 1));
            sendError(store, ref, "Could not place plot sign (blocked or invalid spot).");
            return false;
        }
        Path prefabPath = resolvePrefabAssetPath(def.getPrefabPath());
        if (prefabPath != null) {
            IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
            try {
                Vector3i prefabOrigin = prefabOriginForSign(signPos, def.getPlotAnchorOffset());
                PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf);
                PlotInstance inst =
                    new PlotInstance(
                        plotId,
                        session.getConstructionId(),
                        PlotInstanceState.BLUEPRINTING,
                        fp,
                        signPos.x,
                        signPos.y,
                        signPos.z,
                        System.currentTimeMillis()
                    );
                town.addPlotInstance(inst);
                tm.updateTown(town);
            } finally {
                buf.release();
            }
        } else {
            PlotFootprintRecord mini = new PlotFootprintRecord(signPos.x, signPos.y, signPos.z, signPos.x, signPos.y, signPos.z);
            town.addPlotInstance(
                new PlotInstance(
                    plotId,
                    session.getConstructionId(),
                    PlotInstanceState.BLUEPRINTING,
                    mini,
                    signPos.x,
                    signPos.y,
                    signPos.z,
                    System.currentTimeMillis()
                )
            );
            tm.updateTown(town);
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw("Plot sign placed."));
        }
        return true;
    }

    private void scheduleRefreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                refreshPreview(ref, store);
            }
        );
    }

    private void scheduleRebuild(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                rebuild();
            }
        );
    }

    private void scheduleCancel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc != null) {
                    PlotPlacementSessions.remove(uc.getUuid());
                }
                close();
            }
        );
    }

    private void schedulePlace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                if (tryPlace(ref, store)) {
                    PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
                    UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                    if (uc != null) {
                        PlotPlacementSessions.remove(uc.getUuid());
                    }
                    close();
                } else {
                    rebuild();
                }
            }
        );
    }

    private void refreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
            return;
        }
        Path prefabPath = resolvePrefabAssetPath(def.getPrefabPath());
        if (prefabPath == null) {
            PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
            return;
        }
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            Vector3i prefabOrigin = prefabOriginForSign(session.getAnchor(), def.getPlotAnchorOffset());
            PlotPreviewSpawner.rebuild(store, prefabOrigin, session.getPrefabYaw(), buf, session.getPreviewEntityRefs());
        } finally {
            buf.release();
        }
    }

    private void sendError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String text) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw(text));
        }
    }

    @Nonnull
    private static Vector3i prefabOriginForSign(@Nonnull Vector3i signPosition, int[] plotAnchorOffset) {
        int[] o = plotAnchorOffset != null && plotAnchorOffset.length == 3 ? plotAnchorOffset : new int[] {0, 0, 0};
        return new Vector3i(signPosition.x + o[0], signPosition.y + o[1], signPosition.z + o[2]);
    }

    /**
     * Catalog order: first construction the player has a plot token for becomes the default when opening placement.
     */
    @Nonnull
    private static List<String> listConstructionIdsWithPlotTokens(
        @Nonnull AetherhavenPlugin plugin, @Nonnull CombinedItemContainer inv
    ) {
        ObjectArrayList<String> ids = new ObjectArrayList<>();
        for (ConstructionDefinition d : plugin.getConstructionCatalog().list()) {
            String token = d.getPlotTokenItemId();
            if (token == null || token.isBlank()) {
                continue;
            }
            if (InventoryMaterials.count(inv, token) <= 0) {
                continue;
            }
            ids.add(d.getId());
        }
        return ids;
    }

    @Nonnull
    private static List<DropdownEntryInfo> collectPlotDropdownEntries(
        @Nonnull AetherhavenPlugin plugin, @Nonnull CombinedItemContainer inv
    ) {
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        for (String id : listConstructionIdsWithPlotTokens(plugin, inv)) {
            ConstructionDefinition d = plugin.getConstructionCatalog().get(id);
            if (d == null) {
                continue;
            }
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(d.getDisplayName()), d.getId()));
        }
        return entries;
    }

    private static boolean hasPlotToken(@Nonnull CombinedItemContainer inv, @Nonnull ConstructionDefinition def) {
        String token = def.getPlotTokenItemId();
        if (token == null || token.isBlank()) {
            return false;
        }
        return InventoryMaterials.count(inv, token) > 0;
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

    @Nullable
    public static String defaultConstructionFromInventory(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (plugin == null || player == null) {
            return null;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        List<String> ids = listConstructionIdsWithPlotTokens(plugin, inv);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@ConstructionId", Codec.STRING), (d, v) -> d.constructionId = v, d -> d.constructionId)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String constructionId;
    }
}
