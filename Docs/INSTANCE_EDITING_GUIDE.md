# Instance editing guide — building your Aetherhaven arrival island

This explains how **instances** work in Hytale and how to build (and save) the arrival island structure so it ships with the mod.

---

## "Cannot edit instances when using launcher assets"

If you run `/instances edit load Aetherhaven` and get **"Cannot edit instances when using launcher assets"**, the game is blocking instance edit because the **base asset pack** (the first one loaded, usually the main game install) is marked **immutable**. That happens when:

- The game is run with assets from the **launcher install** (a folder that contains `CommonAssetsIndex.hashes`), or
- The base assets are loaded from a **.zip or .jar** (archives are always read-only).

The check is on the **base** pack, not on your mod. So even if your mod is in a writable folder, instance edit is still blocked when the main game assets are launcher/read-only.

**Workaround:** use **spawn** instead of edit, build your island in the spawned world, then **copy the chunks** from that world folder into your mod. See [Workaround: spawn, build, then copy chunks](#workaround-spawn-build-then-copy-chunks) below.

---

## How instances work

1. **Instance asset** = folder `Server/Instances/<Name>/` in your mod (or base game). It must contain:
   - **instance.bson** — world config (spawn, world gen, gameplay config, etc.).
   - Optionally **chunks/** — saved chunk data (blocks and entities). If missing, the world is empty (e.g. void) until chunks are created.

2. **When a player enters the portal** (`TeleportInstance` → `InstanceName: "Aetherhaven"`):
   - The game looks up the instance asset `Server/Instances/Aetherhaven/` from your mod’s asset pack.
   - It **copies that whole folder** (instance.bson, resources/, chunks/, etc.) into a temporary world directory.
   - The player is teleported into that world. So whatever is in the instance asset folder (including pre-built chunks) is what they see.

3. **Chunk storage**  
   With `ChunkStorage: { "Type": "Hytale" }` (as in your instance.bson), the game uses the default provider, which stores chunks under a **chunks** folder relative to the world’s save path.

So: **the “structure” in Forgotten Temple comes from chunk files that live in the instance asset folder.** Those chunks were created by editing that instance and saving; the same workflow applies to Aetherhaven.

---

## How to generate and save chunk files (your arrival island)

You don’t generate chunk files by hand. You **build in-game in “instance edit” mode**, and the game **saves chunks automatically** into the instance folder. Then you make sure that folder (including `chunks/`) is in your mod.

### 1. Run the server with the mod in a writable place

Instance editing only works when the asset pack is **writable** (not read-only launcher assets). So:

- Run the server from the **Aetherhaven project** with `./gradlew runServer` (or your usual dev setup that uses `build/resources/main` or an unpacked mod folder).

Do **not** rely on a launcher install where the mod is inside a read-only pack; in that case the game will say assets are immutable and block editing.

### 2. Load the instance for editing

In-game, use one of these:

- **Command:**  
  `/instances edit load Aetherhaven`  
  This loads the instance asset from your mod and opens it as a world, and (if you’re a player) teleports you into it.

- **UI:**  
  If the game has an instance list / asset browser (e.g. in the built-in Instance List page), you can select **Aetherhaven** and choose the “load for edit” action there. That does the same thing as the command.

Under the hood, the game calls `loadInstanceAssetForEdit("Aetherhaven")`, which:

- Finds your mod’s `Server/Instances/Aetherhaven/` (asset path).
- Loads `instance.bson` and creates a world **whose save path is that same folder**.
- So when chunks are saved, they go into `Server/Instances/Aetherhaven/chunks/` (and any other world data under that path).

### 3. Build your structure

- You’re in the Aetherhaven instance world in **Creative** (instance edit forces that).
- Build the arrival island: platform, **Aetherhaven_Portal_Exit** block, decoration, etc.
- Move the spawn in `instance.bson` if needed so players spawn on the platform (you can edit `instance.bson` and set `SpawnProvider.SpawnPoint` to the desired X,Y,Z, then reload).

Chunks are saved **automatically** as the world runs (and when the server shuts down). There is no separate “save instance” command; saving the world saves the chunks into the instance folder.

### 4. Get the chunk files into your mod source

When you run with `./gradlew runServer`, the mod is usually loaded from **build output** (e.g. `build/resources/main`). So:

- Chunks are written to:  
  `<build_output>/Server/Instances/Aetherhaven/chunks/`  
  (and possibly `resources/` under the same instance folder).

Your Aetherhaven build is already set up with a **syncAssets** task that runs when the server stops:

- It copies from `build/resources/main` **into** `src/main/resources`.
- So after you stop the server, **syncAssets** will copy the new `chunks/` (and any updated `resources/`) from build into `src/main/resources/Server/Instances/Aetherhaven/`.

Then:

- Rebuild the mod (`./gradlew build`). The JAR will include the updated instance folder (instance.bson + chunks + resources).
- Next time a player enters the Aetherhaven portal, they’ll see the island you built.

If you don’t use syncAssets (e.g. you run the game in another way), then **manually copy** the `chunks` folder (and any changed files) from wherever the game wrote them (the instance asset path on disk) into your mod’s `src/main/resources/Server/Instances/Aetherhaven/`.

---

## Summary workflow

| Step | What you do |
|------|---------------------|
| 1 | Run server from Aetherhaven project (`./gradlew runServer`) so the instance asset path is writable. |
| 2 | In-game: `/instances edit load Aetherhaven` (or use the instance list UI to load Aetherhaven for edit). |
| 3 | Build the arrival island in that world (platform, Aetherhaven_Portal_Exit, etc.). Chunks save automatically. |
| 4 | Stop the server so syncAssets runs and copies `build/.../Server/Instances/Aetherhaven/` (including `chunks/`) into `src/main/resources/Server/Instances/Aetherhaven/`. |
| 5 | Run `./gradlew build` so the JAR includes the updated instance. |

---

## Workaround: spawn, build, then copy chunks

When **instance edit** is blocked (e.g. "launcher assets"), build the arrival island by **spawning** the instance and copying the result into your mod.

0. **Temporarily allow building:** Aetherhaven's gameplay config disables block placement and breaking. Edit `src/main/resources/Server/GameplayConfigs/Aetherhaven.json` and set `World.AllowBlockPlacement`, `World.AllowBlockBreaking`, and `World.AllowBlockGathering` to `true`. Rebuild (`./gradlew build`) and start the server. **Remember to set them back to `false`** after you finish building and copying chunks, so players cannot break or place blocks in the dimension.
1. **Spawn:** In-game run `/instances spawn Aetherhaven`. You are teleported into a copy of the instance (Creative). The world is stored in the server's `worlds/` folder (e.g. `worlds/instance-Aetherhaven-<uuid>/`), not in your mod.
2. **Build:** Place the arrival platform, **Aetherhaven_Portal_Exit**, and any decoration. Chunks save automatically.
3. **Stop the server** so the world is fully saved.
4. **Copy chunks:** Find the world folder (name like `instance-Aetherhaven-<uuid>` under `worlds/`). Copy the **entire `chunks` folder** from that world directory to `Aetherhaven/src/main/resources/Server/Instances/Aetherhaven/chunks/`. Copy `resources/` too if present.
5. **Restore config:** Set `World.AllowBlockPlacement`, `World.AllowBlockBreaking`, and `World.AllowBlockGathering` back to `false` in `Server/GameplayConfigs/Aetherhaven.json` so the shipped dimension stays protected.
6. **Rebuild:** Run `./gradlew build`. The JAR will include the new chunks; the next portal entry will show your island.

**Optional:** Edit `instance.bson` and set `SpawnProvider.SpawnPoint` (X, Y, Z, Pitch, Yaw, Roll) to the platform position so players spawn on the platform.

No separate “generate chunks” or “save instance” command is needed; building in the instance and stopping the server (with syncAssets) is enough to get chunk files into your mod.
