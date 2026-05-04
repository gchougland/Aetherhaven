package com.hexvane.aetherhaven;

import java.util.UUID;
import javax.annotation.Nullable;

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

    /**
     * Vanilla NPC corpse-despawn puff ({@code Template_Predator} / {@code DeathParticles}); used when purification removes a
     * spawn.
     */
    public static final String PURIFICATION_DESPAWN_PARTICLE_SYSTEM_ID = "Effect_Death_Medium";

    /** Vanilla undead despawn sound — generic “poof” close to common enemy death feedback. */
    public static final String PURIFICATION_DESPAWN_SOUND_EVENT_ID = "SFX_Zombie_Despawn";
    /** Root interaction id used by preview proxy entities so F/use invokes purification. */
    public static final String ROOT_INTERACTION_PURIFY_SPAWN_USE = "AetherhavenPurifySpawnUse";

    /**
     * Permission for POI tool use, visualization, and edit. Grant to server operators via the permission system.
     */
    public static final String PERMISSION_POI_TOOL = "aetherhaven.poi.tool";

    /** Survival path spline designer item id (shovel visuals; see item JSON). */
    public static final String PATH_TOOL_ITEM_ID = "Aetherhaven_Path_Tool";

    /**
     * Legacy item id only: older builds placed this block in-world; {@link com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService}
     * still clears it if present. Preview is drawn with {@code DisplayDebug} like the path tool.
     */
    public static final String CONSTRUCTION_PREVIEW_BLOCK_TYPE_ID = "Aetherhaven_Construction_Preview";

    /** Channels frontier placement over half a second per brush when aimed at assembly preview cubes. */
    public static final String BUILDING_STAFF_ITEM_ID = "Aetherhaven_Building_Staff";

    /**
     * Chebyshev distance (max of dx,dy,dz) from the ray-selected center cell: staff places every frontier block in this
     * cube (e.g. 1 = 3×3×3) after the channel completes.
     */
    public static final int BUILDING_STAFF_ASSEMBLY_BRUSH_CHEBYSHEV_RADIUS = 1;

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

    /** One-shot green sparkle along staff→preview for “material” feedback (no item entities). */
    public static final String BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID = "Aetherhaven_Building_Staff_MaterialBead";

    /** Soft beads along the building-staff primary guide bolt (longer dissolve than {@link #BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID}). */
    public static final String BUILDING_STAFF_GUIDE_TRAIL_PARTICLE_SYSTEM_ID = "Aetherhaven_Building_Staff_GuideTrail";

    public static final String PERMISSION_PATH_TOOL = "aetherhaven.path.tool";

    /** Slot id for Buuz135 MHUD ({@code com.buuz135.mhud.MultipleHUD}) when that mod is present. */
    public static final String PATH_TOOL_MHUD_SLOT = "AetherhavenPathTool";

    /** Revert a placed path that was registered with an undo snapshot (e.g. operator). */
    public static final String PERMISSION_PATH_REVERT = "aetherhaven.path.revert";

    public static final String PATH_BLOCK_PATHWAY = "Soil_Pathway";
    public static final String PATH_BLOCK_MUD_DRY = "Soil_Mud_Dry";
    public static final String PATH_BLOCK_GRASS = "Soil_Grass";
    public static final String PATH_BLOCK_GRASS_DEEP = "Soil_Grass_Deep";

    /** Bypass town ownership checks for commands (grant to moderators). Creative mode also bypasses. */
    public static final String PERMISSION_TOWN_ADMIN = "aetherhaven.town.admin";

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

    /** Blacksmith workplace; definition in {@code Server/Aetherhaven/Buildings/plot_blacksmith_shop.json}. */
    public static final String CONSTRUCTION_PLOT_BLACKSMITH_SHOP = "plot_blacksmith_shop";

    /** Gaia altar workplace; definition in {@code Server/Aetherhaven/Buildings/plot_gaia_altar.json}. */
    public static final String CONSTRUCTION_PLOT_GAIA_ALTAR = "plot_gaia_altar";

    /** Miners hut workplace; definition in {@code Server/Aetherhaven/Buildings/plot_miners_hut.json}. */
    public static final String CONSTRUCTION_PLOT_MINERS_HUT = "plot_miners_hut";

    /** Lumbermill workplace; definition in {@code Server/Aetherhaven/Buildings/plot_lumbermill.json}. */
    public static final String CONSTRUCTION_PLOT_LUMBERMILL = "plot_lumbermill";

    /** Barn workplace; definition in {@code Server/Aetherhaven/Buildings/plot_barn.json}. */
    public static final String CONSTRUCTION_PLOT_BARN = "plot_barn";

    public static final String QUEST_BUILD_INN = "q_build_inn";

    public static final String QUEST_BUILD_TOWN_HALL = "q_build_town_hall";

    public static final String QUEST_MERCHANT_STALL = "q_merchant_stall";

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

    public static final String NPC_MERCHANT = "Aetherhaven_Merchant";
    public static final String NPC_BLACKSMITH = "Aetherhaven_Blacksmith";
    public static final String NPC_FARMER = "Aetherhaven_Farmer";

    public static final String NPC_PRIESTESS = "Aetherhaven_Priestess";

    public static final String NPC_MINER = "Aetherhaven_Miner";

    public static final String NPC_LOGGER = "Aetherhaven_Logger";

    public static final String NPC_RANCHER = "Aetherhaven_Rancher";

    public static final String PLOT_TOKEN_MARKET_STALL = "Aetherhaven_Plot_Token_Market_Stall";

    public static final String PLOT_TOKEN_FARM = "Aetherhaven_Plot_Token_Farm";

    public static final String PLOT_TOKEN_BLACKSMITH_SHOP = "Aetherhaven_Plot_Token_Blacksmith_Shop";

    public static final String PLOT_TOKEN_PARK = "Aetherhaven_Plot_Token_Park";

    public static final String PLOT_TOKEN_HOUSE = "Aetherhaven_Plot_Token_House";

    public static final String PLOT_TOKEN_TOWN_HALL = "Aetherhaven_Plot_Token_Town_Hall";

    public static final String PLOT_TOKEN_GAIA_ALTAR = "Aetherhaven_Plot_Token_Gaia_Altar";

    public static final String PLOT_TOKEN_MINERS_HUT = "Aetherhaven_Plot_Token_Miners_Hut";

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

    /** Gold coins charged per jewelry appraisal at the merchant UI. */
    public static final int JEWELRY_APPRAISAL_GOLD_COST = 10;

    /** Free appraisal UI on the placed bench; must match {@code Aetherhaven_Appraisal_Bench.json}. */
    public static final String PAGE_JEWELRY_APPRAISAL_BENCH = "AetherhavenJewelryAppraisalBench";

    public static final String ITEM_HAND_MIRROR = "Aetherhaven_Hand_Mirror";

    /** Block item id for the appraisal bench. */
    public static final String ITEM_APPRAISAL_BENCH = "Aetherhaven_Appraisal_Bench";

    /** Jewelry crafting bench; recipe from merchant rep 100; must match item JSON. */
    public static final String ITEM_JEWELRY_CRAFTING_BENCH = "Aetherhaven_Jewelry_Crafting_Bench";

    /** OpenCustomUI id; must match block interaction in {@code Aetherhaven_Jewelry_Crafting_Bench.json}. */
    public static final String PAGE_JEWELRY_CRAFTING_BENCH = "AetherhavenJewelryCraftingBench";

    public static final String PAGE_TREASURY = "AetherhavenTreasury";

    /** Gaia statue revival UI; OpenCustomUI page id matches block interaction. */
    public static final String PAGE_GAIA_STATUE = "AetherhavenGaiaStatue";

    /** Block and item id for the Statue of Gaia (revival interaction). */
    public static final String STATUE_OF_GAIA_BLOCK_TYPE_ID = "Aetherhaven_Statue_of_Gaia";

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

    /** Shown during autonomous campfire “eating”; matches vanilla cooked meat (consume / third-person eat anim). */
    public static final String CAMPFIRE_EAT_ITEM_ID = "Food_Wildmeat_Cooked";

    /**
     * NPC role state with {@code BodyMotion: Nothing} while {@link com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem}
     * moves or plays POI animations, so Idle {@code WanderInRect} does not fight scripted motion.
     */
    public static final String NPC_STATE_AUTONOMY_POI = "AetherhavenAutonomy";

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

    /**
     * Gold tithe for the next shard upgrade after {@code completedShardUpgrades} successful upgrades (0-based: first
     * upgrade uses completed count 0).
     */
    public static long gaiaDraughtShardUpgradeGoldCost(int completedShardUpgrades) {
        int n = Math.max(0, Math.min(GAIAS_DRAUGHT_UPGRADE_MAX_PER_TYPE - 1, completedShardUpgrades));
        return 100L + 85L * n;
    }

    /** Gold tithe for the next catalyst upgrade after {@code completedCatalystUpgrades} successful upgrades. */
    public static long gaiaDraughtCatalystUpgradeGoldCost(int completedCatalystUpgrades) {
        int n = Math.max(0, Math.min(GAIAS_DRAUGHT_UPGRADE_MAX_PER_TYPE - 1, completedCatalystUpgrades));
        return 150L + 100L * n;
    }

    /** Missing health divided by this, rounded up, is the priestess heal gold cost. */
    public static final int PRIESTESS_HEAL_HEALTH_PER_GOLD_COIN = 10;

    public static final String REP_PRIESTESS_75 = "rep_priestess_75";

    public static final String REP_PRIESTESS_100 = "rep_priestess_100";

    private AetherhavenConstants() {}
}
