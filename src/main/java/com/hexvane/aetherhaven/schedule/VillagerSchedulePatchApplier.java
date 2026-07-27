package com.hexvane.aetherhaven.schedule;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Applies {@link AetherhavenAssetPaths#VILLAGER_SCHEDULE_PATCHES} onto loaded villager schedules. */
public final class VillagerSchedulePatchApplier {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private VillagerSchedulePatchApplier() {}

    public static int applyAllPackPatches(
        @Nonnull Gson gson,
        @Nonnull Map<String, VillagerScheduleDefinition> schedulesByRoleId,
        @Nonnull VillagerDefinitionCatalog villagerCatalog,
        @Nonnull ScheduleLocationCatalog locationCatalog
    ) {
        List<PackJsonFile> files =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.VILLAGER_SCHEDULE_PATCHES);
        int applied = 0;
        for (PackJsonFile f : files) {
            try (InputStream in = Files.newInputStream(f.absolutePath())) {
                VillagerSchedulePatchDefinition patch =
                    gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), VillagerSchedulePatchDefinition.class);
                if (applyPatch(schedulesByRoleId, villagerCatalog, locationCatalog, patch, f.packName() + ":" + f.absolutePath())) {
                    applied++;
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load villager schedule patch %s", f.absolutePath());
            }
        }
        if (applied > 0) {
            LOGGER.atInfo().log("Applied %s villager schedule patch file(s) from asset packs", applied);
        }
        return applied;
    }

    /** Visible for tests. */
    public static boolean applyPatch(
        @Nonnull Map<String, VillagerScheduleDefinition> schedulesByRoleId,
        @Nonnull VillagerDefinitionCatalog villagerCatalog,
        @Nonnull ScheduleLocationCatalog locationCatalog,
        @Nonnull VillagerSchedulePatchDefinition patch,
        @Nonnull String label
    ) {
        if (patch.getTargetScheduleRoleId() == null || patch.getTargetScheduleRoleId().isBlank()) {
            LOGGER.atWarning().log("Skipping villager schedule patch with missing targetScheduleRoleId: %s", label);
            return false;
        }
        if (patch.schemaVersionOrDefault() != VillagerSchedulePatchDefinition.SUPPORTED_SCHEMA_VERSION) {
            LOGGER.atWarning().log(
                "Villager schedule patch %s schemaVersion %s (expected %s)",
                label,
                patch.schemaVersionOrDefault(),
                VillagerSchedulePatchDefinition.SUPPORTED_SCHEMA_VERSION
            );
        }
        String roleId = patch.getTargetScheduleRoleId().trim();
        VillagerScheduleDefinition fileSchedule = schedulesByRoleId.get(roleId);
        boolean hasEmbedded = hasEmbeddedScheduleForRole(villagerCatalog, roleId);
        if (fileSchedule == null && !hasEmbedded) {
            LOGGER.atWarning().log("Villager schedule patch %s targets unknown schedule role %s", label, roleId);
        }
        warnUnknownLocations(patch, locationCatalog, label);

        int removedIds = 0;
        int removedTimes = 0;
        int added = 0;

        if (fileSchedule != null) {
            removedIds += fileSchedule.removeTransitionsByIds(patch.removeTransitionIdsOrEmpty());
            removedTimes += fileSchedule.removeTransitionsByTime(patch.removeTransitionsOrEmpty());
            for (VillagerScheduleTransition t : patch.addTransitionsOrEmpty()) {
                if (t != null) {
                    fileSchedule.addOrReplaceTransition(t);
                    added++;
                }
            }
        } else if (!patch.addTransitionsOrEmpty().isEmpty() || !patch.removeTransitionIdsOrEmpty().isEmpty()
            || !patch.removeTransitionsOrEmpty().isEmpty()) {
            VillagerScheduleDefinition created = new VillagerScheduleDefinition();
            created.setSchemaVersion(VillagerSchedulePatchDefinition.SUPPORTED_SCHEMA_VERSION);
            removedIds += created.removeTransitionsByIds(patch.removeTransitionIdsOrEmpty());
            removedTimes += created.removeTransitionsByTime(patch.removeTransitionsOrEmpty());
            for (VillagerScheduleTransition t : patch.addTransitionsOrEmpty()) {
                if (t != null) {
                    created.addOrReplaceTransition(t);
                    added++;
                }
            }
            if (!created.getTransitions().isEmpty()) {
                schedulesByRoleId.put(roleId, created);
            }
        }

        for (VillagerDefinition villager : villagerCatalog.allByNpcRoleId().values()) {
            if (!roleId.equals(villager.effectiveScheduleRoleId())) {
                continue;
            }
            VillagerScheduleDefinition embedded = villager.getWeeklySchedule();
            if (embedded == null || embedded.getTransitions().isEmpty()) {
                continue;
            }
            removedIds += embedded.removeTransitionsByIds(patch.removeTransitionIdsOrEmpty());
            removedTimes += embedded.removeTransitionsByTime(patch.removeTransitionsOrEmpty());
            for (VillagerScheduleTransition t : patch.addTransitionsOrEmpty()) {
                if (t != null) {
                    embedded.addOrReplaceTransition(t);
                    added++;
                }
            }
        }

        LOGGER
            .atInfo()
            .log(
                "Applied villager schedule patch %s to %s (-%s ids, -%s by time, +%s transitions)",
                label,
                roleId,
                removedIds,
                removedTimes,
                added
            );
        return true;
    }

    private static boolean hasEmbeddedScheduleForRole(
        @Nonnull VillagerDefinitionCatalog villagerCatalog,
        @Nonnull String scheduleRoleId
    ) {
        for (VillagerDefinition villager : villagerCatalog.allByNpcRoleId().values()) {
            if (!scheduleRoleId.equals(villager.effectiveScheduleRoleId())) {
                continue;
            }
            VillagerScheduleDefinition embedded = villager.getWeeklySchedule();
            if (embedded != null && !embedded.getTransitions().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void warnUnknownLocations(
        @Nonnull VillagerSchedulePatchDefinition patch,
        @Nonnull ScheduleLocationCatalog locationCatalog,
        @Nonnull String label
    ) {
        for (VillagerScheduleTransition t : patch.addTransitionsOrEmpty()) {
            if (t == null) {
                continue;
            }
            String loc = t.getLocation();
            if (loc == null || loc.isBlank()) {
                continue;
            }
            if (!locationCatalog.isKnownSymbol(loc)) {
                LOGGER.atWarning().log(
                    "Villager schedule patch %s references unknown location symbol '%s'",
                    label,
                    loc.trim()
                );
            }
        }
    }
}
