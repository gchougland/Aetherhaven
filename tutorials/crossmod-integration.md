# Crossmod integration with Aetherhaven

This guide explains how another mod can add villagers, buildings, dialogue, quests, quest board pools, shop stock, prices, and field-rescue talk using assets (and a little Java when you need custom UI or conditions).

Aetherhaven scans every registered asset pack for files under `Server/Aetherhaven/`. Your mod should ship an asset pack (`IncludesAssetPack: true`) and list Aetherhaven as an optional or hard dependency so it loads in a sensible order.

## Manifest

```json
{
  "Group": "YourGroup",
  "Name": "YourMod",
  "IncludesAssetPack": true,
  "OptionalDependencies": {
    "Hexvane:Aetherhaven": "*"
  }
}
```

Use a hard `Dependencies` entry if your mod cannot run without Aetherhaven.

## Folder map

Place content under your pack root:


| Path                                       | Purpose                                                             |
| ------------------------------------------ | ------------------------------------------------------------------- |
| `Server/Aetherhaven/Villagers/`            | Villager gameplay defs (dialogue trees, gifts, schedules, inn pool) |
| `Server/Aetherhaven/VillagerGiftPatches/`  | Append gift loves/likes/dislikes onto an existing villager          |
| `Server/Aetherhaven/VillagerSchedules/`    | Weekly schedules by NPC role id                                     |
| `Server/Aetherhaven/ScheduleLocations/`    | Custom schedule location symbols mapped to buildings                |
| `Server/Aetherhaven/VillagerSchedulePatches/` | Add, replace, or remove schedule times on existing roles         |
| `Server/Aetherhaven/GuideTopics/`          | Journal guide markdown pages (`<locale>/<topicId>.md`)              |
| `Server/Aetherhaven/GuidePatches/`         | Append guide hub `sub-topics` (e.g. under `villagers`)              |
| `Server/Aetherhaven/NpcRoles/`            | Engine NPC roles (only loaded when Aetherhaven is present)          |
| `Server/Aetherhaven/NpcModels/`           | Optional NPC models for those roles (AH-gated)                      |
| `Server/Aetherhaven/Dialogue/`             | Full dialogue trees (same id overrides earlier packs)               |
| `Server/Aetherhaven/DialoguePatches/`      | Inject choices/nodes into an existing tree                          |
| `Server/Aetherhaven/Quests/`               | Story / housing quests                                              |
| `Server/Aetherhaven/QuestBoardExtensions/` | Extra quest board fetch/hunt/raid entries                           |
| `Server/Aetherhaven/Buildings/`            | Construction definitions                                            |
| `Server/Aetherhaven/ShopPrices/`           | Gold prices for shop spots                                          |
| `Server/Aetherhaven/ShopLoot/`             | Shop spot loot tables (append or replace)                           |
| `Server/Aetherhaven/Townsfolk/`            | Townsfolk character pool                                            |
| `Server/Aetherhaven/Personalities/`        | Personality traits                                                  |
| `Server/Prefabs/`                          | Building prefabs referenced by constructions                        |
| `Server/Languages/en-US/`                  | English lang keys                                                   |


Same content id from a later pack replaces an earlier pack for whole-file catalogs (villagers, dialogue trees, quests, buildings). Prices, loot, quest board pools, dialogue patches, gift patches, and schedule patches merge as described below.

## Villagers and NPC roles

Crossmod villagers should put **engine NPC roles** under `Server/Aetherhaven/NpcRoles/`, **not** under `Server/NPC/Roles/`. Hytale only auto-loads `Server/NPC/Roles/`; Aetherhaven scans `NpcRoles` after its dialogue action, attitude groups, and `Aetherhaven_Human` exist. If Aetherhaven is missing, those role files are ignored.

1. Add role JSON under `Server/Aetherhaven/NpcRoles/` (role id = filename without `.json`). Interact should open Aetherhaven dialogue (`OpenAetherhavenDialogue`), same pattern as Aetherhaven’s own roles. Optional component files can live under `NpcRoles/.../Components/` (loaded before spawnable roles).
2. Optional appearance models under `Server/Aetherhaven/NpcModels/` (model id = filename). You may still parent models to `Aetherhaven_Human` or reuse Aetherhaven villager models that already ship with Aetherhaven under `Server/Models/`.
3. Add a villager definition under `Server/Aetherhaven/Villagers/` with matching `npcRoleId`, `residentTreeId` / `visitorTreeId`, gifts, schedule bindings, and so on.
4. For a shop worker who starts in the inn pool: set `innPoolEligible`, `visitorBindingKind` (e.g. `visitor_angler`), `workConstructionId` to **your shop building id** (not `plot_house`), and `dialogueVillagerKind` (resident kind after promotion, e.g. `angler`). Use `countsAsConstructionId: ["plot_house"]` on the building when it should also offer a resident bed; Aetherhaven uses house residency separately from workplace assignment and ignores generic house/park/player-shop aliases when listing worker slots on the town records shelf. When that shop plot completes, Aetherhaven promotes the matching inn visitor if the shop quest is active or completed. Set `assignNpcRoleId` on the shop quest to the same NPC role and add a `try_promote_inn_pool_shops` lifecycle effect on quest complete so promotion runs after dialogue turn-in when the shop is already built.
5. **Display name for Aetherhaven UI** (town journal, quest board, gift history, and similar): add a line to your pack’s `Server/Languages/en-US/aetherhaven_ui_journal_items_tail.lang`:

```lang
npcRoles.YourMod_Fisherman.name=Reed Castwell
```

Runtime id is `aetherhaven_ui_journal_items_tail.npcRoles.YourMod_Fisherman.name`. Aetherhaven looks up that key by role id. The NPC role’s own `NameTranslationKey` (for engine nametags) can use a different bundle; journal and quest UI do not use it.

You can ship a brand new villager this way, or only contribute prices and dialogue patches for an existing Aetherhaven villager.

### Gift list patches

Same `npcRoleId` under `Villagers/` replaces the **whole** villager definition. To add loves/likes/dislikes onto someone like the Merchant without copying their file, use `Server/Aetherhaven/VillagerGiftPatches/`:

```json
{
  "schemaVersion": 1,
  "targetNpcRoleId": "Aetherhaven_Merchant",
  "addGiftLoves": ["Fish_Salmon_Item"],
  "addGiftLikes": ["CozyFishing_Wooden_Rod"],
  "addGiftDislikes": ["Deco_Trash"]
}
```

Item ids are appended (duplicates skipped). Put full gift lists on your own villager def when you own that role.

## Villager schedules

Villagers follow a weekly routine: at set days and times they go to symbolic locations (home, work, inn, park, gaia altar, shop, or your own registered symbols). Aetherhaven resolves those symbols to town plots at runtime.

### Built in location symbols

| Symbol | Meaning |
| --- | --- |
| `home` | The villager's house plot, or their workplace if they have no house yet |
| `work` | Their job plot (from `workConstructionId` or villager kind) |
| `inn` | A completed inn plot |
| `park` | A completed park plot |
| `gaia_altar` | A completed Gaia altar plot |
| `shop` | Browsing shop tagged buildings town wide |

Symbols are case insensitive.

### Register a custom location

When your mod adds a new building villagers should visit on a schedule, register its symbol once under `Server/Aetherhaven/ScheduleLocations/`:

```json
{
  "schemaVersion": 1,
  "constructionId": "plot_fishing_shop",
  "displayNameLangKey": "example_mod.schedule.fishing_dock.name"
}
```

The file name (without `.json`) is the symbol id, for example `fishing_dock.json` → `fishing_dock`. The building must exist under `Server/Aetherhaven/Buildings/`. If several complete plots use the same construction, set `"scheduleSharedUtilityPick": true` on that building JSON (same as inn and park) so villagers pick one plot and stick with it for the day.

Optional `displayNameLangKey` is used in the town journal schedule view. Add the English line in your pack's `Server/Languages/en-US/` files.

You cannot register symbols named `home`, `work`, `inn`, `park`, `gaia_altar`, or `shop`.

### Per villager location override

On a villager definition, `scheduleSharedLocations` maps a symbol to a different construction id for that villager only:

```json
"scheduleSharedLocations": {
  "fishing_dock": "plot_special_pier"
}
```

Use this when one villager should visit a different building than the global registration.

### Full schedule files

Ship a complete week under `Server/Aetherhaven/VillagerSchedules/<scheduleRoleId>.json`. Same role id from a later pack replaces the whole file. You can also embed `weeklySchedule` on your villager def (that overrides the file).

Example transition:

```json
{
  "dayOfWeek": "WEDNESDAY",
  "hour": 14,
  "minute": 0,
  "location": "fishing_dock"
}
```

`dayOfWeek` can be `MONDAY` through `SUNDAY` or `1` through `7` (Monday = 1).

### Schedule patches (extend an existing villager)

To add or tweak times on an Aetherhaven villager without copying their full schedule, use `Server/Aetherhaven/VillagerSchedulePatches/`:

```json
{
  "schemaVersion": 1,
  "targetScheduleRoleId": "Aetherhaven_Merchant",
  "removeTransitionIds": ["example_wed_fishing"],
  "removeTransitions": [
    { "dayOfWeek": "TUESDAY", "hour": 17, "minute": 0 }
  ],
  "addTransitions": [
    {
      "id": "example_wed_fishing",
      "dayOfWeek": "WEDNESDAY",
      "hour": 14,
      "minute": 0,
      "location": "fishing_dock"
    }
  ]
}
```

Patch order per target schedule:

1. `removeTransitionIds` drops transitions with a matching optional `id`.
2. `removeTransitions` drops transitions at the exact day, hour, and minute (for bundled schedules that have no ids).
3. `addTransitions` appends each entry, or **replaces** an existing transition when the same `id` is already present.

`targetScheduleRoleId` is the schedule file name (usually the same as `npcRoleId`, or the villager's `scheduleRoleId` when set). Later patch files win on id conflicts.

Register the location symbol before referencing it in a patch. The server log warns if a patch uses an unknown symbol.

## Townsfolk (optional mod characters)

Townsfolk are hand designed characters that can appear as **tourists**, **guards**, or **guild hall adventurers** in Aetherhaven towns. They are separate from named **villagers** (inn workers with `npcRoleId` and dialogue trees).

Ship in your asset pack:

1. **Character definition** — `Server/Aetherhaven/Townsfolk/<characterId>.json` (same fields as Aetherhaven’s townsfolk: `modelAssetId`, `personalityIds`, `allowedAssignmentKinds`, optional `moveInRequirements` for tourist housing quests, optional `equipmentProfileId` for guards).
2. **Model asset** — `Server/Models/Townsfolk/<ModelAssetId>.json` (global model catalog; `modelAssetId` must match the filename without `.json`). You can parent to `"Player"` or to your own base model and attach **vanilla player cosmetics** in `DefaultAttachments` (greyscale textures plus `GradientSet` / `GradientId` tints work the same as on human townsfolk).
3. **Portrait** — `Common/Icons/ModelsGenerated/<ModelAssetId>.png`, referenced from the model JSON `Icon` field.
4. **Personalities** — reuse Aetherhaven personality ids under `Server/Aetherhaven/Personalities/`, or add new ones in your pack plus English greeting lines in `Server/Languages/en-US/`.
5. **Optional plugin gate** — when your character only makes sense with your mod installed, set:

```json
"requiresOptionalPlugin": {
  "group": "hexvane",
  "name": "YourModName"
}
```

Use the same `group` and `name` as your plugin manifest (`Group` / `Name`). Matching is case insensitive at runtime.

Aetherhaven still **loads** the definition (so existing save checkouts stay valid), but the character is **excluded from new pool draws** until that plugin is loaded and enabled. Example: Machinaria robot townsfolk `reginald_volt` and `copper_pin` use the shared `Machinaria_Robot_Base` model with cosmetic attachments.

Tourists should include `moveInRequirements` (item ids the visitor asks for before accepting a house). See Aetherhaven’s townsfolk JSON for examples.

## Journal guide pages

The town journal **Guide** tab walks markdown topics from Aetherhaven’s wiki tree (`welcome` → hubs like `villagers` / `mechanics`). Crossmod pages do **not** go under `Common/Docs/`; use the namespaced folders below so you do not replace Aetherhaven’s hubs.

1. Add a topic under `Server/Aetherhaven/GuideTopics/<locale>/<topicId>.md` (ship at least `en-US`). Same front matter as Aetherhaven wiki pages (`name`, `description`, optional `npcRoleId`, optional `sub-topics`).
2. Add a patch under `Server/Aetherhaven/GuidePatches/` that appends your topic id onto an existing hub:

```json
{
  "schemaVersion": 1,
  "targetTopicId": "villagers",
  "addSubTopics": ["villager_angler"]
}
```

Common hubs: `welcome`, `getting_started`, `mechanics`, `villagers`, `faq`. Later packs append; duplicate ids are skipped. Same `locale` + `topicId` from a later pack replaces the markdown. Guide cache clears when Aetherhaven reloads asset catalogs.

Example topic `Server/Aetherhaven/GuideTopics/en-US/villager_angler.md`:

```markdown
---
name: "Reed Castwell"
description: "Quests and gifts for Reed Castwell."
npcRoleId: YourMod_Angler
---

Reed runs the fishing shop once it is built.

### Quests

#### Fishing shop
1. Talk to Reed at the inn and accept the quest.
2. Build the fishing shop.
3. Find Reed at the shop and close the quest in dialogue.
```

Optional images can use the same `wiki/...` AssetImage paths as Aetherhaven pages, or rely on `npcRoleId` so villager hero art resolves from the NPC portrait.

## Buildings

Add a construction JSON under `Server/Aetherhaven/Buildings/` and the prefab it points at under `Server/Prefabs/`. Follow existing Aetherhaven building files for fields like `id`, `prefabPath`, materials, and plot tokens.

### Variant Of (plot creator)

The plot creator staff **Variant** kind lets players make a style variant that `countsAsConstructionId` points at a base building. The Variant Of dropdown is built from the live construction catalog:

- Eligible bases: constructions with **no** `countsAsConstructionId` of their own, and that are not decoration plots or wall segments
- Any mod's building under `Server/Aetherhaven/Buildings/` appears automatically when both mods are installed
- Aetherhaven's `plot_creator_main_constructions.json` only sets preferred order and label lang keys for core buildings

For shop-like bases (so the wizard asks for shop spots / shop POIs), set building `tags` to include `shop`, and/or add a POI with tag `SHOP`. Use `home` / `house`, `work`, `amenity`, or `player_shop` tags when you want those wizard substeps.

Example fishing shop base (other players can then make variants of it):

```json
{
  "id": "plot_fishing_shop",
  "displayName": "Fishing Shop",
  "displayNameLangKey": "example_mod.building.fishing_shop.name",
  "prefabPath": "plot_fishing_shop.prefab.json",
  "tags": ["shop", "work"],
  "pois": [
    {
      "localX": 0,
      "localY": 1,
      "localZ": 0,
      "tags": ["WORK", "SHOP"],
      "capacity": 1,
      "interactionKind": "WORK_SURFACE"
    }
  ]
}
```

Do **not** set `countsAsConstructionId` on the base itself. Variants created in the plot creator will point at this id.

## Dialogue trees vs patches



### Full trees

`Server/Aetherhaven/Dialogue/*.json` with `{ "id", "entry", "nodes" }`. Same `id` replaces the whole tree.

### Patches (recommended for adding one option to a villager hub)

`Server/Aetherhaven/DialoguePatches/*.json`:

**Item requirements on choices** — Add an `itemRequirements` array (same shape as building materials: `itemId`, `count`) on any choice. The dialogue UI shows large item icons on the right, greys the row when the player’s inventory is short, and only enables the choice when they have everything. Tourist move in lines can keep `"icon": "move_in_item"` instead; requirements are inferred from the speaking townsfolk. Use `"whenFalse": "disabled"` with a `condition` when you also need quest or flag checks.

```json
{
  "schemaVersion": 1,
  "targetTreeId": "aetherhaven_merchant",
  "addNodes": {
    "example_fish_hub": {
      "speaker": "example_mod.dialogue.fish.speaker",
      "text": "example_mod.dialogue.fish.body",
      "choices": [
        {
          "text": "example_mod.dialogue.fish.openJournal",
          "actions": [{ "type": "example_open_journal" }],
          "next": null
        },
        {
          "text": "example_mod.dialogue.fish.back",
          "next": "main_hub"
        }
      ]
    }
  },
  "nodePatches": [
    {
      "nodeId": "main_hub",
      "addChoices": [
        {
          "id": "example_fish_talk",
          "text": "example_mod.dialogue.fish.hubChoice",
          "condition": { "type": "example_mod_ready" },
          "whenFalse": "hide",
          "next": "example_fish_hub"
        }
      ]
    }
  ]
}
```

Stable hub node ids in Aetherhaven trees are usually `main_hub`. Give patched choices an `id` so a later pack can replace the same choice instead of duplicating it.

### Custom actions (open your own UI)

Resolve Aetherhaven on enable and register action types:

```java
AetherhavenPlugin ah = AetherhavenPlugin.get();
if (ah == null) {
    return;
}
ah.getDialogueActionRegistry().register("example_open_journal", (action, playerRef, store, out, npcRef) -> {
    out.setCloseDialogue(true);
    out.setAfterClose(() -> {
        Ref<EntityStore> pref = playerRef; // capture carefully; re-resolve from PlayerRef if needed
        // Open your CustomUI page here (same world.execute pattern Aetherhaven uses for shops).
    });
});
```

`setAfterClose` runs after the dialogue page finishes, so you can open another UI without fighting page ack counters. Unregister on disable.

Built-in action types still work in your JSON (`open_barter_shop`, `start_quest`, `complete_quest`, `despawn_npc`, `give_item`, and so on).

### Field rescues (portal / instance dialogue)

Field-rescue talk often runs inside a **portal instance** while the player’s town lives on the overworld. Aetherhaven resolves town state for dialogue **across all loaded worlds**, so you do **not** need a custom soft-dep action just to complete a town quest or check quest conditions in that instance.

Typical rescue closer (stock actions only):

```json
{
  "actions": [
    { "type": "complete_quest", "id": "q_your_rescue" },
    { "type": "despawn_npc" },
    { "type": "close" }
  ]
}
```

- `complete_quest` / town quest conditions (`townQuestActive`, `townQuestCompleted`, and the other town checks) find the player’s town even when the dialogue world is a temporary instance.
- `despawn_npc` always plays a vanish particle and sound. Default is the Crystal Keeper rescue poof (`Aetherhaven_Crystal_Keeper_Vanish`). If the NPC has an Aetherhaven rescue binding (Crystal Keeper / Pyrotechnic), that trigger’s FX is used instead. Soft mods without a `TownVillagerBinding` still get the default.
- Optional overrides on `despawn_npc`: `particleSystemId` and/or `soundEventId` (non-blank values win over the trigger / default for the fields you set).

You still need a quest JSON under `Server/Aetherhaven/Quests/`, a dialogue tree (or patch) that runs those actions, and an NPC interact that opens Aetherhaven dialogue. Spawning and quest-start wiring stay on your mod; Aetherhaven only needs the stock complete + despawn path for the talk-out in the instance.

### Custom conditions

```java
ah.getDialogueConditionRegistry().register("example_mod_ready", (condition, playerRef, store, npcRef) -> {
    return /* your check */;
});
```

Unknown types fall through to Aetherhaven’s built-in conditions; registered types are checked first.

**Built-in NPC and town checks (dialogue JSON):**

| Type | Params | Meaning |
|------|--------|---------|
| `npc_other_villager_nearby` | `radius` (default 8), optional `kind` | Another town villager is near the speaker |
| `npc_reputation_hearts_at_least` | `hearts` (0–10) | Player friendship with this villager is at least N hearts |
| `npc_reputation_hearts_below` | `hearts` | Player friendship is below N hearts |
| `npc_mood_at_least` | `percent` (0–100) | Villager’s lowest need (hunger, energy, fun) is at least N% |
| `npc_mood_below` | `percent` | Villager’s lowest need is below N% |
| `npc_hunger_at_least` | `percent` | Villager hunger is at least N% |
| `npc_hunger_below` | `percent` | Villager hunger is below N% |
| `town_has_building` | `constructionId` | Alias for `town_has_complete_plot` |
| `player_recently_finished_quest_board_quest` | `withinDays` (default 3), optional `giverRoleId`, optional `configEntryId` | Player finished a town quest board quest recently |
| `player_recently_failed_quest_board_quest` | `withinDays` (default 3) | Player failed or abandoned a town quest board quest recently |

## Quests

Add quest JSON under `Server/Aetherhaven/Quests/`. Same quest `id` overrides. You can reference your items and constructions. Use `requiresSubplugin` only if you are tying into Aetherhaven’s optional feature packs.

For an inn visitor shop arc, grant the shop with `grantPlotTokenConstructionId` or `grantPlotBlueprintConstructionId` matching the villager’s `workConstructionId`. The visitor is promoted when the shop plot completes (with that quest active or completed), not when the quest completes by itself.

### Objective kinds (shop and housing)

Copy **objective `kind` values** from bundled Aetherhaven quests (`subplugin-assets/Quests/Server/Aetherhaven/Quests/`). Do **not** use `"kind": "journal"` for build, assign, or dialogue turn-in steps on shop or housing arcs.

Ordered objectives drive progression: events (plot placed, resident assigned, and so on) advance only the **current** non-journal step. Dialogue `complete_quest` completes the current step only when its kind is `dialogue_turn_in`. Using `journal` for the last step (or for steps before `assign_house_resident`) leaves the quest stuck on an earlier kind even when the player did the work and the turn-in line appears in dialogue.

| Arc | Typical objective sequence |
| --- | --- |
| **Shop (plot token)** | `plot_token_received` → `construction_built` (`constructionId` = shop id) → `dialogue_turn_in` |
| **Shop (blueprint)** | `plot_blueprint_received` → `plot_blueprint_learned` (`constructionId` = shop id) → `construction_built` → `dialogue_turn_in` |
| **Housing** | `plot_token_received` → `construction_built` (`constructionId`: `plot_house`) → `assign_house_resident` (`npcRoleId` = your role) → `dialogue_turn_in` |

Set `assignNpcRoleId` on housing quests to the villager role id. House dialogue turn-in should require `town_quest_active` plus `town_npc_home_resident_house` on the NPC the player is talking to (same pattern as `aetherhaven_blacksmith` / bundled `q_house_*` quests).

Minimal housing quest (match fields to your villager):

```json
{
  "id": "q_house_example",
  "category": "housing",
  "grantPlotTokenConstructionId": "plot_house",
  "assignNpcRoleId": "YourMod_Fisherman",
  "objectives": [
    { "id": "token", "kind": "plot_token_received", "text": "..." },
    {
      "id": "build",
      "kind": "construction_built",
      "constructionId": "plot_house",
      "text": "..."
    },
    {
      "id": "assign",
      "kind": "assign_house_resident",
      "npcRoleId": "YourMod_Fisherman",
      "text": "..."
    },
    { "id": "talk", "kind": "dialogue_turn_in", "text": "..." }
  ]
}
```

## Quest board extensions

Do not copy the full `quest_board.json`. Add a small file under `Server/Aetherhaven/QuestBoardExtensions/`:

```json
{
  "schemaVersion": 1,
  "villagers": {
    "Aetherhaven_Merchant": {
      "fetchEntries": [
        {
          "id": "example_fish_haul",
          "rank": "E",
          "minRank": "E",
          "maxRank": "C",
          "weight": 8,
          "daysLimit": 3,
          "titleLangKey": "example_mod.questBoard.fish_haul.title",
          "descriptionLangKey": "example_mod.questBoard.fish_haul.description",
          "itemSets": [
            {
              "weight": 1,
              "items": [{ "itemId": "Fish_Salmon_Item", "count": 5 }]
            }
          ],
          "rewards": [
            {
              "kind": "item",
              "itemId": "Aetherhaven_Gold_Coin",
              "count": 25,
              "grantTo": "player"
            },
            {
              "kind": "reputation",
              "amount": 5,
              "npcRoleId": "Aetherhaven_Merchant",
              "grantTo": "player"
            }
          ]
        }
      ]
    }
  }
}
```

Gold on the board is an **item** reward (`Aetherhaven_Gold_Coin`), not a `"type": "currency"` shorthand. Entries merge by `id` per villager role. Later packs replace the same id. Full `Server/Aetherhaven/quest_board.json` files from packs are also deep-merged the same way.

## Shop prices

`Server/Aetherhaven/ShopPrices/*.json`:

```json
{
  "catalogRevision": 1,
  "prices": {
    "Fish_Salmon_Item": 8,
    "CozyFishing_Wooden_Rod": { "gold": 40, "batchSize": 1 }
  }
}
```

Merge order: Aetherhaven bundled catalog → pack files (later pack wins on the same item id) → server data folder `shop_prices.json` overrides.

## Shop loot tables

`Server/Aetherhaven/ShopLoot/<tableId>.json` (file name is the table id):

```json
{
  "entries": [
    { "itemId": "Fish_Salmon_Item", "weight": 10, "stockMin": 2, "stockMax": 6 }
  ]
}
```

By default entries are appended to the existing table. Set `"replace": true` to replace the table for that pack layer. A file in the server’s plugin data `shop_loot/<tableId>.json` fully replaces the merged table (admin override). New installs no longer seed full default copies into that folder so pack merges can extend bundled tables; only add a data file when you want a full override.

## Jewelry in other mods’ loot

There is **no** pack/asset API for unidentified or rolled jewelry. Putting jewelry item ids in a normal drop list only gives plain stacks with no appraisal metadata.

When Aetherhaven is present, soft-depend and call the Java helpers from your loot code, for example:

- `UnidentifiedJewelry.rollEnchantedStack(random)` — random gem jewelry with rolled traits, still unappraised
- `JewelryChestLoot.rollForChest(random, ah.getConfig().get())` — chest-style roll including rare artifacts
- `JewelryMetadata.ensureRolled(stack)` — attach rolled meta to a specific jewelry item id

If Aetherhaven is missing, skip those rolls or fall back to non-jewelry loot.

## Languages

Add English keys under `Server/Languages/en-US/*.lang`. Runtime keys are `filename.short.key`. Keep player facing text simple. Do not use dashes in player facing text. Do not mention implementation details.

Lang files with the same filename as Aetherhaven’s (for example `aetherhaven_ui_journal_items_tail.lang` or `aetherhaven_buildings.lang`) merge by key. Ship only the keys you add; do not copy Aetherhaven’s full file.

**Villager display names in Aetherhaven UI** must live in `aetherhaven_ui_journal_items_tail.lang` as `npcRoles.<NpcRoleId>.name` (see Villagers and NPC roles above). Without that line, the journal and quest board show the raw message id.

**Dialogue trees:** speaker and choice strings passed to Aetherhaven dialogue must use a message id that starts with `aetherhaven_` (for example file `aetherhaven_dialogue_your_villager.lang`). Keys that do not match are treated as raw text and show as the id.

## Worked example (fictional ExampleFish mod)

1. Manifest depends on `Hexvane:Aetherhaven`, `IncludesAssetPack: true`.
2. `Server/Aetherhaven/Buildings/plot_fishing_shop.json` and prefab for the fishing shop.
3. `Server/Aetherhaven/ScheduleLocations/fishing_dock.json` maps symbol `fishing_dock` to that building.
4. `Server/Aetherhaven/VillagerSchedulePatches/merchant_fishing.json` adds a weekly visit for the Merchant.
5. `Server/Aetherhaven/ShopPrices/example_fish.json` prices fish and rods.
6. `Server/Aetherhaven/ShopLoot/merchant_food.json` appends fish to the merchant food table.
7. `Server/Aetherhaven/DialoguePatches/merchant_fish.json` adds a hub choice on `aetherhaven_merchant` / `main_hub`.
8. On plugin enable, register `example_open_journal` and `example_mod_ready`.
9. `Server/Languages/en-US/example_mod.lang` holds dialogue, quest board, and schedule location strings.
10. Optional: `QuestBoardExtensions/merchant_fish.json` adds a fetch entry for salmon.



## Reload and debugging

Aetherhaven reloads catalogs when asset packs register and when configs reload. Check the server log for lines about merged shop prices, shop loot append/replace, quest board extensions, applied dialogue patches, loaded schedule locations, and applied villager schedule patches.

## Important engine notes

- Dialogue action handlers run outside that restriction for normal UI flows, but still prefer `setAfterClose` when opening another page.
- Prefer soft dependency: if Aetherhaven is missing, skip registration and skip shipping broken references.



## Manual check

1. Install Aetherhaven and your mod.
2. Confirm log lines for your price keys, loot merge, and dialogue patch.
3. Talk to the patched villager and confirm the new hub choice.
4. Confirm the choice opens your UI when the custom action is registered.
5. Confirm shop spot tooltips show your item prices.
6. For a field rescue: talk to your rescue NPC inside a portal instance, complete dialogue, and confirm the town quest completes on the overworld and the NPC vanishes with particle/sound (no custom Java action required).

