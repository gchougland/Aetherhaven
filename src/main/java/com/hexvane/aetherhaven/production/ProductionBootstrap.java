package com.hexvane.aetherhaven.production;

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
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            ProductionStoragePage.class,
            AetherhavenConstants.PAGE_PRODUCTION_STORAGE,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PRODUCTION)) {
                    return null;
                }
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                var store = ref.getStore();
                World world = store.getExternalData().getWorld();
                BlockType bt = world.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
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
                    if (!ProductionCatalog.isProductionWorkplaceConstruction(gameplayCid)) {
                        continue;
                    }
                    if (pi.containsWorldBlock(targetBlock.x, targetBlock.y, targetBlock.z)) {
                        plot = pi;
                        break;
                    }
                }
                if (plot == null) {
                    return null;
                }
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
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            ProductionStorageUnlocksPage.class,
            AetherhavenConstants.PAGE_PRODUCTION_STORAGE_UNLOCKS,
            (ref, componentAccessor, playerRef, context) -> null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new ProductionTickSystem(core));
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
