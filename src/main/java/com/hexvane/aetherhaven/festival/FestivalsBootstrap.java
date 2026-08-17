package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenFestivalCommand;
import com.hexvane.aetherhaven.festival.carnival.CarnivalBalloonComponent;
import com.hexvane.aetherhaven.festival.carnival.CarnivalBalloonDirectorSystem;
import com.hexvane.aetherhaven.festival.carnival.CarnivalBalloonSystem;
import com.hexvane.aetherhaven.festival.carnival.CarnivalDialogueHandlers;
import com.hexvane.aetherhaven.festival.carnival.CarnivalFestivalMechanic;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWhackComponent;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWhackDirectorSystem;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWhackHitSystem;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWhackSystem;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWheelDirectorSystem;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWheelFaceComponent;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWheelSystem;
import com.hexvane.aetherhaven.festival.firework.FireworkLaunchInteraction;
import com.hexvane.aetherhaven.festival.firework.FireworkRocketComponent;
import com.hexvane.aetherhaven.festival.firework.FireworkRocketSystem;
import com.hexvane.aetherhaven.festival.lettuce.FestivalLettuceAbsorbSystem;
import com.hexvane.aetherhaven.festival.lettuce.FestivalLettuceBurstInteraction;
import com.hexvane.aetherhaven.festival.lettuce.FestivalLettuceBurstSystem;
import com.hexvane.aetherhaven.festival.lettuce.FestivalLettuceComponent;
import com.hexvane.aetherhaven.festival.lettuce.FestivalLettuceGrowthSystem;
import com.hexvane.aetherhaven.festival.lettuce.FestivalLettuceInteractSystem;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceDialogueHandlers;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceFestivalMechanic;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceRacerComponent;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceSystem;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveBatComponent;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveBatDirectorSystem;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveDialogueHandlers;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveFestivalMechanic;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveOrbCollectSystem;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveOrbComponent;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveOrbVisibilitySystem;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEvePumpkinBurstInteraction;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEvePumpkinBurstSystem;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEvePumpkinComponent;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEvePumpkinGrowthSystem;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEvePumpkinInteractSystem;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveRaceSystem;
import com.hexvane.aetherhaven.festival.market.MarketDialogueHandlers;
import com.hexvane.aetherhaven.festival.market.MarketFestivalMechanic;
import com.hexvane.aetherhaven.festival.market.MarketJudgeDirectorSystem;
import com.hexvane.aetherhaven.festival.market.MarketStallComponent;
import com.hexvane.aetherhaven.festival.market.MarketStallInteractSystem;
import com.hexvane.aetherhaven.festival.market.MarketStallUseInteraction;
import com.hexvane.aetherhaven.festival.snowball.SnowballDialogueHandlers;
import com.hexvane.aetherhaven.festival.snowball.SnowballFestivalMechanic;
import com.hexvane.aetherhaven.festival.snowball.SnowballFightSystem;
import com.hexvane.aetherhaven.festival.snowball.SnowballHitSystem;
import com.hexvane.aetherhaven.festival.snowball.SnowballPileUseInteraction;
import com.hexvane.aetherhaven.festival.snowball.SnowballProjectileHitInteraction;
import com.hexvane.aetherhaven.festival.snowball.SnowballVillagerSystem;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbDialogueHandlers;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbFestivalMechanic;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbRaceSystem;
import com.hexvane.aetherhaven.festival.wintertide.WintertideDialogueHandlers;
import com.hexvane.aetherhaven.festival.wintertide.WintertideFestivalMechanic;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekState;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekSystem;
import com.hexvane.aetherhaven.festival.wintertide.WintertidePlayerGiftInteractSystem;
import com.hexvane.aetherhaven.festival.wintertide.WintertidePlayerGiftInteraction;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

/** Wires festivals into the plugin: mechanics, the admin command, and the clock that opens and closes them. */
public final class FestivalsBootstrap {
    private FestivalsBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenFestivalLettuceBurst",
                FestivalLettuceBurstInteraction.class,
                FestivalLettuceBurstInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenFireworkLaunch",
                FireworkLaunchInteraction.class,
                FireworkLaunchInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenFestivalJackLanternBurst",
                HallowsEvePumpkinBurstInteraction.class,
                HallowsEvePumpkinBurstInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenFestivalMarketStallUse",
                MarketStallUseInteraction.class,
                MarketStallUseInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenFestivalWintertidePlayerGift",
                WintertidePlayerGiftInteraction.class,
                WintertidePlayerGiftInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenFestivalSnowballPileUse",
                SnowballPileUseInteraction.class,
                SnowballPileUseInteraction.CODEC
            );
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenFestivalSnowballHit",
                SnowballProjectileHitInteraction.class,
                SnowballProjectileHitInteraction.CODEC
            );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        FestivalLettuceComponent.register(plugin.getEntityStoreRegistry());
        PigRaceRacerComponent.register(plugin.getEntityStoreRegistry());
        CarnivalBalloonComponent.register(plugin.getEntityStoreRegistry());
        CarnivalWhackComponent.register(plugin.getEntityStoreRegistry());
        CarnivalWheelFaceComponent.register(plugin.getEntityStoreRegistry());
        FireworkRocketComponent.register(plugin.getEntityStoreRegistry());
        HallowsEveOrbComponent.register(plugin.getEntityStoreRegistry());
        HallowsEvePumpkinComponent.register(plugin.getEntityStoreRegistry());
        HallowsEveBatComponent.register(plugin.getEntityStoreRegistry());
        MarketStallComponent.register(plugin.getEntityStoreRegistry());
        WintertideGiftSeekState.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new FestivalLettuceAbsorbSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FestivalLettuceGrowthSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FestivalLettuceBurstSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FestivalLettuceInteractSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FestivalSquareBreakBlockSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new FestivalSquarePlaceBlockSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new FestivalDanceSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PigRaceSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CarnivalBalloonSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CarnivalBalloonDirectorSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CarnivalWhackSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CarnivalWhackDirectorSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CarnivalWhackHitSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CarnivalWheelDirectorSystem());
        plugin.getEntityStoreRegistry().registerSystem(new CarnivalWheelSystem());
        plugin.getEntityStoreRegistry().registerSystem(new TreeClimbRaceSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FireworkRocketSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HallowsEveRaceSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HallowsEveOrbCollectSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HallowsEveOrbVisibilitySystem());
        plugin.getEntityStoreRegistry().registerSystem(new HallowsEvePumpkinGrowthSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HallowsEvePumpkinBurstSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HallowsEvePumpkinInteractSystem());
        plugin.getEntityStoreRegistry().registerSystem(new HallowsEveBatDirectorSystem());
        plugin.getEntityStoreRegistry().registerSystem(new MarketStallInteractSystem());
        plugin.getEntityStoreRegistry().registerSystem(new MarketJudgeDirectorSystem());
        plugin.getEntityStoreRegistry().registerSystem(new WintertideGiftSeekSystem());
        plugin.getEntityStoreRegistry().registerSystem(new WintertidePlayerGiftInteractSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SnowballFightSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SnowballHitSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SnowballVillagerSystem());
        core.getFestivalMechanicRegistry().register(NewLifeFestivalMechanic.MECHANIC_ID, new NewLifeFestivalMechanic());
        core.getFestivalMechanicRegistry().register(PigRaceFestivalMechanic.MECHANIC_ID, new PigRaceFestivalMechanic());
        core.getFestivalMechanicRegistry().register(CarnivalFestivalMechanic.MECHANIC_ID, new CarnivalFestivalMechanic());
        core.getFestivalMechanicRegistry().register(TreeClimbFestivalMechanic.MECHANIC_ID, new TreeClimbFestivalMechanic());
        core.getFestivalMechanicRegistry().register(HallowsEveFestivalMechanic.MECHANIC_ID, new HallowsEveFestivalMechanic());
        core.getFestivalMechanicRegistry().register(MarketFestivalMechanic.MECHANIC_ID, new MarketFestivalMechanic());
        core.getFestivalMechanicRegistry().register(WintertideFestivalMechanic.MECHANIC_ID, new WintertideFestivalMechanic());
        core.getFestivalMechanicRegistry().register(SnowballFestivalMechanic.MECHANIC_ID, new SnowballFestivalMechanic());
        PigRaceDialogueHandlers.register(core);
        CarnivalDialogueHandlers.register(core);
        TreeClimbDialogueHandlers.register(core);
        HallowsEveDialogueHandlers.register(core);
        MarketDialogueHandlers.register(core);
        WintertideDialogueHandlers.register(core);
        SnowballDialogueHandlers.register(core);
        core.registerAetherhavenSubcommand(new AetherhavenFestivalCommand());
    }

    @Nonnull
    public static GameTimeTickListener createFestivalGameTimeListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                FestivalService.applyForWorld(world, store, core);
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
                FestivalService.applyForWorld(world, store, core);
            }
        };
    }
}
