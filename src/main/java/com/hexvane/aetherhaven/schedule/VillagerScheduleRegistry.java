package com.hexvane.aetherhaven.schedule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Villager weekly schedules from {@code Server/Aetherhaven/VillagerSchedules/} under each asset pack (plus classpath
 * fallback). Role id is the JSON filename without extension (e.g. {@code Aetherhaven_Merchant.json} →
 * {@code Aetherhaven_Merchant}); later packs override earlier definitions for the same role.
 */
public final class VillagerScheduleRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, VillagerScheduleDefinition> byRoleId;

    private VillagerScheduleRegistry(Map<String, VillagerScheduleDefinition> byRoleId) {
        this.byRoleId = byRoleId;
    }

    @Nonnull
    public static VillagerScheduleRegistry empty() {
        return new VillagerScheduleRegistry(Collections.emptyMap());
    }

    @Nonnull
    public static VillagerScheduleRegistry loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        Map<String, VillagerScheduleDefinition> map = new LinkedHashMap<>();
        List<PackJsonFile> packFiles = AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.VILLAGER_SCHEDULES);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                String roleId = roleIdFromFileName(f.absolutePath());
                if (roleId.isEmpty()) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    putFromStream(gson, in, f.packName() + ":" + f.absolutePath(), roleId, map);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load villager schedule %s", f.absolutePath());
                }
            }
            LOGGER.atInfo().log(
                "Loaded %s villager schedule(s) from asset packs under %s",
                map.size(),
                AetherhavenAssetPaths.VILLAGER_SCHEDULES
            );
        } else {
            List<String> paths = ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.villagerSchedulesPrefix());
            for (String path : paths) {
                String roleId = roleIdFromResourcePath(path);
                if (roleId.isEmpty()) {
                    continue;
                }
                loadFromClasspath(classLoader, gson, path, roleId, map);
            }
            LOGGER.atInfo().log(
                "Loaded %s villager schedule(s) from classpath %s",
                map.size(),
                AetherhavenAssetPaths.villagerSchedulesPrefix()
            );
        }
        return new VillagerScheduleRegistry(Collections.unmodifiableMap(map));
    }

    private static void loadFromClasspath(
        @Nonnull ClassLoader classLoader,
        @Nonnull Gson gson,
        @Nonnull String resourcePath,
        @Nonnull String roleId,
        @Nonnull Map<String, VillagerScheduleDefinition> map
    ) {
        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.atWarning().log("Villager schedule file not found: %s", resourcePath);
                return;
            }
            putFromStream(gson, in, resourcePath, roleId, map);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load villager schedule %s", resourcePath);
        }
    }

    private static void putFromStream(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull String roleId,
        @Nonnull Map<String, VillagerScheduleDefinition> map
    ) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            VillagerScheduleDefinition def = gson.fromJson(reader, VillagerScheduleDefinition.class);
            if (def == null || def.getTransitions().isEmpty()) {
                LOGGER.atWarning().log("Villager schedule %s empty or invalid (%s)", roleId, label);
                return;
            }
            if (map.containsKey(roleId)) {
                LOGGER.atInfo().log("Villager schedule role %s overridden by later asset (%s)", roleId, label);
            }
            map.put(roleId, def);
            LOGGER.atInfo().log("Loaded villager schedule: %s (%s transitions) (%s)", roleId, def.getTransitions().size(), label);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to parse villager schedule %s", label);
        }
    }

    @Nonnull
    private static String roleIdFromFileName(@Nonnull Path absolutePath) {
        return roleIdFromBaseName(absolutePath.getFileName().toString());
    }

    @Nonnull
    private static String roleIdFromResourcePath(@Nonnull String resourcePath) {
        int slash = resourcePath.lastIndexOf('/');
        String name = slash >= 0 ? resourcePath.substring(slash + 1) : resourcePath;
        return roleIdFromBaseName(name);
    }

    @Nonnull
    private static String roleIdFromBaseName(@Nonnull String fileName) {
        if (!fileName.endsWith(".json")) {
            return "";
        }
        return fileName.substring(0, fileName.length() - 5);
    }

    /**
     * Returns the parsed schedule for {@code roleId}, or null if missing or invalid.
     */
    @Nullable
    public VillagerScheduleDefinition getOrLoad(@Nonnull String roleId) {
        if (roleId.isBlank()) {
            return null;
        }
        return byRoleId.get(roleId.trim());
    }
}
