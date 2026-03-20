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

**Main districts and islands are built by hand in the instance; plot sign blocks mark where buildings can go.**

- The arrival island, district islands (Residential, Market, Military, Civic), and landmark islands
  (Watchtower, Forge, etc.) are built directly in the Aetherhaven instance world. Chunks are saved
  as part of the mod assets (see `Docs/INSTANCE_EDITING_GUIDE.md`). No programmatic island placement.
- **Plot signs** — a custom block type, placed manually on each empty building footprint — mark
  where a prefab can be built. Interacting with a plot sign opens the construction GUI; the sign
  is not an NPC. District types: Residential, Market, Military, Civic (stretch: Museum). Landmark
  islands (Watchtower, Forge, later Museum) are built the same way.
- Bridges and layout are part of the hand-built world. A grid system is not required for the main
  village; a grid setup may be introduced later as a stretch goal for the museum (e.g. exhibit
  placement) and does not need to be built until museum work begins.

This keeps art direction and level design in the hands of the designer while the code focuses on
plot-sign logic, prefab placement on those plots, and villager progression.

---

## Core Technical Systems

### 1. Void Dimension + Portal

A custom Hytale world with no terrain generation — sky only. Entry via an Aether Portal structure
the player builds or discovers. The portal triggers a dimension teleport.

- `AetherDimension` — registers the void world at plugin setup, handles player transitions
- Portal structure: built from a specific block pattern; Java detects the completed shape via
  `BlockChange` sensor on a manager NPC (or event listener), triggers the teleport interaction
- Players respawn at a designated arrival platform island if they die in the dimension

### 2. Plot Signs + Prefab Placement

Islands and districts are built by hand in the instance; no IslandGrid is used for the main village.
A **plot sign** is a custom block placed manually on each empty building footprint. Each sign
references a building type (e.g. which prefab and which villager “owns” the plot).

**Plot sign behaviour:**
- Interacting with the block opens a **Construction GUI** (see §4). Building the structure
  requires: (1) the villager for that plot is in town, and (2) the player has the required
  resources. The GUI shows the required NPC and materials; requirement text is green if met, red
  if not. A “Build” button is greyed out until both conditions are satisfied.
- When the player builds, the system consumes resources and places the building prefab at the
  plot (anchor derived from the sign position). The plot sign can be removed or repurposed per
  design (e.g. become a “completed” sign or stay as decoration).

**`BuildingData`** (or equivalent) still defines per-building prefab, cost, and which villager
is tied to the plot. No `IslandData` or grid is required for core flow. A grid setup may be
added later as a stretch goal for the museum (e.g. exhibit slots) and is out of scope until then.

**`PrefabLoader` (Java system):**
- Prefab files are JSON: block palette + 3D run-length-encoded block array
- `place(prefabId, World, Vector3d anchor, Rotation)` — places the building prefab when the
  player confirms construction from a plot sign
- Async-friendly: can batch placements over multiple ticks to avoid freeze on large prefabs
- Also used for raid cleanup (e.g. placing/removing raid structures if needed)

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
- Full node text and choices shown together (no typewriter; avoids custom-page ack churn)
- Exits cleanly on Escape or when `next` is null

### 4. Construction System

Each empty building plot has a **plot sign block** (custom block, placed manually). Interacting
with the plot sign opens the Construction GUI. Two conditions must both be satisfied to build:

1. **Villager in town** — The NPC assigned to this plot must be present in the village (e.g. has
   already appeared at the inn and the player has accepted their quest; they may still be “at the
   inn” or have moved into another building, but they must not be locked behind progression).
2. **Player has resources** — The player’s inventory (or tracked materials) must meet the
   building’s required item list.

**Construction GUI (CustomUIPage):**
- **Close** button — dismisses the window.
- **Required NPC** — shows which villager must be in town for this plot; text colour **green** if
  that villager is in town, **red** if not.
- **Required materials** — list of items and counts; each line **green** if the player has
  enough, **red** if not.
- **Build** button — builds the structure when pressed. **Greyed out** (disabled) until both
  “villager in town” and “all materials present” are true. When enabled and pressed: server
  consumes resources, calls `PrefabLoader.place()` at the plot anchor (derived from the sign
  position), updates village state (e.g. villager moves in, achievement unlock).

**`BuildingData` (JSON asset):** defines prefab, cost, and which villager is tied to the plot
(e.g. `requiredVillagerId`, `spawnsNpcs` for who moves in after build). Example shape:
```json
{
  "id": "market_stall_a",
  "displayName": "Market Stall",
  "prefabFile": "Buildings/Market_Stall.prefab",
  "plotAnchorOffset": [0, 0, 0],
  "requiredVillagerId": "merchant",
  "cost": [
    { "item": "Wood_Plank", "count": 20 },
    { "item": "Iron_Ingot", "count": 5 }
  ],
  "spawnsNpcs": ["merchant"],
  "unlockAchievement": "first_market_stall"
}
```

### 5. Villager Progression

Villagers appear and unlock in a fixed sequence; who is “in town” drives which plot signs can be
used to build.

**Initial state**
- Only the **Village Elder** is present, in the center of town. He greets the player and asks
  them to restore the town.

**After the player accepts the Elder’s request**
- The **Innkeeper** appears in the village and asks the player to restore the **village Inn**.
  Until the inn is built, no other villagers appear.

**After the Inn is restored**
- New villagers appear **in the inn** based on the player’s **achievements**. Each achievement can
  unlock a specific villager type. Only villagers that are already unlocked can appear.
- **At most two** unlocked NPCs are present in the inn at any time (e.g. two from the pool of
  unlocked types, chosen by design or rotation).
- When the player talks to a villager in the inn, that villager asks to have their **house**
  restored first (or place of work, but house first). The corresponding plot sign(s) become
  buildable once that villager is in town (and the player has resources).

**When a villager’s house is restored**
- That villager **moves in** to the new building and no longer counts as “in the inn.”
- A **new slot** opens in the inn, so another unlocked villager (if any) can appear, keeping the
  “max two in the inn” rule.

So: Elder → accept quest → Innkeeper appears → build Inn → villagers start appearing in the inn
(up to 2, gated by achievements) → player talks to them, they ask for their house → player builds
via plot sign (villager in town + resources) → villager moves in, new villager can take their
place in the inn.

### 6. Achievement + Village Growth System

`AchievementSystem` (Java) tracks a list of named boolean flags per player and globally for the
village. Achievements fire when conditions are met (building constructed, item donated, raid
survived, etc.).

Each achievement entry in `achievements.json` defines:
- `id`, `displayName`, `description`
- `onUnlock` actions: **unlock a villager** (so they can appear in the inn), send dialogue, add
  museum exhibit slot (stretch)
- `icon` (for museum exhibit display, stretch)

Achievements drive **which villagers can appear in the inn** (see §5). Islands and districts
are already built in the instance; growth is expressed through which NPCs are in town and which
buildings the player has constructed via plot signs. Tiers (e.g. “Settlement”, “Town”) can still
be used to group achievements and narrative (e.g. “restore the inn” → “first villagers in inn” →
“survive a raid” → more villagers unlocked). No IslandGrid or programmatic island placement.

### 7. Economy + Currency

**Aether Shards** — custom item that acts as the village currency. Dropped by raid enemies,
earned from completing construction objectives, and traded at the market.

**`VillageEconomy` (Java):**
- Tracks a village-wide resource pool (optional, for future supply/demand pricing)
- Simple initial version: fixed barter prices on Merchant NPCs
- Merchant's `OpenBarterShop` action uses standard Hytale barter — the economy layer can tune
  prices by modifying the barter asset at runtime based on supply (stretch goal)

### 8. Raid System

Raids spawn when the village reaches a growth tier threshold and a cooldown timer has expired.

**Raid flow:**
1. `RaidSystem` (Java, tick-based) monitors village state + last raid time
2. On raid trigger: choose an anchor position (e.g. near the village edge), call `PrefabLoader.place()`
   to place the **Sky Raider Barge** block structure prefab, then spawn a wave of Raider NPCs near it
3. Raiders use the full Walk MotionController + Combat Action Evaluator to path toward the village
   and attack buildings/players
4. Guards respond via `Alarm` + `Beacon` — one guard spots a Raider (using the `Mob` sensor),
   fires a beacon, all guards switch to combat state and converge
5. Raid ends when all Raiders are dead (or timer expires): remove the barge prefab (block clear or
   prefab removal as implemented), `AchievementSystem` fires `raid_survived`, unlock/advance checks run
6. Escalating difficulty: each subsequent raid adds more Raiders and uses a larger barge prefab

**Sky Raider Barge prefab:** A Blockbench-modelled flying galleon skeleton — rough planks, crows
nests, a prow figure. Dark aesthetic contrasts with the warm village islands. No NPC entity; just
a static block structure that exists for the duration of the raid.

---

## NPC Roster (10 Types for Contest Submission)

| # | NPC | Role in Village | Key Systems Used | Unlocks At |
|---|-----|-----------------|------------------|------------|
| 1 | **Village Elder** | Quest hub, arrival greeter, asks player to restore the town | Full dialogue tree, achievement listeners, custom component for village knowledge | Start (center of town) |
| 2 | **Guard** (×2 initially) | Patrols village, defends against raids | Combat Action Evaluator, Alarm+Beacon, Walk+path patrol, flock behavior | Start (or as designed) |
| 3 | **Guard Captain** | Commands guards, escalates raid response, has own combat abilities | CAE with coordinated orders via Beacon, multi-phase raid AI, timer-gated abilities | Unlocked by achievement; appears in inn |
| 4 | **Merchant** | Sells goods, buys Aether Shards, daily schedule | OpenBarterShop interaction, daily open/close schedule (Time sensor), custom dialogue | Unlocked by achievement; appears in inn (max 2 at a time) |
| 5 | **Farmer** | Produces food for economy, visible daily work routine | Day/night schedule (Time + timer states), PlaceBlock + PickUpItem AI actions, no combat | Unlocked by achievement; appears in inn |
| 6 | **Blacksmith** | Unlocks guard equipment upgrades, gives crafting quests | Dialogue-driven upgrade system, custom component tracking upgrade tiers, idle forge animations | Unlocked by achievement; appears in inn |
| 7 | **Scholar** | Drives collection quests, links to museum | Dialogue tree with objectives, HasTask sensor for quest tracking, custom achievement hooks | Unlocked by achievement; appears in inn |
| 8 | **Innkeeper** | Social hub, sells food/lodging bonuses, daily schedule | OpenShop interaction, time-based open/close, ambient socialising AI (Observe + Wander) | Appears after player accepts Elder’s “restore the town” request; asks to restore Inn |
| 9 | **Village Child** | Ambient life, reacts to events, plays/runs during raids | Simple curiosity AI (random wander + Watch player), Flee on Damage, Beacon listener for raids | Unlocked by achievement; appears in inn |
| 10 | **Visiting Trader** | Periodic rare-goods vendor, arrives and departs on schedule | Timer-gated spawn/despawn, rare barter inventory, departure dialogue, unique model variant | Unlocked / schedule (every N minutes) |

**Stretch NPC:**
**Museum Curator** — Present when the Museum (hand-built in instance) is used; commission quests,
exhibit dialogues, achievement display management. Reuses the dialogue system heavily.

---

## Week-by-Week Schedule

**Week 1 (Mar 10–16) — Foundation**
- [ ] Set up Aetherhaven mod project, build pipeline, CurseForge page (get it live early)
- [X] Void dimension registration, portal structure detection, player teleport
- [X] `PrefabLoader` core: JSON format spec, block palette, placement API
- [X] Hand-build first test island prefab (flat stone island, small) to validate placement
- [X] Plot sign block: custom block asset, placement in instance; interaction opens placeholder Construction GUI

**Week 2 (Mar 17–23) — Village Scaffolding**
- [X] `BuildingData` JSON assets (prefab, cost, requiredVillagerId) defined and loading
- [X] Plot sign: wire to Construction GUI (NPC required + materials, green/red text, Build button greyed out when requirements not met)
- [ ] `AchievementSystem`: flag tracking, unlock actions (villager unlock for inn), persistence
- [ ] Village arrival island built in instance (Elder in center); plot signs placed where buildings will go
- [ ] Village Elder NPC: dialogue asking player to restore the town; on accept, Innkeeper appears

**Week 3 (Mar 24–30) — Dialogue System**
- [X] Dialogue tree JSON format, loader, node resolution
- [X] `DialogueUI` (CustomUIPage + .ui template): text area, choice buttons
- [ ] Optional: typewriter / keyboard navigation (deferred; server-driven typewriter hits custom-page ack limits)
- [ ] Wire Elder and Innkeeper to dialogue (Elder: “restore the town”; Innkeeper: “restore the Inn”)
- [ ] Condition evaluation: check achievements, “villager in town”, inventory
- [ ] Villager progression: after Elder accept → Innkeeper appears; after Inn built → up to 2 unlocked villagers in inn; they ask for house first

**Week 4 (Mar 31–Apr 6) — Construction + Economy**
- [ ] Construction UI: required NPC (green/red), required materials (green/red), Close, Build (greyed out until both conditions met)
- [ ] Server-side: villager-in-town check, resource consumption on confirm, PrefabLoader.place() at plot, villager moves in, achievement unlock
- [ ] Aether Shard custom item + currency tracking
- [ ] Inn prefab + Innkeeper; first unlocked villagers (e.g. Merchant, Farmer) appear in inn, ask for house
- [ ] Building prefabs for first residences/market; plot signs on hand-built islands
- [ ] End-to-end: build Inn → villagers in inn → talk to villager → build their house → they move in, new slot in inn

**Week 5 (Apr 7–13) — Guards + Raids**
- [ ] Guard NPC: patrol path, Combat Action Evaluator setup, Alarm/Beacon chain
- [ ] Guard Captain: coordinated commands, multi-phase raid response AI
- [ ] `RaidSystem`: state-gated trigger, barge prefab placement at anchor, Raider NPC wave, cleanup
- [ ] Sky Raider Barge prefab (block structure)
- [ ] Raider NPC roles: basic combat AI with Walk controller, target village NPCs + player
- [ ] Raid survival → achievement → unlock new villagers for inn

**Week 6 (Apr 14–19) — Remaining NPCs + Content Pass**
- [ ] Blacksmith, Scholar, Village Child, Visiting Trader: unlocked by achievements, appear in inn (max 2 at a time), ask for house/work
- [ ] All 10 NPC dialogue trees written and wired to progression
- [ ] Second and third raid difficulty tiers
- [ ] Aetherhaven dimension: full island art built in instance (districts, landmarks, plot signs)

**Week 7 (Apr 20–24) — Polish**
- [ ] Playtest full arc: portal → Elder → accept → Innkeeper → build Inn → villagers in inn → build houses → move-in, new villagers
- [ ] Performance pass on prefab placement (async batching)
- [ ] Sound pass: ambient village sounds, raid alarm, construction completion
- [ ] UI polish: Construction GUI (plot sign), dialogue UI, achievement notification
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
- Village state (achievement flags, which villagers are unlocked / in town / moved in, economy)
  serialises to a JSON file in the world's plugin data directory on server stop. Loaded back on startup.
- Per-player state (dialogue progress, objectives) uses a custom ECS component.

**Extensibility (the "hire me" signal):**
- `AchievementSystem` has a public `register(AchievementDefinition)` API so other mods can add
  achievements that drive new villagers (inn pool) and (stretch) museum exhibits.
- `BuildingData` is loaded from asset directories — other mods can add building definitions.
  Plot signs reference building IDs; islands and layout are hand-built in the instance.

---

## Fallback Scoping

**If behind at Week 5:** Cut Visiting Trader and Scholar, simplify raids to enemy waves without
the barge prefab. Village Elder and Innkeeper carry more of the dialogue weight.

**If behind at Week 6:** Submit with Elder → Innkeeper → Inn → a subset of villagers (e.g. 2–3
types) appearing in inn and moving into houses. Raid defense and plot-sign construction still
demonstrate the full loop.

**If ahead:** Add Museum (stretch): museum island built in instance, grid setup for exhibit slots
if needed, Curator NPC, and at least 5 exhibit displays driven by achievements. Roughly one week.

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
