package com.hexvane.aetherhaven.festival.snowball;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gson root for {@code snowball_leaderboard.json} per world. */
public final class SnowballLeaderboardWorldFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("entries")
    @Nullable
    private List<SnowballLeaderboard.Entry> entries;

    @Nonnull
    public List<SnowballLeaderboard.Entry> entriesView() {
        return entries != null ? List.copyOf(entries) : List.of();
    }

    @Nullable
    public SnowballLeaderboard.Entry find(@Nonnull String playerUuid) {
        if (entries == null) {
            return null;
        }
        for (SnowballLeaderboard.Entry e : entries) {
            if (e != null && playerUuid.equals(e.playerUuid())) {
                return e;
            }
        }
        return null;
    }

    /** Keeps the higher single-fight hit count for a player. Returns true when the board changed. */
    public boolean recordBest(@Nonnull String playerUuid, @Nonnull String playerName, int hits) {
        if (hits <= 0) {
            return false;
        }
        if (entries == null) {
            entries = new ArrayList<>();
        }
        for (int i = 0; i < entries.size(); i++) {
            SnowballLeaderboard.Entry e = entries.get(i);
            if (e == null || !playerUuid.equals(e.playerUuid())) {
                continue;
            }
            if (hits < e.hits()) {
                if (!playerName.equals(e.playerName())) {
                    entries.set(i, new SnowballLeaderboard.Entry(playerUuid, playerName, e.hits()));
                    return true;
                }
                return false;
            }
            if (hits == e.hits() && playerName.equals(e.playerName())) {
                return false;
            }
            entries.set(i, new SnowballLeaderboard.Entry(playerUuid, playerName, Math.max(hits, e.hits())));
            return true;
        }
        entries.add(new SnowballLeaderboard.Entry(playerUuid, playerName, hits));
        return true;
    }

    public static SnowballLeaderboardWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new SnowballLeaderboardWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            SnowballLeaderboardWorldFile f = GSON.fromJson(r, SnowballLeaderboardWorldFile.class);
            return f != null ? f : new SnowballLeaderboardWorldFile();
        }
    }

    public void writeAtomic(@Nonnull Path path) throws IOException {
        Path dir = path.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
