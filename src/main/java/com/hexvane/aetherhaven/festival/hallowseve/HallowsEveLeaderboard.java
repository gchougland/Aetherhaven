package com.hexvane.aetherhaven.festival.hallowseve;

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

/** World-shared Hallow's Eve maze scores (one board for the whole world). */
public final class HallowsEveLeaderboard {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, HallowsEveLeaderboardWorldFile> CACHE = new ConcurrentHashMap<>();
    public static final int TOP_N = 10;

    private HallowsEveLeaderboard() {}

    public record Entry(
        @SerializedName("playerUuid") @Nonnull String playerUuid,
        @SerializedName("playerName") @Nonnull String playerName,
        @SerializedName("collected") int collected,
        @SerializedName("total") int total,
        @SerializedName("remainingMs") long remainingMs
    ) {
        @Nonnull
        public HallowsEveScore score() {
            return HallowsEveScore.of(collected, total, remainingMs);
        }
    }

    @Nonnull
    public static Path leaderboardFile(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return TownManager.pluginData(plugin)
            .resolve("worlds")
            .resolve(sanitizeWorldDirName(world.getName()))
            .resolve("hallows_eve_leaderboard.json");
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
    public static HallowsEveLeaderboardWorldFile getOrLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return CACHE.computeIfAbsent(world.getName(), n -> readFromDisk(world, plugin));
    }

    @Nonnull
    private static HallowsEveLeaderboardWorldFile readFromDisk(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return new HallowsEveLeaderboardWorldFile();
        }
        Path path = leaderboardFile(world, plugin);
        try {
            return HallowsEveLeaderboardWorldFile.readOrEmpty(path);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load Hallow's Eve scoreboard for world %s", world.getName());
            return new HallowsEveLeaderboardWorldFile();
        }
    }

    public static void recordRun(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull PlayerRef playerRef,
        int collected,
        int total,
        long remainingMs
    ) {
        if (collected <= 0) {
            return;
        }
        String name = playerRef.getUsername();
        if (name == null || name.isBlank()) {
            name = "Player";
        }
        HallowsEveLeaderboardWorldFile file = getOrLoad(world, plugin);
        boolean changed = file.recordBest(playerUuid.toString(), name.trim(), collected, total, remainingMs);
        if (changed) {
            save(world, plugin, file);
        }
    }

    public static void save(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull HallowsEveLeaderboardWorldFile file
    ) {
        CACHE.put(world.getName(), file);
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        try {
            file.writeAtomic(leaderboardFile(world, plugin));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save Hallow's Eve scoreboard for world %s", world.getName());
        }
    }

    @Nonnull
    public static List<Entry> topEntries(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        HallowsEveLeaderboardWorldFile file = getOrLoad(world, plugin);
        List<Entry> all = new ArrayList<>(file.entriesView());
        all.sort(Comparator.comparing(Entry::score, HallowsEveScore::compareBestFirst));
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
