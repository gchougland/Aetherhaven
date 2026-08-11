/**
 * Runs the viewer's real getBlockDef against every block name in prefabs, so a block that
 * silently fails to draw can be traced without opening a browser. Accepts files or
 * directories to walk.
 *
 * Usage: node scripts/debug-resolve-blocks.mjs <prefab|dir> [more...]
 */
import fs from "node:fs";
import path from "node:path";

const ASSET_BASE = "web/hytale-assets";
const COMMON = path.join(ASSET_BASE, "Common");

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

const norm = (p) => String(p || "").replace(/\\/g, "/").replace(/^\/+/, "").replace(/^Common\//i, "");
const onDisk = (p) => Boolean(p) && fs.existsSync(path.join(COMMON, norm(p)));

/** @param {string} target */
function collectPrefabs(target, out = []) {
  const stat = fs.existsSync(target) ? fs.statSync(target) : null;
  if (!stat) {
    return out;
  }
  if (stat.isFile()) {
    if (target.endsWith(".json")) {
      out.push(target);
    }
    return out;
  }
  for (const ent of fs.readdirSync(target, { withFileTypes: true })) {
    collectPrefabs(path.join(target, ent.name), out);
  }
  return out;
}

const targets = process.argv.slice(2);
const files = targets.flatMap((t) => collectPrefabs(t));

/** @type {Map<string, { count: number, files: Set<string>, verdict: string, detail: string }>} */
const seen = new Map();
let prefabCount = 0;

for (const file of files) {
  let doc;
  try {
    doc = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    continue;
  }
  if (!Array.isArray(doc.blocks)) {
    continue;
  }
  prefabCount += 1;
  for (const b of doc.blocks) {
    const name = String(b.name || "");
    if (!name || name === "Empty") {
      continue;
    }
    const hit = seen.get(name);
    if (hit) {
      hit.count += 1;
      hit.files.add(path.basename(file));
      continue;
    }
    const def = catalog.getBlockDef(name);
    let verdict = "drawable";
    let detail = "";
    if (!def) {
      verdict = "no catalog entry";
    } else {
      const assets = [def.customModel, def.customModelTexture, ...Object.values(def.textures || {})].filter(Boolean);
      const missing = assets.filter((a) => !onDisk(a));
      if (!assets.length) {
        verdict = "nothing to draw";
      } else if (missing.length) {
        verdict = "asset not on disk";
        detail = missing.join(", ");
      } else {
        detail = def.customModel || Object.values(def.textures || {})[0] || "";
      }
    }
    seen.set(name, { count: 1, files: new Set([path.basename(file)]), verdict, detail });
  }
}

const groups = new Map();
for (const [name, info] of seen) {
  if (!groups.has(info.verdict)) {
    groups.set(info.verdict, []);
  }
  groups.get(info.verdict).push([name, info]);
}

console.log(`${prefabCount} prefabs, ${seen.size} distinct block names\n`);
for (const [verdict, list] of [...groups].sort((a, b) => (a[0] === "drawable" ? 1 : -1))) {
  const placed = list.reduce((sum, [, i]) => sum + i.count, 0);
  console.log(`${verdict}: ${list.length} names, ${placed} placed blocks`);
  if (verdict === "drawable") {
    continue;
  }
  for (const [name, info] of list.sort((a, b) => b[1].count - a[1].count)) {
    console.log(`   x${String(info.count).padStart(5)}  ${name}${info.detail ? `  (${info.detail})` : ""}`);
    console.log(`          in ${[...info.files].slice(0, 3).join(", ")}`);
  }
}
