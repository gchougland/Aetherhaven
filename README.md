# Aetherhaven

**A Hytale mod by Hexvane** — *Hytale New Worlds Modding Contest 2026* (NPCs / Experiences)

A living sky-village in its own void dimension. Floating islands connected by bridges, NPCs with daily schedules and memory, and growth driven by construction, achievements, and defense against sky-raider invasions.

---

## Vision

Aetherhaven is a cluster of floating islands in a void world. Players enter via an **Aether Portal**, then help the village grow by:

- **Building** — Construct market stalls, residences, and civic buildings on district plots.
- **Interacting** — Talk to the Village Elder, merchants, guards, and other NPCs through a custom dialogue system.
- **Defending** — Survive raids from Sky Raiders; guards and the Guard Captain respond via alarm/beacon and combat.
- **Growing** — Unlock new district islands and NPCs as the village tiers up (Settlement → Town → Haven).

Stretch goals include a Museum island with a Curator NPC and achievement-driven exhibits.

---

## Core Systems (Planned)

| System | Description |
|--------|-------------|
| **Void dimension + portal** | Custom world with no terrain; entry via Aether Portal structure; respawn at arrival platform. |
| **Island grid** | 2D grid of island slots (district/special/bridge); prefab placement and persistence across restarts. |
| **Prefab loader** | JSON-defined block prefabs (islands, buildings, raid barges) placed at anchors; batched over ticks for performance. |
| **Dialogue system** | Branching dialogue trees (JSON), CustomUI with portrait and choices; conditions and actions (objectives, achievements). |
| **Construction** | Plot Marker NPCs at empty plots; Construction UI for resources and confirm; prefab placement and NPC spawn on build. |
| **Achievements + growth** | Per-player and village-wide flags; unlock actions (spawn NPCs, new districts); tier progression. |
| **Economy** | Aether Shards as currency; merchants with barter; optional village resource pool. |
| **Raids** | Tier- and cooldown-gated; Sky Raider Barge prefab + Raider NPCs; Guard/Guard Captain alarm and combat; cleanup on raid end. |

---

## NPC Roster (10 for contest)

Village Elder, Guards (×2) + Guard Captain, Merchant, Farmer, Blacksmith, Scholar, Innkeeper, Village Child, Visiting Trader. Each ties into dialogue, schedules, combat, or economy.

---

## Project layout

```
Aetherhaven/
├── src/main/java/com/hexvane/aetherhaven/
│   └── AetherhavenPlugin.java   # Entry point; systems registered in setup()
├── src/main/resources/
│   ├── manifest.json
│   └── Server/                  # Items, NPCs, prefabs, dialogue, etc.
├── build.gradle.kts
├── gradle.properties            # plugin_group=Hexvane, plugin_author=Hexvane
├── Aetherhaven_Dev_Plan.md      # Full design, schedule, and architecture
└── README.md                    # This file
```

---

## License

See [LICENSE.md](LICENSE.md).
