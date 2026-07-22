package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.farming.SprinklerActivateInteraction;
import com.hexvane.aetherhaven.farming.SprinklerWateringService;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtCraftSystem;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtInventoryNormalizeSystem;
import com.hexvane.aetherhaven.gaiadraught.GaiasDraughtConsumeInteraction;
import com.hexvane.aetherhaven.gaiadraught.PendingCraftRecipeUnlockTickSystem;
import com.hexvane.aetherhaven.geode.GeodeLootFiles;
import com.hexvane.aetherhaven.growthserum.GrowthSerumUseInteraction;
import com.hexvane.aetherhaven.heartberry.HeartberryUseInteraction;
import com.hexvane.aetherhaven.huntingknife.HuntingKnifeBonusDropSystem;
import com.hexvane.aetherhaven.plot.SprinklerBlock;
import com.hexvane.aetherhaven.purification.PurificationPowderPlayerRemoveSystem;
import com.hexvane.aetherhaven.purification.PurificationPowderUseInteraction;
import com.hexvane.aetherhaven.purification.PurificationPowderVisualizationSystem;
import com.hexvane.aetherhaven.purification.PurificationPreviewEntity;
import com.hexvane.aetherhaven.rootremover.RootRemoverUseInteraction;
import com.hexvane.aetherhaven.ui.GeodeOpenPage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

public final class ReputationUnlocksBootstrap {
    private ReputationUnlocksBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenSprinklerActivate",
                SprinklerActivateInteraction.class,
                SprinklerActivateInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPurificationPowderUse",
                PurificationPowderUseInteraction.class,
                PurificationPowderUseInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenRootRemoverUse",
                RootRemoverUseInteraction.class,
                RootRemoverUseInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenGrowthSerumUse",
                GrowthSerumUseInteraction.class,
                GrowthSerumUseInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenHeartberryUse",
                HeartberryUseInteraction.class,
                HeartberryUseInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenGaiasDraughtConsume",
                GaiasDraughtConsumeInteraction.class,
                GaiasDraughtConsumeInteraction.CODEC
            );
        OpenCustomUIInteraction.registerSimple(
            core,
            GeodeOpenPage.class,
            AetherhavenConstants.PAGE_GEODE_ANVIL,
            playerRef ->
                AetherhavenFeatures.isLoaded(AetherhavenPluginIds.REPUTATION_UNLOCKS)
                    ? new GeodeOpenPage(playerRef, false)
                    : null
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        GeodeLootFiles.ensureDefaultLootFile(core);
        SprinklerBlock.register(plugin.getChunkStoreRegistry());
        plugin
            .getEntityRegistry()
            .registerEntity(
                "AetherhavenPurificationPreview",
                PurificationPreviewEntity.class,
                world -> {
                    PurificationPreviewEntity e = new PurificationPreviewEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                PurificationPreviewEntity.CODEC
            );
        plugin.getEntityStoreRegistry().registerSystem(new PurificationPowderVisualizationSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PurificationPowderPlayerRemoveSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HuntingKnifeBonusDropSystem());
        plugin.getEntityStoreRegistry().registerSystem(new GaiaDraughtCraftSystem());
        plugin.getEntityStoreRegistry().registerSystem(new GaiaDraughtInventoryNormalizeSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PendingCraftRecipeUnlockTickSystem(core));
    }

    @Nonnull
    public static GameTimeTickListener createSprinklerGameTimeListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                SprinklerWateringService.scheduleFromHub(world, store, core);
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
                    SprinklerWateringService.catchUpAfterTimeJump(world, store, core, from, to);
                }
                SprinklerWateringService.scheduleFromHub(world, store, core);
            }
        };
    }
}
