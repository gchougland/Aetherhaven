package com.hexvane.aetherhaven.asset;

import javax.annotation.Nonnull;

/**
 * Namespaced paths under each {@link com.hypixel.hytale.assetstore.AssetPack} root so other mods can use
 * {@code Server/Quests/}, {@code Server/Dialogue/}, etc. without colliding with Aetherhaven data.
 */
public final class AetherhavenAssetPaths {
    /** Relative to pack root: quest JSON (recursive). */
    public static final String QUESTS = "Server/Aetherhaven/Quests";

    /** Relative to pack root: dialogue tree JSON (recursive). */
    public static final String DIALOGUE = "Server/Aetherhaven/Dialogue";

    /** Relative to pack root: one construction definition JSON per file (recursive). */
    public static final String BUILDINGS = "Server/Aetherhaven/Buildings";

    /** Relative to pack root: villager weekly schedule JSON per NPC role id (recursive). */
    public static final String VILLAGER_SCHEDULES = "Server/Aetherhaven/VillagerSchedules";

    /** Relative to pack root: villager gameplay metadata JSON (dialogue keys, rep, inn pool, schedule bindings). */
    public static final String VILLAGERS = "Server/Aetherhaven/Villagers";

    private AetherhavenAssetPaths() {}

    @Nonnull
    public static String questsPrefix() {
        return QUESTS + "/";
    }

    @Nonnull
    public static String dialoguePrefix() {
        return DIALOGUE + "/";
    }

    @Nonnull
    public static String buildingsPrefix() {
        return BUILDINGS + "/";
    }

    @Nonnull
    public static String villagerSchedulesPrefix() {
        return VILLAGER_SCHEDULES + "/";
    }

    @Nonnull
    public static String villagersPrefix() {
        return VILLAGERS + "/";
    }
}
