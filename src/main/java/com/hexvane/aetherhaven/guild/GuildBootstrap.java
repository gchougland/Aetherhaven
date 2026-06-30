package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenGuildCommand;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerEntity;
import com.hexvane.aetherhaven.guild.marker.AdventurerSpawnMarkerSystems;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

public final class GuildBootstrap {
    private GuildBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {}

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        GuildHallDisplayAnchor.register(plugin.getEntityStoreRegistry());
        plugin
            .getEntityRegistry()
            .registerEntity(
                "AetherhavenAdventurerSpawnMarker",
                AdventurerSpawnMarkerEntity.class,
                world -> {
                    AdventurerSpawnMarkerEntity e = new AdventurerSpawnMarkerEntity();
                    if (world != null) {
                        e.loadIntoWorld(world);
                    }
                    return e;
                },
                AdventurerSpawnMarkerEntity.CODEC
            );
        plugin.getEntityStoreRegistry().registerSystem(new AdventurerSpawnMarkerSystems.EnsurePrefabCopyable());
        plugin.getEntityStoreRegistry().registerSystem(new GuildHallDisplayAnchorSystem());
        core.registerAetherhavenSubcommand(new AetherhavenGuildCommand());
    }

    @Nonnull
    public static GameTimeTickListener createGuildAdventurerPoolListener(@Nonnull AetherhavenPlugin core) {
        return new GameTimeTickListener() {
            @Override
            public void onSmoothGameMinuteAdvanced(
                @Nonnull Store<EntityStore> store,
                @Nonnull World world,
                @Nonnull WorldTimeResource wtr,
                long prevEpochMinute,
                long newEpochMinute
            ) {
                GuildHallAdventurerPoolService.scheduleTickFromHub(world, core, wtr);
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
                GuildHallAdventurerPoolService.scheduleTickFromHub(world, core, wtr);
            }
        };
    }
}
