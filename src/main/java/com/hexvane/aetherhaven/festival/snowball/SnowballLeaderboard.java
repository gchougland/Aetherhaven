package com.hexvane.aetherhaven.festival.snowball;

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

/** World-shared snowball fight scores: most hits in a single fight. */
public final class SnowballLeaderboard {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, SnowballLeaderboardWorldFile> CACHE = new ConcurrentHashMap<>();
    public static final int TOP_N = 10;

    private SnowballLeaderboard() {}

    public record Entry(
        @SerializedName("playerUuid") @Nonnull String playerUuid,
        @SerializedName("playerName") @Nonnull String playerName,
        @SerializedName("hits") int hits
    ) {}

    @Nonnull
    public static Path leaderboardFile(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(world.getName()))
            .resolve("snowball_leaderboard.json");
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
    public static SnowballLeaderboardWorldFile getOrLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return CACHE.computeIfAbsent(world.getName(), n -> readFromDisk(world, plugin));
    }

    @Nonnull
    private static SnowballLeaderboardWorldFile readFromDisk(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return new SnowballLeaderboardWorldFile();
        }
        Path path = leaderboardFile(world, plugin);
        try {
            return SnowballLeaderboardWorldFile.readOrEmpty(path);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load snowball scoreboard for world %s", world.getName());
            return new SnowballLeaderboardWorldFile();
        }
    }

    public static void recordFight(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nullable PlayerRef playerRef,
        int hits
    ) {
        if (hits <= 0) {
            return;
        }
        String name = playerRef != null ? playerRef.getUsername() : null;
        if (name == null || name.isBlank()) {
            name = "Player";
        }
        SnowballLeaderboardWorldFile file = getOrLoad(world, plugin);
        boolean changed = file.recordBest(playerUuid.toString(), name.trim(), hits);
        if (changed) {
            save(world, plugin, file);
        }
    }

    public static void save(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull SnowballLeaderboardWorldFile file
    ) {
        CACHE.put(world.getName(), file);
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        try {
            file.writeAtomic(leaderboardFile(world, plugin));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save snowball scoreboard for world %s", world.getName());
        }
    }

    @Nonnull
    public static List<Entry> topEntries(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        SnowballLeaderboardWorldFile file = getOrLoad(world, plugin);
        List<Entry> all = new ArrayList<>(file.entriesView());
        all.sort(
            Comparator.comparingInt(Entry::hits)
                .reversed()
                .thenComparing(Entry::playerName, String.CASE_INSENSITIVE_ORDER)
        );
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
