# Aetherhaven — Dev Plan (v2)

**Contest:** Hytale New Worlds Modding Contest  
**Deadline:** April 28, 2026  
**Start Date:** ~March 10, 2026  
**Category:** NPCs (primary), Experiences (secondary / stretch)  
**Goal:** Win NPC category + demonstrate systems-level engineering to Hypixel  

**Irreversible design pillar:** The mod is built around **world-integrated colonies** in the **player’s Hytale world** — not a hand-sculpted void layout. Growth is **free placement** of governed **plots** and **POIs**; NPCs use **Sims-like autonomy** (needs, wants, utility / affordance scoring) backed by a **POI registry** and a **quest system** (including dailies and expansion arcs). The **museum** is a **fixed-footprint plot building** whose **interior** is a separate **expandable instance** (TARDIS model). **Expansion mods** register plots, quests, NPC roles, POI types, and exhibits through documented extension points.

---

## Vision (long term)

Players found and grow a **town** in the overworld (or any dimension you officially support). They place the **charter block**, which spawns the **Village Elder** and kicks off a **build-the-Inn** arc. Once the **Inn** exists, **visitors** appear from an **unlocked pool** (cap **two at a time** in the Inn); they offer quests to build **their** house or workplace on **player-placed plots**. **Villagers** are autonomous day-to-day: they satisfy **needs** and pursue **wants** via **POIs**. **Quests** (story, expansion, daily, NPC-specific) tie progression to construction, economy, combat, and discovery. **Raids** (when the owner/party is present) apply **controlled** building damage. The **museum** is a small door in the world and a vast interior for exhibits — including **other mods** via data registration.

---

## Design questions — answer these to lock details

> **How to use this section:** Copy answers inline, or tick choices. Until answered, implementation defaults are suggestions in *italics*.

### Town ownership & multiplayer

- **Q1 — Colony identity:** Is a town **per player**, **per party / guild**, **per chunk claim**, or **server-wide shared**? **Legal owner** is always **one player UUID**; **party** = permission group (members get configured rights; see Q3). Not server-wide shared.
- **Q2 — Multiple towns:** Can one player own **more than one** active town in a world? If yes, how are NPC budgets split? **Default: one town per player per dimension** (unless creative/admin overrides).
- **Q3 — Permissions:** Who can place plots, destroy buildings, withdraw from shared storage, trigger town upgrades? RBAC matrix (owner / member / visitor)? Owners and members can place plots and withdraw from shared storage and trigger town upgrade. Only Owners can destroy buildings. Visitors can't do any of these things. This should all be configurable in the config file though.
- **Q4 — PvP & griefing:** Are town plots **protected** from other players’ block breaking? Explosions? Fluid? *Needs explicit policy for public servers.* By default it should be protected but should be configurable.

### Governed placement rules

- **Q5 — Town anchor:** What **exact item/block** establishes the town (town hall kit, charter block, NPC-placed ritual)? **Can the anchor be moved** after placement? Charter block and can't be broken normally, has option in blocks gui though to destroy town and pickup charter. This is irreversible. 
- **Q6 — Territory shape:** **Radius from anchor**, **chunk-aligned grid**, **polygon** from boundary markers, or **scanned connected plots**? Max radius or chunk count? Should be chunk aligned with radius around anchor. But expands with constructed plots near edge of radius. Radius should be configurable.
- **Q7 — Plot spacing:** Minimum edge-to-edge distance between building footprints? **Shared walls** allowed (like city row houses) or **mandatory gap**? Shared walls allowed
- **Q8 — Verticality:** Can plots stack (multi-floor cities) or **ground-contact only**? Basements? Verticality would be preferred if feasible
- **Q9 — Biome / dimension:** Town allowed **everywhere**, or **blacklisted biomes** (e.g. deep ocean)? Support **multiple dimensions** or **overworld only** for v1? Support multiple dimension, no blacklisted biomes
- **Q10 — Validation UX:** Ghost preview + red/green outline? Snapping to **90°** only? **Terrain flatten** option or strict “must be flat”?Yes ghost preview, Yes snap to 90 degree only, Terrain flatten option.

### NPCs & performance

- **Q11 — Population cap:** Hard cap per town by **tier**, **performance profile**, or **player setting**? by tier
- **Q12 — LOD / simulation:** Do distant/off-screen NPCs use **abstract simulation** (needs tick down, no pathing) or **full AI** always? abstract simulation using game time, similar to how crops grow using game time
- **Q13 — Naming & persistence:** Procedural names? Permadeath? **Respawn** same villager or new entity? Most npcs are hand-crafted with specific names, possibly have basic "worker" npcs that have procedural names. Villagers can be revived at the charter stone for a cost and should be the same villager.

### Sims-like autonomy

- **Q14 — Need list (v1):** Which needs ship in contest MVP? (e.g. **Energy**, **Hunger**, **Fun**, **Social**, **Hygiene**, **Bladder**, **Environment**.) Which are **stretch**? MVP: Hunger, Energy, Fun; Stretch: Social, Hygiene, Bladder, Environment
- **Q15 — Decay curves:** Linear decay per game-minute, **sleep-dependent**, **activity-dependent**? **All of the above** (combine linear time decay with modifiers for sleep and current activity).
- **Q16 — Failure states:** At 0 need: **mood debuff only**, **refusal to work**, **collapse / pass out**, **leave town**? Mood debuff and refusal to work
- **Wants / whims:** Separate from needs? **UI notification** to player? How often rerolled? Separate from needs, no notification but should have somewhere the player can see overview, every few in-game days
- **Q17 — Player control:** Can the player **direct-assign** jobs / “go here” like Rimworld drafts, or **pure autonomy** with only indirect influence (build POIs)? For most NPCs they will have specific roles like blacksmith, chef, etc. Generic workers will need to be directly assigned jobs. Mostly pure autonomy.

### Quests

- **Q18 — Quest sources:** **NPC dialogue only**, **physical quest board**, **both**, **radio/mail** stretch? NPC dialogue for MVP, quest board and radio/mail for stretch
- **Q19 — Daily reset:** **Server midnight**, **per-player 24h from first login**, **fixed UTC**? Configurable
- **Q20 — Co-op quests:** Party progress shared or individual turn-in? Shared
- **Q21 — Failure & abandon:** Time limits? **Abandon** penalty? **Retry** rules? Depends on quest, usually no penalty for abandoning. Important quests should always be able to be picked up again from source.

### Museum (TARDIS)

- **Q22 — Instance ownership:** **Per player**, **per town**, or **global server** museum? Per town
- **Q23 — Multiplayer inside:** Can two players be in the **same** museum instance together? **Party only**? **Per town** museum instance. **Visitors** (non–town-members) may **enter and look around only** — no placing exhibits, donations, museum quests, or any modification. **Town members** (owner + party per Q3) can **contribute** in the full sense: exhibits, donations, museum quests, and any future museum progression hooks.
- **Q24 — Exhibit placement:** **Auto-slot** along gallery spine, **grid picker UI**, or **manual creative placement** in instance? Auto-slot along gallery spine
- **Q25 — Exit rules:** Always return to **same door block**? What if door is **broken** while inside? Always return to same door block, if broken go to world spawn

### Modding & versioning

- **Q26 — API stability:** Semantic versioning for **data schemas** (quests, POI defs) — breaking changes only on major? yes
- **Q27 — Namespace:** Required **`aetherhaven:`** / **`modid:`** prefix for all registered IDs? yes
- **Q28 — Optional dependency:** Base mod runs **without** expansion packs; expansions **soft-fail** if base API missing features? yes

### Contest vs vision

- **Q29 — MVP sacrifice:** For April 28, are you willing to ship **abstract raid** (waves at edge) instead of **barge prefab**, or **2–3 needs** instead of full set? **Contest submission excludes raids and museum entirely** (see § Contest MVP). If slipping, cut **breadth** (dailies, NPCs, park) per fallback table — **not** core loop. Post-contest, raids/museum return per roadmap.
- **Q30 — Judge story:** Single **scripted demo biome** allowed for video while code is **generic** for any world? **Demo will likely use a relatively flat area for video**; this is **production / recording choice only** and **does not constrain code** (gameplay remains generic for any valid world).

---

## Locked decisions summary (from Q&A)

Use this table for implementation; inline bullets above remain the **source of truth** for nuance.

| Topic | Decision |
|--------|----------|
| **Colony identity** | **Single legal owner** (one player UUID). **Party** = permission group with configurable rights (Q3). |
| **Multiple towns** | **One town per player per dimension** by default (creative/admin may allow more). |
| **Permissions** | Owner + members: place plots, shared storage, town upgrades. **Only owner** destroys buildings. Visitors: none of the above. **All configurable** in config. |
| **Protection** | Town plots **protected** by default (break / explosion / fluid TBD per-field in config); configurable. |
| **Anchor** | **Charter block**; not breakable normally; GUI option **destroys town** and returns charter (**irreversible**). |
| **Territory** | **Chunk-aligned** radius from anchor; **expands** when valid plots are built near edge; max radius **configurable**. |
| **Plot spacing** | **Shared walls allowed** (row houses OK). |
| **Verticality** | **Preferred** if engine/feasibility allows (multi-floor / stacking). |
| **Biomes / dimensions** | **Multiple dimensions** supported; **no** biome blacklist for v1. |
| **Placement UX** | Ghost preview; **90°** snap; **optional terrain flatten**. |
| **Population cap** | **By town tier**. |
| **Off-screen NPCs** | **Abstract simulation** using **game time** (crop-like), not full pathing. |
| **Villagers** | Mostly **hand-crafted names**; optional procedural **workers**; **revive at charter** for cost — **same identity**. |
| **MVP needs** | **Hunger, Energy, Fun**. Stretch: Social, Hygiene, Bladder, Environment. |
| **Need decay** | Combined: time + sleep + activity modifiers. |
| **Need at 0** | **Mood debuff** + **refusal to work**. |
| **Wants** | Separate from needs; **no** toast; **overview UI**; reroll ~every few **in-game days**. |
| **Player control** | **Role NPCs** autonomous; **generic workers** **directly assignable**; mostly autonomy. |
| **Quest sources (MVP)** | **NPC dialogue**; board + mail **stretch**. |
| **Daily reset** | **Configurable** (server midnight / per-player / UTC modes). |
| **Co-op quests** | **Shared** party progress. |
| **Quest abandon** | Per-quest rules; **usually no** abandon penalty; **key quests** re-offered from source if dropped. |
| **Museum** | **Per town** instance. **Town members** (owner + party): full **contribute** (exhibits, donations, museum quests, progression). **Visitors**: **walk and view only** — no modifications. |
| **Exhibits** | **Auto-slot** along gallery spine. |
| **Museum exit** | Return to **door**; if door destroyed → **world spawn**. |
| **Modding** | **Semver** schemas; **`modid:`** namespaced IDs; expansions **soft-fail**; **data-only** packs supported. |
| **Contest MVP** | **Ship-quality** loop for judges/players; **no Raids, no Museum** in contest build (both **post-contest**). If slipping, cut **content breadth** (fewer dailies / one less NPC), not core loop. |
| **Tier progression** | **Hybrid:** quests + economy + population. |
| **Roads** | Prefer **low-strength ENVIRONMENT POI** along roads. |
| **Buildings** | **Management block** per plot: upgrades, construction UI, residents, destroy. |
| **Social / memory** | Relationships **yes**; gossip **maybe**; memories affect **dialogue + utility**. |
| **Dailies** | Procedural templates; parameters influenced by **which villagers exist** and **who posted** the quest. |
| **Localization** | All player-facing text **data-driven**. |
| **Construction** | **Blueprint** flow (not one-click from chest for survival). |
| **Raid rebuild** | **Player-paid repair quest** preferred — **stretch**. |
| **Museum interior** | **Procedural generation** on first visit. |
| **Raid structural damage** | **Raids are an exception** to normal plot protection: raiders **only target a few** buildings (marked **damaged**), **minor block destruction** (no critical/griefing blocks — design allowlist). **Repair** restores state. Not open-ended griefing. |
| **Raid activation** | Raids **only run while the town-owning context is present** — i.e. **at least one relevant player is in/near their town** (exact radius TBD). No offline / empty-town raid punishment. |
| **Demo / video (Q30)** | Flat area for trailer **only**; **code stays world-generic**. |
| **Town treasury** | **Shared currency pool** for the town, usable by **owner + party members** (aligned with Q3 storage/upgrades). Player/party **owns** pooled town money; not a separate NPC wallet unless needed for barter UI. |
| **Inn pool progression** | **Retained** with free placement: see **§1a Inn pool & villager lock**. |

---

## Architecture overview

| Layer | Responsibility |
|--------|----------------|
| **Town / colony** | Anchor placement, territory bounds, permissions, town tier, save data |
| **Plot system** | Plot types (building, park, decor, special), validation, footprint, rotation, upgrade levels |
| **POI registry** | Runtime index of interactable affordances: position, type, capacity, owner plot, interaction tags |
| **Autonomy core** | Needs decay, want rolls, utility scoring, action queue, path target selection |
| **Quest engine** | Quest definitions, objectives, triggers, rewards, daily schedules, town-state prerequisites |
| **Construction** | Prefab placement, costs, prerequisites, POI registration after build |
| **Museum module** | Exterior plot → dimension/instance id → interior chunk allocator / wing graph |
| **Extension API** | Registries + events for third-party mods |

**Java vs data split (target end state):**

- **Java:** town simulation, POI registry, quest state machine, autonomy scheduler, persistence, validation, events.
- **Data (JSON or engine equivalents):** plot types, building prefabs, need curves, interaction tables, quest graphs, dialogue nodes, NPC role templates.
- **Expansion mods:** add asset packs + small Java plugin that **registers** definitions at `PluginSetup` / dedicated bootstrap event.

---

## 1. Governed town system

**Town anchor** establishes legal placement volume and **town id** in save data.

**Suggested mechanics (tune via Q5–Q10):**

- Player places **anchor** → system creates `TownRecord` (owner uuid, dimension, anchor block pos, tier, flags).
- **Plots** are special placed blocks or “plot frame” items that reserve a **footprint** (AABB or template bounds). Only valid if:
  - Inside territory (radius / chunks / boundary rules)
  - No overlap with incompatible plots (or allow overlap rules per type)
  - Ground stability / clearance rules satisfied
  - Owner has permission

**Town tier** gates: max population, plot count, building catalog, raid difficulty, quest chapters.

> **Q31 — Tier progression:** Purely **quest-driven**, **economy + population**, **built POI score**, or hybrid? Hybrid of quest-driven and economy+population

---

## 1a. Inn pool & villager lock (progression)

This is the **same core loop as the original void-village design**, adapted to **free placement**: the Inn is a **buildable plot type** the player places (once unlocked), not a hand-sculpted island.

**Flow**

1. **Charter placed** → `TownRecord` created → **Village Elder** spawns (near charter or fixed offset).  
2. Elder offers the **founding quest**: build the **Inn** (blueprint + costs per §5). Inn requires a **valid plot** anywhere inside territory.  
3. **After Inn completes** → **Inn visitor pool** activates: each day (or on configurable schedule), up to **two** NPCs from the **eligible pool** appear **in the Inn** (seats / rooms / markers as POIs). Eligibility = **unlocked** by tier / achievements / story flags (data-driven).  
4. Player talks to a visitor → they offer a **build my house / workplace** quest tied to a **plot type** and prefab.  
5. **Lock rule:** When the player **accepts** that quest, that villager is **`locked_in`**: they **do not** get shuffled out on the next daily pool refresh. They remain until the building is completed (or quest abandoned per Q21 — define whether abandon **unlocks** shuffle again; yes, return to pool unless one-off story NPC).  
6. When the building completes → villager **moves in** / assumes job POI → **frees an Inn slot** → next refresh can pull another eligible NPC from the pool.

**Why max two:** Keeps dialogue and UI scannable; matches original design; avoids ten simultaneous “build me” quests early game.

**Implementation notes**

- Track `inn_occupants: [npcId, npcId]` (max 2) + `lockedQuestNpcIds: set`.  
- Daily shuffle: only refill **empty** slots from pool; **never** remove `locked_in` or `moved_in` villagers from their commitments.  
- **Free placement** only changes **where** the Inn and follow-up plots sit — not this state machine.

---

## 2. POI registry & plot types

**POI (point of interest)** — logical gameplay object, not only a block:

- **Spatial:** anchor, facing, interaction volumes (bench seat, stove use point).
- **Tags:** `WORK_FORGE`, `EAT`, `SLEEP`, `SOCIALIZE`, `FUN_PARK`, `SHOP`, `QUEST_BOARD`, etc.
- **Capacity:** how many NPCs can use simultaneously; queue / steal chair rules.
- **Availability:** hours, broken state, fuel required.

**Plot categories (examples):**

| Category | Examples | NPC affordances |
|----------|----------|-----------------|
| **Residential** | House, apartment | Sleep, hygiene, storage, private social |
| **Civic** | Hall, notice board | Meetings, quests, buffs |
| **Commercial** | Market stall, inn | Shop, eat, social, work shift |
| **Industrial** | Forge, mill | Craft, work need fulfillment |
| **Recreation** | Park, plaza, fountain, gym | Fun, social, optional skill buffs |
| **Infrastructure** | Wall segment, lamp (if POI), road **decorative** | Mood / environment need (stretch) |
| **Special** | Museum entrance, raid beacon | Instance teleport, combat triggers |

**Registration lifecycle:**

1. Player confirms build → prefab placed → **PlotInstance** created.
2. **POIExtractor** (data-driven per building id) spawns POI entries linked to that plot.
3. On upgrade / damage / removal → POIs updated or unregistered.

> **Q32 — Decorative roads:** Purely cosmetic, or do they **register** low-strength `ENVIRONMENT` POI along polylines? Preferably register low-strength POI
> **Q33 — Modular buildings:** Multi-phase construction (foundation → walls → roof) one plot or **staged plot ids**? Most buildings will have a management block that can be used to interface with the plot including upgrades, construction, residents, and destruction

---

## 3. Sims-like autonomy (needs & wants)

**Needs** — scalar meters (0–100 or 0–1). Tick down over time; **interactions** at POIs restore them.

**Wants** — short-term **weighted desires** (e.g. “wants to chat”, “wants premium meal”) that **bias** utility scores until satisfied or timeout.

**Decision loop (simplified):**

1. Gather eligible POIs: in town, registered, available, path-reachable (or approximate).
2. For each candidate interaction, compute **utility** = f(need deficits filled, want match, distance penalty, social factors, job obligation).
3. Select top action; path; play interaction **state** (duration, animation hook).
4. On interrupt (raid, fire, conversation with player), **replan**.

**Job / duty layer:** Optional **scheduled obligation** (guard shift, shop hours) as **hard constraint** or **large utility bonus** — answers Q17.

> **Q34 — Social graph:** Relationship scores per villager pair? Gossip system?  Yes; Maybe?
> **Q35 — Memories:** Event log affecting dialogue only, or **utility modifiers** (“afraid of raiders”)? yes

---

## 4. Quest system

**Quest types:**

| Type | Description |
|------|-------------|
| **Story / chapter** | Gates town tier, NPC unlocks, new plot types |
| **Town expansion** | “Build X”, “Place Y parks”, “Reach N population” |
| **Daily / rotating** | Reset on schedule; rewards currency / rapport |
| **NPC personal** | Chain per villager; may require relationship |
| **Collection / museum** | Donate items, unlock exhibits |
| **Event** | Raid survived, trader arrived, seasonal (stretch) |

**Quest definition (conceptual fields):**

- `id`, `namespace`, `title`, `description`
- `prerequisites` (town tier, completed quest ids, flags)
- `objectives[]` (place building, kill count, deliver item, talk to NPC, maintain need average)
- `rewards` (items, currency, unlock `building_id`, spawn NPC template)
- `giver` / `turn_in` (NPC id, block POI, auto-complete)
- `repeat` (none, daily, weekly)
- `fail_conditions` (optional)

**Integration:**

- Dialogue **actions**: `StartQuest`, `AdvanceObjective`, `CompleteQuest`
- **World events**: construction complete, raid end, time tick → objective progress
- **Quest board POI**: offers subset of dailies without NPC conversation
- **Inn pool** (§1a): `StartQuest` from an Inn visitor can set **`locksInnSlot: true`** (or equivalent) so the NPC is excluded from next shuffle until complete/abandon.

> **Q36 — Procedural dailies:** Template pools with **random parameters** (deliver 5–10 of item A)? yes, but some parameters are driven by what villagers are in the town and who put up the quest
> **Q37 — Voice / tone:** All text data-driven for **localization** later? Yes

---

## 5. Construction & prefabs

Reuse **`PrefabLoader`** pattern: JSON (or project format) prefabs, anchored placement, rotation.

**Changes from v1 void plan:**

- Placement triggered by **validated plot** at player-chosen location (not hand-placed sign in instance).
- **Cost** and **unlocks** still data-driven (`BuildingData` / `PlotTypeData`).
- **Required NPC in town** remains a valid gate for narrative buildings; optional for generic housing.

> **Q38 — Blueprint vs instant:** Survival places block-by-block **preview** then consumes items, or **one-click** prefab if materials in town chest? Blueprint

---

## 6. Raids & combat (world-integrated)

- **Trigger:** town tier + cooldown + optional time-of-day; **only when at least one town-relevant player is in range** of the town (owner or party member — match Q3). **No raids** against an “empty” town while everyone is far away/offline unless you add an explicit opt-in later.
- **Spawn anchor:** **Toward territory edge** facing town center, or random valid air/ground ring — define in data.
- **Defense:** guard POIs, alarm POI, beacon buff — adapted to **dynamic** town layout.
- **Structural damage (exception to Q4 protection):** Raiders **do not** free-grief the whole base. They **select a small number** of target buildings (data: max count by tier), mark them **`DAMAGED`**, and may **remove/replace only non-critical blocks** from an allowlist (cosmetic / repairable layers — no charter, no management block, no chest core, etc. — **exact rules in data**). **Repair** (block restore + clear damaged flag) via gameplay; **player-paid repair quest** as preferred loop — **stretch** (Q39).

> **Q39 — Rebuild rules:** Auto-repair timer, player-paid repair quest, or **permanent** until rebuilt? Player-paid repair quest would be preferred but is stretch goal

---

## 7. Museum (TARDIS model)

**Exterior:** One **museum plot type** — fixed footprint (e.g. 3×3 door facade). Matches your “same size outside” rule.

**Interior:** Separate **dimension or isolated instance** per museum (or per town — Q22):

- **Chunk / wing allocator:** data-driven **gallery modules** snap to a growing graph (linear hall, cross junction, vertical stair wing).
- **Exhibit slots:** registry entries from base mod + expansions (`exhibit_id` → prefab / entity / item display).
- **Infinite** in practice = **on-demand generation** until cap for performance; **soft cap** configurable.

**Player flow:** Use door interaction → teleport to **saved interior state** for that museum entity id → exit returns to door.

**Permissions (Q23):** **Town members** (owner + party per Q3) may complete museum quests, donate, place/unlock exhibits, and any other **mutating** action. **Non-members** who can enter (e.g. server guests) have **view-only** access — no donations, no exhibit placement, no progression hooks that change state.

> **Q40 — Loading:** Interior chunks generated **procedurally** on first visit vs **preauthored template** segments mixed with empty slots? generated procedurally on first visit

---

## 8. Modularity & expansion mods

**Registration surfaces (public API / events):**

| Registry | Contents |
|----------|----------|
| `PlotTypeRegistry` | id, footprint, category, validator hooks |
| `BuildingRegistry` | prefab, costs, POI blueprint, unlock conditions |
| `PoiInteractionRegistry` | interaction id → duration, need deltas, animation keys |
| `QuestRegistry` | full quest definitions or scripted factories |
| `NpcArchetypeRegistry` | stats, default needs, job tags, dialogue entry |
| `MuseumExhibitRegistry` | exhibit metadata, unlock condition, interior prefab |
| `NeedTypeRegistry` | optional custom needs for weird races/mods |

**Event hooks (examples):**

- `TownCreated`, `PlotPlaced`, `PlotRemoved`, `RaidWaveStart`, `QuestCompleted`, `NpcNeedCritical`
- **Cancelable** validation: `CanPlacePlot` for compatibility mods

**Documentation deliverable (post-MVP):** `Docs/EXTENSION_GUIDE.md` — ID conventions, folder layout, example expansion project, **schema version** field in JSON.

> **Q41 — Code vs data-only expansions:** Support **data-only** packs with no Java, or Java required for anything beyond assets? Support data-only packs with no Java

---

## 9. Economy & progression

- **Currency** (e.g. Aether Shards): mob drops, quest rewards, trade.
- **Town treasury:** **Shared pool** of currency for the town, **owned by the owner + party members** in the sense of **who may spend / withdraw** per config (Q3). Deposits and withdrawals should respect the same permission tiers as shared storage where applicable. Blueprint costs can pull from **treasury** and/or **player inventory** — *recommend: configurable per recipe (early game player-held, late game treasury)*.
- **Supply / demand** (stretch): dynamic prices from town production POIs.

---

## 10. Dialogue system

Keep **branching dialogue** (JSON trees + `CustomUIPage`) as in v1; extend **actions** to hook **quest**, **autonomy** (temporary buff), and **town** state.

---

## Contest MVP — scope & timeline

**Goal:** A **polished, fun** judge-and-player experience — not a tech demo. The submission must **feel like a small slice of the final game**: founding a town, watching villagers **live** (needs + POIs), **talking** to them (dialogue), and **growing** the settlement (plots + blueprints + quests).

**Explicitly out of scope for the contest build (April 28):**

- **Raids** — no raid waves, no raid damage, no guard-combat loop for submission (moves to **Phase A** post-contest).
- **Museum** — no TARDIS door, no interior instance, no exhibits for submission (moves to **Phase C** post-contest).

**Still in scope:** Charter, territory, plots, blueprint construction, **Inn pool (max 2)** + lock-on-accept, **Sims-like autonomy** (MVP needs: **Hunger, Energy, Fun**), **story chapter 1** + **daily quests** (NPC-given per Q18), economy/treasury basics, permissions/config, persistence, **juice** (feedback, tutorial hints, stable UX).

---

### Contest MVP — master deliverables checklist

Use this as the **definition of done** for submission. Every row should be assignable to a week below.

### Progress snapshot (Mar 23, 2026)

What the repo implements today (see Java under `com.hexvane.aetherhaven` and `Server/` assets):

- **Done (MVP slice):** `TownRecord` / `TownManager` → `worlds/<world>/towns.json`; charter place → town + **Elder** spawn; **CharterTownPage**; fixed **chunk-radius** territory + footprint **overlap** checks; **plot placement tool** + **PlotPlacementPage** (ghost `BlockEntity` preview, 90° yaw, nudge, token-gated types); **PlotPlacementValidator** + **PlotPlacementCommit**; **plot sign** + **PlotConstructionPage** + **ConstructionAnimator** (batched prefab place, materials); **ConstructionCatalog** from `constructions.json`; **PlotFootprintRecord** on town after place; **PoiRegistry** API (stub — not fed by builds yet); branching **dialogue** (JSON + **DialoguePage**); plugin **config.json** (territory radius, construction tuning).
- **Not started / stub only:** dissolve town + return charter; territory **expansion** when plots hug edge; formal **`PlotInstance`** state machine; **`POIExtractor`**; autonomy, inn pool, quest engine, treasury, permissions service, flatten toggle, most contest GUIs/NPC roster/quest data.

#### Core simulation (Java / persistence)

- [x] **`TownRecord`**: owner UUID, world name, charter position, tier, territory chunk radius, plot footprint list, elder-spawned flag, created time (`towns.json`). *Dimension is implicit via world file path.*
- [x] **Charter block**: place → create town + spawn **Elder**; **CharterTownPage** shows town info. *Still missing: irreversible dissolve + return item; break-protection rules.*
- [x] **Territory**: chunk-aligned radius from charter; overlap / inside checks for placement + prefab footprint. *Still missing: auto-expand when plots built near edge.*
- [x] **Plot tool / plot block**: construction from catalog + optional plot token; ghost preview; 90° snap; server validation (territory, overlap, prefab load). *Still missing: flatten toggle; slope messaging.*
- [ ] **`PlotInstance`**: state `EMPTY | BLUEPRINTING | COMPLETE`, rotation, owner town, links to management block entity id.
- [x] **Prefab placement pipeline**: `ConstructionAnimator` + `PrefabBufferUtil` / `PrefabStore`; batched place on Build. *No standalone `PrefabLoader` type name.*
- [x] **Plot sign “management” flow**: **PlotConstructionPage** — materials (green/red), Build consumes inventory, prefab completes, sign removed. *No residents list, destroy-building GUI, or upgrade stages.*
- [x] **`PoiRegistry`**: add/remove/query by tag + town (in-memory stub). *Nothing registers POIs from completed builds yet.*
- [ ] **`POIExtractor`**: data-driven POI markers from completed building id (sleep, eat, work bench, fun node, inn counter, etc.).
- [ ] **`AutonomySystem`**: need decay (time + sleep + activity modifiers); utility pick; path to POI; interaction duration; interrupt on dialogue; **abstract sim** when chunk unloaded (Q12).
- [ ] **`InnPoolSystem`**: max 2 occupants; eligible pool from data; daily/periodic refresh; **lock** on accepted quest; free slot on building complete / abandon rules.
- [ ] **`QuestEngine`**: prerequisites, objectives (place_building, talk_to_npc, deliver_item, reach_tier), rewards, abandon, persistence; **party-shared progress** (Q20). *Dialogue actions exist but no full quest state machine.*
- [ ] **Daily reset**: configurable mode; reroll eligible dailies; persist cooldowns.
- [ ] **`TownTreasury`**: deposit/withdraw permissions (owner/member per config); balance in save data; pay from treasury **or** player inv per recipe (pick one default for MVP and document).
- [ ] **Permissions service**: canPlacePlot, canOpenManagement, canWithdraw, etc. (mirrors Q3 + config).

#### GUIs (CustomUIPage + `.ui` templates)

| GUI id | Purpose | Opened from |
|--------|---------|-------------|
| **`CharterTownPanel`** | Dissolve town, confirm irreversible, show town name/tier (minimal) | Charter block interact |
| **`PlotPlacementHud`*** | If not world-only: show footprint name, valid/invalid reason, rotate hint | Plot tool in hand |
| **`ConstructionPanel`** | Blueprint stages, material list (green/red per line), progress, **Build** / **Cancel**, linked NPC requirement if any | Management block |
| **`DialoguePanel`** | Branching text, portrait, 4 choices, actions | NPC interact |
| **`QuestJournal`** | Active + completed (contest: last 10 ok), pin/track, abandon with confirm | Key bind + Elder “remind me” |
| **`DailyQuestOffer`** | Optional compact panel when Elder has new daily (or embed in dialogue only for MVP) | Elder dialogue action |
| **`VillagerNeedsOverview`** | Town roster: per-villager **Hunger / Energy / Fun** bars + current action (“Eating at inn…”) | Management block “Town” tab or key bind |
| **`TreasuryPanel`** | Balance, deposit, withdraw (if not deferred to chat commands) | Town hall / charter sub-menu or management block |

\* *Plot placement may be entirely in-world (ghost only); if so, still ship **on-screen validation text** or action-bar hints so players know why placement fails.*

**GUI status (Mar 23, 2026):** **CharterTownPage** and **PlotPlacementPage** ship; **PlotConstructionPage** + **PlotSignAdminPage** for sign/build; **DialoguePage** + choice row. **Quest journal**, **needs overview**, **treasury**, **daily offer** not built yet.

#### NPCs (contest roster — hand-authored)

| NPC | Role | Spawn / home | MVP dialogues (minimum nodes) | Autonomy / behavior |
|-----|------|----------------|--------------------------------|---------------------|
| **Elder Lyren** (id `elder`) | Founding guide, daily quest giver | On charter place; then wanders near charter / “commons” POI | `elder_intro`, `elder_inn_quest`, `elder_daily_hub` (3–5 branches), `elder_hint_build` | Wander + idle; **no** job shift; pause autonomy while player in dialogue |
| **Innkeeper Mara** (id `innkeeper`) | Runs inn; explains visitor pool | After Inn built; **inn counter** POI as work anchor | `inn_welcome`, `inn_explain_pool`, `inn_small_talk` | When inn hours (always on for MVP): **Work** at counter utility bonus; **Eat/Sleep** at inn POIs; needs-driven otherwise |
| **Merchant Vex** (id `merchant`) | Wants **market stall** built | Inn pool visitor #1 (until locked/moved) | `vex_arrival`, `vex_quest_stall`, `vex_stall_complete`, `vex_repeat_shop_stub`* | **Work** at stall when complete; **Fun** at park; idle at stall |
| **Farmer Corra** (id `farmer`) | Wants **house** (optional small farm POI if time) | Inn pool visitor #2 | `corra_arrival`, `corra_quest_home`, `corra_home_complete` | **Sleep** home; **Eat** inn or home; **Work** at farm POI if present, else generic field marker |

\* *`vex_repeat_shop_stub`: barter can be “coming soon” text if economy integration slips — prefer minimal **sell 1 item** if API allows.*

**Contest count:** **4** named NPCs; **max 2** in Inn at once before they move to their buildings.

#### Dialogue files (JSON trees) — minimum episode list

- `dialogue/elder_*.json` — **≥25 nodes total** across files (intro, choices, conditions for “inn built”, “daily available”).
- `dialogue/innkeeper_*.json` — **≥12 nodes**.
- `dialogue/merchant_*.json` — **≥12 nodes**.
- `dialogue/farmer_*.json` — **≥12 nodes**.
- All dialogue actions wired: **`StartQuest`**, **`CompleteQuest`**, **`OpenQuestJournal`**, **`GrantItem`** (if needed), **`SetTownFlag`**.

#### Quests (data)

- **Story chapter 1** (`quests/chapter1_founding.json` or split files):
  - `q_found_town` (implicit complete on charter — optional formal quest)
  - `q_build_inn` — objective: complete **Inn** blueprint on valid plot; rewards: unlock inn pool + spawn innkeeper
  - `q_merchant_stall` — from Vex; place + complete **Market stall** plot
  - `q_farmer_house` — from Corra; place + complete **Settler house** plot
- **Dailies** (`quests/dailies_pool.json`): **≥3** distinct templates, e.g.  
  - Deliver **N** of item X to Elder (N random in range)  
  - Talk to **named** NPC after they moved in  
  - “Keep **Energy** above Y% for M minutes” (stretch objective — cut if hard)  
  Reset per Q19 config; **1 active daily** at a time for MVP is OK if polish demands.

#### Buildings & art (Blockbench / prefabs — you must produce)

| Asset id | Plot footprint | POIs inside (minimum) | Notes |
|----------|----------------|-------------------------|--------|
| **`charter_monolith`** | 1×1 block (not a plot) | — | Distinct readable silhouette; particle optional |
| **`plot_inn`** | e.g. 12×10 (adjust to art) | counter (**Work**), **2 sleep**, **4 eat** seats, **fun** hearth | Inn interior readable; management block visible |
| **`plot_settler_house`** | smaller | **1 sleep**, **1 eat** | Cozy; window lighting |
| **`plot_market_stall`** | small | **1 work**, **1 shop** interact | Sign readable from distance |
| **`plot_park_small`** | medium | **2 fun** benches, **1 fun** fountain | Proves recreation POI; villagers path here |
| **Road segment** (optional MVP) | decor | low `ENVIRONMENT` POI strip | Only if Week 6 has time |

Each completed building: **management block** embedded in prefab + **POI marker** blocks/entities stripped before export if you use editor helpers.

#### NPC behavior specs (AI / animation)

- **Global:** While in dialogue → **pause autonomy** for that NPC. On close → replan.
- **Elder:** `Idle`, `WanderNear(charter, radius)`, `TurnToPlayer` on interact.
- **Innkeeper:** Prefer `WorkAt(inn_counter)` when Energy > threshold; else satisfy Hunger > Fun > Sleep at inn POIs.
- **Merchant / Farmer post-move:** Prefer `WorkAt(job_poi)` in morning slice (simple time bucket: day = work bias); else autonomy fills needs; **refuse work** if need at 0 (Q16).
- **Path failure:** fall back to **Wander** in plot + show **!** mood particle once (juice); do not hard-lock.

#### Audio & juice (minimum polish)

- [ ] **UI** click/confirm sounds on Construction + Quest Journal.
- [ ] **Blueprint complete** chord + short particle at management block.
- [ ] **Quest complete** sting + toast line (allowed for quests even if wants use overview only).
- [ ] **Ambient:** inn fire loop OR village wind bed (one loop is enough).
- [ ] **Footsteps** optional if engine hooks easy.

#### Config & docs for judges

- [x] **Plugin config** (`config.json` in plugin data): `DefaultTerritoryChunkRadius`, construction batching, dev toggles. *Not yet: permissions toggles, daily reset, treasury (toml naming optional).*
- [ ] **`README.md`**: still describes legacy void-village vision; needs contest **60-second** town-start guide + **no raids/museum** note.
- [ ] **`Docs/JUDGE_ARCHITECTURE.md`**: one-page diagram: Town → Plots → POI → Autonomy → Quest.

---

### Week-by-week schedule (contest)

**Calendar anchor:** Start **Mar 10**, deadline **Apr 28** — **8 weeks**. Adjust labels yearly; **do not** slip scope into raids/museum.

---

#### Week 1 — Mar 10–16 — Foundation & town birth

| Track | Deliverables |
|--------|----------------|
| **Engineering** | Gradle/Hytale plugin stable; package `com.hexvane.aetherhaven`; plugin `setup()` registers empty registries. **`TownRecord`** serialize/deserialize to world plugin data; load on boot, save on stop. |
| **Charter** | Block/item definition; onPlace → create town, persist, spawn **Elder** at offset; interaction opens **`CharterTownPanel`** (stub OK: title + “Dissolve” disabled until Week 5). |
| **Plots** | **One** `PlotType`: `inn_placeholder` footprint only; plot item places ghost mesh; **server-side** validity: inside radius, no overlap; **failure reason** string for client HUD text. |
| **POI** | `PoiRegistry` stub: register/unregister/list by town id. |
| **NPC** | Elder entity spawns; **no** dialogue yet — interaction logs or chat message “…” |
| **Art** | **Charter block** model + texture; **plot frame** ghost model; flat test platform prefab for dev. |
| **Dialogue** | — |
| **GUI** | Placeholder page for charter (blank layout wired). |
| **QA** | Single-player: place charter, relog, town persists. |
| **Status** | **Met (Mar 23, 2026)** — charter + Elder + `towns.json` persistence; plot placement tool with ghost, rotation, and validation; construction from plot sign for catalog entries. Dissolve town deferred. |

**Exit criteria:** Player can place charter, see Elder, place one invalid/valid plot with feedback.

---

#### Week 2 — Mar 17–23 — Blueprint pipeline & first prefab

| Track | Deliverables |
|--------|----------------|
| **Engineering** | `PlotInstance` state machine; **management block** tile entity links to plot id. **`PrefabLoader`** places **Inn** prefab from JSON; clear/regenerate on cancel if multi-stage. |
| **Construction** | **`ConstructionPanel` v1**: list materials from `BuildingData`; green/red counts; **Build** consumes inv (or treasury flag TODO); calls prefab place; sets plot **COMPLETE**. |
| **POI** | **`POIExtractor`** reads `buildings/inn.json` POI list; registers on complete. |
| **Needs** | Component `VillagerNeeds` (hunger, energy, fun); **decay** tick using world time / abstract when unloaded. |
| **NPC** | Elder **idle + wander** within radius of charter. |
| **Art** | **Inn prefab v1** (blockout OK): includes management block + innkeeper spawn marker. |
| **Dialogue** | `elder_intro` + `elder_inn_quest` minimal: **StartQuest(`q_build_inn`)** on choice. |
| **GUI** | **`DialoguePanel`** functional with 2 test trees. **`ConstructionPanel`** wired to management block. |
| **QA** | Complete inn via cheats/fast-cost config; POIs appear in debug overlay/list command. |
| **Status** | **Partial (Mar 23, 2026)** — `PlotConstructionPage` + `ConstructionAnimator` + `constructions.json`; test house / inn placeholder prefab path. **Not yet:** `PlotInstance` state machine, `POIExtractor`, `VillagerNeeds`, elder `StartQuest(q_build_inn)` wiring, POI debug listing. |

**Exit criteria:** Elder gives quest; player can finish Inn blueprint; world shows completed inn.

---

#### Week 3 — Mar 24–30 — Autonomy v1 + Innkeeper life

| Track | Deliverables |
|--------|----------------|
| **Engineering** | **`AutonomySystem` v1**: utility = need deficit × POI restore weight − distance; pick best; **path** (or straight-line + collision stub if path API lags). **Interaction** runs timer → apply need deltas. |
| **Inn pool** | On `q_build_inn` complete: mark `inn_active`; spawn **Innkeeper** at marker; **initialize pool** (empty). |
| **NPC** | Innkeeper uses **inn POIs**; Elder keeps wandering. |
| **Art** | Inn **art pass**: signs, lights, interior props; **innkeeper** skin/model variant. |
| **Dialogue** | `inn_welcome`, `inn_explain_pool`; Elder lines for “inn built” branch. |
| **GUI** | **`VillagerNeedsOverview` v1**: lists entities with needs in town. |
| **QA** | Watch innkeeper **walk** to seat and eat/sleep in test world; fix stuck. |

**Exit criteria:** At least **one** NPC autonomously fills **two** different need types using **two** POI kinds.

---

#### Week 4 — Mar 31–Apr 6 — Inn visitors, lock-on-accept, second building

| Track | Deliverables |
|--------|----------------|
| **Engineering** | **`InnPoolSystem`**: roll **Merchant** into slot 0 on timer; **lock** on `StartQuest` with flag; second slot optional duplicate test NPC or empty until Week 5. |
| **Quest** | **`QuestEngine` v1** persistence; objectives: `TalkTo`, `PlaceOrCompleteBuilding` (implement as “building id complete”). |
| **Building** | **`plot_market_stall`** prefab + `BuildingData` + POIs (**work**, **shop** stub). |
| **NPC** | Merchant NPC template; appears in inn; moves to stall on quest complete. |
| **Dialogue** | `vex_arrival`, `vex_quest_stall`, completion branch. |
| **GUI** | **`QuestJournal`**: active quests, abandon confirm, track to map optional (text only OK). |
| **QA** | Full loop: inn → visitor → accept → locked → build stall → merchant relocates; **save/load** mid-quest. |

**Exit criteria:** **Lock-on-accept** proven; merchant no longer shuffled after accept.

---

#### Week 5 — Apr 7–13 — Third villager, park, dailies, permissions

| Track | Deliverables |
|--------|----------------|
| **Engineering** | **Second inn slot** + **Farmer** rotation rules; tier cap = 4 NPCs for contest. **Permissions** on management/charter/treasury per Q3 + config. |
| **Building** | **`plot_settler_house`** prefab; **`plot_park_small`** prefab + **fun** POIs. |
| **Quest** | `q_farmer_house`; **≥2 daily templates**; Elder `elder_daily_hub` offers rerolled daily. |
| **NPC** | Farmer autonomy + move-in; **merchant + farmer** use park when Fun low. |
| **Dialogue** | Corra full chain; Elder daily branches; fail conditions (optional). |
| **GUI** | **`CharterTownPanel`** finalize **Dissolve** flow + confirm modal. **`TreasuryPanel`** OR slash-commands documented if panel slips. |
| **QA** | **Two visitors** in inn simultaneously before both locked; edge: abandon quest → return to pool. |

**Exit criteria:** Town has **Inn + stall + house + park**; dailies work; permissions tested with second account if possible.

---

#### Week 6 — Apr 14–19 — Polish, economy, tutorial friction pass

| Track | Deliverables |
|--------|----------------|
| **Engineering** | **Treasury** finalize (deposit/withdraw + construction pay rules). **Need @ 0** debuff + refuse work (Q16). **Want** roll stub optional (skip if time — overview only). |
| **Economy** | **Aether Shard** item (if not done); quest rewards pay into treasury or inv; merchant **sell 1 item** OR dialogue stub with clear “post-contest” label (prefer real if easy). |
| **Art** | **Beauty pass** all buildings; consistent palette; **Elder staff** / **Corra hat** readable silhouettes. |
| **Dialogue** | Bark lines when needs critical (“I’m starving!”) — **data-driven** hooks from autonomy. |
| **GUI** | Journal polish: empty state art; construction panel **tooltips** for materials. First-time **hint** after charter: “Place a plot from your inventory…” |
| **Audio** | As per audio checklist minimum. |
| **QA** | **30-minute** fresh playthrough timed; note boring segments; fix top 3 friction bugs. |

**Exit criteria:** Non-author playtester completes loop **without** developer coaching.

---

#### Week 7 — Apr 20–24 — Hardening, performance, multiplayer smoke

| Track | Deliverables |
|--------|----------------|
| **Engineering** | **Stress:** 4 NPCs + full POIs; spread autonomy ticks across frames. **Chunk unload:** abstract sim verified. **Multiplayer:** two clients same town — quest progress shared, no dup exploit. |
| **Bugs** | No hard locks; dialogue re-entry safe; plot overlap edge cases; dissolve town cleans entities. |
| **Content** | **Third daily** template if cut earlier; optional road POI strip. |
| **Docs** | `JUDGE_ARCHITECTURE.md` draft; config comments. |
| **QA** | **Release candidate** build tagged; full checklist at top of § executed. |

**Exit criteria:** RC build exists; **no P0/P1** bugs on happy path.

---

#### Week 8 — Apr 25–28 — Submission package

| Track | Deliverables |
|--------|----------------|
| **Video** | **90–120s** hook + **4–6 min** deep dive: charter → plot → blueprint → inn → visitors → autonomy B-roll → daily → park. Flat demo area OK (Q30). |
| **CurseForge** | Page live: screenshots, feature list, **explicit “Contest build: no raids or museum yet”**. |
| **Zip** | Clean install test on second machine or VM. |
| **Stretch only if ahead:** `Docs/EXTENSION_GUIDE.md` stub; **not** required for contest polish. |

**Exit criteria:** Submission uploaded before deadline.

---

### Fallback if behind (contest — do **not** add raids/museum)

| Priority cut | Sacrifice |
|--------------|-----------|
| 1 | Third **daily** template → keep **2** |
| 2 | **Treasury panel** → commands + post-contest UI |
| 3 | **Farmer** → ship **Merchant + Innkeeper + Elder** only; delay house quest to post-contest |
| 4 | **Park** → one **fun** POI inside inn only |
| 5 | **Want** system entirely post-contest |
| 6 | Reduce dialogue node count **20%** on Corra/Vex |

**Never cut for contest:** Charter → Elder → Inn → inn pool lock → autonomy on **3 needs** → **1** daily → quest journal → persistence.

---

## Post-contest roadmap — toward final vision

Phases are **sequential guides**, not commitments. Length depends on team size and Hytale API maturity.

### Phase A — Colony completeness (≈ 2–4 months post-contest)

- Full **tier** progression; **party permissions**; **terrain** tools (flatten optional)
- **8–12** building types; **4+** recreation POIs; **guard / economy** loops
- **Relationship** + **memory** light model (if desired)
- **Balanced raid** tiers + **controlled** structural damage + **in-town-only** raid presence rule

### Phase B — Quest & narrative depth (≈ 2–3 months)

- **Chapter** system; **branching** consequences; **NPC personal** chains
- **Procedural daily** templates; **town board** + **mail** (if Q18 includes)
- **Voice / localization** pipeline (if Q37)

### Phase C — Museum & collection (≈ 2–4 months)

- **Full wing generator**; **exhibit** dependency graph; **cross-mod** exhibits
- **Interior multiplayer** policy (**Q23** — view-only for non–town-members; full contribute for owner + party)
- **Curator** NPC + donation quests

### Phase D — Extension ecosystem (ongoing)

- Stable **semver** APIs; **example mods**; **CurseForge** “Aetherhaven Extensions” tag
- **Steam Workshop**-style zip packs if platform allows

### Phase E — Advanced autonomy (ongoing)

- **Group activities** (multi-NPC interactions)
- **Emergent schedules** from utility only vs **hybrid** job shifts
- **Mood / trait** depth; **fears** and **aspirations**

---

## Future features catalog (backlog)

Use this as a **pick list**; none are promised on a date.

- Weather / season affects needs and quests  
- Festivals (temporary POI overlays)  
- Farming / production chains tied to POI output  
- Visitor NPCs (immigrants) with requirements  
- Crime / happiness / taxation systems  
- Transportation (minecart-like) between distant plots inside territory  
- Underground / sky **secondary anchors** (subtowns)  
- War between AI factions at world edge  
- Pet / livestock POIs  
- Photo mode exhibits in museum  
- Achievements mirrored as **physical trophies** in museum  
- Server admin commands: reset town, migrate owner  
- Creative **flat test dimension** optional (not default gameplay)  
- **Replay** / clip system for contest-style trailers (stretch)

---

## Risk register (updated)

| Risk | Mitigation |
|------|------------|
| Pathfinding fails in player-built towns | Conservative POI placement; **reachability** check; fallback actions; **flat demo area** for video only (Q30 — does not affect code paths) |
| Scope explosion (Sims + quests + colony) | **Contest MVP** contract: **no raids/museum**; use § fallback table; timebox autonomy v1 |
| Multiplayer desync on town state | **Server authority**; single writer for `TownRecord`; test two clients early |
| Mod API churn | **Version** field; integration test mod in CI (when available) |
| Museum infinite interior perf | **Soft caps**; lazy chunk gen; **instanced** physics scope |
| Contest date slip | Prioritize **video-ready** slice over feature count |

---

## Deprecated assumptions (from v1 plan)

The following are **no longer** the primary design:

- Sky void as **main** village space (optional **test / video** dimension only — see Q30)
- Hand-built **island** layout with static plot signs in shipped world

**Explicitly retained from v1 narrative:** **Inn pool / max two visitors**, Elder-led opening, visitors requesting **houses/workplaces** — now implemented via **charter → plot-placed Inn → governed plots** (§1a), not void islands.

**Reusable from v1:** `PrefabLoader`, dialogue UI pattern, achievement-style flags (fold into **quest/town** state), economy item ideas, NPC **character** concepts (roles map to **archetypes** + POI jobs).

---

## Next step

1. **README + judge doc** — align README with world-integrated colony design; add `Docs/JUDGE_ARCHITECTURE.md`.
2. Derive **exact** JSON schemas for `PlotType`, `Quest`, `PoiBlueprint`, `TownConfig`, and **`InnPoolState`** (occupants, locked NPC ids, eligible pool ids, refresh schedule) from the locked summary — align with **§ Contest MVP** master checklist.  
3. Spike **territory expansion** rule: precise algorithm when “plot near edge” extends chunk radius (prevent exploit: zigzag single plots — consider **min plot value** or **tier gate**).  
4. Decide **quest abandon** for locked Inn visitors: **return to shuffle pool** vs **cooldown**? Return to pool with optional story exceptions (`innPoolBehavior` per quest).  
5. **Post-contest:** data-design **raid damage allowlist** per building tier (Raids not in contest build).
