package com.hexvane.aetherhaven.schedule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Loads and caches one JSON schedule file per NPC role id from {@code Server/VillagerSchedules/<roleId>.json}. */
public final class VillagerScheduleRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ClassLoader classLoader;
    private final Gson gson = new GsonBuilder().create();
    private final Map<String, VillagerScheduleDefinition> cache = new ConcurrentHashMap<>();

    public VillagerScheduleRegistry(@Nonnull ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Returns the parsed schedule for {@code roleId}, or null if the file is missing or invalid.
     * Results are cached for the server lifetime.
     */
    @Nullable
    public VillagerScheduleDefinition getOrLoad(@Nonnull String roleId) {
        if (roleId.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(roleId.trim(), this::loadUnchecked);
    }

    @Nullable
    private VillagerScheduleDefinition loadUnchecked(@Nonnull String roleId) {
        String path = "Server/VillagerSchedules/" + roleId + ".json";
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                VillagerScheduleDefinition def = gson.fromJson(reader, VillagerScheduleDefinition.class);
                if (def == null || def.getTransitions().isEmpty()) {
                    LOGGER.atWarning().log("Schedule %s empty or invalid", path);
                    return null;
                }
                LOGGER.atInfo().log("Loaded villager schedule: %s (%s transitions)", path, def.getTransitions().size());
                return def;
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to load villager schedule for role %s", roleId);
            return null;
        }
    }
}
