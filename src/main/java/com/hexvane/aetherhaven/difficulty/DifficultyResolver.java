package com.hexvane.aetherhaven.difficulty;

import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves effective difficulty for gameplay. When server force is on, all towns and loot use the shared
 * server settings; otherwise per-town settings apply.
 */
public final class DifficultyResolver {
    private DifficultyResolver() {}

    public static boolean isForced() {
        return ServerDifficultyPersistence.getOrLoad().isForceAllTowns();
    }

    @Nonnull
    public static ServerDifficultyState serverState() {
        return ServerDifficultyPersistence.getOrLoad();
    }

    @Nonnull
    public static TownDifficultySettings effectiveForTown(@Nullable TownRecord town) {
        ServerDifficultyState server = ServerDifficultyPersistence.getOrLoad();
        if (server.isForceAllTowns()) {
            return server.effectiveForcedSettings();
        }
        if (town == null) {
            return TownDifficultySettings.normalUntilChosen();
        }
        return town.getDifficultySettings().effectiveForGameplay();
    }

    /**
     * Loot rarity resolution: forced server settings, else the player's affiliated town in {@code preferWorld},
     * else Normal.
     */
    @Nonnull
    public static TownDifficultySettings effectiveForLoot(@Nullable UUID playerUuid, @Nullable World preferWorld) {
        ServerDifficultyState server = ServerDifficultyPersistence.getOrLoad();
        if (server.isForceAllTowns()) {
            return server.effectiveForcedSettings();
        }
        if (playerUuid == null) {
            return TownDifficultySettings.normalUntilChosen();
        }
        TownManager prefer = null;
        if (preferWorld != null) {
            prefer = AetherhavenWorldRegistries.getTownManagerIfLoaded(preferWorld);
        }
        TownRecord town = AetherhavenWorldRegistries.findTownForPlayerAcrossWorlds(playerUuid, prefer);
        return effectiveForTown(town);
    }
}
