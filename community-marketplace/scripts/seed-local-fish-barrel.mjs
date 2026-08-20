#!/usr/bin/env node
/**
 * Seeds one built-in Aetherhaven prop into the local marketplace data dir for website testing.
 * Usage (from community-marketplace/): node scripts/seed-local-fish-barrel.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const repoRoot = path.resolve(root, "..");

// Must match marketplace prop id rules: prop_community_[a-z0-9_]{8,80}
const id = "prop_community_localdev_fish_barrel";

const srcPrefab = path.join(repoRoot, "src/main/resources/Server/Prefabs/Props/Fish_Barrel.prefab.json");
const srcProp = path.join(repoRoot, "src/main/resources/Server/Aetherhaven/Props/fish_barrel.json");

if (!fs.existsSync(srcPrefab)) {
  console.error("Missing prefab:", srcPrefab);
  process.exit(1);
}
if (!fs.existsSync(srcProp)) {
  console.error("Missing prop definition:", srcProp);
  process.exit(1);
}

const approvedDir = path.join(root, "data/approved", id);
const debugDir = path.join(root, "web/debug");
fs.mkdirSync(approvedDir, { recursive: true });
fs.mkdirSync(debugDir, { recursive: true });

fs.copyFileSync(srcPrefab, path.join(approvedDir, "prefab.prefab.json"));
fs.copyFileSync(srcPrefab, path.join(debugDir, "fish_barrel.prefab.json"));

function readJsonFile(filePath) {
  const raw = fs.readFileSync(filePath, "utf8").replace(/^\uFEFF/, "");
  return JSON.parse(raw);
}

const prop = readJsonFile(srcProp);
prop.id = id;
prop.prefabPath = `Props/${id}.prefab.json`;
fs.writeFileSync(path.join(approvedDir, "prop.json"), JSON.stringify(prop, null, 2));

const now = new Date().toISOString();
const meta = {
  id,
  contentType: "prop",
  displayName: prop.displayName || "Fish Barrel",
  description: "Local seed of the built-in Fish Barrel prop for marketplace testing.",
  creatorUuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  creatorName: "LocalDev",
  styleId: "misc",
  tags: ["prop", "decoration"],
  blockIdVersion: 11,
  status: "approved",
  approvedAt: now,
  version: "1",
};
fs.writeFileSync(path.join(approvedDir, "meta.json"), JSON.stringify(meta, null, 2));

const manifestPath = path.join(root, "data/manifest.json");
const manifest = fs.existsSync(manifestPath)
  ? JSON.parse(fs.readFileSync(manifestPath, "utf8"))
  : { version: 1, entries: [] };

manifest.entries = (manifest.entries || []).filter((e) => e.id !== id);
manifest.entries.push({
  id,
  contentType: "prop",
  displayName: meta.displayName,
  description: meta.description,
  creatorUuid: meta.creatorUuid,
  creatorName: meta.creatorName,
  styleId: meta.styleId,
  tags: meta.tags,
  decorationPlot: false,
  wallSegment: false,
  festivalVariant: false,
  blockIdVersion: meta.blockIdVersion,
  prefabBytes: fs.statSync(path.join(approvedDir, "prefab.prefab.json")).size,
  version: meta.version,
  approvedAt: meta.approvedAt,
});
manifest.version = (manifest.version || 0) + 1;
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));

console.log("Seeded", id);
console.log("Open the Props tab on the catalog to see Fish Barrel.");
console.log(
  "Debug URL: /internal/prefab-render.html?prefabUrl=%2Fdebug%2Ffish_barrel.prefab.json"
);
console.log(
  "Catalog entries:",
  manifest.entries.map((e) => `${e.displayName} (${e.contentType || "building"})`).join(", ")
);
