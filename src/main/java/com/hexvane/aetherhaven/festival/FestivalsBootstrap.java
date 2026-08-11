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
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        FestivalLettuceComponent.register(plugin.getEntityStoreRegistry());
        PigRaceRacerComponent.register(plugin.getEntityStoreRegistry());
        CarnivalBalloonComponent.register(plugin.getEntityStoreRegistry());
        CarnivalWhackComponent.register(plugin.getEntityStoreRegistry());
        CarnivalWheelFaceComponent.register(plugin.getEntityStoreRegistry());
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
        core.getFestivalMechanicRegistry().register(NewLifeFestivalMechanic.MECHANIC_ID, new NewLifeFestivalMechanic());
        core.getFestivalMechanicRegistry().register(PigRaceFestivalMechanic.MECHANIC_ID, new PigRaceFestivalMechanic());
        core.getFestivalMechanicRegistry().register(CarnivalFestivalMechanic.MECHANIC_ID, new CarnivalFestivalMechanic());
        PigRaceDialogueHandlers.register(core);
        CarnivalDialogueHandlers.register(core);
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
