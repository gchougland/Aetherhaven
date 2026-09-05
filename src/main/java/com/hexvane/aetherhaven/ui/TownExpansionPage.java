package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.plotcreator.RuntimeCommonIconBroadcast;
import com.hexvane.aetherhaven.territory.TownExpansionAssetDelivery;
import com.hexvane.aetherhaven.territory.TownExpansionClaimService;
import com.hexvane.aetherhaven.territory.TownExpansionMapThumbnails;
import com.hexvane.aetherhaven.territory.TownExpansionMapThumbnails.CellOverlay;
import com.hexvane.aetherhaven.territory.TownExpansionMapThumbnails.ThumbnailPrep;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class TownExpansionPage extends AetherhavenInteractiveCustomUIPage<TownExpansionPage.PageData> {
    private static final int GRID = 7;
    private static final int CHUNK_GROUP = TownTerritoryClaims.CLAIM_BLOCK_CHUNK_SIZE;
    private static final String GRID_ROWS = "#MapGridRows";

    private final Ref<ChunkStore> managementBlockRef;
    @Nonnull
    private final Vector3i managementBlockPos;
    @Nonnull
    private final UUID townUuid;

    private boolean templateAppended;
    /** Min-chunk X of 2×2 block shown in map cell (0,0). */
    private int viewGridOriginChunkX;
    /** Min-chunk Z of 2×2 block shown in map cell (0,0). */
    private int viewGridOriginChunkZ;
    private int selectedChunkX = Integer.MIN_VALUE;
    private int selectedChunkZ = Integer.MIN_VALUE;
    @Nullable
    private String lastErrKey;
    private int mapColorGeneration;
    private final AtomicInteger pendingMapLoads = new AtomicInteger(0);
    @Nullable
    private List<TownRecord> cachedAllTowns;
    @Nonnull
    private final UUID viewerAssetNamespace;

    private boolean viewGridOriginInitialized;

    public TownExpansionPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<ChunkStore> managementBlockRef,
        @Nonnull Vector3i managementBlockPos,
        @Nonnull UUID townUuid,
        int initialViewChunkX,
        int initialViewChunkZ
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.managementBlockRef = managementBlockRef;
        this.managementBlockPos = new Vector3i(managementBlockPos);
        this.townUuid = townUuid;
        this.viewGridOriginChunkX = initialViewChunkX;
        this.viewGridOriginChunkZ = initialViewChunkZ;
        UUID viewer = playerRef.getUuid();
        this.viewerAssetNamespace = viewer != null ? viewer : townUuid;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/TownExpansionPage.ui");
            AetherhavenUiLocalization.applyTownExpansionPage(commandBuilder);
            bindStaticEvents(eventBuilder);
            templateAppended = true;
        }
        applyDynamic(ref, store, commandBuilder, eventBuilder);
    }

    private void applyDynamic(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            return;
        }
        TownTerritoryClaims.migrateIfNeeded(town);
        if (!viewGridOriginInitialized) {
            viewGridOriginChunkX = TownTerritoryClaims.expansionMapGridOriginX(town);
            viewGridOriginChunkZ = TownTerritoryClaims.expansionMapGridOriginZ(town);
            viewGridOriginInitialized = true;
        }
        var cfg = plugin.getConfig().get();
        long cost = TownTerritoryClaims.nextClaimBlockCostGold(town, cfg);
        commandBuilder.set(
            "#ExpansionCostLabel.TextSpans",
            Message.translation("aetherhaven_town.aetherhaven.ui.expansion.claimCost").param("cost", Long.toString(cost))
        );
        boolean canClaim =
            selectedChunkX != Integer.MIN_VALUE
                && TownTerritoryClaims.canClaimBlock(town, selectedChunkX, selectedChunkZ, tm.allTowns(), cfg);
        boolean canSell =
            selectedChunkX != Integer.MIN_VALUE
                && TownTerritoryClaims.canSellClaimBlock(town, selectedChunkX, selectedChunkZ);
        boolean atLimit = TownTerritoryClaims.expansionClaimLimitReached(town, cfg);
        commandBuilder.set("#ExpansionClaimButton.Disabled", !canClaim || atLimit);
        commandBuilder.set("#ExpansionSellButton.Disabled", !canSell);
        if (canSell) {
            long refund = TownTerritoryClaims.sellClaimBlockRefundGold(town, cfg);
            commandBuilder.set("#ExpansionSellRefundLabel.Visible", true);
            commandBuilder.set(
                "#ExpansionSellRefundLabel.TextSpans",
                Message.translation("aetherhaven_town.aetherhaven.ui.expansion.sellRefund")
                    .param("refund", Long.toString(refund))
            );
        } else {
            commandBuilder.set("#ExpansionSellRefundLabel.Visible", false);
        }
        if (lastErrKey != null) {
            commandBuilder.set("#ExpansionErr.Visible", true);
            commandBuilder.set("#ExpansionErr.TextSpans", Message.translation(lastErrKey));
        } else {
            commandBuilder.set("#ExpansionErr.Visible", false);
        }
        boolean pushedMapAssets = buildGrid(world, tm, town, cfg, commandBuilder, eventBuilder);
        scheduleMapTileLoads(world, town, tm);
        if (pushedMapAssets) {
            world.execute(
                () -> {
                    Ref<EntityStore> liveRef = playerRef.getReference();
                    if (liveRef == null || !liveRef.isValid()) {
                        return;
                    }
                    refreshDynamic(liveRef, liveRef.getStore());
                }
            );
        }
    }

    /** Partial refresh only. {@link #rebuild()} clears the page tree and breaks selectors. */
    private void refreshDynamic(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (isDismissed()) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getPageManager().getCustomPage() != this) {
            return;
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        applyDynamic(ref, store, commandBuilder, eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void scheduleMapTileLoads(@Nonnull World world, @Nonnull TownRecord town, @Nonnull TownManager tm) {
        WorldMapManager mapManager = world.getWorldMapManager();
        int generation = ++mapColorGeneration;
        pendingMapLoads.set(0);
        List<TownRecord> allTowns = tm.allTowns();
        cachedAllTowns = allTowns;
        int queued = 0;
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int anchorX = cellAnchorChunkX(col, town);
                int anchorZ = cellAnchorChunkZ(row, town);
                for (int dx = 0; dx < CHUNK_GROUP; dx++) {
                    for (int dz = 0; dz < CHUNK_GROUP; dz++) {
                        int chunkX = anchorX + dx;
                        int chunkZ = anchorZ + dz;
                        if (mapManager.getImageIfInMemory(chunkX, chunkZ) != null) {
                            continue;
                        }
                        queued++;
                    }
                }
            }
        }
        if (queued == 0) {
            return;
        }
        pendingMapLoads.set(queued);
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int anchorX = cellAnchorChunkX(col, town);
                int anchorZ = cellAnchorChunkZ(row, town);
                for (int dx = 0; dx < CHUNK_GROUP; dx++) {
                    for (int dz = 0; dz < CHUNK_GROUP; dz++) {
                        int chunkX = anchorX + dx;
                        int chunkZ = anchorZ + dz;
                        if (mapManager.getImageIfInMemory(chunkX, chunkZ) != null) {
                            continue;
                        }
                        mapManager.getImageAsync(chunkX, chunkZ).whenComplete((ignored, err) -> {
                            if (generation != mapColorGeneration) {
                                return;
                            }
                            if (pendingMapLoads.decrementAndGet() == 0) {
                                world.execute(
                                    () -> {
                                        Ref<EntityStore> liveRef = playerRef.getReference();
                                        if (liveRef == null || !liveRef.isValid()) {
                                            return;
                                        }
                                        refreshDynamic(liveRef, liveRef.getStore());
                                    });
                            }
                        });
                    }
                }
            }
        }
    }

    private int cellAnchorChunkX(int col, @Nonnull TownRecord town) {
        return viewGridOriginChunkX + col * CHUNK_GROUP;
    }

    private int cellAnchorChunkZ(int row, @Nonnull TownRecord town) {
        return viewGridOriginChunkZ + row * CHUNK_GROUP;
    }

    private boolean buildGrid(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        WorldMapManager mapManager = world.getWorldMapManager();
        List<TownRecord> allTowns = cachedAllTowns != null ? cachedAllTowns : tm.allTowns();
        commandBuilder.clear(GRID_ROWS);
        String townColor = TownPortalTravelColor.resolveHex(town);
        List<CommonAsset> assetsToPush = new ArrayList<>();
        for (int row = 0; row < GRID; row++) {
            commandBuilder.append(GRID_ROWS, "Aetherhaven/TownExpansionMapRow.ui");
            String rowPath = GRID_ROWS + "[" + row + "]";
            String cellsPath = rowPath + " #MapGridCells";
            for (int col = 0; col < GRID; col++) {
                int anchorX = cellAnchorChunkX(col, town);
                int anchorZ = cellAnchorChunkZ(row, town);
                commandBuilder.append(cellsPath, "Aetherhaven/TownExpansionChunkCell.ui");
                String cellPath = cellsPath + "[" + col + "]";
                String btn = cellPath + " #CellButton";
                String thumb = cellPath + " #CellButton #MapThumb";
                MapImage nw = mapManager.getImageIfInMemory(anchorX, anchorZ);
                MapImage ne = mapManager.getImageIfInMemory(anchorX + 1, anchorZ);
                MapImage sw = mapManager.getImageIfInMemory(anchorX, anchorZ + 1);
                MapImage se = mapManager.getImageIfInMemory(anchorX + 1, anchorZ + 1);
                boolean selected = anchorX == selectedChunkX && anchorZ == selectedChunkZ;
                CellOverlay overlay = overlayForBlock(town, allTowns, anchorX, anchorZ, selected, cfg);
                String overlayHex = overlayHex(townColor, overlay);
                ThumbnailPrep prep =
                    TownExpansionMapThumbnails.prepareBlockThumbnail(
                        packId,
                        viewerAssetNamespace,
                        anchorX,
                        anchorZ,
                        nw,
                        ne,
                        sw,
                        se,
                        overlay,
                        overlayHex
                    );
                if (prep.pushToClient() != null) {
                    assetsToPush.add(prep.pushToClient());
                }
                commandBuilder.set(thumb + ".AssetPath", prep.assetPath());
                commandBuilder.set(thumb + ".Visible", true);
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    btn,
                    new EventData()
                        .append("Action", "SelectChunk")
                        .append("ChunkX", Integer.toString(anchorX))
                        .append("ChunkZ", Integer.toString(anchorZ)),
                    false
                );
            }
        }
        if (!assetsToPush.isEmpty()) {
            RuntimeCommonIconBroadcast.invalidateRequiredAssetsCache();
            TownExpansionAssetDelivery.pushInGame(playerRef, assetsToPush);
            return true;
        }
        return false;
    }

    @Nonnull
    private static CellOverlay overlayForBlock(
        @Nonnull TownRecord town,
        @Nonnull List<TownRecord> allTowns,
        int anchorChunkX,
        int anchorChunkZ,
        boolean selected,
        @Nonnull com.hexvane.aetherhaven.config.AetherhavenPluginConfig cfg
    ) {
        if (selected) {
            return CellOverlay.SELECTED;
        }
        if (blockFullyOwned(town, anchorChunkX, anchorChunkZ)) {
            return CellOverlay.OWNED;
        }
        for (int dx = 0; dx < CHUNK_GROUP; dx++) {
            for (int dz = 0; dz < CHUNK_GROUP; dz++) {
                TownRecord other =
                    TownTerritoryClaims.findTownOwningChunk(
                        allTowns,
                        town.getWorldName(),
                        anchorChunkX + dx,
                        anchorChunkZ + dz,
                        town.getTownId()
                    );
                if (other != null) {
                    return CellOverlay.OTHER_TOWN;
                }
            }
        }
        if (TownTerritoryClaims.canClaimBlock(town, anchorChunkX, anchorChunkZ, allTowns, cfg)) {
            return CellOverlay.CAN_CLAIM;
        }
        return CellOverlay.NONE;
    }

    private static boolean blockFullyOwned(@Nonnull TownRecord town, int anchorChunkX, int anchorChunkZ) {
        for (int dx = 0; dx < CHUNK_GROUP; dx++) {
            for (int dz = 0; dz < CHUNK_GROUP; dz++) {
                if (!TownTerritoryClaims.contains(town, anchorChunkX + dx, anchorChunkZ + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nonnull
    private static String overlayHex(@Nonnull String townColor, @Nonnull CellOverlay overlay) {
        return switch (overlay) {
            case OWNED -> townColor;
            case OTHER_TOWN -> "#8B4040";
            case CAN_CLAIM -> "#9AD45D";
            case SELECTED -> "#FFFFFF";
            case NONE -> "#000000";
        };
    }

    private void bindStaticEvents(@Nonnull UIEventBuilder eventBuilder) {
        bindPan(eventBuilder, "#BtnPanNorth", "PanNorth");
        bindPan(eventBuilder, "#BtnPanSouth", "PanSouth");
        bindPan(eventBuilder, "#BtnPanWest", "PanWest");
        bindPan(eventBuilder, "#BtnPanEast", "PanEast");
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ExpansionBackButton",
            new EventData().append("Action", "Back"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ExpansionClaimButton",
            new EventData().append("Action", "Claim"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ExpansionSellButton",
            new EventData().append("Action", "Sell"),
            false
        );
    }

    private static void bindPan(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            selector,
            new EventData().append("Action", action),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        String action = data.action.trim();
        if (action.equalsIgnoreCase("Back")) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager()
                    .openCustomPage(
                        ref,
                        store,
                        new PlotConstructionPage(playerRef, managementBlockRef, managementBlockPos, true, 0, false)
                    );
            }
            return;
        }
        if (action.equalsIgnoreCase("PanNorth")) {
            viewGridOriginChunkZ -= CHUNK_GROUP;
            lastErrKey = null;
            refreshDynamic(ref, store);
            return;
        }
        if (action.equalsIgnoreCase("PanSouth")) {
            viewGridOriginChunkZ += CHUNK_GROUP;
            lastErrKey = null;
            refreshDynamic(ref, store);
            return;
        }
        if (action.equalsIgnoreCase("PanWest")) {
            viewGridOriginChunkX -= CHUNK_GROUP;
            lastErrKey = null;
            refreshDynamic(ref, store);
            return;
        }
        if (action.equalsIgnoreCase("PanEast")) {
            viewGridOriginChunkX += CHUNK_GROUP;
            lastErrKey = null;
            refreshDynamic(ref, store);
            return;
        }
        if (action.equalsIgnoreCase("SelectChunk")) {
            int chunkX = parseChunkCoord(data.chunkX);
            int chunkZ = parseChunkCoord(data.chunkZ);
            if (chunkX != Integer.MIN_VALUE && chunkZ != Integer.MIN_VALUE) {
                selectedChunkX = chunkX;
                selectedChunkZ = chunkZ;
                lastErrKey = null;
                refreshDynamic(ref, store);
            }
            return;
        }
        if (action.equalsIgnoreCase("Claim")) {
            if (selectedChunkX == Integer.MIN_VALUE) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.getTown(townUuid);
            if (town == null) {
                return;
            }
            UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null) {
                return;
            }
            String errKey =
                TownExpansionClaimService.tryClaimChunk(
                    world, plugin, town, playerUuid, ref, store, selectedChunkX, selectedChunkZ
                );
            if (errKey != null) {
                lastErrKey = errKey;
                refreshDynamic(ref, store);
                return;
            }
            selectedChunkX = Integer.MIN_VALUE;
            selectedChunkZ = Integer.MIN_VALUE;
            lastErrKey = null;
            playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.ui.expansion.claimSuccess"));
            refreshDynamic(ref, store);
            return;
        }
        if (action.equalsIgnoreCase("Sell")) {
            if (selectedChunkX == Integer.MIN_VALUE) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            World world = store.getExternalData().getWorld();
            if (plugin == null) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.getTown(townUuid);
            if (town == null) {
                return;
            }
            UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null) {
                return;
            }
            String errKey =
                TownExpansionClaimService.trySellChunk(
                    world, plugin, town, playerUuid, selectedChunkX, selectedChunkZ
                );
            if (errKey != null) {
                lastErrKey = errKey;
                refreshDynamic(ref, store);
                return;
            }
            selectedChunkX = Integer.MIN_VALUE;
            selectedChunkZ = Integer.MIN_VALUE;
            lastErrKey = null;
            playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.ui.expansion.sellSuccess"));
            refreshDynamic(ref, store);
        }
    }

    private static int parseChunkCoord(@Nullable String raw) {
        if (raw == null) {
            return Integer.MIN_VALUE;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("ChunkX", Codec.STRING), (d, v) -> d.chunkX = v, d -> d.chunkX)
            .add()
            .append(new KeyedCodec<>("ChunkZ", Codec.STRING), (d, v) -> d.chunkZ = v, d -> d.chunkZ)
            .add()
            .build();

        private String action;
        @Nullable
        private String chunkX;
        @Nullable
        private String chunkZ;
    }
}
