package com.hexvane.aetherhaven.festival.treeclimb;

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

/** Gson root for {@code tree_climb_leaderboard.json} per world. */
public final class TreeClimbLeaderboardWorldFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("entries")
    @Nullable
    private List<TreeClimbLeaderboard.Entry> entries;

    @Nonnull
    public List<TreeClimbLeaderboard.Entry> entriesView() {
        return entries != null ? List.copyOf(entries) : List.of();
    }

    @Nullable
    public TreeClimbLeaderboard.Entry find(@Nonnull String playerUuid) {
        if (entries == null) {
            return null;
        }
        for (TreeClimbLeaderboard.Entry e : entries) {
            if (e != null && playerUuid.equals(e.playerUuid())) {
                return e;
            }
        }
        return null;
    }

    /** Updates best time for a player. Returns true when the board changed. */
    public boolean recordBest(@Nonnull String playerUuid, @Nonnull String playerName, double seconds) {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        for (int i = 0; i < entries.size(); i++) {
            TreeClimbLeaderboard.Entry e = entries.get(i);
            if (e == null || !playerUuid.equals(e.playerUuid())) {
                continue;
            }
            if (seconds >= e.bestSeconds()) {
                if (!playerName.equals(e.playerName())) {
                    entries.set(i, new TreeClimbLeaderboard.Entry(playerUuid, playerName, e.bestSeconds()));
                    return true;
                }
                return false;
            }
            entries.set(i, new TreeClimbLeaderboard.Entry(playerUuid, playerName, seconds));
            return true;
        }
        entries.add(new TreeClimbLeaderboard.Entry(playerUuid, playerName, seconds));
        return true;
    }

    public static TreeClimbLeaderboardWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new TreeClimbLeaderboardWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            TreeClimbLeaderboardWorldFile f = GSON.fromJson(r, TreeClimbLeaderboardWorldFile.class);
            return f != null ? f : new TreeClimbLeaderboardWorldFile();
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
