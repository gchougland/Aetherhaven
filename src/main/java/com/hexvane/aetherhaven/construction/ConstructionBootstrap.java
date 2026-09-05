package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistSystem;
import com.hexvane.aetherhaven.charter.CharterPlaceEventSystem;
import com.hexvane.aetherhaven.construction.assembly.AssemblyMarkerSpawner;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffAssemblyChannelComponent;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffFrontierTracerComponent;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffFrontierTracerInteraction;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffFrontierTracerTickSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffHotbarManaHudSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffManaRegenSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffMarkerCleanupSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffMarkerEntity;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffPreviewPlayerComponent;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffPreviewPlayerRemoveSystem;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffSecondaryInteraction;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyPreviewSystem;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyTickSystem;
import com.hexvane.aetherhaven.loot.PlayerBlockBreakBonusSystem;
import com.hexvane.aetherhaven.monument.FounderMonumentBreakSystem;
import com.hexvane.aetherhaven.monument.FounderMonumentPlaceSystem;
import com.hexvane.aetherhaven.monument.FounderMonumentStatueRestoreSystem;
import com.hexvane.aetherhaven.placement.PlotBlockPreviewCleanupSystem;
import com.hexvane.aetherhaven.placement.PlotConstructionBlockResolver;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.ManagementBreakBlockSystem;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hexvane.aetherhaven.scaffold.ScaffoldColumnCascadeBreakSystem;
import com.hexvane.aetherhaven.scaffold.ScaffoldStackPlaceInteraction;
import com.hexvane.aetherhaven.scaffold.ScaffoldUseExtendInteraction;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberBlockAccess;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.CharterAmendmentsPage;
import com.hexvane.aetherhaven.ui.CharterTownPage;
import com.hexvane.aetherhaven.ui.PlotConstructionPage;
import com.hexvane.aetherhaven.ui.PlotCraftingPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

public final class ConstructionBootstrap {
    private ConstructionBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenBuildingStaffSecondary",
                BuildingStaffSecondaryInteraction.class,
                BuildingStaffSecondaryInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenBuildingStaffFrontierTracer",
                BuildingStaffFrontierTracerInteraction.class,
                BuildingStaffFrontierTracerInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenScaffoldStackPlace",
                ScaffoldStackPlaceInteraction.class,
                ScaffoldStackPlaceInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenScaffoldUseExtend",
                ScaffoldUseExtendInteraction.class,
                ScaffoldUseExtendInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "OpenTownPlanningBench",
                OpenTownPlanningBenchInteraction.class,
                OpenTownPlanningBenchInteraction.CODEC
            );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            PlotConstructionPage.class,
            AetherhavenConstants.PAGE_PLOT_CONSTRUCTION,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.CONSTRUCTION)) {
                    return null;
                }
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, PlotSignBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                Vector3i blockWorld = target.blockWorldPos();
                Store<ChunkStore> chunkStore = blockRef.getStore();
                PlotSignBlock plotSign = chunkStore.getComponent(blockRef, PlotSignBlock.getComponentType());
                if (plotSign == null || plotSign.getPlotId().isBlank()) {
                    return null;
                }
                UUID playerUuid = playerRef.getUuid();
                if (playerUuid == null) {
                    return null;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin == null) {
                    return null;
                }
                UUID plotUuid;
                try {
                    plotUuid = UUID.fromString(plotSign.getPlotId().trim());
                } catch (IllegalArgumentException e) {
                    return null;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = tm.findTownOwningPlot(plotUuid);
                if (TownMemberBlockAccess.denyIfNotMember(playerRef, town, playerUuid)) {
                    return null;
                }
                return new PlotConstructionPage(playerRef, blockRef, blockWorld, false);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            PlotConstructionPage.class,
            AetherhavenConstants.PAGE_PLOT_MANAGEMENT,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.CONSTRUCTION)) {
                    return null;
                }
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, ManagementBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                Vector3i blockWorld = target.blockWorldPos();
                Store<ChunkStore> chunkStore = blockRef.getStore();
                ManagementBlock mb = chunkStore.getComponent(blockRef, ManagementBlock.getComponentType());
                if (mb == null || mb.getTownId().isBlank()) {
                    return null;
                }
                UUID playerUuid = playerRef.getUuid();
                if (playerUuid == null) {
                    return null;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin == null) {
                    return null;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                if (TownMemberBlockAccess.denyIfNotMember(playerRef, tm, mb.getTownId(), playerUuid)) {
                    return null;
                }
                return new PlotConstructionPage(playerRef, blockRef, blockWorld, true);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            CharterTownPage.class,
            AetherhavenConstants.PAGE_CHARTER_TOWN,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.CONSTRUCTION)) {
                    return null;
                }
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
                if (chunk == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, targetBlock.x, targetBlock.y, targetBlock.z);
                if (blockRef == null || blockRef.getStore().getComponent(blockRef, CharterBlock.getComponentType()) == null) {
                    return null;
                }
                UUID playerUuid = playerRef.getUuid();
                if (playerUuid == null) {
                    return null;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin == null) {
                    return null;
                }
                CharterBlock ch = blockRef.getStore().getComponent(blockRef, CharterBlock.getComponentType());
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord linked = ch != null ? TownMemberBlockAccess.townFromId(tm, ch.getTownId()) : null;
                boolean unowned = linked != null && !linked.hasOwner();
                if (!unowned
                    && ch != null
                    && TownMemberBlockAccess.denyIfNotMember(playerRef, tm, ch.getTownId(), playerUuid)) {
                    return null;
                }
                return new CharterTownPage(playerRef, blockRef);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            CharterAmendmentsPage.class,
            AetherhavenConstants.PAGE_CHARTER_AMENDMENTS,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.CONSTRUCTION)) {
                    return null;
                }
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                BlockType bt = ChunkSectionBlockUtil.blockType(world, targetBlock.x, targetBlock.y, targetBlock.z);
                if (bt == null || bt == BlockType.EMPTY
                    || !AetherhavenConstants.ITEM_CHARTER_AMENDMENTS_TABLE.equals(bt.getId())) {
                    return null;
                }
                UUID playerUuid = playerRef.getUuid();
                if (playerUuid == null) {
                    return null;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin == null) {
                    return null;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = tm.findTownContainingBlock(world.getName(), targetBlock.x, targetBlock.z);
                if (TownMemberBlockAccess.denyIfNotMember(playerRef, town, playerUuid)) {
                    return null;
                }
                return new CharterAmendmentsPage(playerRef);
            }
        );
        OpenCustomUIInteraction.registerSimple(
            core,
            PlotCraftingPage.class,
            AetherhavenConstants.PAGE_PLOT_CRAFTING_BENCH,
            playerRef ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.CONSTRUCTION) ? new PlotCraftingPage(playerRef) : null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        PlotSignBlock.register(plugin.getChunkStoreRegistry());
        ManagementBlock.register(plugin.getChunkStoreRegistry());
        CharterBlock.register(plugin.getChunkStoreRegistry());
        FounderMonumentBlock.register(plugin.getChunkStoreRegistry());
        // FounderMonumentStatueSkin + BuilderConstructionAssistState: parent (AetherhavenSharedEntityComponents).
        BuildingStaffAssemblyChannelComponent.register(plugin.getEntityStoreRegistry());
        BuildingStaffFrontierTracerComponent.register(plugin.getEntityStoreRegistry());
        BuildingStaffPreviewPlayerComponent.register(plugin.getEntityStoreRegistry());
        plugin
            .getEntityRegistry()
            .registerEntity(
                "AetherhavenBuildingStaffMarker",
                BuildingStaffMarkerEntity.class,
                world -> {
                    BuildingStaffMarkerEntity e = new BuildingStaffMarkerEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                BuildingStaffMarkerEntity.CODEC
            );
        plugin.getEntityStoreRegistry().registerSystem(new PlotAssemblyTickSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PlotAssemblyPreviewSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new BuildingStaffMarkerCleanupSystem());
        plugin.getEntityStoreRegistry().registerSystem(new BuildingStaffFrontierTracerTickSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new BuildingStaffManaRegenSystem());
        plugin.getEntityStoreRegistry().registerSystem(new BuildingStaffHotbarManaHudSystem.SlotChangeHandler());
        plugin.getEntityStoreRegistry().registerSystem(new BuilderConstructionAssistSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new CharterPlaceEventSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new ManagementBreakBlockSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PlotBlockPreviewCleanupSystem());
        plugin.getEntityStoreRegistry().registerSystem(new ScaffoldColumnCascadeBreakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PlayerBlockBreakBonusSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new FounderMonumentPlaceSystem(core));
        plugin.getChunkStoreRegistry().registerSystem(new FounderMonumentStatueRestoreSystem.BlockLoad());
        plugin.getEntityStoreRegistry().registerSystem(new FounderMonumentBreakSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new BuildingStaffPreviewPlayerRemoveSystem());

        plugin
            .getEventRegistry()
            .registerGlobal(
                StartWorldEvent.class,
                event -> {
                    World world = event.getWorld();
                    world.execute(() -> AssemblyMarkerSpawner.purgeAllInWorld(world));
                }
            );
        plugin
            .getEventRegistry()
            .registerGlobal(BootEvent.class, event -> FounderMonumentStatueRestoreSystem.restorePendingAfterBoot());
        plugin
            .getEventRegistry()
            .registerGlobal(
                PlayerReadyEvent.class,
                event -> {
                    if (event.getPlayer() == null || event.getPlayer().getWorld() == null) {
                        return;
                    }
                    FounderMonumentStatueRestoreSystem.scanLoadedPedestals(event.getPlayer().getWorld());
                }
            );
    }

    @Nonnull
    public static GameTimeTickListener createPlotAssemblyGameTimeListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                PlotAssemblyService.schedulePassiveFromHub(world, core);
            }

            @Override
            public void onGameTimeDiscontinuity(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                @Nonnull Instant from,
                @Nonnull Instant to,
                @Nonnull LocalDateTime toDateTime,
                boolean backward
            ) {
                PlotAssemblyService.schedulePassiveFromHub(world, core);
            }
        };
    }
}
