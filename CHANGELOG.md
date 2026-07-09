# Changelog

## [2.2.0] - Unreleased

### Fixed

- **Assembled plot chests and benches** — Prefab assembly no longer places chests and workbenches via force `setBlock` on non-ticking chunks (which left container block entities unusable until break-and-replace). Interactive block entities are deferred during incremental assembly and placed once at build completion on ticking chunks with explicit entity attachment.
- **Quest board missing block** — The quest board hitbox now ships in the Quests subplugin pack with its block item, so the block registers reliably when other mod asset packs change load order.

## [2.1.2] - 7/7/2026

### Added

- **Creative menu tab** — Aetherhaven items appear under their own creative library tab (house icon).
- **Tourist portal town records shelf** — The tourist portal prefab now includes a town records shelf so you can manage the plot after it is built.
- **Villager role purge** — `/ah villager purge <role>` removes every loaded NPC of that role in the world (no town-binding filter). Town save data is preserved so `respawn` / `reset` can bring the tracked villager back.
- **Tourist purge** — `/ah tourist purge` removes active visiting tourists in every town in the world. Invited, housed, and citizen tourists are kept; hired guards and guild hall adventurers are not affected.
- **Fixing Stick (NPC debug telemetry)** — World Editor staff can craft the Fixing Stick at the Town Planning Desk (1 stick). Bonk an NPC to write a JSON diagnostics dump to `npc_telemetry` under plugin data and get the filepath in chat. Tracks spawn origin, town and pool bindings, and issue flags. Bonk plays a short combat impact and the NPC’s hurt reaction.

### Fixed

- **Move building preview height** — Opening Move Building now starts the ghost at the building’s stored height, not the terrain-snapped plot sign height.
- **Town member staff assembly finish** — When a second player finishes a build with the building staff, deferred plants/lights and prefab entities are placed and the plot is marked complete (no longer stuck assembling or stripped by rehydrate).
- **Tourist portal demolish and move** — Demolishing or moving a tourist portal building now removes its registry entry so tourists no longer spawn or try to leave at the old location. Moves preserve portal ids and retarget tourists already walking home; demolish reassigns them to another portal when one remains, otherwise despawns them.
- **Duplicate tourist portal registry entries** — The portal is a 2-block structure; only the base cell is registered now. Existing filler-voxel entries are purged on the next tourist tick.
- **Crystal Keeper, Pyrotechnic, Florist, and Bard gifts** — Gift confirm choices used missing lang keys (`give` / `cancel`) and the gift action lacked reaction nodes, so buttons showed as raw keys and giving always failed with “You cannot give that gift right now.”
- **Charter and plot sign interaction** — Town charters and blueprinting plot signs that lost their block-entity link (visible but unusable) can be repaired with `/ah plots repair`. `/ah replace-charter` also re-attaches the charter entity correctly instead of failing with no server log.
- **Broken portal tourists** — Tourists that stood still with empty dialogue (no name, portrait, or greeting) and never left are repaired on load, or removed if they cannot be recovered. Stuck return-home tourists are force-despawned instead of lingering forever.
- **Tourist purge orphan shells** — `/ah tourist purge` now removes legacy unbound `Aetherhaven_Townsfolk` entities (no town/tourist components or save rows) left behind by older tourist bugs, and purges live tourists that lost their town binding.
- **Quest board missing block** — The quest board hitbox now ships in the Quests subplugin pack with its block item, so the block registers reliably when other mod asset packs change load order.

## [2.1.0] - 7/1/2026

### Added

- **Subplugins** — Aetherhaven is split into a core mod plus optional feature packs you can turn on or off per server.
- **Jszza Buildings** — Many new community buildings by Jszza.
- **Jimmy Buildings** — Many new community buildings by Jimmy G.
- **Debug town targeting** — Server staff can run most town debug commands on another player’s town or a town by name.
- **Balloon plot blueprints** — White balloon gifts can drop plot blueprint pages for eligible buildings. Blueprints you already own are skipped; if you have them all, you may get a duplicate instead.
- **Plot crafting bench style filters** — Filter the building list by style so you can browse variants more easily.
- **Plot creator building style** — Tag custom buildings with a style in the plot creator for use with the new filters.
- **Bard song: Aetherhaven** — Elias can play **Aetherhaven by Dreadful Ditties**.
- **Building staff salvage** — Building staffs can be salvaged at the salvage bench like their matching pickaxe tier.
- **Town Journal command** — `/ah journal` opens the Town Journal without needing the journal item.
- **Single villager respawn** — `/ah villager respawn <villager>` (debug) brings one core story citizen back near you and clears duplicate copies.

### Changed

- **Large plot assembly** — Clearing and building very large prefabs is much faster and smoother.

### Fixed

- **Inn bed mount on building complete** — Villagers no longer stay stuck above their bed when their workplace finishes building.
- **Villager activity interruptions** — When a villager is sent to a new job or their schedule changes, they stop eating, sitting, sleeping, and other POI animations instead of carrying them over.
- **Prefab teleporter warps** — Teleporters in community plot blueprints no longer break nearby chunks; bad warps are repaired automatically.
- **Inn visitor pool timing** — Inn guests arrive and reroll at dawn only. Accepting a guest’s quest keeps them for the day; the inn bell recalls guests already staying, not empty slots.
- **Inn visitor duplicates** — Duplicate inn guests are cleaned up and the inn stops over-spawning when guests are already present.
- **Duplicate POI spawn markers** — Buildings no longer pile up extra spawn markers; the POI staff won’t place a second marker on the same spot.
- **Wall wand height** — Walls and towers build at preview height; plot sign height stays independent.
- **Plot crafting bench preview** — Survival players can see the rotatable 3D building preview while the bench is open.
- **Large plot clearing lag** — Less server lag when clearing big buildings, holding the building staff, or with a builder on site.
- **Building staff clearing preview** — Clearing previews stay responsive on large footprints.
- **Quest board item requirements** — Fetch quests no longer ask for items that don’t exist in the game.
- **Quest board fetch turn-in** — Fetch jobs can be turned in to the villager who posted them, including when required items are in your backpack.
- **Fetch quest journal details** — Active fetch jobs in the Town Journal now show the items you need to deliver.
- **Quest board raid quests at high rank** — Raid contracts appear through the full quest rank range.
- **Tourist portal visitors** — Tourists wander and shop by day, return at night, and refresh each morning.
- **Tourist dialogue** — Tourists at the portal now have proper names, portraits, and conversation.
- **Town villager dialogue** — Villagers no longer get stuck and refuse to chat after you close a conversation.
- **Stuck villagers and tourists** — NPCs can recover when wedged on terrain or doorways.
- **Villager first-meeting introductions** — Named residents introduce themselves the first time you talk to them.
- **Builder clearing speed** — Rowan now breaks multiple blocks at once during clearing, same as when placing blocks.
- **Plot token descriptions** — Plot tokens show each building’s own description in the tooltip instead of a generic line.

## [2.0.3] - 6/19/2026

### Added

- **Plot link repair** `/ah plots repair` re-links town records shelves, Gaia statues, treasuries, and shop safes for every completed plot in your town. Town Journal Settings includes a **Repair plot links** button that runs the same check.
- **Plot reconstruct** `/ah plots reconstruct <constructionId> [index] [townName]` clears a plot footprint and re-pastes the prefab (same plot id in town data). Use the index when several buildings share one construction id.
- **Plot link diagnose** `/ah plots diagnose` lists each plot’s link status without changing anything (debug / World Editor).
- **Automatic plot link reconciliation** On world load and every few minutes while players are online, completed plots are checked and special blocks are re-stamped when their link data drifted from `towns.json`.
- **Building staff assembly markers** While a plot is assembling, the building staff shows red markers on blocks to break and markers on spots where blocks will go. Markers swell while you hold the staff to place or break.

### Fixed

- **Sporadic plot/building unlinking** Town records shelves and Gaia altars could show “no construction” or “not linked to a town” after restart or mid-session when block components and `towns.json` drifted apart. Reconciliation, repair commands, and save/load hardening address the common causes.
- **Gaia altar statue linking** New Gaia altars could finish with an unlinked statue (“not linked to a town”) and `/ah plots repair` could not fix them. Plot link stamping now matches the statue block id case-insensitively, stamps the multi-block prop’s base cell, and falls back to scanning the plot footprint when the expected column misses.
- **finishassembly / journal finish-plot** Creative instant-finish could place most blocks but fail before `finishBuild`, leaving shelves and statues with empty link data. Empty frontiers now retry and brute-force remaining blocks; completion always verifies block links afterward.
- **towns.json reload** Re-entering a world no longer overwrites unsaved in-memory town data with an older disk snapshot. Saves keep a `towns.json.bak` fallback; failed loads keep the current in-memory towns.
- **Town records shelf break protection** Linked management blocks (town records shelves) can no longer be broken in survival while the plot still exists in town data, matching treasury and shop safe behavior.
- **Management UI diagnostics** Opening a shelf that cannot resolve its plot now logs whether the block ids, town row, or plot row is missing (server log).
- **Villagers Eating Tools** The villagers now have enough iron in their diets and don't need to eat their tools.
- **Building Process Fixes** A bunch of fixes have been done to the builder and building system to alleviate the issues players have been having.
- **Builder home quest** Rowan sometimes stayed an inn visitor after his hut was finished, which hid the home quest dialogue. Inn-pool promotion and repair now include the builder, and shop completion can promote him even when he is not listed at the inn.
- **Shop spot listing removal** Taking an item off your shop spot with a full inventory no longer disconnects you from the server.
- **Building staff preview markers** Assembly markers no longer flicker or vanish when plot footprint overlays refresh. Destruction and placement markers are sized correctly and stay visible while the staff is held. Furniture placement hints use a wood plank look when a block texture cannot be shown on the marker.

## [2.0.2] - 6/15/2026

### Added

- **New shops to plot creator variants** The bomb, flower, and crystal shops didn't have options for variant in the plot creator staff. Now they do.
- **Plot Unlock Commands** Added commands to unlock specific tokens and a one to unlock all tokens.
- **Journal/Guide Translation** Added translations for all the guide pages and missing parts of journal UI.

### Fixed

- **Building Height Issue** Fixed issue where building was not building at the correct height.
- **Plot Creator Entities** Fixed issue where plot creator wouldn't save item entities placed with the entity tool.
- **Missing Townsfolk Portrait** Added missing portraits for the two newest townsfolk.
- **Shop plot relocation** Moving a shop plot from the town records shelf no longer leaves floating shop spot item displays at the old site.
- **Guard Hiring Bug** A blank dialogue would show after hiring a guard, this is fixed now.
- **Production Upgrade Requirements** The text for production upgrade materials only said "Bars" instead of what type of bars.

## [2.0.1] - 6/15/2026

### Added

- **Plot creator build costs** The materials step fills build costs from your build shape automatically. The build materials menu shows the list without spawning items. You can edit counts, add items from inventory, or clear prefab costs. Hard mode material lists are generated when the server starts and when you save a new building.
- **Rogue Guard Class** Now some townsfolk are Rogues which wield daggers and wear leather armor.

### Changed

- **Floating gift cap** Only one gift balloon can be active per world at a time (was eight). Existing server configs keep their saved `MaxActivePerWorld` until you change it.

### Fixed

- **Plot creator materials menu** F opens a paginated build materials menu with count editing instead of a chest full of spawned items.
- **Founder Monument Crash** Founder monument would cause a crash when it gets damaged. Its no longer able to be damaged.
- **Command mode self visibility** Guard command mode no longer hides your character from yourself, so per-player equipment effects keep working during and after command mode.
- **Command mode hotbar restore** Exiting guard command mode now restores full hotbar item stacks (enchantments, jewelry metadata, durability, and other item data) instead of stripping everything down to item id and quantity.
- **Command post breaking** The guard command post now uses stone break sounds and requires a pickaxe to mine instead of wood sounds and a hatchet.
- **Plot Crafting** Plot crafting now takes gold from backpacks as well.
- **Plot token pickup** Picking up a plot sign now returns the correct configured plot token (building name and metadata) instead of a blank unified token.
- **Custom Plot Tokens in Town Journal** Custom tokens would show up as the regular plot sign instead of their icons.
- **Tourist Pathing** Attempted to fix path issue where tourists walk in circles.

## [2.0.0] - 6/13/2026

### Added

- **Command Post** A new block that when used puts the player in command mode which is a top down RTS view where you can command groups of guards.
- **Quest board** Guild hall quest boards now open a three card work panel. Townsfolk post fetch jobs with rank tiers, rewards, and time limits. Complete jobs for rank XP, turn items in through villager dialogue, and track active board quests in the town journal.
- **Hunt Quests** Daily quest type where the player must kill a certain amount of a mob.
- **Fetch Quests** Daily quest type where the player must get a certain item for villager.
- **Raid Quests** Daily quest type where a group of mobs of a certain type spawns on the outskirts of town and head towards the town. The player must kill all the mobs in the group to complete the quest. Map markers and a Raid Health bar indicate which mobs to kill.
- **Plot Creator Staff** A new staff that allows the player to create and configure a building they've created as a fully working plot for villagers. It walks you through it step by step.
- **POI debug staff overhaul** Three modes (Q): edit existing POIs, place new prefab-saveable POI markers via a configuration GUI (need type, capacity, mount toggle, work equipment), and adventurer spawn markers. JSON building POIs still register on construction and merge with prefab markers (markers win at the same local cell). Guild hall adventurers spawn facing their marker and stand still at the spot.
- **Townsfolk** Non-essential npcs that are chosen from a large pool of hand-designed characters. They each have three personality traits assigned to them that determine what dialogue lines they use. This will be used for tourists, guards, and more.
- **Guild Hall** A place adventurers gather and can be hired as guards.
- **Guards** Adventurer's can be hired as guards that will patrol the town and fight any hostile enemies.
- **Patrol Wand** A new wand that lets you create and assign patrol routes to guards.
- **Glow rings** Artifact rings that shed light while worn from the hand mirror. Gorruk the miner gifts them at 50 and 100 reputation. They can rarely appear in world chest jewelry rolls.
- **Firewood** Dried log fuel that burns about twice as long as charcoal. Seren Fairhollow shares the pattern at 50 reputation. Craft five bundles at the town planning desk from any log trunk and fire essence.
- **Root Remover** Right click a tree trunk to clear buried roots and stumps below ground, leaving dirt and dropping the wood at your feet. Seren Fairhollow shares the recipe at 100 reputation. Craft five at the town planning desk from dirt and life essence.
- **Growth Serum** Right click a young animal to help it grow up right away. Thalen Meadowrun shares the recipe at 50 reputation. Craft it at the alchemist's bench from life essence, bone fragments, and blood petals.
- **Hunting Knife** A one handed blade with iron dagger damage and sword attacks. Kill animals with it to sometimes get extra raw meat, hide, and feathers. Thalen Meadowrun shares the recipe at 100 reputation. Craft it at the weapons bench from iron bars, light leather, and linen scraps.
- **Shop spot** Creative-only stall block for finished building plots. NPC shops roll stock at dawn from loot tables. Use (F) opens a buy panel with ±1/±10/max quantity; look-at HUD shows item, price, and stock. Stall facing rotates the floating item display.
- **Plot Crafting Bench** Plot tokens are now crafted at the plot crafting bench which has a 3D preview of the building.
- **Plot Blueprints** Some buildings are now unlocked by finding and using plot blueprints. Plot tokens found in balloon gifts have been replaced with these blueprints. You will need to delete floating_gift_loot.json if upgrading from an old save to find them.
- **Reworked Vex's Shop** Vex's store now sells items in world through shop spots. He is more of a general store now that sells furniture, blocks, and some consumables.
- **Builder Villager** A new villager and building. The builder helps build out buildings in your town faster.
- **Florist Villager** A new villager and shop that sells plants and flowers.
- **Pyrotechnic Villager** A new villager and shop that sells explosives. The new villager must be found out in the world before he will show up in the Inn.
- **Crystal Keeper Villager** A new villager and shop that sells crystals and gems. The new villager must be found out in the world before he will show up in the Inn.
- **Tourist Portal** A new tourist portal building allows for tourists to come visit your town. They show up some time in the morning, walk around your town, and leave at night. They will buy items from player shops. They can also be invited to live in your town if you build them a house.
- **Player Shop** Vex will now give a quest for the player to build their own shop. Players can put items up for sale and villagers and tourists will occasionally go shopping and buy stuff at player shops.
- **Production Building Upgrades** Production buildings can now be upgraded in the town recoreds shelf.
- **Multiple Balloon Gift Types** Balloon gifts now come in three different types with different loot. Green for jewelry, red for furniture, and white for plot blueprints. They have also been made rarer.
- **Path designer shovel overhaul** Q now cycles five modes including Remove and Style designer. Remove mode shows placed paths and lets you delete them in world. Style designer opens a style manager with a double chest grid for weighted block columns. The status HUD uses key boxes with mode specific help text and a reminder to switch to Place mode.
- **Villager faces** Villagers move their mouth when you talk to them and pick dialogue choices. Their face also reflects how well their needs are met while they walk around town.
- **Villager waves** Befriendable villagers with 75+ reputation may wave at you when you walk nearby while they are idle.
- **Inn Bell** A bell that can be used to respawn the visitors at the Inn.
- **Citizen dawn revival** Town villagers who die return to the charter at dawn so you are not stuck waiting for a Gaia altar.
- **Smokestack** A decorative item for creating a smoke particle effect for chimneys.
- **Bard Villager** A Bard now shows up after the guild hall is built that will play music for you in the guild hall.

### Changed

- **Improved Dialogue GUI** Made the dialogue window look nicer.
- **Building Costs** All buildings now require the crafting benches found in their prefabs.
- **Improved Door Interaction** Villagers and tourists wait to close doors until they reach their destination, avoid closing on another traveler, and temporarily ignore NPC push-apart separation while sharing a doorway so two arrivals no longer wedge each other in the frame.
- **Shop spot interactions** Removed LMB/RMB quantity overlay (it blocked breaking blocks). Player listings use RMB with an item in hand; buys use the F buy UI.
- **Wall Costs** Walls now cost any type of wood instead of planks and now cost some gold to build.
- **Resident Assigning** The gui for assigning residents has been improved.
- **Instances** Town saves were being created in instances which doesn't make much sense, this was fixed. Towns now can't be created in instances, only permanent worlds.

### Fixed

- **Lootr Integration** Fixed loot spawning in lootr chests.

## [1.8.0] - 5/26/2026

### Updated to Hytale 0.5.0

### Removed

- **DynamicTooltipsLib Dependency**
- **MultipleHUD Soft Dependency**

### Added

- **Fibre** Added Fibre to logger production options.

### Changed

- Migrated to Update 5 server APIs: keyed custom HUDs (path tool no longer requires Buuz135 MHUD), `org.joml.Matrix4d` debug overlays, ECS `InventoryChangeEvent` package, `PersistentDisplayName` for founder monuments, and `setPermissionGroups` for commands.
- Manifest `ServerVersion` range is now `>=0.5.0-pre.0 <0.6.0`.

## [1.7.0] - 5/22/2026

### Added

- **Wall wand** Craft at the town planning desk to lay out stone walls in a tilted birds-eye view. Direction arrows place a plot sign and start the next piece along that run; a separate pad picks which side you look from. Tower connection arrows toggle up to two sides (dim when off, bright when on). Completed wall pieces stay in the world but do not appear in the town journal. Primary use on a wall lets you extend the run or remove a piece.
- **Innkeeper park quest** Corin Mosscup can ask you to build the town park as soon as the inn is standing, alongside his house quest.
- **Commands guide** New journal Guide page lists every `/ah` command with permissions, grouped for players, world hosts, and debug tools.
- **Getting Started and mechanics guides** New journal pages cover founding a town, villager needs, reputation, and jewelry (including gemstone stat effects).
- **Quest journal reputation preview** Active quest detail now shows reputation rewards alongside item rewards when a quest grants them.

### Fixed

- **Assembly after re-entering a world** Plot builds that were mid-assembly could lose their in-memory assembly job when leaving and re-entering a world (especially singleplayer). Passive building and journal “Finish one building now” then reported no active job. Jobs are now restored on the world thread with retries, re-created on demand when finishing from the journal, and passively re-registered when a player is in the world.

### Changed

- **Town command permissions** Town membership commands (`/ah town invite`, `accept`, and related) are available in Adventure mode by default.
- **Command help text** Simplified English descriptions for `/ah` tab help.
- **Removed `/ah plot`** Removed the old plot sign admin command that gave configured plot tokens (use the town planning desk and plot tokens instead).

## [1.6.0] - 5/19/2026

### Added

- **World difficulty** Choose Easy, Normal, Hard, or custom building cost multipliers per world. Hard mode requires every block from each building blueprint. The founding player sets difficulty when placing the first town charter; operators can change it later in the journal Settings tab or with `/ah difficulty`.
- **Plot construction deposits** Plot sign building UI shows a scrollable material grid with icons. Deposit materials over time; gold shows as one spendable total versus required amount.
- **Breakable container coins** Crates, barrels, pots, sacks, and coffins in the world can rarely drop gold coins when smashed. Uses weighted rolls so most breaks find nothing and one or two coins are less common.
- **Town Borders on Map** Added town border on the map with a town waypoint. Can be toggled per player in the journal.

### Fixed

- **Gaia's Draught** Fixed some issues with it not having the right number of uses and not being able to store it in chests.

### Changed

- **Guide Image Resolution** Resized guide images to be smaller. Might help with black texture issues.
- **Plot Token Recipes** All plot tokens now take 5 gold coins to craft and can be salvaged for 5 gold coins.
- **Misc Recipes** Changed any recipes that used wood planks to take any kind of wood for consistency.

## [1.5.1] - 5/15/2026

### Fixed

- **Variant Display In Journal** Variants displayed as the regular version in the journal, this has been fixed.
- **Production Storage** Extracting resources with a full inventory would delete them, this has been fixed.
- **Jewelry UI Crash** Fixed errors in appraisal and hand mirror guis.

## [1.5.0] - 5/13/2026

### Added

- **Reworked Journal** The quest journal is now the Town Journal with many more features in it.
  - **Guide** The journal now has a page for an in-game mod wiki of Aetherhaven. It also has support for Voile.
  - **Town** A page where you can see all villagers and plots. You can choose to destroy plots from this page and you can see villagers current locations and reputation easily.
  - **Reworked Quests** Quests page has been improved a bit.
  - **Settings & Debug** A page where you can modify some of the mods setting from in-game and buttons for repairing things easily.
- **Floating Gift Furniture** Floating gifts can now contain random furniture.
- **Token Salvage** Tokens from balloons can be salvaged for gold coins.

### Changed

- **Ore Production Storage** Corrected maximum storage for all ores to be 25.
- **Taxes** Taxes are now collected whether or not the town is loaded as long as one of the town members is on the server. Also added a notification each morning saying how much was earned.
- **Removed Test Villager** Removed old test villager that isn't used any more.
- **Large Prefab Sectioned** Large prefabs like the potted treehouse are now built in smaller sections at a time to prevent lag.

### Fixed

- **Floating Gift** Fixed errors related to floating gift balloons.
- **Variant houses** Fixed bug where variant houses don't count for quests.
- **Produciton GUI** Fixed occassional customUI error in the produciton gui.
- **Villager Despawn** Added some protection to villagers so they don't get despawned by the game.
- **Passive building** Fixed a rare crash when walking away from a town while a building was still assembling.

## [1.4.0] - 5/8/2026

### Added

- **Wood scaffolding** Added a scaffold block for reaching higher places with the building staff (but also any other building the player wants to do). If you break the bottom they all fall to the ground. You can use F on the side of a scaffold to have it stack vertically, or on the top of the scaffold to build out horizontally.
- **Plot Token Icons** Changed plot tokens icons to reflect the building its for.
- **Miner Block Options** Added dirt and gravel to the options for the miner.
- **Variant Buildings** Added new variants for buildings built by HytinyBuilds.
- **Floating Gifts** Balloons carrying gifts will now float by the player occasionally and can be shot down for gold coins and special building variants.
- **Tiered Building Staves** Added 5 tiers of building staff with increasing build radii. Built in the planning desk. Planning desk now has upgrades to unlock each tier of building staff.
- **Replace Charter Command** Added /ah replace-charter command that respawns the charter where its supposed to be in cases where its been broken.

### Changed

- **Town Quest Rewards** Recipe unlocks from quests should now unlock for all players part of a town.
- **Improved Building Performance** Large improvement to building stability and performance to allow for massive buildings.

### Fixed

- **Villager Duplication Bug** Fixed bugs where multiples of a villager would spawn in the Inn.
- **Charter Breaking** Charter would break if block under it was broken, this has been fixed.
- **Building Entites Smackable** Player could smack entities in buildings. This has been fixed.

## [1.3.0] - 5/4/2026

### Added

- **Gaia's Draught:** Implemented new upgradeable, reusable healing Draught unlocked from the first mob killing quest from the Priestess. This draught can be upgraded by finding two new resources (Shard of Gaia, Verdant Catalyst) rarely in loot chests.
- **Priestess Healing:** Priestess can now heal the player for gold coins.
- **Varied Dialogue:** Added a bunch of new dialogue lines for villagers so they say different things each day. Including hint dialogue for gifts.
- **Various Sound:** Added sound cues to several villager actions, like opening geodes and appraisal.
- **Memories:** Added icons and lang keys for Aetherhaven memories.

### Changed

- **Building Resource Costs:** Changed resource costs for buildings to be closer to actual block counts.
- **Treasury Remote Usage:** Most things that require gold coins can now take from both the treasury and player inventory.
- **Refactored lang files:** Refactored the monolithic language file into a bunch more organized smaller ones.

### Fixed

- **Floating blocks when building:** Fixed issue where blocks would remain where filler blocks were in the prefab.
- **Production GUI:** Fixed spamming of ack gate from progress bars, clicks should be more responsive now in menu.
- **Charter:** Added prevention of building a plot on top of the charter.

## [1.2.1] - 5/3/2026

### Added

- **ProductionTimeMultiplier:** Added new config option for speeding up or slowing down production speed globally.

### Fixed

- **Production Page Error:** Fixed bug that was causing crash in the Production/Unlock GUI
- **Item Range:** Fixed interaction range for most items.
- **HStats:** Fixed issue with HStats integration.

### Changed

- **Default Gold Coins in Loot Chests:** Change default gold coins in loot chests to be 5-10 instead of 5-20.
- **Plot Signs:** Plot signs now use resources from nearby chests.

## [1.2.0] - 5/2/2026

### Added

- **Building Staff:** Implemented a new building mechanic where you use the building staff to paint the buildings into existence. Buildings also slowly build themselves but is much slower.
- **Plot Command:** Added commands to list and remove plots in case of plot issues.

### Fixed

- **Path tool / world crash:** Preview no longer calls `World.getBlockType` from the path preview tick (that could load chunks and tick the entity store nested inside `Store.tick`, causing `Store is currently processing!` and a world shutdown). Replace checks use in-memory chunks only, same as path grounding.

### Changed

- **Path tool:** New sessions default to **width 5** (was 1). Saved width per player is unchanged.
- **Path tool:** Paths can be planned and placed **under rubble** (`Rubble_*` block ids): grounding targets the terrain below, and on commit rubble above the surface is **broken** with normal break behavior (drops) before the path block is placed.
- **Town Permissions:** Each players permissions now has its own window which has many more permissions than before.
- **Starter Kit command:** Modified starter kit command to give building wand and not give plot token or quest book. (They are given by the villagers anyways)

## [1.1.0] - 5/1/2026

### Added

- **HStats** Integrated HStats for player metrics

### Fixed

- **Villagers stuck as visitors:** Job villagers (stall, farm, shop, altar, mine, lumbermill, barn, etc.) are promoted to **town residents** more reliably: when the **build finishes**, when you **turn in the quest in dialogue**, when **`/aetherhaven villager reset`** runs (includes inn-pool repair like **`fixinn`**), and an extra repair pass right after construction completes.
- **Town needs / management:** All saved residents can show in the list even when their NPC isn’t in a loaded chunk (not only elder/innkeeper).

### Changes

- **Job building completion:** If the matching quest is already marked **complete** but promotion never ran (e.g. the NPC wasn’t loaded when the build finished), finishing or revisiting that building can still apply resident promotion instead of doing nothing.

## [1.0.0] - 4/30/2026 - The Production Update

### Added

- **Production Systems** Implemented systems for buildings/villagers producing resources
- **Logger** Added Logger villager and building. Produces wood products.
- **Miner** Added Miner villager and building. Produces cobblestones and ore.
- **Rancher** Added Rancher villager and building. Produces animal products.

### Changes

- **Farmer Production** Farmer now produces crops and life essence.
- **Updated Gift Preferences** All villagers now have intended gift preferences.

## [0.9.2] - 4/30/2026

### Fixed

- **Plot placement & charter move:** the white/red building outline (and gray outlines for other plots) stays on screen instead of flashing once and disappearing. (The path tool was clearing debug overlays in the background for anyone with path-tool permission, even when the tool wasn’t in your hand.)
- **Villager doors:** villagers opening or closing doors use the same kind of placement checks as the base game, so doors are less likely to shift, clip, or break—especially on wide or multi-piece doors.

### Added

- **Innkeeper:** extra dialogue when the inn has no visitors in rotation, so the conversation still fits the situation.
- **Debug / staff (town quest access + command permissions):** `/aetherhaven villager reset` — removes broken town-villager tracking, respawns villagers near you, and updates IDs across town data (homes, inn pool, locks, registry) while keeping quests, reputation, and gifts where the mod can carry them forward.

### Changes

- **Plot placement UI:** your building’s outline is always shown first; in very large towns, only the closest other plots get a gray outline so the important box stays easy to see.
- **Debug commands:** the `DebugCommandsEnabled` config switch is removed. `/aetherhaven` debug subcommands are always available; who can run them is controlled only by the game’s command permissions (same as other commands).

## [0.9.1] - 4/27/2026

### Fixed

- MultipleHUD issues with Path Tool
- Bug where doors get raised a block when villagers go through them

## [0.9.0] - 4/26/2026

### Added

- **Path tool:** build, preview, and place custom paths in town; integrates with town villager pathing for travel between locations.

## [0.8.1] - 4/25/2026

### Added

- **Debug command:** `/aetherhaven villager fixinn` (`/ah villager fixinn`) repairs inn-pool consistency for your current town by re-locking quest-critical inn visitors, promoting eligible visitors to residents when their plot is already complete, and removing stale non-visitor entries from the inn pool list.

### Fixed

- **Inn visitor rotation / resident desync:** active inn-quest villagers are now protected from morning rotation even when lock state is stale, and inn spawn/fill now prioritizes active quest roles to keep required quest NPCs available.

## [0.8.0] - 4/25/2026

### Added

- **Villager gifting:** Give items to town residents through dialogue when you are holding something they can accept. Reactions follow each villager’s **gift lists** in their villager JSON (`giftLoves` / `giftLikes` / `giftDislikes`); anything else is **neutral**. Successful gifts apply **reputation**, respect **daily** (one gift per villager per in-game day) and **weekly** caps, play a matching emotion effect, and append an entry to a **per-town gift log** (role + giver), persisted on the town record.
- **Gift history UI** (from **Town needs** on a selected villager): shows **only items you have already given** that villager, grouped into **Loved / Liked / Neutral / Disliked** with a wrapping **item grid** per tier (one icon per item id; latest gift wins if you repeat an item). Shows **gifts used this week** for that relationship. **Previous / next** arrows at the bottom cycle through town residents in the same order as the needs list (wraps). Layout uses a **fixed-height** list area so the window does not jump between empty and filled states.
- **Debug** (requires `DebugCommandsEnabled` in plugin `config.json`): `/aetherhaven gift resetLimits` — clears daily/weekly gift-limit state for all players and all villagers in every town in the world; `/aetherhaven gift fillHistory <npcRoleId>` — appends one town log line per listed love/like/dislike item for your town and that role (for testing the log / UI).
- **Purification Powder:** a new tool that reveals nearby enemy spawn points and lets you cleanse them before they keep sending threats into your town.
- Lootr compatability
- **Event banners for quest/reputation flow:** dialogue quest actions now show top-center event-title banners. Quest **start** and **complete** now use banner headlines with quest name as the large title, and villager **reputation reward unlocks** also use banners with the unlocked item’s name.

### Fixed

- Villagers no longer walk around while sleeping.

## [0.7.0] - 4/24/2026

### Added

- **Server language files** (`server.lang`) for **12 additional locales** alongside `en-US`: Chinese (Simplified `zh-CN` and Traditional `zh-TW`), French (`fr-FR`), German (`de-DE`), Japanese (`ja-JP`), Korean (`ko-KR`), Portuguese Brazil (`pt-BR`), Russian (`ru-RU`), Spanish Spain (`es-ES`) and Latin America (`es-419`), Turkish (`tr-TR`), and Ukrainian (`uk-UA`).
- **Feast system:** serve feasts from the banquet table (costs, town effects, villager gather); Steward’s Ledger, Hearthglass Vigil, and Berrycircle Concord, with innkeeper-reputation unlocks.
- **Treasury:** new tab that shows the **tax calculation** (per-villager lines, adjustments, feast multiplier when applicable, totals).
- **New** `/aetherhaven` subcommands (including starter kit for creative; use `/aetherhaven` help in-game for the list).
- **Town needs UI:** **Teleport / rescue** control (teleport icon) for the **selected** resident—moves them next to you and resets their autonomy/pathing so they can get unstuck. Tooltip explains the action; uses the standard tooltip style with other mod tooltips.

### Changes

- **Improved villager pathfinding** (navigation and feast routing).

### Fixed

- **Daily / morning tax:** tax sometimes did not come in as expected (game-time morning window / tithe application).
- **Reputation rewards:** rewards were not granted in dialogue (including Elder milestones); fixed resolver + pending-milestone handling so recipe/item rewards apply correctly.

## [0.6.0] - 4/23/2026

### Added

- Schedule location **`gaia_altar`**: villagers can be sent to a completed Gaia altar plot (same resolution as inn/park; skipped if the altar is not built). All villager schedules include a **Sunday 08:00–09:00** visit.
- **Jewelry System** that adds rings and necklaces which can be equipped through the Hand Mirror, granting stat increases.
  - Jewelry can be found in loot chests alongside gold coins now.
  - Found jewelry will need to be identified by the merchant to see its stats.
  - Befriending the merchant unlocks the Appraisal Bench and Jewelry Crafting bench allowing players 
  to appraise and craft their own jewelry.

### Changes

- Improved Villager pathfinding to help avoid villagers getting stuck
- Staggered Villager schedules for the park so they don't all go there at the same time.

## [0.5.0] - 4/21/2026

### Added

- **Charter amendments table** (workbench recipe, reputation-gated schematic from the Elder): place in town to open **charter amendments**—choose **tier 1** tithe style (per-resident vs needs-weighted) and **tier 2** town specialization (mining, logging, farming, smithing). Choices persist on the town record and affect treasury math / future hooks. Includes custom block model and UI with dedicated icons, tooltips, and tier layout.
- **Founder’s monument** block: place a **stone statue** of the placer’s **cosmetics silhouette** (resolved from the cosmetic registry, not the base player model alone), with **Statue** collision, **DoublePipe** placement rotation from the placer’s facing, and **block-entity persistence** so the statue survives reloads.
- **Mod icon** for the plugin package.
- Dialogue to the Elder for explaining how to obtain gold coins

### Changes

- Added gold coins to most quest rewards
- Updated Farm prefab
- **Improved villager pathfinding and idle behavior:** Schedule-driven **commute** to the assigned plot footprint when villagers would otherwise wander off-plot (e.g. after Gaia revival); POI picking respects work vs break; fence/window and **`Furniture_Village_Counter`** use low wander weights so NPCs rarely end steps on thin colliders or shop counters; Gaia revival reapplies weekly schedules and travel kicks on the world thread.
- **Villagers look at the player when speaking to them:** While dialogue is open (`$Interaction`), dialogue roles inline **watch the player** (Target + HeadMotion Watch; engine rules forbid putting `$Interaction` in a referenced Component).
- Improved plot management, town needs, charter, and related confirmation UIs for clarity and layout.
- Town **idle wander** uses `WanderInRectGroundPreference`: **normal terrain** uses `DefaultGroundWeight` (1.0); **benches, seats, beds**, and configured obstacles use `ObstacleWeight` (default 0.12); optional `GroundWeights` override by `BlockTypeId`.
- Re-organized Town Planning Desk

## [0.4.0] - 4/19/2026

### Added

- **Move completed buildings:** From a plot’s management block, **Move building** opens the placement UI at the current sign; you can nudge, rotate, and confirm. The old footprint is cleared (blocks and fluids), prefab entities in the volume are removed (players and town NPCs stay protected), and the construction is rebuilt at the new pose. A confirmation step warns about items and loose entities in the footprint.
- **Move town charter:** The town charter UI (owner) includes **Move charter**, which opens the same placement-style flow to pick a new charter block position and rotation. Territory stays a chunk-radius square centered on the charter; placement is blocked if any registered plot would fall outside that area. A block preview shows position and rotation before you commit.
- **Dissolve town:** The charter UI (owner) includes **Dissolve town** with a confirmation step. Dissolving removes town NPCs and buildings, clears related POIs and persistence, and destroys the charter block.

### Changes

- **Game time hub:** A single per-world coordinator (`AetherhavenGameTimeCoordinatorSystem`) now drives villager schedules, inn pool ticks, and sprinkler morning passes from **smooth in-game minute** advances and **time discontinuities** (e.g. `/time set`). Replaces per-entity schedule ticking and per-player inn/sprinkler tick spam. Time jumps run schedule logic at the **final** game time only; inn and sprinklers **catch up** skipped mornings when the configured morning hour falls inside the skipped interval, then apply normal logic at the new time.
- Removed crafting time from recipes
- Corrected cost of building Town Hall
- **Debug commands** (command permissions apply):
  - `/aetherhaven reputation set` (`/ah rep set`) — set your reputation with a villager (0–100); crossing milestones **queues** reward dialogue as with normal gains, including tiers skipped when jumping straight to a high value.
  - `/aetherhaven reputation reward list` (`/ah rep reward list`) — list reputation milestone definitions; optional filter by NPC role id.
  - `/aetherhaven reputation reward grant` (`/ah rep reward grant`) — grant one milestone reward immediately (items/recipe learn) and mark it claimed.
  - `/aetherhaven villager list` (`/ah villager list`) — list town villager entity UUIDs and sources (for copy/paste into other commands).
  - `/aetherhaven villager locate` (`/ah villager locate`) — print a town villager’s world position; optional `true` argument to **teleport** to them (**operators / OP group only**).
- Reputation and villager commands accept either a **villager entity UUID** or an **NPC role id** scoped to **your town** (e.g. `Aetherhaven_Blacksmith`). If multiple villagers share the same role, use the UUID from `villager list`.

### Fixed

- Fixed capitlization for the Market Stall prefab which was causing issues on Mac.