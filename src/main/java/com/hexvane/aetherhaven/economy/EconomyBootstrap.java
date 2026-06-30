package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenTaxCommand;
import com.hexvane.aetherhaven.placement.PlotConstructionBlockResolver;
import com.hexvane.aetherhaven.plot.ShopSafeBlock;
import com.hexvane.aetherhaven.plot.TreasuryBlock;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hexvane.aetherhaven.shop.ShopSafeUseInteraction;
import com.hexvane.aetherhaven.ui.TreasuryPage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

public final class EconomyBootstrap {
    private EconomyBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenShopSafeUse", ShopSafeUseInteraction.class, ShopSafeUseInteraction.CODEC);
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            TreasuryPage.class,
            AetherhavenConstants.PAGE_TREASURY,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.ECONOMY)) {
                    return null;
                }
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, TreasuryBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                return new TreasuryPage(playerRef, target.blockRef());
            }
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        TreasuryBlock.register(plugin.getChunkStoreRegistry());
        ShopSafeBlock.register(plugin.getChunkStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new TreasuryBreakBlockSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new ShopSafeBreakBlockSystem(core));
        core.registerAetherhavenSubcommand(new AetherhavenTaxCommand());
    }

    @Nonnull
    public static GameTimeTickListener createEconomyGameTimeListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                TownEconomyTimeService.onGameTimeFromHub(world, core, wtr, store);
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
                    TownEconomyTimeService.onGameTimeFromHub(world, core, wtr, store);
                }
            }
        };
    }
}
