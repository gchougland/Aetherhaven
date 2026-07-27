package com.hexvane.aetherhaven.support;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Builds a zip of mod data and recent server logs for remote support. */
public final class SupportBundleCollector {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final int MAX_LOG_FILES = 3;
    private static final long MAX_LOG_BYTES_TOTAL = 10L * 1024L * 1024L;

    private SupportBundleCollector() {}

    public static final class Result {
        @Nullable
        public final byte[] zipBytes;
        @Nullable
        public final String errorKey;
        @Nonnull
        public final List<String> worldNames;
        @Nonnull
        public final List<String> includedPaths;
        @Nullable
        public final String serverUuid;

        Result(
            @Nullable byte[] zipBytes,
            @Nullable String errorKey,
            @Nonnull List<String> worldNames,
            @Nonnull List<String> includedPaths,
            @Nullable String serverUuid
        ) {
            this.zipBytes = zipBytes;
            this.errorKey = errorKey;
            this.worldNames = worldNames;
            this.includedPaths = includedPaths;
            this.serverUuid = serverUuid;
        }
    }

    @Nonnull
    public static Result collect(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nullable String note,
        int maxBundleBytes
    ) {
        Path dataDir = plugin.getDataDirectory();
        List<String> includedPaths = new ArrayList<>();
        List<String> worldNames = listWorldNames(dataDir);
        String serverUuid = readHstatsServerUuid();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zip = new ZipOutputStream(baos)) {
            if (Files.isRegularFile(dataDir.resolve("config.json"))) {
                addFile(zip, dataDir.resolve("config.json"), "config.json", includedPaths);
            }
            addDirectory(zip, dataDir.resolve("worlds"), "worlds", includedPaths);
            addDirectory(zip, dataDir.resolve("villager_audit"), "villager_audit", includedPaths);
            addDirectory(zip, dataDir.resolve("npc_telemetry"), "npc_telemetry", includedPaths);
            addRecentServerLogs(zip, includedPaths);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("uploadedAtEpochMs", System.currentTimeMillis());
            manifest.put("playerUuid", playerUuid.toString());
            manifest.put("playerName", playerName);
            manifest.put("modVersion", plugin.getManifest().getVersion().toString());
            manifest.put("note", note != null ? note : "");
            manifest.put("serverUuid", serverUuid != null ? serverUuid : "");
            manifest.put("worldNames", worldNames);
            manifest.put("includedPaths", includedPaths);
            byte[] manifestBytes = GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
            zip.putNextEntry(new ZipEntry("support_manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
            includedPaths.add("support_manifest.json");

            zip.finish();
            byte[] zipBytes = baos.toByteArray();
            if (zipBytes.length > maxBundleBytes) {
                return new Result(null, "too_large", worldNames, includedPaths, serverUuid);
            }
            if (zipBytes.length == 0) {
                return new Result(null, "empty_bundle", worldNames, includedPaths, serverUuid);
            }
            return new Result(zipBytes, null, worldNames, includedPaths, serverUuid);
        } catch (IOException e) {
            return new Result(null, "io_error", worldNames, includedPaths, serverUuid);
        }
    }

    @Nonnull
    private static List<String> listWorldNames(@Nonnull Path dataDir) {
        Path worlds = dataDir.resolve("worlds");
        if (!Files.isDirectory(worlds)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(worlds)) {
            stream.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().forEach(names::add);
        } catch (IOException ignored) {
        }
        return List.copyOf(names);
    }

    private static void addDirectory(
        @Nonnull ZipOutputStream zip,
        @Nonnull Path root,
        @Nonnull String zipPrefix,
        @Nonnull List<String> includedPaths
    ) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    if (shouldSkipModDataRelative(relative)) {
                        return;
                    }
                    addFile(zip, path, zipPrefix + "/" + relative, includedPaths);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static boolean shouldSkipModDataRelative(@Nonnull String relative) {
        String normalized = relative.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".bak")) {
            return false;
        }
        return normalized.startsWith("community/")
            || normalized.startsWith("server/")
            || normalized.startsWith("common/");
    }

    private static void addRecentServerLogs(@Nonnull ZipOutputStream zip, @Nonnull List<String> includedPaths)
        throws IOException {
        Path logsDir = Path.of("logs");
        if (!Files.isDirectory(logsDir)) {
            return;
        }
        List<Path> logFiles = new ArrayList<>();
        try (var stream = Files.list(logsDir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith("_server.log"))
                .forEach(logFiles::add);
        }
        logFiles.sort(Comparator.comparingLong(p -> {
            try {
                return -Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));

        long total = 0L;
        int added = 0;
        for (Path logFile : logFiles) {
            if (added >= MAX_LOG_FILES) {
                break;
            }
            long size;
            try {
                size = Files.size(logFile);
            } catch (IOException e) {
                continue;
            }
            if (total + size > MAX_LOG_BYTES_TOTAL) {
                continue;
            }
            String entryName = "logs/" + logFile.getFileName();
            addFile(zip, logFile, entryName, includedPaths);
            total += size;
            added++;
        }
    }

    private static void addFile(
        @Nonnull ZipOutputStream zip,
        @Nonnull Path file,
        @Nonnull String entryName,
        @Nonnull List<String> includedPaths
    ) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName.replace('\\', '/')));
        Files.copy(file, zip);
        zip.closeEntry();
        includedPaths.add(entryName.replace('\\', '/'));
    }

    @Nullable
    private static String readHstatsServerUuid() {
        Path serverUuidFile = Path.of("hstats-server-uuid.txt");
        try {
            if (!Files.isRegularFile(serverUuidFile)) {
                return null;
            }
            String content = Files.readString(serverUuidFile);
            String[] lines = content.split("\n");
            if (lines.length < 5) {
                return null;
            }
            String enabled = lines[3].split("=")[1].trim();
            if (!enabled.equalsIgnoreCase("true")) {
                return null;
            }
            return lines[4].trim();
        } catch (IOException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }
}
