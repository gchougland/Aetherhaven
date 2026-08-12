package com.hexvane.aetherhaven.prop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
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
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gson root for {@code props.json} per world (alongside {@code pois.json}, {@code towns.json}). */
public final class PropWorldFile {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("props")
    private List<Row> props = new ArrayList<>();

    @Nonnull
    public List<Row> getProps() {
        if (props == null) {
            props = new ArrayList<>();
        }
        return props;
    }

    /** Gson row; UUIDs and rotation as strings. */
    public static final class Row {
        @Nullable
        public String instanceId;
        @Nullable
        public String propId;
        public int anchorX;
        public int anchorY;
        public int anchorZ;
        @Nullable
        public String rotationYaw;
        /** Optional; UUIDs of decorative entities spawned with this prop. */
        @Nullable
        public List<String> linkedEntityIds;
    }

    @Nonnull
    public static PropWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new PropWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            PropWorldFile f = GSON.fromJson(r, PropWorldFile.class);
            return f != null ? f : new PropWorldFile();
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
    public static List<PropInstance> toInstances(@Nonnull PropWorldFile file) {
        List<PropInstance> out = new ArrayList<>();
        for (Row row : file.getProps()) {
            if (row == null || row.instanceId == null || row.propId == null) {
                LOGGER.atWarning().log("Skipping prop row with missing instanceId or propId");
                continue;
            }
            try {
                UUID id = UUID.fromString(row.instanceId);
                Rotation yaw = parseRotation(row.rotationYaw);
                List<UUID> linked = parseLinkedEntityIds(row.linkedEntityIds);
                out.add(new PropInstance(id, row.propId, row.anchorX, row.anchorY, row.anchorZ, yaw, linked));
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().withCause(e).log("Skipping invalid prop row instanceId=%s", row.instanceId);
            }
        }
        return out;
    }

    @Nonnull
    public static PropWorldFile fromInstances(@Nonnull List<PropInstance> instances) {
        PropWorldFile f = new PropWorldFile();
        for (PropInstance p : instances) {
            Row r = new Row();
            r.instanceId = p.getInstanceId().toString();
            r.propId = p.getPropId();
            r.anchorX = p.getAnchorX();
            r.anchorY = p.getAnchorY();
            r.anchorZ = p.getAnchorZ();
            r.rotationYaw = p.getYaw().name();
            if (!p.getLinkedEntityIds().isEmpty()) {
                List<String> ids = new ArrayList<>();
                for (UUID u : p.getLinkedEntityIds()) {
                    ids.add(u.toString());
                }
                r.linkedEntityIds = ids;
            }
            f.getProps().add(r);
        }
        return f;
    }

    @Nonnull
    private static List<UUID> parseLinkedEntityIds(@Nullable List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                out.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {
                // Skip bad ids from older or hand-edited files.
            }
        }
        return out;
    }

    @Nonnull
    private static Rotation parseRotation(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Rotation.None;
        }
        try {
            return Rotation.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return Rotation.None;
        }
    }
}
