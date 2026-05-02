# Changelog

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