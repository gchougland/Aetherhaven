package com.hexvane.aetherhaven.worldnpc;

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

public final class WorldNpcsWorldFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("placements")
    private List<WorldNpcPlacementRecord> placements = new ArrayList<>();

    @Nonnull
    public List<WorldNpcPlacementRecord> getPlacements() {
        if (placements == null) {
            placements = new ArrayList<>();
        }
        return placements;
    }

    @Nonnull
    public static WorldNpcsWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new WorldNpcsWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            WorldNpcsWorldFile f = GSON.fromJson(r, WorldNpcsWorldFile.class);
            return f != null ? f : new WorldNpcsWorldFile();
        }
    }

    public void writeAtomic(@Nonnull Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
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
