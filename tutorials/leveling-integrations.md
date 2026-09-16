# Optional leveling integrations

Aetherhaven supports Endless Leveling (verified binary: **12.3.0**) and RPG Leveling (**0.3.13**). Neither is required or bundled.

In Aetherhaven's server `config.json`, `LevelingIntegration` accepts:

| Value | Behavior |
| --- | --- |
| `AUTO` (default) | Use Endless Leveling when available, otherwise RPG Leveling. |
| `ENDLESS_LEVELING` | Only use Endless Leveling. |
| `RPG_LEVELING` | Only use RPG Leveling. |
| `NONE` | Disable Aetherhaven's owner-level overrides. |

Restart after changing providers. If both mods are installed, Aetherhaven selects one; it does **not** disable either mod's independent global combat systems. Configure those mods to avoid stacking their native mob multipliers. An unsupported binary/API logs a warning and leaves the rest of Aetherhaven working.

## Scaling behavior

- Town-bound villagers, townsfolk, visiting NPCs and hired guards use their **town owner's level**. Loaded NPCs refresh about every ten seconds. Native health and combat scaling/settings remain under the selected leveling mod's control; Aetherhaven does not add a second stat multiplier.
- Quest-board raid enemies all use one owner-level snapshot taken when the raid starts. The snapshot persists on each enemy across chunk unloads and restarts, and does not change when other defenders arrive or the owner levels up.
- Online player levels are read on that player's world thread. The last observed owner level is stored with the town for offline owners. A new town whose owner has never been observed cannot start a scaled raid until the owner joins and their level is available. Unknown levels do not silently fall back to the accepting player's level or level one.
- Owner changes and provider changes invalidate offline fallbacks. An active raid's snapshot is never translated numerically into another leveling mod's progression; finish active raids before changing providers.
- World disables, role blacklists and native level limits still apply. Endless may clamp requested levels to its configured range. Raid composition/difficulty continues to control which enemies appear.
- Level changes preserve the NPC's health percentage. Overrides are reapplied after load and their runtime entries cleaned on unload/removal.

## Completion XP

With a leveling provider active, a successful quest turn-in grants the completing player XP based on the full XP cost of their current level. `QuestCompletionXpPercent` defaults to `5`; `RaidCompletionXpPercent` defaults to `15`. Both accept 0–100, with zero disabling that reward. A raid uses its raid percentage instead of also receiving the ordinary quest bonus. Native enemy-kill XP remains separate.

Town dialogue quests, town quest-board claims, world-NPC quests and world-NPC board claims award after transitioning the accepted/active quest to completed. Objective updates, failed raids, abandon actions and repeated claims do not award completion XP. RPG maximum-level characters receive no bonus. Offline characters are not queued a second reward. This does not award all nearby defenders: the player claiming the quest receives the completion reward.

Endless uses `getXpForNextLevel(UUID)` and `grantXp(UUID, double)`. RPG uses `getPlayerLevelInfo`'s `getXpNeededForNext()` and `addXP(PlayerRef, double, XPSource)` with source `AetherhavenQuest`, preserving the provider's XP events and level-up logic.

## Compatibility details

Bridges load through each plugin's own classloader. Endless uses `setMobEntityLevelOverride(Ref, int)`, the persistent override component and `applyMobScalingNow`. Version 12.3.0's public setter does not invalidate already-settled NPCs, so the bridge validates and clears **only the target NPC's level-cache fields** before requesting native scaling. It retains native base HP, ranks and augments and never requests a world-wide reroll. This private-cache compatibility shim needs rechecking when Endless changes versions.

RPG uses its public plugin hooks `putSpawnLevelForEntity`, `MobLevelData`, `calculateMonsterHpMultiplier` and `applyHealthModifier`. These per-entity hooks are present in 0.3.13 but are not part of its documented facade. They update native combat/XP level lookups and replace RPG's existing named health modifier. The read API uses `getPlayerLevelInfo(PlayerRef, Store)` to avoid the UUID-only online-holder limitation.

References: [Endless Leveling API](https://github.com/Airijko/Endless-Leveling-API), [RPG Leveling API](https://docs.rpg-leveling.zuxaw.com/api).

## Verification

Run `gradlew test --tests "com.hexvane.aetherhaven.leveling.*"`. Optional binary contract tests look for locally supplied `build/leveling-research/EndlessLeveling.jar` and `RPGLeveling.jar`; they skip when absent. These tests check compatibility, persistence and policy, not a live server simulation.

In game, check each leveling mod separately: start a raid with a defender whose level differs from the owner, inspect enemy levels/HP, injure a guard before leveling up, unload/reload a raid, then repeat with the owner offline. Also verify a world with neither mod loads normally. Native nameplates/HUD and actual combat require this live check.
