package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberBlockAccess;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.ui.ProductionStoragePage;
import com.hexvane.aetherhaven.ui.ProductionStorageUnlocksPage;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.Nonnull;
import com.hypixel.hytale.component.Store;

public final class ProductionBootstrap {
    private ProductionBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register("AetherhavenBarnPet",BarnPetInteraction.class,BarnPetInteraction.CODEC);
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            ProductionStoragePage.class,
            AetherhavenConstants.PAGE_PRODUCTION_STORAGE,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                var store = ref.getStore();
                World world = store.getExternalData().getWorld();
                BlockType bt = ChunkSectionBlockUtil.blockType(world, targetBlock.x, targetBlock.y, targetBlock.z);
                if (bt == null
                    || bt == BlockType.EMPTY
                    || !AetherhavenConstants.BLOCK_PRODUCTION_STORAGE.equals(bt.getId())) {
                    return null;
                }
                AetherhavenPlugin p = AetherhavenPlugin.get();
                if (p == null) {
                    return null;
                }
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc == null) {
                    return null;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, p);
                TownRecord town = tm.findTownContainingBlock(world.getName(), targetBlock.x, targetBlock.z);
                if (TownMemberBlockAccess.denyIfNotMember(playerRef, town, uc.getUuid())) {
                    return null;
                }
                PlotInstance plot = null;
                for (PlotInstance pi : town.getPlotInstances()) {
                    if (pi.getState() != PlotInstanceState.COMPLETE) {
                        continue;
                    }
                    String gameplayCid = p.getConstructionCatalog().resolveGameplayConstructionId(pi.getConstructionId());
                    if (com.hexvane.aetherhaven.hunter.HuntService.CONSTRUCTION.equals(gameplayCid)
                        && pi.containsWorldBlock(targetBlock.x, targetBlock.y, targetBlock.z)) {
                        return new com.hexvane.aetherhaven.warehouse.WarehousePage(playerRef,town.getTownId(),pi.getPlotId());
                    }
                    if (!ProductionCatalog.isProductionWorkplaceConstruction(gameplayCid)) {
                        continue;
                    }
                    if (pi.containsWorldBlock(targetBlock.x, targetBlock.y, targetBlock.z)) {
                        plot = pi;
                        break;
                    }
                }
                if (plot == null) {
                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("Place this storage inside a completed production building or Hunter's Lodge."));
                    return null;
                }
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PRODUCTION)) return null;
                if(NativeHerdService.barn(p,plot))return new com.hexvane.aetherhaven.workplace.RancherHerdPage(playerRef,town.getTownId(),plot.getPlotId());
                // A lumbermill that tends Rootstones keeps its stores and its grove on one screen.
                if(town.playerCanManageConstructions(uc.getUuid())&&town.getOrCreatePlotProduction(plot.getPlotId()).usesPhysicalTimber()
                    &&p.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL))
                    return new com.hexvane.aetherhaven.workplace.GrovePage(playerRef,town.getTownId(),plot.getPlotId(),null);
                return new ProductionStoragePage(
                    playerRef,
                    town.getTownId(),
                    plot.getPlotId(),
                    targetBlock.x,
                    targetBlock.y,
                    targetBlock.z
                );
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(core, com.hexvane.aetherhaven.workplace.GrovePage.class, Rootstone.PAGE,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition target = context.getTargetBlock();
                AetherhavenPlugin p = AetherhavenPlugin.get();
                if (target == null || p == null || !AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PRODUCTION)) return null;
                World world = ref.getStore().getExternalData().getWorld();
                BlockType bt = ChunkSectionBlockUtil.blockType(world, target.x, target.y, target.z);
                if (bt == null || !Rootstone.is(bt.getId())) return null;
                TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, p).findTownContainingBlock(world.getName(), target.x, target.z);
                if (town == null) {
                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("A Rootstone only wakes inside your town's borders."));
                    return null;
                }
                if (!town.playerCanManageConstructions(playerRef.getUuid())) {
                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("You need permission to manage this town's buildings to tend its grove."));
                    return null;
                }
                var block = new TimberHarvestPlan.Position(target.x, target.y, target.z);
                PlotInstance mill = com.hexvane.aetherhaven.workplace.GrovePage.millFor(p, town, block);
                if (mill == null) {
                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("Build a lumbermill first. Its Logger tends the Rootstones."));
                    return null;
                }
                return new com.hexvane.aetherhaven.workplace.GrovePage(playerRef, town.getTownId(), mill.getPlotId(), block);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            ProductionStorageUnlocksPage.class,
            AetherhavenConstants.PAGE_PRODUCTION_STORAGE_UNLOCKS,
            (ref, componentAccessor, playerRef, context) -> null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new MiningNodeLegacyBlocks());
        RanchCaptureCompatibility.register(plugin);
        ProductionCargoDisplays.register(plugin.getEntityStoreRegistry(),core);
        plugin.getEntityStoreRegistry().registerSystem(new ProductionTickSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new TimberPlayerEditSystems.Breaking(core));
        plugin.getEntityStoreRegistry().registerSystem(new TimberPlayerEditSystems.Placing(core));
    }

    @Nonnull
    public static GameTimeTickListener createProductionGameTimeListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                ProductionCatchUpService.onSmoothGameMinuteAdvanced(
                    world,
                    store,
                    core,
                    prevEpochMinute,
                    newEpochMinute
                );
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
                ProductionCatchUpService.onGameTimeDiscontinuity(
                    world,
                    store,
                    core,
                    from,
                    to,
                    toDateTime,
                    backward
                );
            }
        };
    }
}
