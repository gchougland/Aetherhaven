package com.hexvane.aetherhaven.poi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gson root for {@code pois.json} per world (alongside {@code towns.json}). */
public final class PoiWorldFile {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("pois")
    private List<Row> pois = new ArrayList<>();

    @Nonnull
    public List<Row> getPois() {
        if (pois == null) {
            pois = new ArrayList<>();
        }
        return pois;
    }

    /** Gson row; UUIDs as strings. */
    public static final class Row {
        @Nullable
        public String id;
        @Nullable
        public String townId;
        public int x;
        public int y;
        public int z;
        @Nullable
        public List<String> tags;
        public int capacity;
        @Nullable
        public String plotId;
    }

    @Nonnull
    public static PoiWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new PoiWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            PoiWorldFile f = GSON.fromJson(r, PoiWorldFile.class);
            return f != null ? f : new PoiWorldFile();
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

    @Nonnull
    public static List<PoiEntry> toEntries(@Nonnull PoiWorldFile file) {
        List<PoiEntry> out = new ArrayList<>();
        for (Row row : file.getPois()) {
            if (row == null || row.id == null || row.townId == null) {
                LOGGER.atWarning().log("Skipping POI row with missing id or townId");
                continue;
            }
            try {
                UUID id = UUID.fromString(row.id);
                UUID townId = UUID.fromString(row.townId);
                Set<String> tags = new HashSet<>();
                if (row.tags != null) {
                    for (String t : row.tags) {
                        if (t != null && !t.isBlank()) {
                            tags.add(t);
                        }
                    }
                }
                UUID plotUuid = null;
                if (row.plotId != null && !row.plotId.isBlank()) {
                    plotUuid = UUID.fromString(row.plotId);
                }
                out.add(new PoiEntry(id, townId, row.x, row.y, row.z, tags, row.capacity, plotUuid));
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().withCause(e).log("Skipping invalid POI row id=%s town=%s", row.id, row.townId);
            }
        }
        return out;
    }

    @Nonnull
    public static PoiWorldFile fromEntries(@Nonnull List<PoiEntry> entries) {
        PoiWorldFile f = new PoiWorldFile();
        for (PoiEntry e : entries) {
            Row r = new Row();
            r.id = e.getId().toString();
            r.townId = e.getTownId().toString();
            r.x = e.getX();
            r.y = e.getY();
            r.z = e.getZ();
            r.tags = new ArrayList<>(e.getTags());
            r.capacity = e.getCapacity();
            UUID p = e.getPlotId();
            r.plotId = p != null ? p.toString() : null;
            f.getPois().add(r);
        }
        return f;
    }
}
