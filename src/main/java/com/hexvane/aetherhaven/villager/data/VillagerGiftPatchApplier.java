package com.hexvane.aetherhaven.villager.data;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Applies {@link AetherhavenAssetPaths#VILLAGER_GIFT_PATCHES} onto loaded villager definitions. */
public final class VillagerGiftPatchApplier {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private VillagerGiftPatchApplier() {}

    public static int applyAllPackPatches(@Nonnull Gson gson, @Nonnull Map<String, VillagerDefinition> byRole) {
        List<PackJsonFile> files =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.VILLAGER_GIFT_PATCHES);
        int applied = 0;
        for (PackJsonFile f : files) {
            try (InputStream in = Files.newInputStream(f.absolutePath())) {
                VillagerGiftPatchDefinition patch =
                    gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), VillagerGiftPatchDefinition.class);
                if (applyPatch(byRole, patch, f.packName() + ":" + f.absolutePath())) {
                    applied++;
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load villager gift patch %s", f.absolutePath());
            }
        }
        if (applied > 0) {
            LOGGER.atInfo().log("Applied %s villager gift patch file(s) from asset packs", applied);
        }
        return applied;
    }

    /** Visible for tests. */
    public static boolean applyPatch(
        @Nonnull Map<String, VillagerDefinition> byRole,
        @Nonnull VillagerGiftPatchDefinition patch,
        @Nonnull String label
    ) {
        if (patch.getTargetNpcRoleId() == null || patch.getTargetNpcRoleId().isBlank()) {
            LOGGER.atWarning().log("Skipping villager gift patch with missing targetNpcRoleId: %s", label);
            return false;
        }
        String roleId = patch.getTargetNpcRoleId().trim();
        VillagerDefinition def = byRole.get(roleId);
        if (def == null) {
            LOGGER.atWarning().log("Villager gift patch %s targets unknown npcRoleId %s", label, roleId);
            return false;
        }
        if (patch.schemaVersionOrDefault() != VillagerGiftPatchDefinition.SUPPORTED_SCHEMA_VERSION) {
            LOGGER.atWarning().log(
                "Villager gift patch %s schemaVersion %s (expected %s)",
                label,
                patch.schemaVersionOrDefault(),
                VillagerGiftPatchDefinition.SUPPORTED_SCHEMA_VERSION
            );
        }
        int loves = patch.addGiftLovesOrEmpty().size();
        int likes = patch.addGiftLikesOrEmpty().size();
        int dislikes = patch.addGiftDislikesOrEmpty().size();
        def.appendGiftLoves(patch.addGiftLovesOrEmpty());
        def.appendGiftLikes(patch.addGiftLikesOrEmpty());
        def.appendGiftDislikes(patch.addGiftDislikesOrEmpty());
        LOGGER
            .atInfo()
            .log(
                "Applied villager gift patch %s to %s (+%s loves, +%s likes, +%s dislikes)",
                label,
                roleId,
                loves,
                likes,
                dislikes
            );
        return true;
    }
}
