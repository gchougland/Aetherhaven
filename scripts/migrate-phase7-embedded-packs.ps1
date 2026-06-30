# Moves optional subplugin assets from src/main/resources into subplugin-assets/<Pack>/.
# Safe to re-run: skips missing sources.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$src = Join-Path $root "src\main\resources"
$destBase = Join-Path $root "subplugin-assets"

function Move-RelToPack {
    param([string]$Pack, [string]$RelativePath)
    $from = Join-Path $src $RelativePath
    if (-not (Test-Path -LiteralPath $from)) {
        return
    }
    $to = Join-Path (Join-Path $destBase $Pack) $RelativePath
    $toParent = Split-Path $to -Parent
    New-Item -ItemType Directory -Force -Path $toParent | Out-Null
    Move-Item -LiteralPath $from -Destination $to -Force
}

function Move-DirToPack {
    param([string]$Pack, [string]$RelativeDir)
    $from = Join-Path $src $RelativeDir
    if (-not (Test-Path -LiteralPath $from)) {
        return
    }
    $to = Join-Path (Join-Path $destBase $Pack) $RelativeDir
    $toParent = Split-Path $to -Parent
    New-Item -ItemType Directory -Force -Path $toParent | Out-Null
    Move-Item -LiteralPath $from -Destination $to -Force
}

function Move-LangToPack {
    param([string]$Pack, [string]$LangFileName)
    Get-ChildItem -Path (Join-Path $src "Server\Languages") -Recurse -File -Filter $LangFileName -ErrorAction SilentlyContinue |
        ForEach-Object {
            $rel = $_.FullName.Substring($src.Length + 1)
            Move-RelToPack -Pack $Pack -RelativePath $rel
        }
}

function Move-GlobToPack {
    param([string]$Pack, [string]$SearchRelative, [string[]]$Patterns)
    $searchRoot = Join-Path $src $SearchRelative
    if (-not (Test-Path -LiteralPath $searchRoot)) {
        return
    }
    foreach ($pattern in $Patterns) {
        Get-ChildItem -Path $searchRoot -Recurse -File -Filter $pattern -ErrorAction SilentlyContinue |
            ForEach-Object {
                $rel = $_.FullName.Substring($src.Length + 1)
                Move-RelToPack -Pack $Pack -RelativePath $rel
            }
    }
}

function Move-UiToPack {
    param([string]$Pack, [string]$UiPattern)
    Get-ChildItem -Path (Join-Path $src "Common\UI\Custom\Aetherhaven") -File -Filter $UiPattern -ErrorAction SilentlyContinue |
        ForEach-Object {
            $rel = $_.FullName.Substring($src.Length + 1)
            Move-RelToPack -Pack $Pack -RelativePath $rel
        }
}

# --- ReputationUnlocks ---
$repUnlockItems = @(
    "Aetherhaven_Purification_Powder.json",
    "Aetherhaven_Root_Remover.json",
    "Aetherhaven_Growth_Serum.json",
    "Aetherhaven_Hunting_Knife.json",
    "Aetherhaven_Geode_Anvil.json",
    "Aetherhaven_Gaias_Draught.json",
    "Aetherhaven_Shard_Of_Gaia.json",
    "Aetherhaven_Verdant_Catalyst.json",
    "Aetherhaven_Sprinkler_Iron.json",
    "Aetherhaven_Sprinkler_Cobalt.json",
    "Aetherhaven_Sprinkler_Thorium.json",
    "Aetherhaven_Sprinkler_Adamantite.json",
    "Aetherhaven_Firewood.json"
)
foreach ($item in $repUnlockItems) {
    Move-RelToPack -Pack "ReputationUnlocks" -RelativePath "Server\Item\Items\Aetherhaven\$item"
}
Move-RelToPack -Pack "ReputationUnlocks" -RelativePath "Server\Item\Interactions\Aetherhaven\Aetherhaven_Consume_Gaia_Draught_Charge.json"
Move-DirToPack -Pack "ReputationUnlocks" -RelativeDir "Common\Blocks\Sprinkler"
Move-GlobToPack -Pack "ReputationUnlocks" -SearchRelative "Server\Particles\Aetherhaven" -Patterns @(
    "Aetherhaven_Purification_*",
    "Aetherhaven_Sprinkler_*"
)
Move-GlobToPack -Pack "ReputationUnlocks" -SearchRelative "Common\Icons\ItemsGenerated" -Patterns @(
    "Aetherhaven_Purification_Powder.png",
    "Aetherhaven_Root_Remover.png",
    "Aetherhaven_Growth_Serum.png",
    "Aetherhaven_Hunting_Knife.png",
    "Aetherhaven_Geode_Anvil.png",
    "Aetherhaven_Gaias_Draught.png",
    "Aetherhaven_Shard_Of_Gaia.png",
    "Aetherhaven_Verdant_Catalyst.png",
    "Aetherhaven_Sprinkler_*.png",
    "Aetherhaven_Firewood.png"
)
Move-RelToPack -Pack "ReputationUnlocks" -RelativePath "Common\Items\Aetherhaven\Purification_Powder.png"
Move-RelToPack -Pack "ReputationUnlocks" -RelativePath "Common\Blocks\Benches\Aetherhaven_Geode_Anvil.blockymodel"
Move-RelToPack -Pack "ReputationUnlocks" -RelativePath "Common\Blocks\Benches\Aetherhaven_Geode_Anvil.png"

# --- Jewelry ---
Move-GlobToPack -Pack "Jewelry" -SearchRelative "Server\Item\Items\Aetherhaven" -Patterns @(
    "Aetherhaven_Geode.json",
    "Aetherhaven_Hand_Mirror.json",
    "Aetherhaven_Appraisal_Bench.json",
    "Aetherhaven_Jewelry_Crafting_Bench.json",
    "Aetherhaven_Ring_*.json",
    "Aetherhaven_Necklace_*.json"
)
Move-DirToPack -Pack "Jewelry" -RelativeDir "Common\Items\Aetherhaven\Jewelry"
Move-RelToPack -Pack "Jewelry" -RelativePath "Common\Items\Aetherhaven\Geode.png"
Move-RelToPack -Pack "Jewelry" -RelativePath "Common\Items\Aetherhaven\Geode.blockymodel"
Move-RelToPack -Pack "Jewelry" -RelativePath "Common\Blocks\Jewelry_Crafting_Bench.blockymodel"
Move-RelToPack -Pack "Jewelry" -RelativePath "Common\Blocks\Jewelry_Crafting_Bench.png"
Move-RelToPack -Pack "Jewelry" -RelativePath "Common\Blocks\Benches\Aetherhaven_Jewelry_Bench.blockymodel"
Move-GlobToPack -Pack "Jewelry" -SearchRelative "Common\Icons\ItemsGenerated" -Patterns @(
    "Aetherhaven_Geode.png",
    "Aetherhaven_Hand_Mirror.png",
    "Aetherhaven_Appraisal_Bench.png",
    "Aetherhaven_Jewelry_Crafting_Bench.png",
    "Aetherhaven_Ring_*.png",
    "Aetherhaven_Necklace_*.png"
)
Move-UiToPack -Pack "Jewelry" -UiPattern "Jewelry*.ui"
Move-UiToPack -Pack "Jewelry" -UiPattern "HandMirror.ui"
Move-UiToPack -Pack "Jewelry" -UiPattern "GeodeOpen*.ui"
Move-GlobToPack -Pack "Jewelry" -SearchRelative "Common\UI\Custom" -Patterns @(
    "Aetherhaven_jewelry_tab_*.png"
)
Move-LangToPack -Pack "Jewelry" -LangFileName "aetherhaven_jewelry_geode.lang"

# --- FloatingGifts ---
Move-DirToPack -Pack "FloatingGifts" -RelativeDir "Common\Items\Aetherhaven\Floating_Gift"
Move-GlobToPack -Pack "FloatingGifts" -SearchRelative "Server\Models" -Patterns @(
    "Floating_Gift.json",
    "Floating_Gift_Green.json",
    "Floating_Gift_Red.json"
)
Move-RelToPack -Pack "FloatingGifts" -RelativePath "Server\Item\Block\Hitboxes\Floating_Gift_Hitbox.json"
# Plugin data defaults (classpath / Java) stay in src/main/resources/defaults/ — not embedded packs.
# Move-RelToPack -Pack "FloatingGifts" -RelativePath "defaults\floating_gift_loot.json"

# --- PathDesigner ---
Move-RelToPack -Pack "PathDesigner" -RelativePath "Server\Item\Items\Aetherhaven\Aetherhaven_Path_Tool.json"
Move-DirToPack -Pack "PathDesigner" -RelativeDir "Server\Particles\Aetherhaven\Route"
Move-UiToPack -Pack "PathDesigner" -UiPattern "PathTool*.ui"
Move-UiToPack -Pack "PathDesigner" -UiPattern "AetherhavenPathToolLegend.ui"

# --- Bard ---
Move-RelToPack -Pack "Bard" -RelativePath "Server\Item\Items\Aetherhaven\Aetherhaven_Lute.json"
Move-RelToPack -Pack "Bard" -RelativePath "Common\Icons\ItemsGenerated\Aetherhaven_Lute.png"
Move-RelToPack -Pack "Bard" -RelativePath "Common\Items\Aetherhaven\Lute.blockymodel"
Move-RelToPack -Pack "Bard" -RelativePath "Common\Items\Aetherhaven\Lute_Texture.png"
Move-RelToPack -Pack "Bard" -RelativePath "Common\Characters\Animations\PlayLute.blockyanim"
Move-DirToPack -Pack "Bard" -RelativeDir "Server\Particles\Aetherhaven\Bard"
Move-DirToPack -Pack "Bard" -RelativeDir "Server\Audio\AmbienceFX\Aetherhaven\Bard"
Move-RelToPack -Pack "Bard" -RelativePath "Server\Aetherhaven\Bard\bard_songs.json"
Move-LangToPack -Pack "Bard" -LangFileName "aetherhaven_bard.lang"

# --- AdminTools ---
Move-RelToPack -Pack "AdminTools" -RelativePath "Server\Item\Items\Aetherhaven\Aetherhaven_Poi_Tool.json"
Move-UiToPack -Pack "AdminTools" -UiPattern "Poi*.ui"
Move-UiToPack -Pack "AdminTools" -UiPattern "AetherhavenPoiToolLegend.ui"
Move-UiToPack -Pack "AdminTools" -UiPattern "DifficultyPage.ui"
Move-LangToPack -Pack "AdminTools" -LangFileName "aetherhaven_difficulty.lang"
Move-LangToPack -Pack "AdminTools" -LangFileName "aetherhaven_world_debug.lang"

# --- Rts ---
Move-GlobToPack -Pack "Rts" -SearchRelative "Server\Item\Items\Aetherhaven" -Patterns @(
    "Aetherhaven_Command_Post.json",
    "Aetherhaven_Rts_*.json"
)
Move-DirToPack -Pack "Rts" -RelativeDir "Server\Particles\Aetherhaven\Rts"
Move-GlobToPack -Pack "Rts" -SearchRelative "Common\Icons\ItemsGenerated" -Patterns @(
    "Aetherhaven_Command_Post.png",
    "Aetherhaven_Rts_*.png"
)
Move-LangToPack -Pack "Rts" -LangFileName "aetherhaven_rts.lang"

# --- PatrolRoutes ---
Move-RelToPack -Pack "PatrolRoutes" -RelativePath "Server\Item\Items\Aetherhaven\Aetherhaven_Patrol_Wand.json"
Move-RelToPack -Pack "PatrolRoutes" -RelativePath "Common\Icons\ItemsGenerated\Aetherhaven_Patrol_Wand.png"
Move-RelToPack -Pack "PatrolRoutes" -RelativePath "Common\Items\Aetherhaven\Patrol_Wand.png"
Move-RelToPack -Pack "PatrolRoutes" -RelativePath "Common\Items\Aetherhaven\Patrol_Wand.blockymodel"
Move-UiToPack -Pack "PatrolRoutes" -UiPattern "PatrolWand*.ui"
Move-UiToPack -Pack "PatrolRoutes" -UiPattern "AetherhavenPatrolWandLegend.ui"

# --- PlotCreator ---
Move-GlobToPack -Pack "PlotCreator" -SearchRelative "Server\Item\Items\Aetherhaven" -Patterns @(
    "Aetherhaven_Plot_Creator_Staff.json",
    "Aetherhaven_Plot_Placement_Tool.json",
    "Aetherhaven_Wall_Wand.json"
)
Move-GlobToPack -Pack "PlotCreator" -SearchRelative "Common\Icons\ItemsGenerated" -Patterns @(
    "Aetherhaven_Plot_Creator_Staff.png",
    "Aetherhaven_Wall_Wand.png"
)
Move-RelToPack -Pack "PlotCreator" -RelativePath "Common\Items\Aetherhaven\Building_Staff\Plot_Creator_Staff.png"
Move-DirToPack -Pack "PlotCreator" -RelativeDir "Common\Items\Aetherhaven\Wall_Wand"
Move-UiToPack -Pack "PlotCreator" -UiPattern "PlotCreator*.ui"
Move-UiToPack -Pack "PlotCreator" -UiPattern "PlotPlacementPage.ui"
Move-UiToPack -Pack "PlotCreator" -UiPattern "WallPlacementPage.ui"
Move-RelToPack -Pack "PlotCreator" -RelativePath "Server\Aetherhaven\plot_creator_main_constructions.json"
Move-LangToPack -Pack "PlotCreator" -LangFileName "aetherhaven_plot_creator.lang"
Move-LangToPack -Pack "PlotCreator" -LangFileName "aetherhaven_wall_placement.lang"

# --- Quests ---
Move-GlobToPack -Pack "Quests" -SearchRelative "Server\Item\Items" -Patterns @(
    "Aetherhaven_Quest_Board.json",
    "Aetherhaven_Quest_Journal.json",
    "Aetherhaven_Portal_Enter.json",
    "Aetherhaven_Portal_Exit.json"
)
Move-RelToPack -Pack "Quests" -RelativePath "Common\Icons\ItemsGenerated\Aetherhaven_Quest_Board.png"
Move-UiToPack -Pack "Quests" -UiPattern "QuestBoard*.ui"
Move-RelToPack -Pack "Quests" -RelativePath "Server\Aetherhaven\quest_board.json"
Move-DirToPack -Pack "Quests" -RelativeDir "Server\Aetherhaven\Quests"
Move-DirToPack -Pack "Quests" -RelativeDir "Server\NPC\Roles\Aetherhaven\Raid"
Move-LangToPack -Pack "Quests" -LangFileName "aetherhaven_story_quests.lang"
Move-LangToPack -Pack "Quests" -LangFileName "aetherhaven_quest_board.lang"
Move-LangToPack -Pack "Quests" -LangFileName "aetherhaven_ui_quest_board.lang"
Move-LangToPack -Pack "Quests" -LangFileName "aetherhaven_quests_portals.lang"

# --- Economy ---
Move-GlobToPack -Pack "Economy" -SearchRelative "Server\Item\Items\Aetherhaven" -Patterns @(
    "Aetherhaven_Treasury.json",
    "Aetherhaven_Shop_Safe.json"
)
Move-RelToPack -Pack "Economy" -RelativePath "Common\Blocks\Shop_Safe.blockymodel"
Move-RelToPack -Pack "Economy" -RelativePath "Common\Blocks\Shop_Safe.png"
Move-GlobToPack -Pack "Economy" -SearchRelative "Common\Icons\ItemsGenerated" -Patterns @(
    "Aetherhaven_Shop_Safe.png",
    "Aetherhaven_Treasury.png"
)
Move-UiToPack -Pack "Economy" -UiPattern "Treasury*.ui"

# --- Commerce ---
Move-GlobToPack -Pack "Commerce" -SearchRelative "Server\Item\Items\Aetherhaven" -Patterns @(
    "Aetherhaven_Shop_Spot.json",
    "Aetherhaven_Tourist_Portal.json",
    "Aetherhaven_Banquet_Table.json",
    "Aetherhaven_Inn_Bell.json"
)
Move-RelToPack -Pack "Commerce" -RelativePath "Server\Item\Block\Hitboxes\Aetherhaven_Shop_Spot.json"
Move-RelToPack -Pack "Commerce" -RelativePath "Server\Item\Block\Hitboxes\Aetherhaven_Inn_Bell.json"
Move-DirToPack -Pack "Commerce" -RelativeDir "Server\Particles\Aetherhaven\TouristPortal"
Move-RelToPack -Pack "Commerce" -RelativePath "Common\Icons\ItemsGenerated\Aetherhaven_Shop_Spot.png"
Move-RelToPack -Pack "Commerce" -RelativePath "Common\Icons\ItemsGenerated\Aetherhaven_Banquet_Table.png"
Move-RelToPack -Pack "Commerce" -RelativePath "Common\Icons\ItemsGenerated\Aetherhaven_Inn_Bell.png"
Move-RelToPack -Pack "Commerce" -RelativePath "Common\Blocks\Banquet_Table.blockymodel"
Move-RelToPack -Pack "Commerce" -RelativePath "Common\Blocks\Banquet_Table.png"
Move-RelToPack -Pack "Commerce" -RelativePath "Common\Blocks\Inn_Bell.blockymodel"
Move-RelToPack -Pack "Commerce" -RelativePath "Common\Blocks\Inn_Bell.png"
Move-UiToPack -Pack "Commerce" -UiPattern "ShopSpot*.ui"
Move-RelToPack -Pack "Commerce" -RelativePath "Server\Prefabs\plot_tourist_portal.prefab.json"
Move-RelToPack -Pack "Commerce" -RelativePath "Server\Prefabs\plot_tourist_portal.prefab.json.lpf"
Move-RelToPack -Pack "Commerce" -RelativePath "Server\Aetherhaven\Buildings\plot_tourist_portal.json"
Move-RelToPack -Pack "Commerce" -RelativePath "Server\Aetherhaven\Buildings\PrefabMaterials\plot_tourist_portal.json"
Move-LangToPack -Pack "Commerce" -LangFileName "aetherhaven_shop.lang"
Move-LangToPack -Pack "Commerce" -LangFileName "aetherhaven_tourist.lang"
Move-LangToPack -Pack "Commerce" -LangFileName "aetherhaven_feasts_production.lang"

# --- Guild ---
Move-RelToPack -Pack "Guild" -RelativePath "Server\Prefabs\Guild_Hall.prefab.json"
Move-RelToPack -Pack "Guild" -RelativePath "Server\Prefabs\Guild_Hall.prefab.json.lpf"
Move-RelToPack -Pack "Guild" -RelativePath "Server\Aetherhaven\Buildings\plot_guild_hall.json"
Move-RelToPack -Pack "Guild" -RelativePath "Server\Aetherhaven\Buildings\PrefabMaterials\plot_guild_hall.json"

Write-Host "Phase 7 asset migration complete."
