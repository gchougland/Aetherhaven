---
name: For developers
description: Guides for mods that integrate with Aetherhaven
author: Hexvane
sub-topics:
  - crossmod_integration
---

# For developers

These pages explain how other mods can add content that works with Aetherhaven through asset packs and, when needed, small Java hooks.

## Start here

Read **[Crossmod integration](?topic=crossmod_integration)** for the folder map under `Server/Aetherhaven/`, including:

* Villager definitions, dialogue trees, and weekly schedules
* Buildings, plot tokens, and schedule location symbols
* Quest patches, gift patches, and quest board entries
* A worked example you can copy into your own pack

## Players looking for addons

Optional companion mods (Machinaria, Cozy Tales Fishing, and similar) are listed under **[Addons](?topic=addons)** in the wiki directory, not on this page.

## Testing changes

Run your pack on a server with Aetherhaven enabled, place test plots with the plot tools, and verify villagers resolve schedules against your new buildings. The crossmod guide lists merge rules when two packs touch the same catalog file.
