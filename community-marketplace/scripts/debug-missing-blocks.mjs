/**
 * Report prefab blocks the viewer cannot draw, so "missing roof" style gaps can be
 * traced to catalog lookups instead of guessed at.
 *
 * Usage: node scripts/debug-missing-blocks.mjs [prefab] [--near x,y,z,radius]
 */
import fs from "node:fs";

const prefabPath = process.argv[2] || "web/debug/cozy_town_hall.prefab.json";
const nearArg = process.argv.find((a) => a.startsWith("--near="));

const catalog = JSON.parse(fs.readFileSync("web/hytale-assets/catalog/block_catalog.json", "utf8"));
const doc = JSON.parse(fs.readFileSync(prefabPath, "utf8"));
const blocks = doc.blocks || doc.Blocks || [];

/** Mirror of BlockCatalog.getBlockDef name fallbacks. */
function lookup(name) {
  if (!name) {
    return null;
  }
  if (catalog[name]) {
    return catalog[name];
  }
  const stateless = String(name).replace(/_State_Definitions_.*$/, "");
  if (catalog[stateless]) {
    return catalog[stateless];
  }
  return null;
}

const missing = new Map();
const drawable = new Map();
for (const block of blocks) {
  const name = block.name || block.Name;
  const def = lookup(name);
  const bucket = def ? drawable : missing;
  const entry = bucket.get(name) || { count: 0, sample: block, def };
  entry.count += 1;
  bucket.set(name, entry);
}

console.log(`${prefabPath}: ${blocks.length} blocks, ${missing.size} unresolved names`);
for (const [name, entry] of [...missing].sort((a, b) => b[1].count - a[1].count)) {
  const s = entry.sample;
  console.log(`  MISSING x${String(entry.count).padStart(4)}  ${name}  e.g. (${s.x},${s.y},${s.z})`);
}

// Resolved but undrawable: no textures and no model means buildPrefabMesh skips it.
const blank = [...drawable].filter(([, e]) => !e.def.customModel && !e.def.textures);
if (blank.length) {
  console.log(`\nresolved but nothing to draw (${blank.length}):`);
  for (const [name, entry] of blank.sort((a, b) => b[1].count - a[1].count)) {
    const s = entry.sample;
    console.log(`  BLANK   x${String(entry.count).padStart(4)}  ${name}  e.g. (${s.x},${s.y},${s.z})`);
  }
}

if (nearArg) {
  const [cx, cy, cz, radius] = nearArg.slice("--near=".length).split(",").map(Number);
  console.log(`\nblocks within ${radius} of (${cx}, ${cy}, ${cz}):`);
  const counts = new Map();
  for (const block of blocks) {
    const dx = block.x - cx;
    const dy = block.y - cy;
    const dz = block.z - cz;
    if (Math.hypot(dx, dy, dz) > radius) {
      continue;
    }
    const name = block.name || block.Name;
    const key = `${lookup(name) ? "ok     " : "MISSING"} ${name}`;
    counts.set(key, (counts.get(key) || 0) + 1);
  }
  for (const [key, count] of [...counts].sort((a, b) => b[1] - a[1])) {
    console.log(`  x${String(count).padStart(4)}  ${key}`);
  }
}
