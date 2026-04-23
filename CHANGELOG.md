# Changelog

## [0.6.0] - Unreleased

### Added

- **Jewelry System** that adds rings and necklaces which can be equipped through the Hand Mirror, granting stat increases.
  - Jewelry can be found in loot chests alongside gold coins now.
  - Found jewelry will need to be identified by the merchant to see its stats.
  - Befriending the merchant unlocks the Appraisal Bench and Jewelry Crafting bench allowing players 
  to appraise and craft their own jewelry.

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
- **Debug commands** (requires `DebugCommandsEnabled` in plugin `config.json`):
  - `/aetherhaven reputation set` (`/ah rep set`) — set your reputation with a villager (0–100); crossing milestones **queues** reward dialogue as with normal gains, including tiers skipped when jumping straight to a high value.
  - `/aetherhaven reputation reward list` (`/ah rep reward list`) — list reputation milestone definitions; optional filter by NPC role id.
  - `/aetherhaven reputation reward grant` (`/ah rep reward grant`) — grant one milestone reward immediately (items/recipe learn) and mark it claimed.
  - `/aetherhaven villager list` (`/ah villager list`) — list town villager entity UUIDs and sources (for copy/paste into other commands).
  - `/aetherhaven villager locate` (`/ah villager locate`) — print a town villager’s world position; optional `true` argument to **teleport** to them (**operators / OP group only**).
- Reputation and villager commands accept either a **villager entity UUID** or an **NPC role id** scoped to **your town** (e.g. `Aetherhaven_Blacksmith`). If multiple villagers share the same role, use the UUID from `villager list`.

### Fixed

- Fixed capitlization for the Market Stall prefab which was causing issues on Mac.