package com.hexvane.aetherhaven;

public final class AetherhavenConstants {
    public static final String PLOT_SIGN_ITEM_ID = "Aetherhaven_Plot_Sign";
    public static final String CHARTER_ITEM_ID = "Aetherhaven_Charter";
    /** Block type id matches item id for block items. */
    public static final String CHARTER_BLOCK_TYPE_ID = "Aetherhaven_Charter";

    public static final String PAGE_PLOT_CONSTRUCTION = "AetherhavenPlotConstruction";
    /** Management bookcase after build; separate id so OpenCustomUI resolves the correct supplier. */
    public static final String PAGE_PLOT_MANAGEMENT = "AetherhavenPlotManagement";
    public static final String PAGE_PLOT_SIGN_ADMIN = "AetherhavenPlotSignAdmin";
    public static final String PAGE_CHARTER_TOWN = "AetherhavenCharterTown";
    public static final String PAGE_PLOT_PLACEMENT = "AetherhavenPlotPlacement";

    /** Reserved for OpenCustomUI wiring; dialogue is opened from NPC action or commands. */
    public static final String PAGE_DIALOGUE = "AetherhavenDialogue";

    public static final String PLOT_PLACEMENT_TOOL_ITEM_ID = "Aetherhaven_Plot_Placement_Tool";

    /** Non-block token: player must hold in inventory to select this plot type in the placement tool. */
    public static final String PLOT_TOKEN_INN_PLACEHOLDER = "Aetherhaven_Plot_Token_Inn";

    /** Inn construction id in {@code constructions.json} and building POI file name. */
    public static final String CONSTRUCTION_INN_V1 = "inn_v1";

    public static final String QUEST_BUILD_INN = "q_build_inn";

    public static final String MANAGEMENT_BLOCK_TYPE_ID = "Aetherhaven_Management_Block";

    public static final String ELDER_NPC_ROLE_ID = "Aetherhaven_Elder_Lyren";

    public static final String INNKEEPER_NPC_ROLE_ID = "Aetherhaven_Innkeeper";

    /** Shown during autonomous campfire “eating”; matches vanilla cooked meat (consume / third-person eat anim). */
    public static final String CAMPFIRE_EAT_ITEM_ID = "Food_Wildmeat_Cooked";

    /**
     * NPC role state with {@code BodyMotion: Nothing} while {@link com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem}
     * moves or plays POI animations, so Idle {@code WanderInRect} does not fight scripted motion.
     */
    public static final String NPC_STATE_AUTONOMY_POI = "AetherhavenAutonomy";

    public static final String PAGE_VILLAGER_NEEDS = "AetherhavenVillagerNeeds";

    /** {@link com.hypixel.hytale.server.core.inventory.ItemStack.Metadata#BLOCK_HOLDER} key for plot sign items. */
    public static final String ITEM_METADATA_BLOCK_HOLDER = "BlockHolder";

    private AetherhavenConstants() {}
}
