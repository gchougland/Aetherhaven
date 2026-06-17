---
name: Commands
description: Chat commands for towns and server tools
author: Hexvane
---

# Commands

`/aetherhaven` and `/ah` are the same. Most players only need the **For players** section below.

**Access** tells you which game mode gets the command by default. **Permission** is what server owners grant manually if access is not enough. Angle brackets are required; square brackets are optional.

## For players

### Town members

- **`/ah town invite <player> [townName]`** — Invite someone online to your town.
  - `<player>` — Player username (must be online).
  - `[townName]` — Full town name with spaces. Omit for your town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.town.invite`
  - Access: Adventure

- **`/ah town accept [townName]`** — Join a town that invited you.
  - `[townName]` — Full town name when you have more than one pending invite. Omit if you only have one.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.town.accept`
  - Access: Adventure

- **`/ah town decline [townName]`** — Turn down a town invite.
  - `[townName]` — Full town name when you have more than one pending invite. Omit if you only have one.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.town.decline`
  - Access: Adventure

- **`/ah town kick <player> [townName]`** — Remove a member from your town.
  - `<player>` — Member username (must be online).
  - `[townName]` — Full town name with spaces. Omit for your own town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.town.kick`
  - Access: Adventure

- **`/ah town role <player> <role> [townName]`** — Set a member role: BUILD, QUEST, or BOTH.
  - `<player>` — Member username (must be online).
  - `<role>` — `BUILD`, `QUEST`, or `BOTH`.
  - `[townName]` — Full town name with spaces. Omit for your own town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.town.role`
  - Access: Adventure

- **`/ah town leave`** — Leave a town you belong to (not as the founder).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.town.leave`
  - Access: Adventure

### Floating gifts

- **`/ah floatinggift next`** — See when your next floating gift balloon can appear.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.floatinggift.next`
  - Access: Adventure

### Path tool

- **`/ah path revert <id>`** — Undo a cemented path using the id from chat when you placed it. You also need path tool access in play.
  - `<id>` — Path revert id (UUID) printed in chat when the path was placed.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.path.revert`
  - Access: Adventure

## For world hosts

These are for creative mode or people running the server. Not needed for normal town play.

- **`/ah difficulty`** — Open the world difficulty menu for building costs.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.difficulty`
  - Access: Creative

- **`/ah reload`** — Reload mod config and data files from disk.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.reload`
  - Access: Creative

- **`/ah starterkit`** — Give yourself the starter tools (placement staff, charter, planning desk, building staff).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.starterkit`
  - Access: Creative

- **`/ah exportskin [path]`** — Save your avatar skin as a model file.
  - `[path]` — Optional output path. Default is plugin data `avatar_exports` with a timestamped filename.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.exportskin`
  - Access: Creative

- **`/ah exportskin <player> [path]`** — Save another player's avatar skin (needs `.other` permission).
  - `<player>` — Target player in the world.
  - `[path]` — Optional output path (same as above).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.exportskin.other`
  - Access: Creative

- **`/ah time <hour>`** — Set the in game schedule clock (villager routines use this clock).
  - `<hour>` — Hour 0 to 23 (example `14` for 2 PM).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.time`
  - Access: Creative

- **`/ah time dawn`** — Set that clock to 6:00 in the morning.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.time.dawn`
  - Access: Creative

- **`/ah plots finishassembly`** — Instantly finish every building still assembling in your town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.plots.finishassembly`
  - Access: Creative

- **`/ah plots remove <plotId>`** — Remove one plot from your town by id.
  - `<plotId>` — Plot id from `plots list`.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.plots.remove`
  - Access: Creative

## Debug commands

For testing and fixing worlds. Not part of normal play.

- **`/ah replace-charter [townName]`** — Put the charter block back at your town's saved spot if it was broken.
  - `[townName]` — Full town name with spaces. Omit for your town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.replace-charter`
  - Access: Adventure

- **`/ah towns`** — List every town in this world.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.towns`
  - Access: Creative

- **`/ah poi list [town]`** — List points of interest for a town.
  - `[town]` — Town id, `me`, or omit for your town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.poi.list`
  - Access: Creative

- **`/ah poi dump`** — List all POIs in the world registry.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.poi.dump`
  - Access: Creative

- **`/ah plots list`** — List plot instances in your town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.plots.list`
  - Access: Creative

- **`/ah needs inspect`** — List villagers with need meters nearby.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.needs.inspect`
  - Access: Creative

- **`/ah needs set <target> <which> <value>`** — Set a villager hunger, energy, or fun meter.
  - `<target>` — Villager handle, `Elder`, or entity id.
  - `<which>` — `hunger`, `energy`, or `fun`.
  - `<value>` — 0 to 100 (100 is full).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.needs.set`
  - Access: Creative

- **`/ah tax breakdown`** — Show tax lines for your town treasury.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.tax.breakdown`
  - Access: Creative

- **`/ah tax now`** — Run morning tax collection right away.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.tax.now`
  - Access: Creative

- **`/ah quest grant [questId]`** — Mark a quest active on your town.
  - `[questId]` — Quest id. Default `q_build_inn` when omitted.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.quest.grant`
  - Access: Creative

- **`/ah quest complete [questId]`** — Mark a quest completed on your town.
  - `[questId]` — Quest id. Default `q_build_inn` when omitted.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.quest.complete`
  - Access: Creative

- **`/ah quest clear [questId]`** — Remove a quest from your town's active list.
  - `[questId]` — Quest id. Default `q_build_inn` when omitted.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.quest.clear`
  - Access: Creative

- **`/ah quest status`** — Show active and completed quests for your town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.quest.status`
  - Access: Creative

- **`/ah reputation set <villager> <value>`** — Set your reputation with a villager.
  - `<villager>` — Villager entity id or role id in your town.
  - `<value>` — Reputation 0 to 100.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.reputation.set`
  - Access: Creative

- **`/ah reputation reward list [roleId]`** — List reputation milestone rewards.
  - `[roleId]` — Optional role id filter (example `Aetherhaven_Merchant`).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.list`
  - Access: Creative

- **`/ah reputation reward grant <villager> <rewardId>`** — Grant one reputation reward now.
  - `<villager>` — Villager entity id or role id in your town.
  - `<rewardId>` — Reward id (example `rep_merchant_50`).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.reputation.reward.grant`
  - Access: Creative

- **`/ah villager list`** — List villager entity ids in your town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.villager.list`
  - Access: Creative

- **`/ah villager locate <villager> [--tp]`** — Show where a villager is (optional teleport for operators).
  - `<villager>` — Villager entity id or role id in your town.
  - `[teleport]` or `--tp` — `true` or `--tp` to teleport (operators only).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.villager.locate`
  - Access: Creative

- **`/ah villager reset`** — Respawn all town villagers near you.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.villager.reset`
  - Access: Creative

- **`/ah villager fixinn`** — Repair inn visitor pool issues for your town.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.villager.fixinn`
  - Access: Creative

- **`/ah gift resetLimits`** — Reset gift limits for all players and villagers in the world.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.gift.resetlimits`
  - Access: Creative

- **`/ah gift fillHistory <roleId>`** — Fill gift history preview rows for testing.
  - `<roleId>` — Villager role id (example `Aetherhaven_Merchant`).
  - Permission: `hexvane.aetherhaven.command.aetherhaven.gift.fillhistory`
  - Access: Creative

- **`/ah debug-autonomy toggle`** — Toggle autonomy debug on the town villager you are looking at.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.toggle`
  - Access: Creative

- **`/ah debug-autonomy show`** — Show whether autonomy debug is on for the villager you are looking at.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.show`
  - Access: Creative

- **`/ah debug-autonomy clear`** — Turn off autonomy debug for the villager you are looking at.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.debug-autonomy.clear`
  - Access: Creative

- **`/ah debug-lootchest fill`** — Force bonus loot rolls on the chest you are looking at.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.debug-lootchest.fill`
  - Access: Creative

- **`/ah dialogue <treeId> [entryNode]`** — Open a dialogue tree by id for testing.
  - `<treeId>` — Dialogue tree id (example `aetherhaven_merchant`).
  - `[entryNode]` — Starting node. Default `root`.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.dialogue`
  - Access: Creative

- **`/ah floatinggift spawn`** — Spawn a floating gift balloon at your position.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.floatinggift.spawn`
  - Access: Creative

- **`/ah path navviz`** — Toggle debug lines for villager path navigation. Requires path tool permission in play.
  - Permission: `hexvane.aetherhaven.command.aetherhaven.path.navviz`
  - Access: Creative
