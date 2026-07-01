package com.hexvane.aetherhaven;

import java.util.UUID;
import javax.annotation.Nullable;

public final class AetherhavenConstants {
    public static final String PLOT_SIGN_ITEM_ID = "Aetherhaven_Plot_Sign";
    public static final String CHARTER_ITEM_ID = "Aetherhaven_Charter";
    /** Block type id matches item id for block items. */
    public static final String CHARTER_BLOCK_TYPE_ID = "Aetherhaven_Charter";

    /** Minecraft-style wooden scaffolding (climbable, physics-linked column). */
    public static final String WOOD_SCAFFOLD_ITEM_ID = "Aetherhaven_Wood_Scaffold";

    public static final String PAGE_PLOT_CONSTRUCTION = "AetherhavenPlotConstruction";
    /** Management bookcase after build; separate id so OpenCustomUI resolves the correct supplier. */
    public static final String PAGE_PLOT_MANAGEMENT = "AetherhavenPlotManagement";
    public static final String PAGE_CHARTER_TOWN = "AetherhavenCharterTown";
    public static final String PAGE_PLOT_PLACEMENT = "AetherhavenPlotPlacement";

    /** Reserved for OpenCustomUI wiring; dialogue is opened from NPC action or commands. */
    public static final String PAGE_DIALOGUE = "AetherhavenDialogue";

    public static final String PAGE_DIFFICULTY = "AetherhavenDifficulty";

    public static final String PLOT_PLACEMENT_TOOL_ITEM_ID = "Aetherhaven_Plot_Placement_Tool";

    public static final String WALL_WAND_ITEM_ID = "Aetherhaven_Wall_Wand";

    public static final String PAGE_WALL_PLACEMENT = "AetherhavenWallPlacement";

    public static final String PAGE_WALL_EDIT = "AetherhavenWallEdit";

    public static final String CONSTRUCTION_PLOT_WALL_SEGMENT = "plot_wall_segment";

    public static final String CONSTRUCTION_PLOT_WALL_GATE = "plot_wall_gate";

    public static final String CONSTRUCTION_PLOT_WALL_TOWER_EASTDOOR_NS = "plot_wall_tower_eastdoor_ns";

    public static final String CONSTRUCTION_PLOT_WALL_TOWER_EASTDOOR_SW = "plot_wall_tower_eastdoor_sw";

    public static final String CONSTRUCTION_PLOT_WALL_TOWER_ENDCAP_S = "plot_wall_tower_endcap_s";

    public static final String CONSTRUCTION_PLOT_WALL_TOWER_OUTERCORNER_SE = "plot_wall_tower_outercorner_se";

    public static final String TOKEN_WALL_SEGMENT = "Aetherhaven_Token_Wall";

    public static final String TOKEN_WALL_GATE = "Aetherhaven_Token_Wall_Gate";

    public static final String TOKEN_WALL_TOWER = "Aetherhaven_Token_Wall_Tower";

    /**
     * Plot sign blocks sit this many cells above the logical anchor used for prefab placement math
     * ({@link com.hexvane.aetherhaven.construction.ConstructionDefinition#resolvePrefabAnchorWorld}). Raises the sign
     * above sunken floors without moving preview or construction.
     */
    public static final int PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR = 1;

    /** Debug POI visualization / move tool item id. */
    public static final String POI_TOOL_ITEM_ID = "Aetherhaven_Poi_Tool";

    /** Purification powder: highlights mob spawn beacons/markers and can remove them. */
    public static final String ITEM_PURIFICATION_POWDER = "Aetherhaven_Purification_Powder";

    /** Root remover: clears underground trunk and root wood from a right clicked tree trunk. */
    public static final String ITEM_ROOT_REMOVER = "Aetherhaven_Root_Remover";

    public static final String ITEM_GROWTH_SERUM = "Aetherhaven_Growth_Serum";

    public static final String ITEM_HUNTING_KNIFE = "Aetherhaven_Hunting_Knife";

    /**
     * Vanilla NPC corpse-despawn puff ({@code Template_Predator} / {@code DeathParticles}); used when purification removes a
     * spawn.
     */
    public static final String PURIFICATION_DESPAWN_PARTICLE_SYSTEM_ID = "Effect_Death_Medium";

    /** Vanilla undead despawn sound — generic “poof” close to common enemy death feedback. */
    public static final String PURIFICATION_DESPAWN_SOUND_EVENT_ID = "SFX_Zombie_Despawn";

    /** One-shot smoke puff when the Crystal Keeper rescue NPC vanishes after dialogue. */
    public static final String CRYSTAL_KEEPER_RESCUE_VANISH_PARTICLE_SYSTEM_ID = "Aetherhaven_Crystal_Keeper_Vanish";

    /** Uses vanilla {@code Entity_Effect_Burn_02.ogg} for the rescue vanish poof. */
    public static final String CRYSTAL_KEEPER_RESCUE_VANISH_SOUND_EVENT_ID = "Aetherhaven_Crystal_Keeper_Vanish";

    /** Vanilla spider cocoon decor; breaking it may spawn the Pyrotechnic rescue NPC. */
    public static final String DECO_SPIDER_COCOON_BLOCK_TYPE_ID = "Deco_Spider_Cocoon";

    /** One-shot smoke puff when the Pyrotechnic rescue NPC vanishes after dialogue. */
    public static final String PYROTECHNIC_RESCUE_VANISH_PARTICLE_SYSTEM_ID = "Explosion_Small";

    public static final String PYROTECHNIC_RESCUE_VANISH_SOUND_EVENT_ID = "SFX_Bomb_Fire_Goblin_Death";
    /** Root interaction id used by preview proxy entities so F/use invokes purification. */
    public static final String ROOT_INTERACTION_PURIFY_SPAWN_USE = "AetherhavenPurifySpawnUse";

    /**
     * Permission for POI tool use, visualization, and edit. Grant to server operators via the permission system.
     */
    public static final String PERMISSION_POI_TOOL = "aetherhaven.poi.tool";

    /** Permission to use the plot creator staff and save custom buildings. */
    public static final String PERMISSION_PLOT_CREATOR = "aetherhaven.plot.creator";

    /** Unified plot token item; {@link com.hexvane.aetherhaven.plot.PlotTokenMetadata} holds the construction id. */
    public static final String PLOT_TOKEN_UNIFIED = "Aetherhaven_Plot_Token";

    /** Plot blueprint page; {@link com.hexvane.aetherhaven.plot.PlotTokenUnlockPageMetadata} holds the construction id. */
    public static final String PLOT_TOKEN_UNLOCK_PAGE = "Aetherhaven_Plot_Token_Unlock_Page";

    public static final String PLOT_CREATOR_STAFF_ITEM_ID = "Aetherhaven_Plot_Creator_Staff";

    public static final String PLOT_CREATOR_HUD_KEY = "AetherhavenPlotCreator";

    /** Survival path spline designer item id (shovel visuals; see item JSON). */
    public static final String PATH_TOOL_ITEM_ID = "Aetherhaven_Path_Tool";

    /** Guard patrol route designer wand. */
    public static final String PATROL_WAND_ITEM_ID = "Aetherhaven_Patrol_Wand";

    public static final String PERMISSION_PATROL_WAND = "aetherhaven.patrol.wand";

    public static final String PATROL_WAND_HUD_KEY = "AetherhavenPatrolWand";

    /** Route particle systems (Wayfinder style visuals). */
    public static final String ROUTE_PARTICLE_TRAIL_ID = "Aetherhaven_Route_Trail";

    public static final String ROUTE_PARTICLE_NODE_ID = "Aetherhaven_Route_Node";

    /** Green variants for the currently selected patrol route preview. */
    public static final String ROUTE_PARTICLE_TRAIL_SELECTED_ID = "Aetherhaven_Route_Trail_Selected";

    public static final String ROUTE_PARTICLE_NODE_SELECTED_ID = "Aetherhaven_Route_Node_Selected";

    /** NPC role state for guard waypoint patrol. */
    public static final String NPC_STATE_GUARD_PATROL = "Patrol";

    /** Channels frontier placement over half a second per brush when aimed at assembly preview cubes. */
    public static final String BUILDING_STAFF_ITEM_ID = "Aetherhaven_Building_Staff";

    /**
     * Fallback brush radius when the held item id is unknown; tiered staffs use {@link
     * com.hexvane.aetherhaven.construction.assembly.BuildingStaffTiers#assemblyBrushChebyshevRadius}.
     */
    public static final int BUILDING_STAFF_ASSEMBLY_BRUSH_CHEBYSHEV_RADIUS_DEFAULT = 1;

    /**
     * Caps per-player clearing obstruction markers ({@link PlotAssemblyPreviewSystem}). One preview entity per cell;
     * keep high enough that typical prefab footprints show every obstructed cell in range.
     */
    public static final int BUILDING_STAFF_CLEARING_PREVIEW_MAX_GHOST_CELLS = 512;

    /**
     * Caps per-player assembly frontier placement markers ({@link PlotAssemblyPreviewSystem}) so huge shells do not
     * spawn tens of thousands of preview entities per player.
     */
    public static final int BUILDING_STAFF_ASSEMBLY_PREVIEW_MAX_GHOST_CELLS = 768;

    /** Model asset for building staff placement preview markers. */
    public static final String MODEL_ASSET_BUILDING_MARKER = "Building_Marker";

    /** Model asset for building staff clearing preview markers. */
    public static final String MODEL_ASSET_DESTRUCTION_MARKER = "Destruction_Marker";

    /** Fallback block texture for furniture and non-cube placement preview markers. */
    public static final String FURNITURE_MARKER_FALLBACK_BLOCK_ID = "Wood_Softwood_Planks";

    /** Root interaction asset id (RequireNewClick false) for staff secondary. */
    public static final String ROOT_INTERACTION_BUILDING_STAFF_SECONDARY = "Aetherhaven_BuildingStaff_Secondary_Root";

    /**
     * Custom cyan/blue sparkle burst when the building staff places an assembly step
     * ({@code Server/Particles/Aetherhaven/Aetherhaven_Building_Staff_Step.particlesystem}).
     */
    public static final String BUILDING_STAFF_STEP_PARTICLE_SYSTEM_ID = "Aetherhaven_Building_Staff_Step";

    /** Mist along look direction while channeling secondary (server-spawned at staff tip with head rotation). */
    public static final String BUILDING_STAFF_STREAM_PARTICLE_SYSTEM_ID = "Aetherhaven_Building_Staff_Stream";

    /**
     * Creative Play {@code Brush_Mode} ({@code CreativePlayDefaults.json}) — short non-looping tick while channeling the
     * assembly brush so audio stops when RMB is released (unlike {@code Brush_Paint} base loop).
     */
    public static final String BUILDING_STAFF_BRUSH_AMBIENT_SOUND_EVENT_ID = "SFX_Creative_Play_Brush_Mode";

    /** Same sting as quest/reputation event titles ({@link com.hexvane.aetherhaven.dialogue.DialogueActionExecutor}). */
    public static final String EVENT_TITLE_SHORT_SUCCESS_SOUND_ID = "SFX_Discovery_Z1_Short";

    /**
     * Vanilla weapon bench craft complete ({@code Server/Item/Items/Bench/Bench_Weapon.json} {@code CompletedSoundEventId}):
     * geode crack, blacksmith repair, jewelry bench craft.
     */
    public static final String SFX_WEAPON_BENCH_CRAFT = "SFX_Weapon_Bench_Craft";

    /**
     * Arcane workbench craft ({@code Server/Item/Items/Bench/Bench_Arcane.json}): jewelry appraisal “reveal” at bench or
     * merchant UI.
     */
    public static final String SFX_ARCANE_WORKBENCH_CRAFT = "SFX_Arcane_Workbench_Craft";

    /**
     * Priestess gold heal: vanilla {@code Sounds/Magic/Heal.ogg} via
     * {@code Server/Audio/SoundEvents/Aetherhaven/Aetherhaven_Magic_Heal.json} (not the potion UI wrapper id).
     */
    public static final String SFX_PRIESTESS_HEAL = "Aetherhaven_Magic_Heal";

    /** Vanilla bench upgrade complete; Gaia's Draught shard / Verdant catalyst tier upgrades (dialogue). */
    public static final String SFX_WORKBENCH_UPGRADE_COMPLETE = "SFX_Workbench_Upgrade_Complete_Default";

    /** Vanilla survival workbench open/close/craft ({@code Bench_Workbench.json}). */
    public static final String SFX_WORKBENCH_OPEN = "SFX_Workbench_Open";
    public static final String SFX_WORKBENCH_CRAFT = "SFX_Workbench_Craft";

    /** One-shot green sparkle along staff→preview for “material” feedback (no item entities). */
    public static final String BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID = "Aetherhaven_Building_Staff_MaterialBead";

    /** Soft beads along the building-staff primary guide bolt (longer dissolve than {@link #BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID}). */
    public static final String BUILDING_STAFF_GUIDE_TRAIL_PARTICLE_SYSTEM_ID = "Aetherhaven_Building_Staff_GuideTrail";

    public static final String PERMISSION_PATH_TOOL = "aetherhaven.path.tool";

    /** Key for the path tool status overlay on {@link com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager}. */
    public static final String PATH_TOOL_HUD_KEY = "AetherhavenPathTool";

    /** Key for the POI tool legend overlay on {@link com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager}. */
    public static final String POI_TOOL_HUD_KEY = "AetherhavenPoiTool";

    /** Revert a placed path that was registered with an undo snapshot (e.g. operator). */
    public static final String PERMISSION_PATH_REVERT = "aetherhaven.path.revert";

    public static final String PATH_BLOCK_PATHWAY = "Soil_Pathway";
    public static final String PATH_BLOCK_MUD_DRY = "Soil_Mud_Dry";
    public static final String PATH_BLOCK_GRASS = "Soil_Grass";
    public static final String PATH_BLOCK_GRASS_DEEP = "Soil_Grass_Deep";

    /** Bypass town ownership checks for commands (grant to moderators). Creative mode also bypasses. */
    public static final String PERMISSION_TOWN_ADMIN = "aetherhaven.town.admin";

    /**
     * Town Journal Settings tab: live config save and repair tools. Operators always have access; grant this permission
     * on a role for trusted server staff without full operator.
     */
    public static final String PERMISSION_JOURNAL_SETTINGS = "aetherhaven.journal.settings";

    /** Non-block token: player must hold in inventory to select this plot type in the placement tool. */
    public static final String PLOT_TOKEN_INN_PLACEHOLDER = "Aetherhaven_Plot_Token_Inn";

    /** Inn construction id; definition in {@code Server/Aetherhaven/Buildings/plot_inn.json}. */
    public static final String CONSTRUCTION_PLOT_INN = "plot_inn";

    /** Market stall plot construction id (Week 4). */
    public static final String CONSTRUCTION_PLOT_MARKET_STALL = "plot_market_stall";

    public static final String CONSTRUCTION_PLOT_PLAYER_SHOP = "plot_player_shop";

    public static final String CONSTRUCTION_PLOT_FARM = "plot_farm";

    public static final String CONSTRUCTION_PLOT_PARK = "plot_park";

    /** Shared residential prefab; completion is tracked per villager via house management assignment. */
    public static final String CONSTRUCTION_PLOT_HOUSE = "plot_house";

    /** Town hall civic building; definition in {@code Server/Aetherhaven/Buildings/plot_town_hall.json}. */
    public static final String CONSTRUCTION_PLOT_TOWN_HALL = "plot_town_hall";

    /** Guild hall workplace; definition in {@code Server/Aetherhaven/Buildings/plot_guild_hall.json}. */
    public static final String CONSTRUCTION_PLOT_GUILD_HALL = "plot_guild_hall";

    /** Blacksmith workplace; definition in {@code Server/Aetherhaven/Buildings/plot_blacksmith_shop.json}. */
    public static final String CONSTRUCTION_PLOT_BLACKSMITH_SHOP = "plot_blacksmith_shop";

    /** Gaia altar workplace; definition in {@code Server/Aetherhaven/Buildings/plot_gaia_altar.json}. */
    public static final String CONSTRUCTION_PLOT_GAIA_ALTAR = "plot_gaia_altar";

    /** Miners hut workplace; definition in {@code Server/Aetherhaven/Buildings/plot_miners_hut.json}. */
    public static final String CONSTRUCTION_PLOT_MINERS_HUT = "plot_miners_hut";

    /** Builder's hut workplace; definition in {@code Server/Aetherhaven/Buildings/plot_builders_hut.json}. */
    public static final String CONSTRUCTION_PLOT_BUILDERS_HUT = "plot_builders_hut";

    /** Lumbermill workplace; definition in {@code Server/Aetherhaven/Buildings/plot_lumbermill.json}. */
    public static final String CONSTRUCTION_PLOT_LUMBERMILL = "plot_lumbermill";

    /** Barn workplace; definition in {@code Server/Aetherhaven/Buildings/plot_barn.json}. */
    public static final String CONSTRUCTION_PLOT_BARN = "plot_barn";

    /** Variant footprint; gameplay id resolves to {@link #CONSTRUCTION_PLOT_BARN}. */
    public static final String CONSTRUCTION_PLOT_BARN_HYTINY = "plot_barn_hytiny";

    /** Non-craftable token for {@link #CONSTRUCTION_PLOT_BARN_HYTINY}. */
    public static final String PLOT_TOKEN_BARN_HYTINY = "Aetherhaven_Plot_Token_Barn_Hytiny";

    /** House variants; gameplay id resolves to {@link #CONSTRUCTION_PLOT_HOUSE}. */
    public static final String CONSTRUCTION_PLOT_HOUSE_HYTINY_COZY_COTTAGE = "plot_house_hytiny_cozy_cottage";

    public static final String CONSTRUCTION_PLOT_HOUSE_HYTINY_JAPANESE_TEA_HOUSE = "plot_house_hytiny_japanese_tea_house";

    public static final String CONSTRUCTION_PLOT_HOUSE_HYTINY_COZY_CAMPER = "plot_house_hytiny_cozy_camper";

    /** Decorative plot only (no gameplay archetype match). */
    public static final String CONSTRUCTION_DECORATION_HYTINY_POTTED_TREEHOUSE = "plot_decoration_hytiny_potted_treehouse";

    public static final String PLOT_TOKEN_HOUSE_HYTINY_COZY_COTTAGE = "Aetherhaven_Plot_Token_House_Hytiny_Cozy_Cottage";

    public static final String PLOT_TOKEN_HOUSE_HYTINY_JAPANESE_TEA_HOUSE = "Aetherhaven_Plot_Token_House_Hytiny_Japanese_Tea_House";

    public static final String PLOT_TOKEN_HOUSE_HYTINY_COZY_CAMPER = "Aetherhaven_Plot_Token_House_Hytiny_Cozy_Camper";

    public static final String PLOT_TOKEN_DECORATION_HYTINY_POTTED_TREEHOUSE = "Aetherhaven_Plot_Token_Decoration_Hytiny_Potted_Treehouse";

    public static final String QUEST_BUILD_INN = "q_build_inn";

    public static final String QUEST_BUILD_TOWN_HALL = "q_build_town_hall";

    public static final String QUEST_TOURIST_PORTAL = "q_tourist_portal";

    public static final String QUEST_BUILD_GUILD_HALL = "q_build_guild_hall";

    public static final String QUEST_HOUSE_GUARD = "q_house_guard";

    public static final String QUEST_HOUSE_GUILD_MASTER = "q_house_guild_master";

    public static final String QUEST_HOUSE_BARD = "q_house_bard";

    public static final String QUEST_MERCHANT_STALL = "q_merchant_stall";

    public static final String QUEST_PLAYER_SHOP = "q_player_shop";

    public static final String QUEST_FARM_PLOT = "q_farm_plot";

    public static final String QUEST_BLACKSMITH_SHOP = "q_blacksmith_shop";

    public static final String QUEST_HOUSE_ELDER = "q_house_elder";

    public static final String QUEST_HOUSE_INNKEEPER = "q_house_innkeeper";

    public static final String QUEST_HOUSE_MERCHANT = "q_house_merchant";

    public static final String QUEST_HOUSE_FARMER = "q_house_farmer";

    public static final String QUEST_HOUSE_BLACKSMITH = "q_house_blacksmith";

    public static final String QUEST_GAIA_ALTAR = "q_gaia_altar";

    /** Priestess follow up: slay undead, earn Gaia's Draught and planning bench recipe. */
    public static final String QUEST_PRIESTESS_GAIA_DRAUGHT = "q_priestess_gaia_draught";

    public static final String QUEST_HOUSE_PRIESTESS = "q_house_priestess";

    public static final String QUEST_HOUSE_MINER = "q_house_miner";

    public static final String QUEST_HOUSE_LOGGER = "q_house_logger";

    public static final String QUEST_HOUSE_RANCHER = "q_house_rancher";

    public static final String QUEST_MINERS_HUT = "q_miners_hut";

    public static final String QUEST_LUMBERMILL = "q_lumbermill";

    public static final String QUEST_BARN = "q_barn";

    public static final String QUEST_PARK_PLOT = "q_park_plot";

    public static final String QUEST_CRYSTAL_KEEPER_RESCUE = "q_crystal_keeper_rescue";

    public static final String QUEST_CRYSTAL_KEEPERS_SHOP = "q_crystal_keepers_shop";

    public static final String QUEST_HOUSE_CRYSTAL_KEEPER = "q_house_crystal_keeper";

    public static final String QUEST_PYROTECHNIC_RESCUE = "q_pyrotechnic_rescue";

    public static final String QUEST_PYROTECHNIC_SHOP = "q_pyrotechnic_shop";

    public static final String QUEST_HOUSE_PYROTECHNIC = "q_house_pyrotechnic";

    public static final String QUEST_FLORIST_SHOP = "q_florist_shop";

    public static final String QUEST_HOUSE_FLORIST = "q_house_florist";

    public static final String QUEST_BUILDERS_HUT = "q_builders_hut";

    public static final String QUEST_HOUSE_BUILDER = "q_house_builder";

    public static final String CONSTRUCTION_PLOT_CRYSTAL_KEEPERS_SHOP = "plot_crystal_keepers_shop";

    public static final String CONSTRUCTION_PLOT_BOMB_SHOP = "plot_bomb_shop";

    public static final String CONSTRUCTION_PLOT_FLOWER_SHOP = "plot_flower_shop";

    public static final String CRYSTALLIZED_PERSON_BLOCK_TYPE_ID = "Aetherhaven_Crystallized_Person";

    public static final String NPC_CRYSTAL_KEEPER = "Aetherhaven_Crystal_Keeper";

    /** One-shot rescue spawn; idle only (no wander) until rescue dialogue completes. */
    public static final String NPC_CRYSTAL_KEEPER_RESCUE = "Aetherhaven_Crystal_Keeper_Rescue";

    public static final String NPC_PYROTECHNIC = "Aetherhaven_Pyrotechnic";

    /** One-shot rescue spawn from a broken spider cocoon. */
    public static final String NPC_PYROTECHNIC_RESCUE = "Aetherhaven_Pyrotechnic_Rescue";

    public static final String NPC_FLORIST = "Aetherhaven_Florist";

    public static final String NPC_BUILDER = "Aetherhaven_Builder";

    public static final String NPC_MERCHANT = "Aetherhaven_Merchant";
    public static final String NPC_BLACKSMITH = "Aetherhaven_Blacksmith";
    public static final String NPC_FARMER = "Aetherhaven_Farmer";

    public static final String NPC_PRIESTESS = "Aetherhaven_Priestess";

    public static final String NPC_MINER = "Aetherhaven_Miner";

    public static final String NPC_LOGGER = "Aetherhaven_Logger";

    public static final String NPC_RANCHER = "Aetherhaven_Rancher";

    public static final String NPC_BARD = "Aetherhaven_Bard";

    /** Shared role for all townsfolk; appearance set per character at spawn. */
    public static final String NPC_TOWNSFOLK = "Aetherhaven_Townsfolk";

    public static final String PLOT_TOKEN_MARKET_STALL = "Aetherhaven_Plot_Token_Market_Stall";

    public static final String PLOT_TOKEN_FARM = "Aetherhaven_Plot_Token_Farm";

    public static final String PLOT_TOKEN_BLACKSMITH_SHOP = "Aetherhaven_Plot_Token_Blacksmith_Shop";

    public static final String PLOT_TOKEN_PARK = "Aetherhaven_Plot_Token_Park";

    public static final String PLOT_TOKEN_HOUSE = "Aetherhaven_Plot_Token_House";

    public static final String PLOT_TOKEN_TOWN_HALL = "Aetherhaven_Plot_Token_Town_Hall";

    public static final String PLOT_TOKEN_GUILD_HALL = "Aetherhaven_Plot_Token_Guild_Hall";

    public static final String PLOT_TOKEN_GAIA_ALTAR = "Aetherhaven_Plot_Token_Gaia_Altar";

    public static final String PLOT_TOKEN_MINERS_HUT = "Aetherhaven_Plot_Token_Miners_Hut";

    public static final String PLOT_TOKEN_BUILDERS_HUT = "Aetherhaven_Plot_Token_Builders_Hut";

    public static final String PLOT_TOKEN_LUMBERMILL = "Aetherhaven_Plot_Token_Lumbermill";

    public static final String PLOT_TOKEN_BARN = "Aetherhaven_Plot_Token_Barn";

    /** Plot production storage (wardrobe visuals); uncraftable, unbreakable by players in survival. */
    public static final String BLOCK_PRODUCTION_STORAGE = "Aetherhaven_Production_Storage";

    public static final String PAGE_PRODUCTION_STORAGE = "AetherhavenProductionStorage";

    public static final String PAGE_PRODUCTION_STORAGE_UNLOCKS = "AetherhavenProductionStorageUnlocks";

    /**
     * Default max stored amount per catalog output when {@code production_catalog.json} omits {@code maxStorage} on
     * that line.
     */
    public static final long PRODUCTION_STORAGE_PER_ITEM_MAX = 200L;

    /**
     * Workplace production advances once per {@link com.hypixel.hytale.component.system.tick.EntityTickingSystem} tick
     * while a worker is at the bench; this matches server tick rate for UI seconds ({@code ticks / this}).
     */
    public static final double PRODUCTION_ENTITY_TICKS_PER_SECOND = 20.0;

    public static final String PAGE_QUEST_JOURNAL = "AetherhavenQuestJournal";

    public static final String ITEM_QUEST_JOURNAL = "Aetherhaven_Quest_Journal";

    public static final String MANAGEMENT_BLOCK_TYPE_ID = "Aetherhaven_Management_Block";

    /** Town treasury chest; visuals match vanilla {@code Furniture_Dungeon_Chest_Epic}; balance stored on the town record. */
    public static final String TREASURY_BLOCK_TYPE_ID = "Aetherhaven_Treasury";

    public static final String SHOP_SAFE_BLOCK_TYPE_ID = "Aetherhaven_Shop_Safe";

    public static final String SHOP_SAFE_ITEM_ID = "Aetherhaven_Shop_Safe";

    /** Currency item; visuals aligned with vanilla {@code Deco_Treasure}. */
    public static final String ITEM_GOLD_COIN = "Aetherhaven_Gold_Coin";

    /** Rare drop from mining ore blocks; opened at the blacksmith for a fee. */
    public static final String ITEM_GEODE = "Aetherhaven_Geode";

    /** Placeable anvil; opens geode UI without gold cost. Recipe learned from blacksmith reputation. */
    public static final String ITEM_GEODE_ANVIL = "Aetherhaven_Geode_Anvil";

    /** OpenCustomUI page id; must match {@code Aetherhaven_Geode_Anvil.json} block interaction. */
    public static final String PAGE_GEODE_ANVIL = "AetherhavenGeodeAnvil";

    /** Craftable furniture; recipe learned from elder reputation. */
    public static final String ITEM_CHARTER_AMENDMENTS_TABLE = "Aetherhaven_Charter_Amendments_Table";

    public static final String PAGE_CHARTER_AMENDMENTS = "AetherhavenCharterAmendments";

    /** Placeable block; spawns a founder statue entity and grants treasury tax bonus while placed. */
    public static final String ITEM_FOUNDER_MONUMENT = "Aetherhaven_Founder_Monument";

    public static final String FOUNDER_MONUMENT_BLOCK_TYPE_ID = "Aetherhaven_Founder_Monument";

    /**
     * Single texture applied to the founder statue's base mesh and every attachment (same silhouette as the placer's
     * skin, stone appearance). Must be under an allowed entity-texture root (e.g. {@code Characters/}), not
     * {@code Blocks/} — the client rejects block paths for player attachment textures.
     */
    public static final String FOUNDER_MONUMENT_STATUE_TEXTURE = "Characters/Aetherhaven/Founder_Monument_Statue_Stone.png";

    /** Gold coins to fully restore an item from 0 durability at the blacksmith (scaled down for partial wear). */
    public static final int BLACKSMITH_REPAIR_COST_FULL = 10;

    /** Gold coins charged per geode opened at the blacksmith UI. */
    public static final int GEODE_OPEN_GOLD_COST = 5;

    /** Daily treasury tithe per housed townsfolk or guard (before town wide tax bonuses). */
    public static final int TOWNSFOLK_TAX_GOLD_PER_DAY = 5;

    /** Gold coins charged per jewelry appraisal at the merchant UI. */
    public static final int JEWELRY_APPRAISAL_GOLD_COST = 10;

    /** Free appraisal UI on the placed bench; must match {@code Aetherhaven_Appraisal_Bench.json}. */
    public static final String PAGE_JEWELRY_APPRAISAL_BENCH = "AetherhavenJewelryAppraisalBench";

    public static final String ITEM_HAND_MIRROR = "Aetherhaven_Hand_Mirror";

    /** Block item id for the appraisal bench. */
    public static final String ITEM_APPRAISAL_BENCH = "Aetherhaven_Appraisal_Bench";

    /** Jewelry crafting bench; recipe from merchant rep 100; must match item JSON. */
    public static final String ITEM_JEWELRY_CRAFTING_BENCH = "Aetherhaven_Jewelry_Crafting_Bench";

    public static final String ITEM_RING_GLOW = "Aetherhaven_Ring_Glow";

    public static final String ITEM_RING_LARGE_GLOW = "Aetherhaven_Ring_Large_Glow";

    /** OpenCustomUI id; must match block interaction in {@code Aetherhaven_Jewelry_Crafting_Bench.json}. */
    public static final String PAGE_JEWELRY_CRAFTING_BENCH = "AetherhavenJewelryCraftingBench";

    public static final String PAGE_PLOT_CRAFTING_BENCH = "AetherhavenPlotCraftingBench";

    public static final String PAGE_QUEST_BOARD = "AetherhavenQuestBoard";

    public static final String QUEST_BOARD_ITEM_ID = "Aetherhaven_Quest_Board";

    public static final String PLOT_CRAFTING_BENCH_ITEM_ID = "Aetherhaven_Plot_Crafting_Bench";

    /** Gold coins spent to craft one plot token at the plot crafting bench. */
    public static final long PLOT_TOKEN_CRAFT_GOLD_COST = 5L;

    public static final String PAGE_TREASURY = "AetherhavenTreasury";

    /** Gaia statue revival UI; OpenCustomUI page id matches block interaction. */
    public static final String PAGE_GAIA_STATUE = "AetherhavenGaiaStatue";

    /** Block and item id for the Statue of Gaia (revival interaction). */
    public static final String STATUE_OF_GAIA_BLOCK_TYPE_ID = "Aetherhaven_Statue_Of_Gaia";

    /** Vanilla ingredient consumed to revive a missing villager at the statue. */
    public static final String ITEM_LIFE_ESSENCE = "Ingredient_Life_Essence";

    /** Concentrated life essence (vanilla); used for high-tier jewelry crafting. */
    public static final String ITEM_LIFE_ESSENCE_CONCENTRATED = "Ingredient_Life_Essence_Concentrated";

    public static final String INGREDIENT_BAR_GOLD = "Ingredient_Bar_Gold";

    public static final String INGREDIENT_BAR_SILVER = "Ingredient_Bar_Silver";

    /** Gold/silver ingots consumed when forging a ring at the jewelry workbench. */
    public static final int JEWELRY_CRAFT_BARS_PER_RING = 5;

    /** Ingot cost is higher for necklaces. */
    public static final int JEWELRY_CRAFT_BARS_PER_NECKLACE = 10;

    /** Life essence stacks consumed per revival at the Gaia statue. */
    public static final int GAIA_STATUE_REVIVE_COST_ESSENCE = 10;

    public static final String ELDER_NPC_ROLE_ID = "Aetherhaven_Elder_Lyren";

    public static final String INNKEEPER_NPC_ROLE_ID = "Aetherhaven_Innkeeper";

    public static final String GUILD_MASTER_NPC_ROLE_ID = "Aetherhaven_Guild_Master";

    public static final String BARD_NPC_ROLE_ID = "Aetherhaven_Bard";

    /** POI tag for the bard's guild hall performance spot. */
    public static final String POI_TAG_BARD = "BARD";

    /** Hired guard NPC role (combat + large patrol wander). Appearance set from townsfolk model at spawn. */
    public static final String NPC_GUARD_KNIGHT = "Aetherhaven_Guard_Knight";
    public static final String NPC_GUARD_ARCHER = "Aetherhaven_Guard_Archer";
    public static final String NPC_GUARD_MAGE = "Aetherhaven_Guard_Mage";
    public static final String NPC_GUARD_ROGUE = "Aetherhaven_Guard_Rogue";

    /** Shown during autonomous campfire “eating”; matches vanilla cooked meat (consume / third-person eat anim). */
    public static final String CAMPFIRE_EAT_ITEM_ID = "Food_Wildmeat_Cooked";

    /**
     * NPC role state with {@code BodyMotion: Nothing} while {@link com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem}
     * moves or plays POI animations, so Idle {@code WanderInRect} does not fight scripted motion.
     */
    public static final String NPC_STATE_AUTONOMY_POI = "AetherhavenAutonomy";

    /** Guild hall display pool: stand still at spawn anchor (no rect wander). */
    public static final String NPC_STATE_GUILD_HALL_DISPLAY = "GuildHallDisplay";

    /** Quest-board raid mobs: timed march toward town charter via staged leash waypoints. */
    public static final String NPC_STATE_RAID_MARCH = "AetherhavenRaidMarch";

    /**
     * Synthetic autonomy target: path to the scheduled plot's footprint (no POI interaction). Vanilla wander rects are
     * centered on the NPC, so off-plot idles (e.g. after Gaia revival) must commute here first.
     */
    public static final UUID SCHEDULE_ZONE_COMMUTE_POI_ID = UUID.fromString("a8e3c2d0-4b1e-4f2a-9c0d-000000000001");

    public static boolean isScheduleZoneCommutePoi(@Nullable UUID poiId) {
        return poiId != null && SCHEDULE_ZONE_COMMUTE_POI_ID.equals(poiId);
    }

    public static final String PAGE_VILLAGER_NEEDS = "AetherhavenVillagerNeeds";

    /** Banquet table block/item id; recipe learned from innkeeper reputation. */
    public static final String ITEM_BANQUET_TABLE = "Aetherhaven_Banquet_Table";

    public static final String PAGE_FEASTS = "AetherhavenFeasts";

    /** Ephemeral POI for feast gathering; excluded from {@code pois.json} persistence. */
    public static final String POI_TAG_FEAST_EPHEMERAL = "FEAST_EPHEMERAL";

    /** Feast table POI: reuse EAT bench visuals; {@link com.hexvane.aetherhaven.feast.FeastService} sets hunger to max on completion. */
    public static final String POI_TAG_FEAST = "FEAST";

    /** {@link com.hypixel.hytale.server.core.inventory.ItemStack.Metadata#BLOCK_HOLDER} key for plot sign items. */
    public static final String ITEM_METADATA_BLOCK_HOLDER = "BlockHolder";

    /** Shared progression Estus style flask; quantity mirrors town stored charges. */
    public static final String ITEM_GAIAS_DRAUGHT = "Aetherhaven_Gaias_Draught";

    public static final String ITEM_SHARD_OF_GAIA = "Aetherhaven_Shard_Of_Gaia";

    public static final String ITEM_VERDANT_CATALYST = "Aetherhaven_Verdant_Catalyst";

    /** Max applications per upgrade path (shard capacity / catalyst heal tier). */
    public static final int GAIAS_DRAUGHT_UPGRADE_MAX_PER_TYPE = 5;

    /** Gold tithe per step; shared by shard and catalyst paths. Index = completed upgrades before this step (0..4). */
    private static final long[] GAIAS_DRAUGHT_UPGRADE_GOLD_BY_STEP = {50L, 80L, 100L, 120L, 150L};

    /**
     * Gold tithe for the next shard upgrade after {@code completedShardUpgrades} successful upgrades (0-based: first
     * upgrade uses completed count 0).
     */
    public static long gaiaDraughtShardUpgradeGoldCost(int completedShardUpgrades) {
        int n = Math.max(0, Math.min(GAIAS_DRAUGHT_UPGRADE_MAX_PER_TYPE - 1, completedShardUpgrades));
        return GAIAS_DRAUGHT_UPGRADE_GOLD_BY_STEP[n];
    }

    /** Gold tithe for the next catalyst upgrade after {@code completedCatalystUpgrades} successful upgrades. */
    public static long gaiaDraughtCatalystUpgradeGoldCost(int completedCatalystUpgrades) {
        int n = Math.max(0, Math.min(GAIAS_DRAUGHT_UPGRADE_MAX_PER_TYPE - 1, completedCatalystUpgrades));
        return GAIAS_DRAUGHT_UPGRADE_GOLD_BY_STEP[n];
    }

    /** Missing health divided by this, rounded up, is the priestess heal gold cost. */
    public static final int PRIESTESS_HEAL_HEALTH_PER_GOLD_COIN = 10;

    public static final String REP_PRIESTESS_75 = "rep_priestess_75";

    public static final String REP_PRIESTESS_100 = "rep_priestess_100";

    public static final String SHOP_SPOT_ITEM_ID = "Aetherhaven_Shop_Spot";
    public static final String SHOP_SPOT_BLOCK_TYPE_ID = "Aetherhaven_Shop_Spot";
    public static final int SHOP_SPOT_ITEM_MAX_STACK = 25;
    public static final String PAGE_SHOP_SPOT_CONFIG = "AetherhavenShopSpotConfig";
    public static final String SHOP_SPOT_HUD_KEY = "AetherhavenShopSpot";
    public static final int SHOP_SPOT_DEFAULT_GOLD_PRICE = 5;
    /** Default NPC stock batch range when a loot table JSON omits stockMin/stockMax. */
    public static final int SHOP_LOOT_DEFAULT_STOCK_MIN = 10;
    public static final int SHOP_LOOT_DEFAULT_STOCK_MAX = 10;

    /** RTS guard command mode — command post block/item id. */
    public static final String COMMAND_POST_ITEM_ID = "Aetherhaven_Command_Post";
    public static final String COMMAND_POST_BLOCK_TYPE_ID = "Aetherhaven_Command_Post";

    public static final String RTS_FLAG_ITEM_ID = "Aetherhaven_Rts_Flag";
    public static final String RTS_SWORD_ITEM_ID = "Aetherhaven_Rts_Sword";
    public static final String RTS_SELECT_ALL_ITEM_ID = "Aetherhaven_Rts_Select_All";
    public static final String RTS_SELECT_KNIGHT_ITEM_ID = "Aetherhaven_Rts_Select_Knight";
    public static final String RTS_SELECT_ARCHER_ITEM_ID = "Aetherhaven_Rts_Select_Archer";
    public static final String RTS_SELECT_MAGE_ITEM_ID = "Aetherhaven_Rts_Select_Mage";
    public static final String RTS_STANCE_BANNER_ITEM_ID = "Aetherhaven_Rts_Stance_Banner";
    public static final String RTS_FREE_ITEM_ID = "Aetherhaven_Rts_Free";
    public static final String RTS_EXIT_ITEM_ID = "Aetherhaven_Rts_Exit";

    public static final String RTS_COMMAND_HUD_KEY = "AetherhavenRtsCommand";
    public static final String RTS_BOX_SELECT_HUD_KEY = "AetherhavenRtsBoxSelect";
    public static final String RTS_GUARD_ROSTER_HUD_KEY = "AetherhavenRtsGuardRoster";

    /** Key for the raid quest health bar overlay on {@link com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager}. */
    public static final String RAID_HEALTH_BAR_HUD_KEY = "AetherhavenRaidHealthBar";
    /** NPC role state while under RTS command (Java-driven seek/aggro). */
    public static final String NPC_STATE_GUARD_RTS_COMMAND = "RtsCommand";

    /** Detection/aggro radius from hold point or guard body while traveling. */
    public static final double RTS_DEFEND_RADIUS = 24.0;
    public static final double RTS_DEFENSIVE_LEASH_RADIUS = 12.0;
    public static final double RTS_AGGRESSIVE_LEASH_RADIUS = 20.0;
    public static final double RTS_STAND_GROUND_LEASH_RADIUS = 0.0;
    public static final double RTS_STAND_GROUND_RANGE = 8.0;
    public static final double RTS_ARRIVE_RADIUS = 4.0;
    /** Attack-move: auto-engage hostiles this close to the guard while traveling to a order point. */
    public static final double RTS_TRAVEL_ENGAGE_RADIUS = 14.0;
    /** Attack-move: max horizontal chase distance from the order point while still traveling. */
    public static final double RTS_ATTACK_MOVE_CHASE_RADIUS = 28.0;
    /** Melee guards begin RTS combat when a hostile is within this horizontal distance. */
    public static final double RTS_MELEE_ENGAGE_RANGE = 3.0;
    /** Ranged guards begin RTS combat when a hostile is within this horizontal distance. */
    public static final double RTS_RANGED_ENGAGE_RANGE = 15.0;
    /** Extra blocks beyond town territory chunk radius for camera pan clamp. */
    public static final int RTS_TERRITORY_OVERLAP_BLOCKS = 16;

    public static final String RTS_MARKER_BLUE_PARTICLE = "Aetherhaven_Rts_Marker_Blue";
    public static final String RTS_MARKER_GREY_PARTICLE = "Aetherhaven_Rts_Marker_Grey";
    public static final String RTS_MARKER_RED_PARTICLE = "Aetherhaven_Rts_Marker_Red";
    /** Model-attached static orbs that follow guards while in command view. */
    public static final String RTS_MARKER_ORB_BLUE_PARTICLE = "Aetherhaven_Rts_Orb_Blue";
    public static final String RTS_MARKER_ORB_GREY_PARTICLE = "Aetherhaven_Rts_Orb_Grey";
    public static final String RTS_MARKER_ORB_RED_PARTICLE = "Aetherhaven_Rts_Orb_Red";
    /** Spiked impact ring for command-sword focus targets (distinct from circle/orb markers). */
    public static final String RTS_FOCUS_TARGET_PARTICLE = "Aetherhaven_Rts_Focus_Target";
    public static final String RTS_SELECT_BOX_DOT_PARTICLE = "Aetherhaven_Rts_SelectBox_Dot";
    public static final String RTS_MOVE_ORDER_MARKER_PARTICLE = "Aetherhaven_Rts_Move_Marker";

    /**
     * Vertical FOV (degrees) for deriving ortho half-height from commander altitude above terrain.
     * Pick drift that worsens when flying higher often means this does not match the client camera.
     */
    public static final float RTS_COMMAND_PICK_VERTICAL_FOV_DEG = 75f;
    /** Viewport width:height ratio for ortho half-width (e.g. {@code 16f / 9f}, {@code 21f / 9f}). */
    public static final float RTS_COMMAND_PICK_ASPECT_RATIO = 16f / 10f;
    /** Fixed camera rig offset in the view-height formula (matches top-down {@code positionOffset Y}). */
    public static final float RTS_COMMAND_PICK_CAMERA_EYE_OFFSET_Y = 3.0f;
    /** Vertical lift for the move-destination particle ring (reduces ground z-fighting). */
    public static final double RTS_MOVE_ORDER_MARKER_SURFACE_LIFT = 0.1;

    public static final String TOURIST_PORTAL_ITEM_ID = "Aetherhaven_Tourist_Portal";
    public static final String TOURIST_PORTAL_BLOCK_TYPE_ID = "Aetherhaven_Tourist_Portal";
    public static final int TOURIST_PORTAL_ITEM_MAX_STACK = 1;

    public static final String INN_BELL_BLOCK_TYPE_ID = "Aetherhaven_Inn_Bell";
    public static final String INN_BELL_RING_SOUND_EVENT_ID = "SFX_Creative_Play_Eyedropper_Select";

    public static final String CONSTRUCTION_PLOT_TOURIST_PORTAL = "plot_tourist_portal";
    public static final String QUEST_HOUSE_TOWNSFOLK = "q_house_townsfolk";

    public static final String POI_TAG_TOURIST_VISIT = "TOURIST_VISIT";

    public static final int TOURIST_DESPAWN_HOUR_MIN = 19;
    public static final int TOURIST_DESPAWN_HOUR_MAX = 22;
    public static final int TOURIST_SPAWN_DAY_END_HOUR_EXCLUSIVE = 12;
    public static final int TOURIST_MIN_DAILY_SPAWNS = 3;
    public static final int TOURIST_MAX_DAILY_SPAWNS = 5;

    /** Ticks without meaningful progress before autonomy teleports to the current destination. */
    public static final int AUTONOMY_STALL_TELEPORT_TICKS = 90;
    /** Horizontal displacement from the stall anchor required to count as real progress. NPCs wedged in a small area
     * can jitter without exceeding this radius.
     */
    public static final double AUTONOMY_STALL_ANCHOR_RADIUS = 2.0;
    /** Meters closer to the leash goal that resets stall tracking. */
    public static final double AUTONOMY_STALL_GOAL_PROGRESS = 0.35;
    /** Reconcile kicks tourists idle at a portal longer than this without starting a visit. */
    public static final long TOURIST_PORTAL_IDLE_KICK_MS = 120_000L;

    public static final String TOURIST_PORTAL_IDLE_PARTICLE = "Aetherhaven_Tourist_Portal_Idle";
    public static final String TOURIST_PORTAL_SPAWN_BURST_PARTICLE = "Aetherhaven_Tourist_Portal_Burst";
    public static final String TOURIST_PORTAL_SPAWN_SOUND = "SFX_Portal_Neutral_Open";

    /** Synthetic POI id for tourist return travel to portal block. */
    public static final UUID TOURIST_PORTAL_RETURN_POI_ID =
        UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    public static boolean isTouristPortalReturnPoi(@Nullable UUID poiId) {
        return TOURIST_PORTAL_RETURN_POI_ID.equals(poiId);
    }

    private AetherhavenConstants() {}
}
