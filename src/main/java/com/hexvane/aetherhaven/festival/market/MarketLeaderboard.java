package com.hexvane.aetherhaven.festival.market;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** World-shared Market Festival town scores. */
public final class MarketLeaderboard {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, MarketLeaderboardWorldFile> CACHE = new ConcurrentHashMap<>();
    public static final int TOP_N = 10;

    private MarketLeaderboard() {}

    public record Entry(
        @SerializedName("townId") @Nonnull String townId,
        @SerializedName("townName") @Nonnull String townName,
        @SerializedName("score") int score
    ) {}

    @Nonnull
    public static Path leaderboardFile(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(world.getName()))
            .resolve("market_leaderboard.json");
    }

    @Nonnull
    private static String sanitizeWorldDirName(@Nonnull String worldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < worldName.length(); i++) {
            char c = worldName.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "world" : sb.toString();
    }

    @Nonnull
    public static MarketLeaderboardWorldFile getOrLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return CACHE.computeIfAbsent(world.getName(), n -> readFromDisk(world, plugin));
    }

    @Nonnull
    private static MarketLeaderboardWorldFile readFromDisk(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return new MarketLeaderboardWorldFile();
        }
        Path path = leaderboardFile(world, plugin);
        try {
            return MarketLeaderboardWorldFile.readOrEmpty(path);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load Market Festival scoreboard for world %s", world.getName());
            return new MarketLeaderboardWorldFile();
        }
    }

    public static void recordTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        int score
    ) {
        if (score <= 0) {
            return;
        }
        String name = town.getDisplayName();
        if (name == null || name.isBlank()) {
            name = "Town";
        }
        MarketLeaderboardWorldFile file = getOrLoad(world, plugin);
        boolean changed = file.recordBest(town.getTownId().toString(), name.trim(), score);
        if (changed) {
            save(world, plugin, file);
        }
    }

    public static void save(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull MarketLeaderboardWorldFile file
    ) {
        CACHE.put(world.getName(), file);
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        try {
            file.writeAtomic(leaderboardFile(world, plugin));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save Market Festival scoreboard for world %s", world.getName());
        }
    }

    @Nonnull
    public static List<Entry> topEntries(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        MarketLeaderboardWorldFile file = getOrLoad(world, plugin);
        List<Entry> all = new ArrayList<>(file.entriesView());
        all.sort(Comparator.comparingInt(Entry::score).reversed());
        if (all.size() > TOP_N) {
            return List.copyOf(all.subList(0, TOP_N));
        }
        return List.copyOf(all);
    }

    @Nullable
    public static Entry bestForTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId
    ) {
        return getOrLoad(world, plugin).find(townId.toString());
    }
}
