package com.hexvane.aetherhaven.festival.hallowseve;

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

/** Gson root for {@code hallows_eve_leaderboard.json} per world. */
public final class HallowsEveLeaderboardWorldFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("entries")
    @Nullable
    private List<HallowsEveLeaderboard.Entry> entries;

    @Nonnull
    public List<HallowsEveLeaderboard.Entry> entriesView() {
        return entries != null ? List.copyOf(entries) : List.of();
    }

    @Nullable
    public HallowsEveLeaderboard.Entry find(@Nonnull String playerUuid) {
        if (entries == null) {
            return null;
        }
        for (HallowsEveLeaderboard.Entry e : entries) {
            if (e != null && playerUuid.equals(e.playerUuid())) {
                return e;
            }
        }
        return null;
    }

    /** Keeps the better maze run for a player. Returns true when the board changed. */
    public boolean recordBest(
        @Nonnull String playerUuid,
        @Nonnull String playerName,
        int collected,
        int total,
        long remainingMs
    ) {
        HallowsEveScore incoming = HallowsEveScore.of(collected, total, remainingMs);
        if (incoming.collected() <= 0) {
            return false;
        }
        if (entries == null) {
            entries = new ArrayList<>();
        }
        for (int i = 0; i < entries.size(); i++) {
            HallowsEveLeaderboard.Entry e = entries.get(i);
            if (e == null || !playerUuid.equals(e.playerUuid())) {
                continue;
            }
            if (!incoming.isBetterThan(e.score())) {
                if (!playerName.equals(e.playerName())) {
                    entries.set(
                        i,
                        new HallowsEveLeaderboard.Entry(
                            playerUuid,
                            playerName,
                            e.collected(),
                            e.total(),
                            e.remainingMs()
                        )
                    );
                    return true;
                }
                return false;
            }
            entries.set(
                i,
                new HallowsEveLeaderboard.Entry(
                    playerUuid,
                    playerName,
                    incoming.collected(),
                    incoming.total(),
                    incoming.remainingMs()
                )
            );
            return true;
        }
        entries.add(
            new HallowsEveLeaderboard.Entry(
                playerUuid,
                playerName,
                incoming.collected(),
                incoming.total(),
                incoming.remainingMs()
            )
        );
        return true;
    }

    public static HallowsEveLeaderboardWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new HallowsEveLeaderboardWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            HallowsEveLeaderboardWorldFile f = GSON.fromJson(r, HallowsEveLeaderboardWorldFile.class);
            return f != null ? f : new HallowsEveLeaderboardWorldFile();
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
