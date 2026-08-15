package com.hexvane.aetherhaven.festival.market;

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

/** Gson root for {@code market_leaderboard.json} per world. */
public final class MarketLeaderboardWorldFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("entries")
    @Nullable
    private List<MarketLeaderboard.Entry> entries;

    @Nonnull
    public List<MarketLeaderboard.Entry> entriesView() {
        return entries != null ? List.copyOf(entries) : List.of();
    }

    @Nullable
    public MarketLeaderboard.Entry find(@Nonnull String townId) {
        if (entries == null) {
            return null;
        }
        for (MarketLeaderboard.Entry e : entries) {
            if (e != null && townId.equals(e.townId())) {
                return e;
            }
        }
        return null;
    }

    /** Keeps the higher score for a town. Returns true when the board changed. */
    public boolean recordBest(@Nonnull String townId, @Nonnull String townName, int score) {
        if (score <= 0) {
            return false;
        }
        if (entries == null) {
            entries = new ArrayList<>();
        }
        for (int i = 0; i < entries.size(); i++) {
            MarketLeaderboard.Entry e = entries.get(i);
            if (e == null || !townId.equals(e.townId())) {
                continue;
            }
            if (score < e.score()) {
                if (!townName.equals(e.townName())) {
                    entries.set(i, new MarketLeaderboard.Entry(townId, townName, e.score()));
                    return true;
                }
                return false;
            }
            entries.set(i, new MarketLeaderboard.Entry(townId, townName, score));
            return true;
        }
        entries.add(new MarketLeaderboard.Entry(townId, townName, score));
        return true;
    }

    public static MarketLeaderboardWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new MarketLeaderboardWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            MarketLeaderboardWorldFile f = GSON.fromJson(r, MarketLeaderboardWorldFile.class);
            return f != null ? f : new MarketLeaderboardWorldFile();
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
