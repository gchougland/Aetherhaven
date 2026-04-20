# Changelog

## [0.3.1]

### Changes

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