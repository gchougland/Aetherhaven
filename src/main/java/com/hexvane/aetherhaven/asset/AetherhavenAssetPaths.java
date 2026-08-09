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

    /**
     * Relative to pack root: one festival definition JSON per file (recursive). Each festival owns a prefab that
     * replaces the festival square prefab for the length of its day.
     */
    public static final String FESTIVALS = "Server/Aetherhaven/Festivals";

    /** Relative to pack root: villager weekly schedule JSON per NPC role id (recursive). */
    public static final String VILLAGER_SCHEDULES = "Server/Aetherhaven/VillagerSchedules";

    /** Crossmod schedule location symbols mapped to gameplay construction ids. */
    public static final String SCHEDULE_LOCATIONS = "Server/Aetherhaven/ScheduleLocations";

    /** Patches that add, replace, or remove transitions on existing villager weekly schedules. */
    public static final String VILLAGER_SCHEDULE_PATCHES = "Server/Aetherhaven/VillagerSchedulePatches";

    /** Relative to pack root: villager gameplay metadata JSON (dialogue keys, rep, inn pool, schedule bindings). */
    public static final String VILLAGERS = "Server/Aetherhaven/Villagers";

    /** Townsfolk character definitions (world pool). */
    public static final String TOWNSFOLK = "Server/Aetherhaven/Townsfolk";

    /** Townsfolk personality traits. */
    public static final String PERSONALITIES = "Server/Aetherhaven/Personalities";

    /** Dialogue speech blip voice profiles (pitch/cadence). */
    public static final String SPEECH_VOICES = "Server/Aetherhaven/SpeechVoices";

    /** Bard song catalog and related config. */
    public static final String BARD = "Server/Aetherhaven/Bard";

    /** Crossmod shop price contribution JSON (merged into the price catalog). */
    public static final String SHOP_PRICES = "Server/Aetherhaven/ShopPrices";

    /** Crossmod shop loot tables (one JSON file per table id). */
    public static final String SHOP_LOOT = "Server/Aetherhaven/ShopLoot";

    /** Additive quest board pool extensions (partial quest_board shape). */
    public static final String QUEST_BOARD_EXTENSIONS = "Server/Aetherhaven/QuestBoardExtensions";

    /** Per-player world quest board profiles (hub / server boards). */
    public static final String WORLD_QUEST_BOARDS = "Server/Aetherhaven/WorldQuestBoards";

    /** Patches that inject nodes/choices into existing dialogue trees. */
    public static final String DIALOGUE_PATCHES = "Server/Aetherhaven/DialoguePatches";

    /** Patches that append gift loves/likes/dislikes onto existing villager defs. */
    public static final String VILLAGER_GIFT_PATCHES = "Server/Aetherhaven/VillagerGiftPatches";

    /**
     * Crossmod journal guide markdown ({@code <locale>/<topicId>.md}), same front matter as
     * {@code Common/Docs/Hexvane_AetherhavenWiki/}.
     */
    public static final String GUIDE_TOPICS = "Server/Aetherhaven/GuideTopics";

    /** Patches that append {@code sub-topics} onto existing guide hub pages (e.g. {@code villagers}). */
    public static final String GUIDE_PATCHES = "Server/Aetherhaven/GuidePatches";

    /**
     * Crossmod NPC role JSON (not auto-loaded by Hytale). Loaded by Aetherhaven after engine NPC roles and
     * {@code OpenAetherhavenDialogue} exist. Role id = filename without {@code .json}.
     */
    public static final String NPC_ROLES = "Server/Aetherhaven/NpcRoles";

    /**
     * Optional model assets for crossmod NPCs (not under {@code Server/Models}). Loaded into the ModelAsset store
     * before {@link #NPC_ROLES} from the same pack. Model id = filename without {@code .json}.
     */
    public static final String NPC_MODELS = "Server/Aetherhaven/NpcModels";

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
    public static String festivalsPrefix() {
        return FESTIVALS + "/";
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

    @Nonnull
    public static String speechVoicesPrefix() {
        return SPEECH_VOICES + "/";
    }
}
