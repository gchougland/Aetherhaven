# Aetherhaven — Dev Plan

**Contest:** Hytale New Worlds Modding Contest
**Deadline:** April 28, 2026
**Start Date:** ~March 10, 2026 (~7 weeks remaining)
**Category:** NPCs (primary), Experiences (secondary/stretch)
**Goal:** Win NPC category + demonstrate systems-level engineering to Hypixel

---

## Vision

A living sky-village in its own void dimension — a cluster of floating islands connected by bridges,
populated by NPCs with daily schedules, jobs, social behaviour, and memory. Players grow the village
by completing objectives, constructing buildings, and defending against sky-raider invasions.
The museum is a stretch-goal capstone that ties the achievement system to a physical space.

---

## Island Layout Decision

**District islands with predefined building plots + dedicated single-building special islands.**

- Each district island is a single prefab with 3–5 building footprints baked into the terrain.
  Plots are identified by a marker NPC placed at the center of each empty footprint.
- District types: Residential, Market, Military, Civic (stretch: Museum)
- Landmark structures get their own small island: Watchtower, Forge, later the Museum.
  These are visually distinct and serve as waypoints the player navigates toward.
- Bridges connect adjacent grid slots. The grid system handles alignment automatically.

This approach lets the art direction of each district tell a story the contest video can show in
seconds, and makes the village feel coherent rather than scattered.

---

## Core Technical Systems

### 1. Void Dimension + Portal

A custom Hytale world with no terrain generation — sky only. Entry via an Aether Portal structure
the player builds or discovers. The portal triggers a dimension teleport.

- `AetherDimension` — registers the void world at plugin setup, handles player transitions
- Portal structure: built from a specific block pattern; Java detects the completed shape via
  `BlockChange` sensor on a manager NPC (or event listener), triggers the teleport interaction
- Players respawn at a designated arrival platform island if they die in the dimension

### 2. Island Grid + Prefab Placement

A 2D integer grid in the void dimension maps grid coordinates to island slots. Each slot has:
- A world-space anchor position (computed from grid coords × cell size)
- An occupancy state: EMPTY, DISTRICT, SPECIAL, BRIDGE
- A reference to the placed IslandData asset

**`IslandData` (JSON asset):**
```json
{
  "id": "district_market",
  "type": "DISTRICT",
  "gridWidth": 1,
  "gridHeight": 1,
  "prefabFile": "Islands/Market_District.prefab",
  "buildingPlots": ["market_stall_a", "market_stall_b", "traders_lodge"],
  "bridgeAttachPoints": { "north": [0, 0], "south": [0, -1], "east": [1, 0], "west": [-1, 0] }
}
```

**`IslandGrid` (Java system):**
- `placeIsland(gridX, gridZ, islandDataId)` — loads prefab, places blocks at anchor, spawns plot
  markers for each empty building plot, places bridge prefabs to occupied adjacent slots
- `removeIsland(gridX, gridZ)` — used for raid cleanup (removes raid-structure prefabs)
- Grid persists in a custom data file so the village survives server restart

**`PrefabLoader` (Java system):**
- Prefab files are JSON: block palette + 3D run-length-encoded block array
- `place(prefabId, World, Vector3d anchor, Rotation)` — iterates block data and calls world block
  placement API for each non-air block
- Async-friendly: can batch placements over multiple ticks to avoid freeze on large prefabs

### 3. Dialogue System

Hytale has NPC interactions but no branching dialogue. This is a custom system.

**Dialogue tree asset (JSON):**
```json
{
  "id": "elder_intro",
  "nodes": {
    "root": {
      "speaker": "Village Elder",
      "text": "Welcome, traveller. Our village is young — we need your help to grow.",
      "choices": [
        { "text": "How can I help?",  "next": "how_help" },
        { "text": "What is this place?", "next": "lore" }
      ]
    },
    "how_help": {
      "text": "Build the market first. Traders will come, and with them, coin.",
      "condition": { "achievementUnlocked": "first_arrival" },
      "actions": [{ "type": "StartObjective", "id": "build_market" }],
      "choices": [{ "text": "Understood.", "next": null }]
    }
  }
}
```

**`DialogueSystem` (Java):**
- Loads dialogue tree assets at plugin setup
- `openDialogue(playerRef, dialogueId, npcRef)` — opens a `CustomUIPage` with the current node's
  text and choices, resolves `condition` blocks against village/player state, fires `actions` on
  choice selection, advances the conversation to the `next` node
- Dialogue state (which node each player is on) tracked per-player per-NPC in a custom component

**`DialogueUI` (CustomUIPage + .ui template):**
- Full-screen dialogue overlay: NPC portrait on the left, text area centre, up to 4 choice buttons
- Typewriter effect on text reveal (handled in the .ui template animation layer)
- Exits cleanly on Escape or when `next` is null

### 4. Construction System

Each empty building plot in a district has a **Plot Marker NPC** — a simple non-combat NPC with
a sign/post appearance and a name matching the building slot.

**Player interaction flow:**
1. Player right-clicks the Plot Marker → `OpenCustomUIInteraction` fires
2. **Construction UI** (`CustomUIPage`) shows:
   - Building name and description
   - Required resources (item list with counts)
   - Player's current inventory (green = have enough, red = missing)
   - "Construct" button (enabled only when all resources met)
3. On confirm: Java checks inventory server-side, consumes resources, calls `PrefabLoader.place()`
   at the plot's anchor, despawns the Plot Marker NPC, spawns the building's resident NPC(s),
   fires the relevant achievement unlock

**`BuildingData` (JSON asset):**
```json
{
  "id": "market_stall_a",
  "displayName": "Market Stall",
  "prefabFile": "Buildings/Market_Stall.prefab",
  "plotAnchorOffset": [0, 0, 0],
  "cost": [
    { "item": "Wood_Plank", "count": 20 },
    { "item": "Iron_Ingot", "count": 5 }
  ],
  "spawnsNpcs": ["merchant"],
  "unlockAchievement": "first_market_stall"
}
```

### 5. Achievement + Village Growth System

`AchievementSystem` (Java) tracks a list of named boolean flags per player and globally for the
village. Achievements fire when conditions are met (building constructed, item donated, raid
survived, etc.).

Each achievement entry in `achievements.json` defines:
- `id`, `displayName`, `description`
- `onUnlock` actions: spawn NPC, unlock new plot on a district island, send dialogue, add museum
  exhibit slot (stretch)
- `icon` (for museum exhibit display, stretch)

**Village Growth Tiers** (drive new district islands unlocking):
- Tier 1 — Arrival (start): 1 district island, 3 plots, Village Elder + 2 Guards
- Tier 2 — Settlement: Market district unlocks after building trade post + 3 residents
- Tier 3 — Town: Military district + Watchtower unlock after surviving first raid
- Tier 4 — Haven: Civic district + Forge unlock after reaching 8 residents
- Stretch — Museum island unlocks at Tier 4 + Scholar NPC arrival

When a new district unlocks, `IslandGrid.placeIsland()` is called and the island appears in
the void — visually the village grows outward.

### 6. Economy + Currency

**Aether Shards** — custom item that acts as the village currency. Dropped by raid enemies,
earned from completing construction objectives, and traded at the market.

**`VillageEconomy` (Java):**
- Tracks a village-wide resource pool (optional, for future supply/demand pricing)
- Simple initial version: fixed barter prices on Merchant NPCs
- Merchant's `OpenBarterShop` action uses standard Hytale barter — the economy layer can tune
  prices by modifying the barter asset at runtime based on supply (stretch goal)

### 7. Raid System

Raids spawn when the village reaches a growth tier threshold and a cooldown timer has expired.

**Raid flow:**
1. `RaidSystem` (Java, tick-based) monitors village tier + last raid time
2. On raid trigger: select a grid-edge anchor point, call `PrefabLoader.place()` to place the
   **Sky Raider Barge** block structure prefab, then spawn a wave of Raider NPCs near it
3. Raiders use the full Walk MotionController + Combat Action Evaluator to path toward the village
   and attack buildings/players
4. Guards respond via `Alarm` + `Beacon` — one guard spots a Raider (using the `Mob` sensor),
   fires a beacon, all guards switch to combat state and converge
5. Raid ends when all Raiders are dead (or timer expires): `IslandGrid.removeIsland()` cleans up
   the barge prefab, `AchievementSystem` fires `raid_survived`, village tier advance check runs
6. Escalating difficulty: each subsequent raid adds more Raiders and uses a larger barge prefab

**Sky Raider Barge prefab:** A Blockbench-modelled flying galleon skeleton — rough planks, crows
nests, a prow figure. Dark aesthetic contrasts with the warm village islands. No NPC entity; just
a static block structure that exists for the duration of the raid.

---

## NPC Roster (10 Types for Contest Submission)

| # | NPC | Role in Village | Key Systems Used | Unlocks At |
|---|-----|-----------------|------------------|------------|
| 1 | **Village Elder** | Quest hub, arrival greeter, tracks village state | Full dialogue tree, achievement listeners, custom component for village knowledge | Start |
| 2 | **Guard** (×2 initially) | Patrols village, defends against raids | Combat Action Evaluator, Alarm+Beacon, Walk+path patrol, flock behavior | Start |
| 3 | **Guard Captain** | Commands guards, escalates raid response, has own combat abilities | CAE with coordinated orders via Beacon, multi-phase raid AI, timer-gated abilities | Tier 3 (Military district) |
| 4 | **Merchant** | Sells goods, buys Aether Shards, daily schedule | OpenBarterShop interaction, daily open/close schedule (Time sensor), custom dialogue | Tier 2 (Market district) |
| 5 | **Farmer** | Produces food for economy, visible daily work routine | Day/night schedule (Time + timer states), PlaceBlock + PickUpItem AI actions, no combat | Tier 2 |
| 6 | **Blacksmith** | Unlocks guard equipment upgrades, gives crafting quests | Dialogue-driven upgrade system, custom component tracking upgrade tiers, idle forge animations | Tier 4 (Forge island) |
| 7 | **Scholar** | Drives collection quests, links to museum | Dialogue tree with objectives, HasTask sensor for quest tracking, custom achievement hooks | Tier 4 (Civic district) |
| 8 | **Innkeeper** | Social hub, sells food/lodging bonuses, daily schedule | OpenShop interaction, time-based open/close, ambient socialising AI (Observe + Wander) | Tier 2 |
| 9 | **Village Child** | Ambient life, reacts to events, plays/runs during raids | Simple curiosity AI (random wander + Watch player), Flee on Damage, Beacon listener for raids | Tier 1 (after first building) |
| 10 | **Visiting Trader** | Periodic rare-goods vendor, arrives and departs on schedule | Timer-gated spawn/despawn, rare barter inventory, departure dialogue, unique model variant | Tier 2+ (every N minutes) |

**Stretch NPC:**
**Museum Curator** — Arrives when Museum island is placed. Commission quests, exhibit dialogues,
achievement display management. Reuses the dialogue system heavily.

---

## Week-by-Week Schedule

**Week 1 (Mar 10–16) — Foundation**
- [ ] Set up Aetherhaven mod project, build pipeline, CurseForge page (get it live early)
- [ ] Void dimension registration, portal structure detection, player teleport
- [ ] `PrefabLoader` core: JSON format spec, block palette, placement API
- [ ] Hand-build first test island prefab (flat stone island, small) to validate placement
- [ ] `IslandGrid` core: grid data structure, anchor computation, slot occupancy

**Week 2 (Mar 17–23) — Village Scaffolding**
- [ ] `IslandData` + `BuildingData` JSON assets defined and loading
- [ ] Plot Marker NPC: appears at empty plots, basic interaction triggers placeholder UI
- [ ] `AchievementSystem`: flag tracking, unlock actions (NPC spawn, tier advance), persistence
- [ ] Village arrival island prefab (the first thing the player sees — make it beautiful)
- [ ] Village Elder NPC with basic (non-branching) dialogue, starter quest

**Week 3 (Mar 24–30) — Dialogue System**
- [ ] Dialogue tree JSON format, loader, node resolution
- [ ] `DialogueUI` (CustomUIPage + .ui template): text area, portrait, choice buttons
- [ ] Typewriter animation, keyboard navigation (1/2/3/4 keys for choices)
- [ ] Wire Elder, Merchant, and Innkeeper NPCs to real dialogue trees
- [ ] Condition evaluation: check achievements, village tier, inventory

**Week 4 (Mar 31–Apr 6) — Construction + Economy**
- [ ] Construction UI (CustomUIPage): resource list, inventory check, confirm button
- [ ] Server-side resource consumption on confirm, prefab placement, NPC spawn
- [ ] Aether Shard custom item + currency tracking
- [ ] Market district prefab + Merchant NPC with barter shop
- [ ] Residential district prefab, Farmer NPC with day/night schedule
- [ ] Tier 2 unlock flow end-to-end (build market → district appears → Merchant arrives)

**Week 5 (Apr 7–13) — Guards + Raids**
- [ ] Guard NPC: patrol path, Combat Action Evaluator setup, Alarm/Beacon chain
- [ ] Guard Captain: coordinated commands, multi-phase raid response AI
- [ ] `RaidSystem`: tier-gated trigger, prefab spawn, Raider NPC wave, cleanup
- [ ] Sky Raider Barge prefab (block structure)
- [ ] Raider NPC roles: basic combat AI with Walk controller, target village NPCs + player
- [ ] Raid survival → achievement → tier advance → Military district + Watchtower unlock

**Week 6 (Apr 14–19) — Remaining NPCs + Content Pass**
- [ ] Blacksmith (Forge island), Scholar (Civic district), Village Child, Visiting Trader
- [ ] Tier 3 + Tier 4 unlock flows end-to-end
- [ ] Second and third raid difficulty tiers
- [ ] All 10 NPC dialogue trees written and wired
- [ ] Aetherhaven dimension: build out the full island art (not just test geometry)

**Week 7 (Apr 20–24) — Polish**
- [ ] Playtest the full arc from portal entry to Tier 4 — fix pacing, tune resource costs
- [ ] Performance pass on prefab placement (async batching)
- [ ] Sound pass: ambient village sounds, raid alarm, construction completion
- [ ] UI polish: construction UI, dialogue UI, achievement notification
- [ ] Edge cases: player death in dimension, server restart persistence, raid during construction

**Week 8 (Apr 25–28) — Submission**
- [ ] Gameplay video: arrival → village growth → raid defense → Tier 4 reveal
- [ ] CurseForge page with screenshots, feature list, install instructions
- [ ] Technical writeup for judges: architecture overview, key system docs
- [ ] Final fresh-install test
- [ ] Submit before April 28

---

## Key Architecture Decisions

**Java vs JSON split:**
- NPC behaviour: JSON-first. All schedule states, combat responses, and dialogue triggers are
  JSON role files. Java only provides the sensors and actions the JSON can't reach.
- Village systems (grid, achievements, economy, construction): Java. These are simulation logic
  that don't belong in NPC role files.
- Prefab data: JSON. The block layout of every island and building is a data file, not hardcoded.

**Persistence:**
- Village state (grid occupancy, achievement flags, economy) serialises to a JSON file in the
  world's plugin data directory on server stop. Loaded back on startup.
- Per-player state (dialogue progress, objectives) uses a custom ECS component.

**Extensibility (the "hire me" signal):**
- `AchievementSystem` has a public `register(AchievementDefinition)` API so other mods can add
  achievements that drive new NPCs and (stretch) museum exhibits.
- `BuildingData` and `IslandData` are loaded from `Server/Aetherhaven/` asset directories — other
  mods can drop files there to add buildings without touching Aetherhaven's Java code.

---

## Fallback Scoping

**If behind at Week 5:** Cut Visiting Trader and Scholar, simplify raids to enemy waves without
the barge prefab (enemies spawn at the grid edge). Village Elder carries more of the dialogue weight.

**If behind at Week 6:** Submit with Tier 1–3 only. A village that grows to Military tier with a
working raid defense is a complete, demonstrable experience. Tier 4 becomes post-contest content.

**If ahead:** Add Museum island (Stretch Tier 5), Curator NPC, and at least 5 exhibit displays
driven by achievements already tracked. The museum wing takes roughly one week.

---

## Risk Register

| Risk | Mitigation |
|------|-----------|
| Prefab placement is slow on large islands | Batch over multiple ticks; cap island size; test early |
| Dialogue system CustomUIPage is complex | Build a minimal 1-node version in Week 3; polish later |
| Void dimension registration fails / quirky | Spike this in Week 1 day 1; it's a hard blocker |
| 10 NPC dialogue trees is a lot of writing | Draft all trees as plain text first; wire in Week 6 |
| Raid pathfinding to village NPCs doesn't work | Fallback: raiders target player only if NPC pathfinding fails |
| Scope creep on island art | Lock island prefabs in Week 5; no new art after that |
