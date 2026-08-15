package com.hexvane.aetherhaven.plotcreator;

/** One guided placement or POI collection step inside a building kind flow. */
public enum PlotCreatorSubstepType {
    MANAGEMENT_BLOCK,
    PRODUCTION_STORAGE,
    TREASURY_BLOCK,
    SHOP_SAFE_BLOCK,
    INN_BELL_BLOCK,
    GAIA_STATUE_BLOCK,
    PLANNING_DESK_POI,
    WORK_POI,
    SLEEP_POI,
    EAT_POI,
    FUN_POI,
    SHOP_SPOT,
    TOURIST_PORTAL_BLOCK,
    TOURIST_VISIT_POI,
    SHOP_POI,
    QUEST_BOARD_POI,
    INNKEEPER_SPAWN,
    VISITOR_SPAWN,
    GUILD_MASTER_SPAWN,
    ADVENTURER_SPAWN,
    BARD_WORK_POI,
    /** Festival-only merchant or stand NPC; {@code workResidentKind} stores the NPC role id. */
    FESTIVAL_NPC,
    /** Visitor stand used while a festival is running. */
    FESTIVAL_TOURIST_SPOT,
    /** Festival centerpiece cell (for example the Springheart Lettuce). */
    FESTIVAL_CENTERPIECE,
    /** One pig race lane: click the start, then the finish. */
    FESTIVAL_RACE_LANE,
    /** Carnival balloon spawn cell. */
    FESTIVAL_BALLOON_SPAWN,
    /** Carnival whack-a-goblin hole cell. */
    FESTIVAL_WHACK_SPAWN,
    /** Carnival wall mounted wheel cell (stores facing). */
    FESTIVAL_WHEEL,
    /** Tree climb race start pad. */
    FESTIVAL_TREE_CLIMB_START,
    /** Tree climb finish crystal cell. */
    FESTIVAL_TREE_CLIMB_FINISH,
    /** Hallow's Eve maze start pad (stores facing). */
    FESTIVAL_MAZE_START,
    /** Hallow's Eve glowing orb spawn cell. */
    FESTIVAL_MAZE_ORB_SPAWN,
    /** Market Festival judging stand (player stall first, then rival stands). */
    FESTIVAL_MARKET_STAND,
    /** Market Festival floating item display slot on the town stall. */
    FESTIVAL_MARKET_DISPLAY
}
