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

    /** Debug POI visualization / move tool item id. */
    public static final String POI_TOOL_ITEM_ID = "Aetherhaven_Poi_Tool";

    /**
     * Permission for POI tool use, visualization, and edit. Grant to server operators via the permission system.
     */
    public static final String PERMISSION_POI_TOOL = "aetherhaven.poi.tool";

    /** Non-block token: player must hold in inventory to select this plot type in the placement tool. */
    public static final String PLOT_TOKEN_INN_PLACEHOLDER = "Aetherhaven_Plot_Token_Inn";

    /** Inn construction id; definition in {@code Server/Aetherhaven/Buildings/plot_inn.json}. */
    public static final String CONSTRUCTION_PLOT_INN = "plot_inn";

    /** Market stall plot construction id (Week 4). */
    public static final String CONSTRUCTION_PLOT_MARKET_STALL = "plot_market_stall";

    public static final String CONSTRUCTION_PLOT_FARM = "plot_farm";

    public static final String CONSTRUCTION_PLOT_PARK = "plot_park";

    /** Shared residential prefab; completion is tracked per villager via house management assignment. */
    public static final String CONSTRUCTION_PLOT_HOUSE = "plot_house";

    /** Town hall civic building; definition in {@code Server/Aetherhaven/Buildings/plot_town_hall.json}. */
    public static final String CONSTRUCTION_PLOT_TOWN_HALL = "plot_town_hall";

    public static final String QUEST_BUILD_INN = "q_build_inn";

    public static final String QUEST_BUILD_TOWN_HALL = "q_build_town_hall";

    public static final String QUEST_MERCHANT_STALL = "q_merchant_stall";

    public static final String QUEST_FARM_PLOT = "q_farm_plot";

    public static final String QUEST_HOUSE_ELDER = "q_house_elder";

    public static final String QUEST_HOUSE_INNKEEPER = "q_house_innkeeper";

    public static final String QUEST_HOUSE_MERCHANT = "q_house_merchant";

    public static final String QUEST_HOUSE_FARMER = "q_house_farmer";

    public static final String QUEST_HOUSE_BLACKSMITH = "q_house_blacksmith";

    public static final String NPC_MERCHANT = "Aetherhaven_Merchant";
    public static final String NPC_BLACKSMITH = "Aetherhaven_Blacksmith";
    public static final String NPC_FARMER = "Aetherhaven_Farmer";

    public static final String PLOT_TOKEN_MARKET_STALL = "Aetherhaven_Plot_Token_Market_Stall";

    public static final String PLOT_TOKEN_FARM = "Aetherhaven_Plot_Token_Farm";

    public static final String PLOT_TOKEN_PARK = "Aetherhaven_Plot_Token_Park";

    public static final String PLOT_TOKEN_HOUSE = "Aetherhaven_Plot_Token_House";

    public static final String PLOT_TOKEN_TOWN_HALL = "Aetherhaven_Plot_Token_Town_Hall";

    public static final String PAGE_QUEST_JOURNAL = "AetherhavenQuestJournal";

    public static final String ITEM_QUEST_JOURNAL = "Aetherhaven_Quest_Journal";

    public static final String MANAGEMENT_BLOCK_TYPE_ID = "Aetherhaven_Management_Block";

    /** Town treasury chest; visuals match vanilla {@code Furniture_Dungeon_Chest_Epic}; balance stored on the town record. */
    public static final String TREASURY_BLOCK_TYPE_ID = "Aetherhaven_Treasury";

    /** Currency item; visuals aligned with vanilla {@code Deco_Treasure}. */
    public static final String ITEM_GOLD_COIN = "Aetherhaven_Gold_Coin";

    public static final String PAGE_TREASURY = "AetherhavenTreasury";

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
