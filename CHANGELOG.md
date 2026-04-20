# Changelog

## [0.5.0] - 4/20/2026

### Changes

- Improved plot management, town needs, charter, and related confirmation UIs for clarity and layout.

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