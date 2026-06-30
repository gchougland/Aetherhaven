package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenNeedsCommand;
import com.hexvane.aetherhaven.command.AetherhavenTownsfolkCommand;
import com.hexvane.aetherhaven.command.AetherhavenVillagerCommand;
import com.hexvane.aetherhaven.guild.VillagerDeathHandlerSystem;
import com.hexvane.aetherhaven.npc.NpcFaceVisualState;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerDataComponent;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerEntity;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerSystems;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hexvane.aetherhaven.rescue.RescueVillagerBreakBlockSystem;
import com.hexvane.aetherhaven.schedule.VillagerScheduleService;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTickState;
import com.hexvane.aetherhaven.town.CitizenDawnRevivalService;
import com.hexvane.aetherhaven.townsfolk.EntityChunkStaleReferenceCleanupSystem;
import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalSystem;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentSystem;
import com.hexvane.aetherhaven.ui.VillagerNeedsOverviewPage;
import com.hexvane.aetherhaven.villager.NpcPersistentModelResyncSystem;
import com.hexvane.aetherhaven.villager.TownVillagerEnvironmentalDamageFilterSystem;
import com.hexvane.aetherhaven.villager.TownVillagerNpcWorldSpawnSanitizeSystems;
import com.hexvane.aetherhaven.villager.VillagerNeedsDecaySystem;
import com.hexvane.aetherhaven.world.WorldSpawnStaleChunkRefCleanupSystem;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.placement.PlotConstructionBlockResolver;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class VillagersBootstrap {
    private VillagersBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            VillagerNeedsOverviewPage.class,
            AetherhavenConstants.PAGE_VILLAGER_NEEDS,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.VILLAGERS)) {
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
                Store<ChunkStore> cs = blockRef.getStore();
                ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
                if (mb == null || mb.getTownId().isBlank()) {
                    return null;
                }
                try {
                    UUID townUuid = UUID.fromString(mb.getTownId().trim());
                    return new VillagerNeedsOverviewPage(playerRef, townUuid);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        PoiMarkerDataComponent.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new TownVillagerNpcWorldSpawnSanitizeSystems.OnAdd());
        plugin.getEntityStoreRegistry().registerSystem(new TownVillagerNpcWorldSpawnSanitizeSystems.EachTick());
        NpcFaceVisualState.register(plugin.getEntityStoreRegistry());
        VillagerAutonomyDebugTag.register(plugin.getEntityStoreRegistry());
        VillagerScheduleTickState.register(plugin.getEntityStoreRegistry());
        plugin
            .getEntityRegistry()
            .registerEntity(
                "AetherhavenPoiMarker",
                PoiMarkerEntity.class,
                world -> {
                    PoiMarkerEntity e = new PoiMarkerEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                PoiMarkerEntity.CODEC
            );
        plugin.getChunkStoreRegistry().registerSystem(new ChunkUnloadMountDisconnectSystem());
        plugin.getChunkStoreRegistry().registerSystem(new EntityChunkStaleReferenceCleanupSystem());
        plugin.getChunkStoreRegistry().registerSystem(new WorldSpawnStaleChunkRefCleanupSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VillagerNeedsDecaySystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new VillagerBlockMountSafetySystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new BlockMountDeathCleanupSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VillagerAutonomySystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new VillagerMoodVisualSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new DoorwaySeparationBypassSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PendingEntityRemovalSystem());
        plugin.getEntityStoreRegistry().registerSystem(new TownsfolkAssignmentSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VillagerDeathHandlerSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new TownVillagerEnvironmentalDamageFilterSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RescueVillagerBreakBlockSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PoiMarkerSystems.EnsurePrefabCopyable());
        plugin.getEntityStoreRegistry().registerSystem(new NpcPersistentModelResyncSystem());
        core.registerAetherhavenSubcommand(new AetherhavenNeedsCommand());
        core.registerAetherhavenSubcommand(new AetherhavenVillagerCommand());
        core.registerAetherhavenSubcommand(new AetherhavenTownsfolkCommand());
    }

    @Nonnull
    public static GameTimeTickListener createVillagerScheduleGameTimeListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                VillagerScheduleService.applyForWorld(world, store, core, false);
                CitizenDawnRevivalService.scheduleTickFromHub(world, core, wtr);
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
                if (!backward) {
                    CitizenDawnRevivalService.catchUpAfterTimeJump(world, core, store, wtr, from, to);
                }
                VillagerScheduleService.applyForWorld(world, store, core, true);
                CitizenDawnRevivalService.scheduleTickFromHub(world, core, wtr);
            }
        };
    }
}
