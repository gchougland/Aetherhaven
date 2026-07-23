package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.feast.FeastService;
import com.hexvane.aetherhaven.inn.InnBellUseInteraction;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hexvane.aetherhaven.tourist.TouristAutonomySystem;
import com.hexvane.aetherhaven.tourist.TouristPortalBlock;
import com.hexvane.aetherhaven.tourist.TouristPortalPlaceEventSystem;
import com.hexvane.aetherhaven.tourist.TouristPortalPlayerStandSystem;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.tourist.TouristPortalTravelPlayerInitSystem;
import com.hexvane.aetherhaven.tourist.TouristPortalTravelPlayerState;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberBlockAccess;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.FeastPage;
import com.hexvane.aetherhaven.ui.ShopSpotConfigPage;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class CommerceBootstrap {
    private CommerceBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenShopSpotUse", ShopSpotUseInteraction.class, ShopSpotUseInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenShopSpotSecondary", ShopSpotSecondaryInteraction.class, ShopSpotSecondaryInteraction.CODEC);
        core
            .getCodecRegistry(Interaction.CODEC)
            .register("AetherhavenInnBellUse", InnBellUseInteraction.class, InnBellUseInteraction.CODEC);
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            FeastPage.class,
            AetherhavenConstants.PAGE_FEASTS,
            (ref, componentAccessor, playerRef, context) -> {
                if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.COMMERCE)) {
                    return null;
                }
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                BlockType bt = world.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
                if (bt == null || bt == BlockType.EMPTY
                    || !AetherhavenConstants.ITEM_BANQUET_TABLE.equals(bt.getId())) {
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
                return new FeastPage(playerRef, targetBlock.x, targetBlock.y, targetBlock.z);
            }
        );
        OpenCustomUIInteraction.registerSimple(
            core,
            ShopSpotConfigPage.class,
            AetherhavenConstants.PAGE_SHOP_SPOT_CONFIG,
            playerRef ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.COMMERCE) ? new ShopSpotConfigPage(playerRef) : null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        ShopLootFiles.ensureDefaultLootTables(core);
        ShopPriceFiles.ensureDefaultPricesFile(core);
        core.reloadShopPriceCatalog();
        ShopSpotBlock.register(plugin.getChunkStoreRegistry());
        TouristPortalBlock.register(plugin.getChunkStoreRegistry());
        ShopSpotPlayerComponent.register(plugin.getEntityStoreRegistry());
        TouristAutonomyState.register(plugin.getEntityStoreRegistry());
        TouristPortalTravelPlayerState.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new TouristPortalTravelPlayerInitSystem());
        plugin.getEntityStoreRegistry().registerSystem(new TouristPortalPlayerStandSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new TouristAutonomySystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new ShopSpotPlaceEventSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new TouristPortalPlaceEventSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new ShopSpotBreakBlockSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new ShopSpotDisplayTickSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new ShopSpotLookAtSystem(core));
        core.registerShopPriceTooltipPackets();
    }

    @Nonnull
    public static GameTimeTickListener createCommerceGameTimeListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                InnPoolService.scheduleTickFromHub(world, core, wtr);
                TouristPortalTickService.scheduleTickFromHub(world, core, wtr);
                FeastService.pruneExpiredForWorld(world, core, store);
                FeastService.checkGatherTimeoutsForWorld(world, core);
                ShopSpotDailyRerollService.scheduleTickFromHub(world, core, wtr);
                ShopSpotRefreshSystem.onGameMinute(world, store, core, wtr);
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
                TouristPortalTickService.catchUpLeaveAfterTimeJump(world, core, store, wtr);
                if (!backward) {
                    InnPoolService.catchUpAfterTimeJump(world, core, store, wtr, from, to);
                    ShopSpotDailyRerollService.catchUpAfterTimeJump(world, core, store, wtr, from, to);
                }
                InnPoolService.scheduleTickFromHub(world, core, wtr);
                TouristPortalTickService.scheduleTickFromHub(world, core, wtr);
                FeastService.pruneExpiredForWorld(world, core, store);
                FeastService.checkGatherTimeoutsForWorld(world, core);
                ShopSpotDailyRerollService.scheduleTickFromHub(world, core, wtr);
                ShopSpotRefreshSystem.onGameMinute(world, store, core, wtr);
            }
        };
    }
}
