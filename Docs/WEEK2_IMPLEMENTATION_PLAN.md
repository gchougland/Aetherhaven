---
name: Week 2 Aetherhaven
overview: "Close Week 2 gaps from Aetherhaven_Dev_Plan.md: PlotInstance, management block (Furniture_Village_Bookcase visual), POIExtractor, VillagerNeeds, Elder dialogue/quest with manual turn-in, inn_v1 prefab, Elder wander. Source reference: C:\\Users\\gchou\\Documents\\HytaleModding\\HytaleSourceCode"
todos:
  - id: plot-instance
    content: Introduce PlotInstance model + Gson migration; extend PlotSignBlock with plotId; update TownRecord/TownManager/PlotPlacementPage/PlotPlacementCommit/validator overlap
    status: pending
  - id: management-block
    content: ManagementBlock chunk component; visual = Furniture_Village_Bookcase (see §2); OpenCustomUI supplier; refactor PlotConstructionPage; onComplete → COMPLETE; prefab uses bookcase voxels
    status: pending
  - id: prefab-poi
    content: PrefabResolveUtil; POIExtractor + Server/Buildings/*.json; PoiRegistry unregister-by-plot; new inn_v1.prefab + constructions.json + POI JSON
    status: pending
  - id: quests-dialogue
    content: Town quest state; DialogueActionExecutor start/complete_quest; new dialogue conditions; AetherhavenDialogueWorldView; elder JSON trees + resolver + NPC DialogueId
    status: pending
  - id: needs-system
    content: VillagerNeeds component + decay system; attach on elder spawn; debug config + subcommands (poi, plot, needs, quest)
    status: pending
  - id: elder-wander
    content: Elder role JSON BodyMotion wander from vanilla reference, or ElderWanderSystem + elder UUID on TownRecord
    status: pending
isProject: false
---

# Week 2 deliverables — implementation plan

> **Canonical schedule row:** [Aetherhaven_Dev_Plan.md](../Aetherhaven_Dev_Plan.md) (Week 2 table + **Week 2 implementation addendum**). This file is the detailed engineering plan kept in-repo for normal editing.

## Current baseline (what already exists)

- `PlotConstructionPage` + `ConstructionAnimator`: batched prefab place; sign removed on **animation complete** via `onComplete`.
- `PoiRegistry`: in-memory index only; nothing registers on build today.
- `TownRecord` + `PlotFootprintRecord`: footprints saved on placement but **no plot id, state, or link to sign/management block**.
- `DialoguePage` + JSON: functional UI; `DialogueActionExecutor` **stubs** `start_quest`.
- `DialogueWorldView.DefaultDialogueWorldView`: always false for flags/achievements — must be replaced.
- Elder role `Aetherhaven_Elder_Lyren.json`: `BodyMotion: Nothing` (no wander yet).

## Source / asset reference (implementation)

Use `C:\Users\gchou\Documents\HytaleModding\HytaleSourceCode` (and extracted base-game assets there) when verifying block JSON shape, interaction wiring, NPC `BodyMotion` types, prefab paste behavior, and APIs not present in the Aetherhaven repo alone.

## Architecture (target data flow)

```mermaid
sequenceDiagram
    participant Player
    participant Placement as PlotPlacementCommit
    participant Town as TownRecord
    participant Sign as PlotSignBlock
    participant Build as PlotConstructionPage
    participant Anim as ConstructionAnimator
    participant POI as POIExtractor
    participant Reg as PoiRegistry
    participant Elder as Dialogue_Elder

    Player->>Placement: confirm plot
    Placement->>Town: add PlotInstance BLUEPRINTING
    Placement->>Sign: set plotId + constructionId
    Player->>Build: Build
    Build->>Anim: place prefab; onComplete
    Anim->>Town: set PlotInstance COMPLETE
    Anim->>POI: load building JSON by constructionId
    POI->>Reg: register PoiEntry list
    Player->>Elder: dialogue turn-in
    Elder->>Town: complete_quest q_build_inn
```

## 1. Plot instance state machine + persistence

**Replace** the flat `List<PlotFootprintRecord>` model with a first-class **PlotInstance** (serialized in `towns.json`), e.g. fields:

- `plotId` (UUID), `constructionId`, `state` (`BLUEPRINTING` | `COMPLETE`)
- Footprint AABB (reuse current min/max fields)
- `signX/Y/Z` (optional; for debugging / migration)
- `builtAtGameTime` or `lastStateChangeEpochMs` (for future abstract sim)

**Migration:** On load, if old saves only have `PlotFootprintRecord`, either drop them or one-time convert; log a warning.

**Chunk components:**

- Extend `PlotSignBlock` with `plotId` (string UUID).
- Add **ManagementBlock** component: `plotId`, `townId` (string), optional `constructionId` cache.

**Placement path:** `PlotPlacementCommit.placePlotSign` — generate `plotId`, write component, append `PlotInstance` in **BLUEPRINTING** with footprint (same computation as `PlotPlacementPage.tryPlace`).

**Overlap checks:** `TownRecord.findOverlappingPlot` should use `PlotInstance` bounds (same intersection logic).

## 2. Management block + dual entry to construction UI

**Visual (locked):** In-world appearance must match base-game **`Furniture_Village_Bookcase`**. Use that block id in **`inn_v1.prefab.json`** for the management voxel unless source review shows vanilla blocks cannot carry mod `ChunkStore` components; in that case, add a thin **mod block** that reuses the bookcase model/appearance (copy asset references from base `Furniture_Village_Bookcase` JSON under `HytaleSourceCode`) and registers **ManagementBlock** + interaction like `PlotSignBlock` / `Aetherhaven_Plot_Sign.json`. Keep a stable logical id in code (e.g. `Aetherhaven_Management_Block`) if a wrapper is required.

- Register `OpenCustomUIInteraction` in `AetherhavenPlugin`: when the targeted block has **ManagementBlock**, resolve `PlotInstance` + `ConstructionDefinition` by `plotId` and open `PlotConstructionPage`.

**Refactor `PlotConstructionPage` constructor** to accept either `(Ref<ChunkStore> plotSignRef, Vector3i signPos)` **or** `(Ref<ChunkStore> managementBlockRef, ManagementBlock comp)`. For **COMPLETE** plots: materials satisfied (all green), **Build** disabled, optional "Completed" subtitle.

**Prefab placement completion:** Pass `onComplete` into `ConstructionAnimator.start` that (world thread):

1. Marks matching `PlotInstance` **COMPLETE** in `TownManager` and saves.
2. Runs **POIExtractor**.
3. Prefab contains **Furniture_Village_Bookcase** (or wrapper) at the management location; `onComplete` may need to **stamp** ManagementBlock + `plotId`/`townId` if paste does not carry components (verify in source; post-pass over offset from `ConstructionDefinition` if needed).

**Multi-stage cancel/regenerate:** TODO hook on `PlotInstance` without full staged builds in Week 2.

## 3. PrefabLoader naming / clarity

Extract **PrefabResolveUtil** (or `PrefabLoader`) consolidating path resolution from `PlotConstructionPage` and `PlotPlacementValidator`; keep `ConstructionAnimator` as the placer.

## 4. POIExtractor + building JSON

- Add `Server/Buildings/<constructionId>.json` (e.g. `inn_v1.json`).
- Schema: `{ "pois": [ { "localX", "localY", "localZ", "tags": ["EAT"], "capacity": 1 } ] }` in prefab-local space.
- **POIExtractor:** load by `constructionId`, rotate offsets with build yaw, register `PoiEntry` (`plotId`, `townId`).
- **Idempotent registration:** `PoiRegistry.unregisterByPlotId` (or equivalent) before re-register.

## 5. Quest slice for Week 2 (`q_build_inn`, manual turn-in)

**Active** from dialogue; **complete** only when player talks to Elder **after** the inn plot is **COMPLETE**.

- Town-scoped quest state on **TownRecord** (`activeQuests` / `completedQuests` or `TownQuestState`).
- **DialogueActionExecutor:** `start_quest` / `complete_quest`; resolve town via `TownManager.findTownForOwnerInWorld`.
- New conditions in **DialogueConditionEvaluator:** `town_quest_active`, `town_quest_completed`, `town_has_complete_plot` (by `constructionId` + `COMPLETE`).
- **AetherhavenDialogueWorldView** with `World` + `TownManager` (inject from plugin when opening dialogue).

**Dialogue assets:** `aetherhaven_elder_week2` (or split trees); turn-in branch with `complete_quest`. Update `dialogues.json`, `DialogueResolver`, Elder `DialogueId`. **Construction id:** `inn_v1` constant in `AetherhavenConstants`.

## 6. VillagerNeeds + decay + debug/QoL

- **VillagerNeeds** component; **VillagerNeedsSystem** using world game time delta (discover API in HytaleSourceCode).
- Attach on Elder spawn when `spawnNPC` allows.
- **Abstract sim stub:** `lastSimulatedTime` on component.

**Debug commands** (gated by `DebugCommandsEnabled` in config): `poi list`, `plot list`, `needs inspect` / `needs set`, `quest grant|complete|clear`.

## 7. Elder idle + wander

- Prefer vanilla **BodyMotion** wander from reference NPC JSON (`HytaleSourceCode` / Assets).
- Fallback: **ElderWanderSystem** + elder UUID on `TownRecord`.

## 8. New `inn_v1` prefab + constructions catalog

- **`Server/Prefabs/inn_v1.prefab.json`:** place **`Furniture_Village_Bookcase`** (or appearance-equivalent mod wrapper) at a clear interior clerk / records spot as the management voxel; optional innkeeper spawn marker (same pattern as `npc_test_villager.prefab.json`).
- **`constructions.json`:** entry `inn_v1` + `plotAnchorOffset` aligned with placement tool.
- **`Server/Buildings/inn_v1.json`:** POI list (subset OK; extensible rows).

## 9. QA checklist

- Charter → Elder → quest active → build inn → COMPLETE → POIs listable → management UI shows completed → Elder turn-in completes quest.
- Needs decay + debug commands; relog persistence sanity.

## Risk / implementation notes

- Whether vanilla **Furniture_Village_Bookcase** can host mod components — confirm in `C:\Users\gchou\Documents\HytaleModding\HytaleSourceCode`.
- Large `inn_v1` prefab diff; reuse `test_house` material patterns where practical.
