---
name: Block palettes for modders
description: Add Block Palettes from another mod
author: Hexvane
---

# Block palettes for modders

Other mods can unlock and remapping looks for their own blocks. Aetherhaven merges every JSON under `Server/Aetherhaven/BlockPalettes/` from all asset packs.

## Easy path: remap groups

Ship a JSON file in your pack, for example `Server/Aetherhaven/BlockPalettes/mymod_walls.json`:

```json
{
  "remapGroups": [
    {
      "id": "mymod_walls",
      "category": "walls",
      "variants": [
        {
          "id": "walls_mymod_oak",
          "displayName": "Oak",
          "iconBlockId": "MyMod_Oak_Wall",
          "blockPrefix": "MyMod_Oak_Wall"
        },
        {
          "id": "walls_mymod_birch",
          "displayName": "Birch",
          "iconBlockId": "MyMod_Birch_Wall",
          "blockPrefix": "MyMod_Birch_Wall"
        }
      ]
    }
  ]
}
```

Each variant becomes an unlockable Block Palette item. When a player picks Birch, any block whose id starts with `MyMod_Oak_Wall` is rewritten to the same trailing text under `MyMod_Birch_Wall`.

Examples:

- `MyMod_Oak_Wall` → `MyMod_Birch_Wall`
- `MyMod_Oak_Wall_Stairs` → `MyMod_Birch_Wall_Stairs`
- `MyMod_Oak_Wall_Half` → `MyMod_Birch_Wall_Half`

Use the longest shared prefix you can so related pieces stay grouped. Missing target blocks are left alone.

Categories can be existing ones (`walls`, `trunks`, `planks`, `cobble`, `bricks`, `cloth`, `roofs`) or a new id of your own.

## Adding looks that follow vanilla naming

If your blocks already match Aetherhaven naming (for example more `Wood_*_Planks` species), you can list them under `categories` with a `familyKey` and skip remap groups:

```json
{
  "categories": [
    {
      "id": "planks",
      "palettes": [
        {
          "id": "planks_mymod_cedar",
          "displayName": "Cedar",
          "familyKey": "MyModCedar",
          "iconBlockId": "Wood_MyModCedar_Planks"
        }
      ]
    }
  ]
}
```

## Java API

After Aetherhaven has loaded, register from your plugin setup:

```java
AetherhavenPlugin ah = AetherhavenPlugin.get();
if (ah == null) {
  return;
}
BlockPaletteCatalog catalog = ah.getBlockPaletteCatalog();
catalog.registerRemapGroup(
  BlockPaletteRemapGroup.builder("mymod_walls", "walls")
    .variant("walls_mymod_oak", "Oak", "MyMod_Oak_Wall", "MyMod_Oak_Wall")
    .variant("walls_mymod_birch", "Birch", "MyMod_Birch_Wall", "MyMod_Birch_Wall")
    .build()
);
```

You can also call `catalog.register(new BlockPaletteDefinition(...))` for a single palette.

## Giving and unlocking

Unlocked lists are per town. Players use the Block Palette item, or creative hosts can run `/ah palette give <paletteId>`.
