/**
 * Check that every roof-corner block state in a prefab resolves to a blockymodel that
 * exists on disk. Usage: node scripts/debug-roof-corners.mjs [prefab]
 */
import fs from "node:fs";
import path from "node:path";

const prefabPath = process.argv[2] || "web/debug/cozy_town_hall.prefab.json";
const ASSETS = "web/hytale-assets/Common";

const catalog = JSON.parse(fs.readFileSync("web/hytale-assets/catalog/block_catalog.json", "utf8"));
const doc = JSON.parse(fs.readFileSync(prefabPath, "utf8"));

function cornerModelCandidates(modelPath, state) {
  const parts = String(modelPath).match(/^(.*\/)?([^/]+)\.blockymodel$/i);
  if (!parts) {
    return [];
  }
  const slope = parts[2].match(/^Slope(_.*)?$/i);
  if (!slope) {
    return [];
  }
  const tokens = String(state).split("_").filter(Boolean);
  if (!tokens.some((t) => /^Corner$/i.test(t))) {
    return [];
  }
  const dir = parts[1] || "";
  const variant = slope[1] || "";
  const side = tokens.find((t) => /^(Left|Right)$/i.test(t));
  const suffix = side ? `_${side}` : "";
  const inverted = tokens.some((t) => /^Inverted$/i.test(t)) ? "_Inverted" : "";
  const names = [
    `Corner${inverted}${variant}${suffix}`,
    `Corner${variant}${inverted}${suffix}`,
    `Corner${variant}${suffix}`,
  ];
  return [...new Set(names)].map((n) => `${dir}${n}.blockymodel`);
}

const states = new Map();
for (const block of doc.blocks || []) {
  const name = block.name || "";
  const m = name.match(/^\*?(.+)_State_Definitions_([A-Za-z0-9_]+)$/i);
  if (!m || !/corner/i.test(m[2])) {
    continue;
  }
  states.set(name, { base: m[1], state: m[2], count: (states.get(name)?.count || 0) + 1 });
}

let unresolved = 0;
for (const [name, info] of states) {
  const base = catalog[info.base];
  const candidates = base?.customModel ? cornerModelCandidates(base.customModel, info.state) : [];
  const hit = candidates.find((c) => fs.existsSync(path.join(ASSETS, c)));
  if (!hit) {
    unresolved += 1;
  }
  console.log(`${hit ? "OK  " : "FAIL"}  ${name}`);
  console.log(`        base model: ${base?.customModel || "(none)"}`);
  console.log(`        resolved:   ${hit || `none of ${candidates.join(", ") || "(no candidates)"}`}`);
}
console.log(`\n${states.size} corner states, ${unresolved} unresolved`);
