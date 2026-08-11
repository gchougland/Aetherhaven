/**
 * Checks that the viewer can draw every block state the game defines, not just the ones
 * that happen to appear in today's prefabs. For each `*Block_State_Definitions_State`
 * name it runs the viewer's real resolver and confirms the result matches what the source
 * asset says the state looks like, with the model and textures present on disk.
 *
 * Usage: node scripts/debug-verify-states.mjs [--all]
 */
import fs from "node:fs";
import path from "node:path";

const ASSET_BASE = "web/hytale-assets";
const COMMON = path.join(ASSET_BASE, "Common");
const SUBPLUGINS = "../subplugin-assets";
const SOURCES = [
  "C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleSourceCode/hytale-shared-source/HytaleAssets/Server/Item",
  "../src/main/resources/Server/Item",
  ...(fs.existsSync(SUBPLUGINS)
    ? fs
        .readdirSync(SUBPLUGINS, { withFileTypes: true })
        .filter((e) => e.isDirectory())
        .map((e) => path.join(SUBPLUGINS, e.name, "Server", "Item"))
    : []),
];
const verbose = process.argv.includes("--all");

globalThis.fetch = async (url) => {
  const file = String(url).replace(/^\/+/, "").replace(/\?.*$/, "");
  if (!fs.existsSync(file)) {
    return { ok: false, status: 404 };
  }
  return { ok: true, status: 200, json: async () => JSON.parse(fs.readFileSync(file, "utf8")) };
};

const builderSource = fs.readFileSync("web/prefab-viewer/PrefabMeshBuilder.js", "utf8");
const specifier = builderSource.match(/from "(\.\/BlockCatalog\.js[^"]*)"/)[1];
const catalog = await import(specifier.replace("./", "../web/prefab-viewer/"));
await catalog.loadCatalogs(ASSET_BASE);

const walk = (dir, out = []) => {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      walk(full, out);
    } else if (ent.name.endsWith(".json")) {
      out.push(full);
    }
  }
  return out;
};

const norm = (p) => String(p || "").replace(/\\/g, "/").replace(/^\/+/, "").replace(/^Common\//i, "");
const onDisk = (p) => Boolean(p) && fs.existsSync(path.join(COMMON, norm(p)));

/** Every state path in a block, e.g. ["Corner_Left"] or ["Tier2", "Processing"] for nested. */
function statePaths(definitions, prefix = []) {
  const out = [];
  for (const [name, def] of Object.entries(definitions || {})) {
    const here = [...prefix, name];
    out.push({ path: here, def });
    if (def?.State?.Definitions) {
      out.push(...statePaths(def.State.Definitions, here));
    }
  }
  return out;
}

const results = { ok: 0, unresolved: [], wrongModel: [], missingAsset: [], blank: [] };
let checked = 0;

for (const root of SOURCES) {
  if (!fs.existsSync(root)) {
    continue;
  }
  for (const file of walk(root)) {
    let raw;
    try {
      raw = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch {
      continue;
    }
    const id = path.basename(file, ".json");
    /** @type {Array<{ name: string, def: any }>} */
    const cases = [];
    for (const state of statePaths(raw.BlockType?.State?.Definitions)) {
      cases.push({
        name: `*${id}${state.path.map((s) => `_State_Definitions_${s}`).join("")}`,
        def: state.def,
      });
    }
    // Item variants: *Id_State_Name, whose look lives in the variant's own BlockType.
    for (const [variantName, variant] of Object.entries(raw.State || {})) {
      if (variant && typeof variant === "object") {
        cases.push({ name: `*${id}_State_${variantName}`, def: variant.BlockType || {} });
      }
    }
    for (const state of cases) {
      checked += 1;
      const name = state.name;
      const def = catalog.getBlockDef(name);
      if (!def) {
        results.unresolved.push(name);
        continue;
      }
      // What the source says this state should look like.
      const wantModel = state.def.CustomModel ? norm(state.def.CustomModel) : null;
      if (wantModel && norm(def.customModel) !== wantModel) {
        results.wrongModel.push(`${name}\n    want ${wantModel}\n    got  ${def.customModel || "(none)"}`);
        continue;
      }
      // An item variant with no block form (fish rarities) still draws as a held item.
      const assets = [
        def.customModel,
        def.customModelTexture,
        def.itemModel,
        def.itemTexture,
        ...Object.values(def.textures || {}),
      ].filter(Boolean);
      if (!assets.length) {
        results.blank.push(name);
        continue;
      }
      const missing = assets.filter((a) => !onDisk(a));
      if (missing.length) {
        results.missingAsset.push(`${name}  missing: ${missing.join(", ")}`);
        continue;
      }
      results.ok += 1;
    }
  }
}

console.log(`checked ${checked} block states from ${SOURCES.length} asset trees\n`);
console.log(`  drawable            ${results.ok}`);
for (const [label, list] of [
  ["did not resolve", results.unresolved],
  ["wrong model", results.wrongModel],
  ["asset not on disk", results.missingAsset],
  ["nothing to draw", results.blank],
]) {
  console.log(`  ${label.padEnd(20)}${list.length}`);
  for (const item of verbose ? list : list.slice(0, 12)) {
    console.log(`      ${item}`);
  }
  if (!verbose && list.length > 12) {
    console.log(`      ... and ${list.length - 12} more (--all to list)`);
  }
}
