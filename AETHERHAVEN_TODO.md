# Aetherhaven TODO

Updated October 7, 2026 from `Z:/aetherhaven-notes-4.txt` on EtherealArchive. The original notes are unchanged. Numbers such as **N08** refer to the nonblank notes in their original order. Duplicate building-yard requests are combined; all 83 notes are represented.

Checked items are implemented. Unchecked items still need work, reproduction, validation, or a decision. Passing the automated suite does not by itself resolve a reported gameplay bug. This checklist covers the network notes, not every outstanding task for the entire 4.0 release.

## Completed

- [x] **N04 · Add a bills icon to the town treasury.** The supplied bill icon is used on both the Treasury and Town Journal Bills tabs. Custom UI validation and packaged asset checks pass.
- [x] **N22 · Let villager unlock requests use carried gold as well as the town treasury.** Workplace unlocks, extra workplace slots, warehouse and alchemy upgrades, and Inn outreach use the shared payment helper. Affordability includes permitted treasury funds and carried coins. Duplicate and insufficient-funds cases are covered by regression tests.
- [x] **N32 · Let bills use inventory gold or treasury gold.** Both Bills screens show available combined funds and use the same payment service. Treasury permissions are respected, while members can pay with their own coins. Repeat payments cannot charge twice.
- [x] **N60 · Let Monster Hunter payments use carried gold as well as treasury gold.** Run entry, creature unlocks and training use the shared payment helper. Creature drops and carried gold are removed together, with no partial charge on failure. Regression tests pass.
- [x] **N08 · Hide locked workplace types in the workplace selection GUI.** Implemented and visually checked. Server-side unlock checks remain in place.
- [x] **N10 · Show tomorrow's tax income on the Bills page.** Uses the same calculation as tax collection; visually checked.
- [x] **N15 · Stop the Remove workplace button text being cut off.** Layout corrected and visually checked.
- [x] **N23 · Show which main workbench each workplace requires.** Bench names appear in workplace selection, management and task screens; visually checked.
- [x] **N24 · Use a Builder's Bench for the builder workplace.** Requires the native Builder's Workbench, including interaction routing, placement checks and help text. Existing yards using an ordinary Workbench need that bench replaced.
- [x] **N28 · Remove completed projects from building yards.** Completed orders retire from all yards, and known completed saved projects are reconciled. Unidentifiable old orphaned decoration orders still require manual review.

## Partly addressed or awaiting confirmation

- [ ] **N02 · Investigate unexplained blocked or invalid plot placement.** Placement arrows now follow head direction. The placement audit identified full sign-hitbox clearance and loaded cells as possible rejection points, but did not reproduce the reported rejection. Capture the failing location and expose the actual reason before declaring this fixed.
- [ ] **N29 · Fix the floating chair in the Artisan Workshop.** Earlier work addressed entity sizing, but that does not prove the chair's vertical placement is correct. Inspect the current prefab and placed building.
- [ ] **N34 · Fix false overlap rejection when placing a tunnel below another tunnel.** Reported again during play on October 7: the planner rejected a lower tunnel because of a tunnel above it, even though their excavated spaces should not overlap. The reported layout uses three-block-high tunnels and stairs descending about seven blocks; confirm the actual saved elevations and dimensions. Check vertical excavation and clearance bounds, then verify stacked tunnels in the affected layout. Earlier floor-navigation and stair-landing fixes do not establish that this case is resolved.
- [ ] **N42 · Keep quest-board quest dialogue above ordinary dialogue.** Shared quest ordering was corrected and the Innkeeper was checked. Verify the specific quest-board turn-in/offer path from this note before closing it.
- [ ] **N52 · Resolve intermittent mineshaft stalls and show current tasks.** Several stair, fitting, room-entry and worker-handoff bugs were fixed; the copied save's room completed normally. The planner has progress/status information, but a clear current-task view and sustained ordinary-play acceptance remain open.
- [ ] **N77 · Continue the lumbermill rework using the owner's additional notes.** The initial implementation is complete: free planting, wood/sap/sticks/sapling harvest outputs, and removal of the unreleased supplied-sapling system. The overall lumbermill rework remains open for the owner's review and further improvements; await those notes before defining the next changes.
- [ ] **N83 · Let mineshafts work through water and other blocks.** The narrow rock/ore/dirt whitelist was removed. Water, protected cells and multi-block furnishings still have restrictions, so the broader note is not complete.

## Remaining work

### Town services and recruitment

- [ ] **N09 · Add a teleport button beside each resident listed in their house management screen.** This is the resident who lives in the house. The existing assigned-worker teleport button does not fulfill this request.
- [ ] **N07 · Make Prowl require corn to move in.**
- [ ] **N11 · Add a move-in quest for the Bard.**
- [ ] **N16 · Ensure every townsfolk character starts with some usable profession skills.** Missing skill values default to level 1, but explicit character/personality values still need auditing so nobody starts unable to work.
- [ ] **N20 · Make the Crafter arrive after the Warehouse Manager.** Both currently require Town Level 3; the intended sequence is not enforced.
- [ ] **N21 · Preserve residents' house assignments through death and revival.** Test both named villagers and townsfolk, including save/reload.
- [ ] **N33 · Have Prowl hold corn while eating.**
- [ ] **N38 · Improve the revival GUI's appearance.** Match the newer panels, spacing and controls.
- [ ] **N44 · Correct the Leatherworker quest's dialogue icon.**
- [ ] **N46 · Keep villagers still briefly after teleporting and while being spoken to.** Ensure their normal schedule resumes afterward.
- [x] **N50 · Give only profession-equipment recipes as reputation rewards, rather than the finished equipment.** Converted the affected rewards and dialogue, removed duplicate later recipe rewards, and added recipe unlocks for previously claimed kits. Clean automated tests pass; in-game testing is left to the player.
- [ ] **N57 · Give the Druid an old man's voice.** His definition still uses `MellowFemale`.
- [ ] **N59 · Stop the Monster Hunter holding a guitar/lute.** His building still assigns `work_bard` equipment to its work spot. Jack's separate lute fix does not fix this.
- [ ] **N64 · Make recruited adventurers start the appropriate house quest.** The shared tourist invitation-item requirement was fixed separately; house-quest initiation still needs checking.
- [ ] **N79 · Disable Catalyst and Shard training buttons when the player lacks the item.** Townsfolk buttons currently check permission and level limits, but not carried item availability.

### Buildings and construction

- [X ] **N12 · Add the required saved air/Editor Empty coverage to the mine prefab.** The editor's general air-preservation behavior is implemented; this specific prefab still needs auditing.
- [ X] **N17 · Add the required saved air/Editor Empty coverage to the restaurant prefab.**
- [ ] **N25, N47 · Automatically add new construction projects to a building yard.** These two notes describe the same feature. Handle multiple yards without duplicate orders.
- [ ] **N26 · Make builder townsfolk hold and swing a hammer while working.**
- [ ] **N27 · Make additional builders increase construction speed.** Verify several workers can contribute effectively without duplicate placements or rewards.
- [X ] **N39 · Add the required saved air/Editor Empty coverage to the player shop and Gaia altar prefabs.**
- [ ] **N48 · Let building construction draw its required resources from the warehouse.** Verify actual material supply, not just worker access to already supplied projects.

### Workplaces and shops

- [ ] **N40 · Put shop townsfolk behind the counter.** Check their work position, facing and route into the shop.
- [ ] **N41 · Keep player-shop item displays visible while the shop is closed.**
- [x] **N45 · Add a nearby workbench access block for warehouse ingredients.** Mara teaches the Warehouse Supply Link recipe at Friendship 25. Nearby native crafting benches use general storage, crafted goods and building outputs, respecting storage permissions and courier reservations. Includes MCP-created compact artwork. Clean automated tests pass; in-game testing is left to the player.
- [ ] **N49 · Allow appraisal of equipped jewelry.** Preserve the equipped item and its slot while applying the appraisal.
- [ ] **N53 · Allow crafting to use materials inside Aetherhaven backpacks.** Ordinary carried inventory access does not imply access to a backpack item's internal contents.
- [ ] **N68 · Fix ranch workplace availability without animals.** The workplace/work spot should remain usable without requiring owned animals; animal production can still require an active, fed animal.
- [ ] **N69 · Investigate crafters waiting despite available work.** The suggestion that they are waiting for a higher-level worker is a hypothesis, not an established cause.
- [ ] **N70 · Add a Salvaging Bench workplace.**
- [ ] **N71 · Add portraits to the work-priority GUI.**
- [ ] **N72 · Investigate slow workplace crafting and material delivery.** Measure travel, reservation, collection and crafting time before changing rates.
- [ ] **N78 · Let upgrades use warehouse materials.** Audit building, workplace and backpack upgrade payment routes, including reserved stock and mixed inventory/warehouse payments.
- [ ] **N80 · Correct ingot-smelting skill requirements.** Compare recipe requirements with the intended metal progression.
- [ ] **N82 · Show stored outputs inside crafting workplace GUIs.** Include quantities and access to collection.

### Mining and production

- [ ] **N13 · Investigate the core Miner standing over a node without working.** Keep this separate from apprentice mineshaft bugs.
- [ ] **N35 · Hide locked Farm and Mine outputs in the Warehouse view.** The Warehouse still generates locked entries for non-Barn production. Keep this distinct from the earlier request to show locked slots in each production building's own GUI.
- [ ] **N37 · Change mineshaft support beams to hardwood.** The fitting definition still uses `Wood_Softwood_Beam`; update the joining pieces consistently.
- [ ] **N43 · Make every Farm crop harvest yield Life Essence.** Current harvesting recognizes Life Essence in native drops, which does not guarantee a yield every harvest.
- [ ] **N65 · Make the feeding trough craftable.** Check its recipe, bench category and unlock requirements.
- [ ] **N73 · Stop workers approaching underground mineshafts from above ground.** Check route selection against actual tunnel entrances and reachable passages.
- [ ] **N75 · Stop workers visiting the Barn for materials it does not contain.** Check source reservations, depleted stock and source changes while a worker is travelling.

### Storage and inventory presentation

- [ ] **N36 · Make the whole ledger desk interactable.** The production wardrobe's full-hitbox fix is a different block and does not close this report.
- [ ] **N56 · Show correct prop and plot-token icons in Backpack and Warehouse GUIs.** Check dynamically generated icons as well as ordinary item icons.

## Needs more explanation

These are the questions to expand on. Existing related features are noted so a new request does not accidentally duplicate them.

- [ ] **N01 · Magnet rings.** What pickup radius and tiers should rings have, and should they stack with the backpack magnet or provide an alternative to it?
- [ ] **N03 · Infinite stack size in the warehouse.** Is this a request for unlimited item quantities, or a report that stacking is currently exceeding its intended limit? General Storage currently uses native stacks and finite slots.
- [ ] **N05 · Villager unlock order.** What order or progression should replace the current one? N20 already specifies that the Crafter should follow the Warehouse Manager.
- [ ] **N06 · Inn refill not working correctly.** Is the issue empty visitor rooms, no new visitors, visitors failing to leave, or a particular refill/invitation action? What happened and what should have happened?
- [ ] **N18 · Move the warehouse earlier.** Should the Warehouse Manager unlock at Town Level 1 or 2, or at another milestone? It is currently Level 3.
- [ ] **N19 · F with the Building Wand opens quick mode selection.** Is that the desired shortcut or the bug? What should F do while holding the wand and looking at an interactable block?
- [ ] **N30 · Furniture and prop workplaces.** Should these be separate professions/workplaces or additional recipe categories for the Crafter? Which benches should they use?
- [ ] **N31 · The Crafter's purpose.** Should the Crafter gain a unique core-villager service, produce items personally, or specialize in particular apprentice recipes? The existing role unlocks crafting workplaces.
- [ ] **N51 · Founder statue armor and poses.** Which poses should be available? Should armor be supplied as real items or selected cosmetically, and should it replace the saved player outfit?
- [ ] **N54 · Memory milestones.** Which features should require memories, and at what thresholds? Should these be player-specific unlocks or town-wide unlocks?
- [ ] **N55 · Lower starting Farm output capacity.** What starting per-item capacity and upgrade increments should it use? The Barn's separate 10-per-item rule is already implemented.
- [ ] **N58 · Druid conservatory rework.** Does “beds” mean planted growing beds or sleeping beds? What should the initial layout contain, and how should the Druid's production loop change?
- [ ] **N61 · JT warehouse needs Editor Empty.** Which exact building/style or prefab is “JT warehouse”? Provide its displayed name or filename so the right prefab is updated.
- [ ] **N62 · Commerce in the Town Journal.** Should this show Commerce skill and priority, shop staffing, sales/profit, or all of these? What would making it work like the other professions change?
- [ ] **N63 · Virel Ashsong requests Motes of Light.** Are those items incorrect, unobtainable, or just too costly? What should the move-in requirement be instead?
- [ ] **N66 · Rubble option for the mine.** Do you mean rubble as a selectable core-Miner output, mineshaft debris/fittings, or something else? Which rubble types?
- [ ] **N67 · Restaurant food upgrades.** Satiety and Service upgrade branches already exist. Do you want new meal/recipe unlocks, food quality tiers, additional effects, or changes to those existing upgrades?
- [ ] **N74 · Teleport stuck townsfolk after a few seconds.** How many seconds, and should they go to the nearest reachable point, their workplace entrance, or their current task target? Should the same rule apply to guards in combat?
- [ ] **N76 · Increase affection from petting.** What gain or progression pace do you want, such as roughly how many days to reach full hearts with daily feeding and petting?
- [ ] **N81 · Smelting output below 100 by default.** What should the starting capacity be, and does this mean storage capacity per output item or the maximum amount queued at once?

## Excluded by request

- **N14 · Loot goblin and treasure maps.** Left out of the active TODO list following your instruction that this idea can be ignored.

## Status references

- [Current implementation and native test history](Docs/UPDATE_4.0_CURRENT.md), especially the notes-file batch, Builder workplace correction, mine floor planning, Bills, and lumbermill checkpoints. Later entries and user instructions supersede older historical claims.
- [Recent validation record](Docs/UPDATE_4.0_TESTING.md), including the clean full-suite build, new storage wardrobe, Backpack/Warehouse layout and Spook House import.
- Current code was checked for treasury-only bills, workplace bench requirements, Farm Life Essence handling, warehouse locked entries, training-button availability, Druid voice, Hunter equipment and restaurant upgrades. Items without a confirmed fix remain unchecked.
