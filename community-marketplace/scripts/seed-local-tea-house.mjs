#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const repoRoot = path.resolve(root, "..");

// Must match marketplace community id rules: plot_community_[a-z0-9_]{8,80}
const id = "plot_community_localdev_japanese_tea_house";
const legacyIds = ["plot_house_hytiny_japanese_tea_house"];

const srcPrefab = path.join(
  repoRoot,
  "src/main/resources/Server/Prefabs/Hytinys_Japanese_Tea_House.prefab.json"
);
const srcBuilding = path.join(
  repoRoot,
  "src/main/resources/Server/Aetherhaven/Buildings/plot_house_hytiny_japanese_tea_house.json"
);

const approvedDir = path.join(root, "data/approved", id);
const debugDir = path.join(root, "web/debug");
fs.mkdirSync(approvedDir, { recursive: true });
fs.mkdirSync(debugDir, { recursive: true });

fs.copyFileSync(srcPrefab, path.join(approvedDir, "prefab.prefab.json"));
fs.copyFileSync(srcPrefab, path.join(debugDir, "japanese_tea_house.prefab.json"));

const building = JSON.parse(fs.readFileSync(srcBuilding, "utf8"));
building.id = id;
building.prefabPath = `${id}.prefab.json`;
fs.writeFileSync(path.join(approvedDir, "building.json"), JSON.stringify(building, null, 2));

const now = new Date().toISOString();
const meta = {
  id,
  displayName: "Hytiny's Japanese Tea House",
  description: "Local seed for entity-heavy prefab viewer testing.",
  creatorUuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  creatorName: "LocalDev",
  styleId: "hytiny",
  tags: ["house", "tea"],
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

const dropIds = new Set([id, ...legacyIds]);
manifest.entries = (manifest.entries || []).filter((e) => !dropIds.has(e.id));
manifest.entries.push({
  id,
  displayName: meta.displayName,
  description: meta.description,
  creatorUuid: meta.creatorUuid,
  creatorName: meta.creatorName,
  styleId: meta.styleId,
  tags: meta.tags,
  decorationPlot: false,
  blockIdVersion: meta.blockIdVersion,
  prefabBytes: fs.statSync(path.join(approvedDir, "prefab.prefab.json")).size,
  version: meta.version,
  approvedAt: meta.approvedAt,
});
manifest.version = (manifest.version || 0) + 1;
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));

for (const legacy of legacyIds) {
  const legacyDir = path.join(root, "data/approved", legacy);
  if (fs.existsSync(legacyDir)) {
    fs.rmSync(legacyDir, { recursive: true, force: true });
  }
}

console.log("Seeded", id);
console.log(
  "Debug URL: /internal/prefab-render.html?prefabUrl=%2Fdebug%2Fjapanese_tea_house.prefab.json"
);
console.log(
  "Catalog entries:",
  manifest.entries.map((e) => e.displayName).join(", ")
);
