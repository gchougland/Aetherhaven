/**
 * Shows what the viewer resolves one or more block names to.
 *
 * Usage: node scripts/debug-resolve-name.mjs "*Block_State_Definitions_State" [more...]
 */
import fs from "node:fs";

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
await catalog.loadCatalogs("web/hytale-assets");

for (const name of process.argv.slice(2)) {
  const def = catalog.getBlockDef(name);
  if (!def) {
    console.log(`${name}\n   (no catalog entry)`);
    continue;
  }
  const tex = def.customModelTexture || def.itemTexture || Object.values(def.textures || {})[0] || "-";
  console.log(`${name}\n   model=${def.customModel || def.itemModel || "-"}\n   texture=${tex}`);
}
