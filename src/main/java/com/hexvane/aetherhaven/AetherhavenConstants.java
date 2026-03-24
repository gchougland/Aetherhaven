package com.hexvane.aetherhaven;

public final class AetherhavenConstants {
    public static final String PLOT_SIGN_ITEM_ID = "Aetherhaven_Plot_Sign";
    public static final String CHARTER_ITEM_ID = "Aetherhaven_Charter";
    /** Block type id matches item id for block items. */
    public static final String CHARTER_BLOCK_TYPE_ID = "Aetherhaven_Charter";

    public static final String PAGE_PLOT_CONSTRUCTION = "AetherhavenPlotConstruction";
    public static final String PAGE_PLOT_SIGN_ADMIN = "AetherhavenPlotSignAdmin";
    public static final String PAGE_CHARTER_TOWN = "AetherhavenCharterTown";
    public static final String PAGE_PLOT_PLACEMENT = "AetherhavenPlotPlacement";

    /** Reserved for OpenCustomUI wiring; dialogue is opened from NPC action or commands. */
    public static final String PAGE_DIALOGUE = "AetherhavenDialogue";

    public static final String PLOT_PLACEMENT_TOOL_ITEM_ID = "Aetherhaven_Plot_Placement_Tool";

    /** Non-block token: player must hold in inventory to select this plot type in the placement tool. */
    public static final String PLOT_TOKEN_INN_PLACEHOLDER = "Aetherhaven_Plot_Token_Inn";

    public static final String CONSTRUCTION_INN_PLACEHOLDER = "inn_placeholder";

    public static final String ELDER_NPC_ROLE_ID = "Aetherhaven_Elder_Lyren";

    private AetherhavenConstants() {}
}
