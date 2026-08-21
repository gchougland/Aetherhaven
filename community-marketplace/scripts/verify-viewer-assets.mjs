#!/usr/bin/env node
/**
 * Check that everything the prefab viewer asks for is actually there.
 *
 * A missing model draws a grey cube and a missing texture draws a grey skin, both
 * silently, so a broken sync or a half finished volume upload looks like a rendering
 * bug. This walks the catalogs and, optionally, the submitted prefabs, and names what
 * is missing instead.
 *
 * Paths are compared case sensitively even on Windows, because the server that serves
 * them is Linux.
 *
 * Usage:
 *   node scripts/verify-viewer-assets.mjs
 *   node scripts/verify-viewer-assets.mjs --base https://aetherhaven.net
 *   node scripts/verify-viewer-assets.mjs --base https://aetherhaven.net --sample 300
 *   node scripts/verify-viewer-assets.mjs --base https://aetherhaven.net --no-prefabs
 *
 * Exit code covers the catalog and the files it points at. Block ids inside submitted
 * prefabs are reported but never fail the run, since builders use mods we do not ship.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const marketplaceRoot = path.resolve(__dirname, "..");
const assetsDir = path.join(marketplaceRoot, "web", "hytale-assets");

const args = process.argv.slice(2);

/** @param {string} name */
function flagValue(name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : null;
}

const baseUrl = (flagValue("--base") || "").replace(/\/+$/, "");
const checkPrefabs = !args.includes("--no-prefabs");
const showAll = args.includes("--all");
const sampleSize = Number(flagValue("--sample")) || 0;
const concurrency = Number(flagValue("--concurrency")) || 16;

/** @param {string} p */
function normalizeAssetPath(p) {
  return String(p || "")
    .replace(/\\/g, "/")
    .replace(/^\/+/, "")
    .replace(/^Common\//i, "");
}

function readCatalog(name) {
  const file = path.join(assetsDir, "catalog", name);
  if (!fs.existsSync(file)) {
    console.error(`Missing ${file}. Run: npm run sync-hytale-assets`);
    process.exit(1);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

/**
 * Every model / texture path a catalog entry can point at, including nested block
 * states and item variants.
 * @param {any} def
 * @param {(p: string) => void} add
 */
function collectDefPaths(def, add) {
  if (!def || typeof def !== "object") {
    return;
  }
  add(def.customModel);
  add(def.customModelTexture);
  add(def.customModelAnimation);
  add(def.itemModel);
  add(def.itemTexture);
  add(def.model);
  add(def.texture);
  if (def.textures && typeof def.textures === "object") {
    for (const face of Object.values(def.textures)) {
      add(face);
    }
  }
  for (const child of [def.states, def.variants]) {
    if (child && typeof child === "object") {
      for (const nested of Object.values(child)) {
        collectDefPaths(nested, add);
      }
    }
  }
}

/**
 * Index of every file under Common, keyed both exactly and lowercased, so a path that
 * only differs in case can be reported as such rather than as simply missing.
 */
function indexLocalCommon() {
  const root = path.join(assetsDir, "Common");
  const exact = new Set();
  const lower = new Map();
  if (!fs.existsSync(root)) {
    return { exact, lower, ok: false };
  }
  const stack = [{ dir: root, rel: "" }];
  while (stack.length) {
    const { dir, rel } = stack.pop();
    for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
      const childRel = rel ? `${rel}/${ent.name}` : ent.name;
      if (ent.isDirectory()) {
        stack.push({ dir: path.join(dir, ent.name), rel: childRel });
      } else {
        exact.add(childRel);
        if (!lower.has(childRel.toLowerCase())) {
          lower.set(childRel.toLowerCase(), childRel);
        }
      }
    }
  }
  return { exact, lower, ok: true };
}

/**
 * @template T, R
 * @param {T[]} items
 * @param {(item: T) => Promise<R>} fn
 */
async function mapLimit(items, fn) {
  const results = [];
  let next = 0;
  const workers = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (next < items.length) {
      const i = next;
      next += 1;
      results[i] = await fn(items[i]);
    }
  });
  await Promise.all(workers);
  return results;
}

/** @param {string} url */
async function headOk(url) {
  try {
    // Some CDNs treat HEAD oddly; a ranged GET is cheap and always honest.
    const res = await fetch(url, { headers: { Range: "bytes=0-0" } });
    return res.status === 200 || res.status === 206;
  } catch {
    return false;
  }
}

async function main() {
  const blocks = readCatalog("block_catalog.json");
  const models = readCatalog("model_catalog.json");

  /** @type {Map<string, Set<string>>} assetPath -> catalog ids that reference it */
  const refs = new Map();
  const addFor = (owner) => (p) => {
    const n = normalizeAssetPath(p);
    if (!n) {
      return;
    }
    if (!refs.has(n)) {
      refs.set(n, new Set());
    }
    refs.get(n).add(owner);
  };
  for (const [id, def] of Object.entries(blocks)) {
    collectDefPaths(def, addFor(id));
  }
  for (const [id, def] of Object.entries(models)) {
    collectDefPaths(def, addFor(id));
  }

  console.log(
    `Catalogs: ${Object.keys(blocks).length} blocks, ${Object.keys(models).length} models, ${refs.size} distinct asset paths`
  );

  let failures = 0;

  // Local check always runs: it is free and catches a bad sync before a bad deploy.
  const local = indexLocalCommon();
  if (!local.ok) {
    console.error(`No assets at ${path.join(assetsDir, "Common")}. Run: npm run sync-hytale-assets`);
    failures += 1;
  } else {
    const missing = [];
    const caseOnly = [];
    for (const [p, owners] of refs) {
      if (local.exact.has(p)) {
        continue;
      }
      const onDisk = local.lower.get(p.toLowerCase());
      const sample = [...owners].slice(0, 3).join(", ");
      if (onDisk) {
        caseOnly.push(`${p} (on disk as ${onDisk}) <- ${sample}`);
      } else {
        missing.push(`${p} <- ${sample}`);
      }
    }
    console.log(`\nLocal sync: ${refs.size - missing.length - caseOnly.length}/${refs.size} present`);
    for (const line of caseOnly) {
      console.log(`  CASE  ${line}`);
    }
    for (const line of missing) {
      console.log(`  GONE  ${line}`);
    }
    failures += missing.length + caseOnly.length;
  }

  if (baseUrl) {
    let paths = [...refs.keys()];
    if (sampleSize > 0 && sampleSize < paths.length) {
      const step = paths.length / sampleSize;
      paths = Array.from({ length: sampleSize }, (_, i) => paths[Math.floor(i * step)]);
    }
    console.log(`\nChecking ${paths.length} paths against ${baseUrl} ...`);
    const results = await mapLimit(paths, async (p) => ({
      p,
      ok: await headOk(`${baseUrl}/hytale-assets/Common/${p}`),
    }));
    const broken = results.filter((r) => !r.ok);
    console.log(`Live assets: ${results.length - broken.length}/${results.length} present`);
    for (const { p } of broken) {
      const sample = [...(refs.get(p) || [])].slice(0, 3).join(", ");
      console.log(`  404   ${p} <- ${sample}`);
    }
    failures += broken.length;

    if (checkPrefabs) {
      await reportPrefabIds(blocks);
    }
  } else if (checkPrefabs) {
    console.log("\nSkipping live checks (pass --base https://aetherhaven.net to include them).");
  }

  console.log(failures ? `\nFAIL: ${failures} problem(s)` : "\nOK: everything the viewer needs is present");
  process.exit(failures ? 1 : 0);
}

/**
 * Submitted prefabs outlive block renames, so an id that no longer resolves draws a
 * grey cube on a page nobody is testing.
 *
 * This never fails the run. Builders play with whatever mods they like, and a block
 * from someone else's furniture pack is simply not something this site can draw. The
 * ids worth acting on are the ones that only differ in case, because those are our own
 * blocks lost to a rename.
 * @param {Record<string, any>} blocks
 */
async function reportPrefabIds(blocks) {
  const byLower = new Map();
  for (const id of Object.keys(blocks)) {
    if (!byLower.has(id.toLowerCase())) {
      byLower.set(id.toLowerCase(), id);
    }
  }

  let entries;
  try {
    const res = await fetch(`${baseUrl}/api/v1/manifest`);
    if (!res.ok) {
      console.log(`\nManifest unavailable (${res.status}); skipping prefab id check.`);
      return;
    }
    const doc = await res.json();
    entries = Array.isArray(doc?.entries) ? doc.entries : [];
  } catch (err) {
    console.log(`\nManifest unreachable; skipping prefab id check. ${err?.message || err}`);
    return;
  }

  console.log(`\nChecking block ids in ${entries.length} submitted prefabs ...`);
  const caseOnly = new Map();
  const unknown = new Map();

  await mapLimit(entries, async (entry) => {
    const id = entry?.id;
    if (!id) {
      return;
    }
    let doc;
    try {
      const res = await fetch(`${baseUrl}/api/v1/buildings/${encodeURIComponent(id)}/prefab.json`);
      if (!res.ok) {
        return;
      }
      doc = await res.json();
    } catch {
      return;
    }
    for (const b of doc?.blocks || []) {
      const name = String(b?.name || "").replace(/^\*/, "");
      if (!name || name === "Empty" || blocks[name]) {
        continue;
      }
      // Peel state suffixes the way BlockCatalog.getBlockDef does.
      const base = name.replace(/_State_Definitions_.*$/, "").replace(/_State_.*$/, "");
      if (blocks[base]) {
        continue;
      }
      const wanted = blocks[name] ? name : base;
      const match = byLower.get(wanted.toLowerCase());
      const bucket = match ? caseOnly : unknown;
      const key = match ? `${wanted} -> ${match}` : wanted;
      if (!bucket.has(key)) {
        bucket.set(key, new Set());
      }
      bucket.get(key).add(id);
    }
  });

  if (!caseOnly.size && !unknown.size) {
    console.log("  every block id resolves");
    return;
  }

  if (caseOnly.size) {
    console.log(`\n  Renamed Aetherhaven blocks (${caseOnly.size}). The viewer's case-insensitive`);
    console.log("  fallback still draws these, but the id drifted and is worth fixing at the source:");
    for (const [key, owners] of caseOnly) {
      console.log(`    ${key}  (${owners.size} prefab(s), e.g. ${[...owners][0]})`);
    }
  }

  if (unknown.size) {
    const shown = showAll ? [...unknown] : [...unknown].slice(0, 15);
    console.log(`\n  Blocks from mods this site does not ship (${unknown.size}). These draw grey`);
    console.log("  cubes and always will; nothing to fix unless one is ours:");
    for (const [key, owners] of shown) {
      console.log(`    ${key}  (${owners.size} prefab(s), e.g. ${[...owners][0]})`);
    }
    if (shown.length < unknown.size) {
      console.log(`    ... and ${unknown.size - shown.length} more (pass --all to list them)`);
    }
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
