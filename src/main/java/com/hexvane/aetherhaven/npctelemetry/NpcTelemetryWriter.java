package com.hexvane.aetherhaven.npctelemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class NpcTelemetryWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withLocale(Locale.ROOT);

    private NpcTelemetryWriter() {}

    @Nonnull
    public static Path resolvePath(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID entityUuid,
        @Nullable String handleHint
    ) throws IOException {
        String worldDir = sanitizeWorldDirName(world.getName());
        Path dir = TownManager.pluginData(plugin).resolve("npc_telemetry").resolve(worldDir);
        Files.createDirectories(dir);

        String ts = FILE_TS.format(Instant.now().atZone(java.time.ZoneOffset.UTC));
        String uuid8 = entityUuid.toString().replace("-", "");
        if (uuid8.length() > 8) {
            uuid8 = uuid8.substring(0, 8);
        }
        String handle = sanitizeFileToken(handleHint != null && !handleHint.isBlank() ? handleHint : "npc");
        return dir.resolve(ts + "_" + uuid8 + "_" + handle + ".json").toAbsolutePath().normalize();
    }

    @Nonnull
    public static Path write(
        @Nonnull Path file,
        @Nonnull Map<String, Object> report
    ) throws IOException {
        Files.createDirectories(file.getParent());
        String json = GSON.toJson(report);
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    @Nonnull
    public static Path write(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID entityUuid,
        @Nullable String handleHint,
        @Nonnull Map<String, Object> report
    ) throws IOException {
        Path file = resolvePath(plugin, world, entityUuid, handleHint);
        return write(file, report);
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
    private static String sanitizeFileToken(@Nonnull String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < 48; i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "npc" : sb.toString();
    }
}
