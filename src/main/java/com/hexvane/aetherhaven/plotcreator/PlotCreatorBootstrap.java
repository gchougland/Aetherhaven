package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenPlotCreatorCommand;
import com.hexvane.aetherhaven.placement.PlotPlacementOpenHelper;
import com.hexvane.aetherhaven.placement.PlotPlacementPlayerRemoveSystem;
import com.hexvane.aetherhaven.placement.PlotPlacementSpectatorSyncSystem;
import com.hexvane.aetherhaven.placement.WallPlacementEditHelper;
import com.hexvane.aetherhaven.placement.WallPlacementOpenHelper;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.ui.PlotPlacementPage;
import com.hexvane.aetherhaven.ui.WallPlacementPage;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

public final class PlotCreatorBootstrap {
    private PlotCreatorBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPlotCreatorUse", PlotCreatorUseInteraction.class, PlotCreatorUseInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenPlotCreatorBlock", PlotCreatorBlockInteraction.class, PlotCreatorBlockInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPlotCreatorStepBack",
                PlotCreatorStepBackInteraction.class,
                PlotCreatorStepBackInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPlotCreatorStepForward",
                PlotCreatorStepForwardInteraction.class,
                PlotCreatorStepForwardInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPlotCreatorCancel",
                PlotCreatorCancelInteraction.class,
                PlotCreatorCancelInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenBuildingEditorUse", BuildingEditorUseInteraction.class, BuildingEditorUseInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenBuildingEditorBlock",
                BuildingEditorBlockInteraction.class,
                BuildingEditorBlockInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenBuildingEditorStepBack",
                BuildingEditorStepBackInteraction.class,
                BuildingEditorStepBackInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenBuildingEditorStepForward",
                BuildingEditorStepForwardInteraction.class,
                BuildingEditorStepForwardInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenBuildingEditorCancel",
                BuildingEditorCancelInteraction.class,
                BuildingEditorCancelInteraction.CODEC
            );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            PlotPlacementPage.class,
            AetherhavenConstants.PAGE_PLOT_PLACEMENT,
            (ref, componentAccessor, playerRef, context) ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PLOT_CREATOR)
                    ? PlotPlacementOpenHelper.tryOpen(ref, componentAccessor, playerRef, context)
                    : null
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            WallPlacementPage.class,
            AetherhavenConstants.PAGE_WALL_PLACEMENT,
            (ref, componentAccessor, playerRef, context) ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PLOT_CREATOR)
                    ? WallPlacementOpenHelper.tryOpenBuild(ref, componentAccessor, playerRef, context)
                    : null
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            WallPlacementPage.class,
            AetherhavenConstants.PAGE_WALL_EDIT,
            (ref, componentAccessor, playerRef, context) ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PLOT_CREATOR)
                    ? WallPlacementEditHelper.tryOpenEdit(ref, componentAccessor, playerRef, context)
                    : null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        plugin
            .getEntityRegistry()
            .registerEntity(
                "AetherhavenPlotCreatorSpotMarker",
                PlotCreatorSpotMarkerEntity.class,
                world -> {
                    PlotCreatorSpotMarkerEntity e = new PlotCreatorSpotMarkerEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                PlotCreatorSpotMarkerEntity.CODEC
            );
        plugin.getEntityStoreRegistry().registerSystem(new PlotCreatorBreakAllowSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PlotCreatorPreviewSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PlotPlacementSpectatorSyncSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PlotPlacementPlayerRemoveSystem());
        core.registerAetherhavenSubcommand(new AetherhavenPlotCreatorCommand());
        plugin
            .getEventRegistry()
            .registerGlobal(
                StartWorldEvent.class,
                event -> {
                    World world = event.getWorld();
                    world.execute(() -> PlotCreatorSpotMarkerSpawner.purgeAllInWorld(world));
                }
            );
    }
}
