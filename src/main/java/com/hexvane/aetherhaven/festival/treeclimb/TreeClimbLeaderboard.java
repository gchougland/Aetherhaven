package com.hexvane.aetherhaven.festival.treeclimb;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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

/** World-shared tree climb best times (one board for the whole world). */
public final class TreeClimbLeaderboard {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, TreeClimbLeaderboardWorldFile> CACHE = new ConcurrentHashMap<>();
    public static final int TOP_N = 10;

    private TreeClimbLeaderboard() {}

    public record Entry(
        @SerializedName("playerUuid") @Nonnull String playerUuid,
        @SerializedName("playerName") @Nonnull String playerName,
        @SerializedName("bestSeconds") double bestSeconds
    ) {}

    @Nonnull
    public static Path leaderboardFile(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(world.getName()))
            .resolve("tree_climb_leaderboard.json");
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
    public static TreeClimbLeaderboardWorldFile getOrLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return CACHE.computeIfAbsent(world.getName(), n -> readFromDisk(world, plugin));
    }

    @Nonnull
    private static TreeClimbLeaderboardWorldFile readFromDisk(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return new TreeClimbLeaderboardWorldFile();
        }
        Path path = leaderboardFile(world, plugin);
        try {
            return TreeClimbLeaderboardWorldFile.readOrEmpty(path);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load tree climb leaderboard for world %s", world.getName());
            return new TreeClimbLeaderboardWorldFile();
        }
    }

    public static void recordTime(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull PlayerRef playerRef,
        double seconds
    ) {
        if (seconds < 0.0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return;
        }
        String name = playerRef.getUsername();
        if (name == null || name.isBlank()) {
            name = "Player";
        }
        TreeClimbLeaderboardWorldFile file = getOrLoad(world, plugin);
        boolean changed = file.recordBest(playerUuid.toString(), name.trim(), seconds);
        if (changed) {
            save(world, plugin, file);
        }
    }

    public static void save(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TreeClimbLeaderboardWorldFile file
    ) {
        CACHE.put(world.getName(), file);
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        try {
            file.writeAtomic(leaderboardFile(world, plugin));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save tree climb leaderboard for world %s", world.getName());
        }
    }

    @Nonnull
    public static List<Entry> topEntries(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TreeClimbLeaderboardWorldFile file = getOrLoad(world, plugin);
        List<Entry> all = new ArrayList<>(file.entriesView());
        all.sort(Comparator.comparingDouble(Entry::bestSeconds));
        if (all.size() > TOP_N) {
            return List.copyOf(all.subList(0, TOP_N));
        }
        return List.copyOf(all);
    }

    @Nullable
    public static Entry bestForPlayer(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid
    ) {
        return getOrLoad(world, plugin).find(playerUuid.toString());
    }
}
