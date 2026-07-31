---
name: Plot creator staff
description: Turn a build into a custom town building definition
author: Hexvane
---

# Plot creator staff

The plot creator staff registers **custom buildings** you built in the world: footprint, specialty blocks, prefab export, costs, and (optionally) a live plot in your town. Most players should use **Decoration** or **Variant**, not the full workplace types below.

Open your **town journal → Guide → Mechanics → Plot creator staff** (this page) while learning the flow. In play: equip the staff, press **F** to start or open the current step panel, **primary-click** blocks in the world, **Q** / **E** previous / next step, **middle mouse** to open the step menu and jump to a step you already reached, **R** to cancel.

## Recommended paths

| Goal | Building type | Notes |
|------|----------------|-------|
| Cosmetic build, no jobs | **Decoration** | Optional town records shelf; no production or villager logic. |
| Alternate look for an existing building | **Variant** | Pick which **main** building it counts as (house, barn, inn, etc.). Substeps follow that main type. |
| New workplace / production plot (modding) | **Work** | For adding new workplace types; needs management block, production storage, and a work surface POI. |

## Building types (picker)

**Decoration** — Parks, props, and builds that should **not** act as homes, shops, or workplaces. Minimal required spots.

**Variant** — A prefab that **counts as** another building id already in the mod (for example a custom house that counts as `plot_house`). You choose the main type from the dropdown; important spots match that main building.

**Home** — Residential plot: town records shelf + sleep POI.

**Work** — **Developer / content author use.** Defines a new **workplace**-style plot: town records shelf, production storage, and a work-surface POI. Use when you are adding a new production or job building type, not for normal cosmetic variants.

**Amenity** — Fun or leisure (park, altar-style amenity): shelf + fun/sit POI.

**Shop** — Stall or shop counter: shelf + shop-tagged work POI.

**Inn** — Full inn layout: shelf, work surface, beds, eat spot, innkeeper and visitor spawns (and optional guild master spawn slot).

**Town hall** — Civic hub: shelf, treasury block, planning desk POI.

**Guild hall** — Adventurer guild: shelf, work surface, adventurer spawn points.

### Overlap and confusion

- **Variant** vs **Home / Work / …** — Variant is for “looks different, behaves like X.” Pick **Variant** + main type, not **Home**, if you are reskinning an existing gameplay building.
- **Work** vs **Shop** — **Shop** is for merchant stalls. **Work** is for farms, mills, forges, and other production workplaces.
- **Amenity** vs **Decoration** — **Decoration** has almost no gameplay hooks. **Amenity** sets fun/amenity tags and POIs for villager schedules.
- **Inn**, **Town hall**, and **Guild hall** are full templates; use **Variant** only if you are matching one of those templates on purpose.

## Flow (short)

1. Mark two opposite corners and an **outside** plot-sign corner.
2. Choose **building type** (and **variant of** if applicable).
3. Place **important spots** (blocks are given one at a time per substep).
4. Open **building settings** (F): name, id, tags, treasury gold cost, self build days, empty space prefab option, and other options.
5. **Export prefab** with F on the save shape step (uses settings from step 4).
6. Set **build materials** (virtual chest; items return to you when you continue).
7. Review and save.

## Permissions

By default, config may allow everyone to use the plot creator; servers can require the `aetherhaven.plot.creator` permission instead.
