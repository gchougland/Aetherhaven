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

    /** Per-construction prefab block material lists for hard difficulty (generated). */
    public static final String PREFAB_MATERIALS = "Server/Aetherhaven/Buildings/PrefabMaterials";

    /** Relative to pack root: villager weekly schedule JSON per NPC role id (recursive). */
    public static final String VILLAGER_SCHEDULES = "Server/Aetherhaven/VillagerSchedules";

    /** Relative to pack root: villager gameplay metadata JSON (dialogue keys, rep, inn pool, schedule bindings). */
    public static final String VILLAGERS = "Server/Aetherhaven/Villagers";

    /** Townsfolk character definitions (world pool). */
    public static final String TOWNSFOLK = "Server/Aetherhaven/Townsfolk";

    /** Townsfolk personality traits. */
    public static final String PERSONALITIES = "Server/Aetherhaven/Personalities";

    /** Bard song catalog and related config. */
    public static final String BARD = "Server/Aetherhaven/Bard";

    /** Crossmod shop price contribution JSON (merged into the price catalog). */
    public static final String SHOP_PRICES = "Server/Aetherhaven/ShopPrices";

    /** Crossmod shop loot tables (one JSON file per table id). */
    public static final String SHOP_LOOT = "Server/Aetherhaven/ShopLoot";

    /** Additive quest board pool extensions (partial quest_board shape). */
    public static final String QUEST_BOARD_EXTENSIONS = "Server/Aetherhaven/QuestBoardExtensions";

    /** Patches that inject nodes/choices into existing dialogue trees. */
    public static final String DIALOGUE_PATCHES = "Server/Aetherhaven/DialoguePatches";

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
    public static String prefabMaterialsPrefix() {
        return PREFAB_MATERIALS + "/";
    }

    @Nonnull
    public static String villagerSchedulesPrefix() {
        return VILLAGER_SCHEDULES + "/";
    }

    @Nonnull
    public static String villagersPrefix() {
        return VILLAGERS + "/";
    }

    @Nonnull
    public static String townsfolkPrefix() {
        return TOWNSFOLK + "/";
    }

    @Nonnull
    public static String personalitiesPrefix() {
        return PERSONALITIES + "/";
    }
}
