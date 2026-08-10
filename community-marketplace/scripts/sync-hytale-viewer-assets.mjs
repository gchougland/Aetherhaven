#!/usr/bin/env node
/**
 * Sync Hytale + Aetherhaven assets used by the marketplace prefab viewer.
 * Assets stay out of git — run locally (or onto a Railway volume) before serving.
 *
 * Usage:
 *   node scripts/sync-hytale-viewer-assets.mjs
 *   HYTALE_ASSETS_SRC=... AETHERHAVEN_ASSETS_SRC=... OUT_DIR=... node scripts/sync-hytale-viewer-assets.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const marketplaceRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(marketplaceRoot, "..");

const DEFAULT_HYTALE = path.resolve(
  repoRoot,
  "..",
  "HytaleSourceCode",
  "hytale-shared-source",
  "HytaleAssets"
);
const DEFAULT_AETHERHAVEN = path.join(repoRoot, "src", "main", "resources");

const hytaleRoot = path.resolve(process.env.HYTALE_ASSETS_SRC || DEFAULT_HYTALE);
const aetherhavenRoot = path.resolve(process.env.AETHERHAVEN_ASSETS_SRC || DEFAULT_AETHERHAVEN);
const outDir = path.resolve(process.env.OUT_DIR || path.join(marketplaceRoot, "web", "hytale-assets"));

/** @type {Set<string>} */
const referencedCommonPaths = new Set();

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function copyFile(src, dest) {
  ensureDir(path.dirname(dest));
  fs.copyFileSync(src, dest);
}

function walkFiles(root, filterFn) {
  /** @type {string[]} */
  const out = [];
  if (!fs.existsSync(root)) {
    return out;
  }
  const stack = [root];
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const ent of entries) {
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        stack.push(full);
      } else if (ent.isFile() && (!filterFn || filterFn(full))) {
        out.push(full);
      }
    }
  }
  return out;
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function normalizeAssetPath(p) {
  return String(p || "")
    .replace(/\\/g, "/")
    .replace(/^\/+/, "")
    .replace(/^Common\//i, "");
}

function trackCommonRef(assetPath) {
  const n = normalizeAssetPath(assetPath);
  if (n) {
    referencedCommonPaths.add(n);
  }
}

function pickTextures(texEntry) {
  if (!texEntry || typeof texEntry !== "object") {
    return null;
  }
  /** @type {Record<string, string>} */
  const out = {};
  for (const key of ["All", "Up", "Down", "Sides", "UpDown", "North", "South", "East", "West", "Left", "Right"]) {
    if (texEntry[key]) {
      out[key] = normalizeAssetPath(texEntry[key]);
      trackCommonRef(out[key]);
    }
  }
  return Object.keys(out).length ? out : null;
}

/**
 * @param {object} raw
 * @param {Map<string, object>} byFileStem parent lookup by filename stem
 */
function extractBlockEntry(raw, byFileStem) {
  const block = raw.BlockType || null;
  const isFluid = Boolean(raw.MaxFluidLevel != null || raw.FluidFXId || (raw.Tags && raw.Tags.Fluid));
  if (!block && !isFluid && !raw.Model) {
    return null;
  }

  /** @type {any} */
  const entry = {};

  if (block) {
    entry.drawType = String(block.DrawType || "Cube");
    entry.opacity = block.Opacity || null;
    if (Array.isArray(block.Textures) && block.Textures.length) {
      const faces = pickTextures(block.Textures[0]);
      if (faces) {
        entry.textures = faces;
      }
    }
    if (block.CustomModel) {
      entry.customModel = normalizeAssetPath(block.CustomModel);
      trackCommonRef(entry.customModel);
    }
    if (Array.isArray(block.CustomModelTexture) && block.CustomModelTexture[0]?.Texture) {
      entry.customModelTexture = normalizeAssetPath(block.CustomModelTexture[0].Texture);
      trackCommonRef(entry.customModelTexture);
    }
    if (Array.isArray(block.TintUp) && block.TintUp[0]) {
      entry.tintUp = block.TintUp[0];
    }
  }

  if (isFluid || raw.MaxFluidLevel != null) {
    entry.kind = "fluid";
    entry.maxFluidLevel = Number(raw.MaxFluidLevel) || 1;
    if (Array.isArray(raw.Textures) && raw.Textures.length) {
      const faces = pickTextures(raw.Textures[0]);
      if (faces) {
        entry.textures = faces;
      }
    }
  }

  if (raw.Model) {
    entry.itemModel = normalizeAssetPath(raw.Model);
    trackCommonRef(entry.itemModel);
  }
  if (raw.Texture) {
    entry.itemTexture = normalizeAssetPath(raw.Texture);
    trackCommonRef(entry.itemTexture);
  }

  if (raw.Parent) {
    entry.parent = String(raw.Parent);
  }

  if (!entry.drawType && !entry.kind && !entry.itemModel && !entry.customModel) {
    return null;
  }
  if (!entry.drawType && entry.customModel) {
    entry.drawType = "Model";
  }
  if (!entry.drawType && entry.kind === "fluid") {
    entry.drawType = "Cube";
  }
  return entry;
}

function resolveParents(catalog) {
  let changed = true;
  let guard = 0;
  while (changed && guard < 20) {
    changed = false;
    guard += 1;
    for (const [id, entry] of Object.entries(catalog)) {
      const parentId = entry.parent;
      if (!parentId) {
        continue;
      }
      const parent = catalog[parentId];
      if (!parent) {
        continue;
      }
      for (const key of [
        "drawType",
        "textures",
        "customModel",
        "customModelTexture",
        "maxFluidLevel",
        "itemModel",
        "itemTexture",
        "opacity",
        "tintUp",
        "kind",
      ]) {
        if (entry[key] == null && parent[key] != null) {
          entry[key] = parent[key];
          changed = true;
        }
      }
      if (entry.kind == null && parent.kind) {
        entry.kind = parent.kind;
        changed = true;
      }
    }
  }
  for (const entry of Object.values(catalog)) {
    delete entry.parent;
  }
}

function ingestItemTree(itemsRoot, catalog) {
  if (!fs.existsSync(itemsRoot)) {
    return;
  }
  const files = walkFiles(itemsRoot, (f) => f.endsWith(".json"));
  /** @type {Map<string, object>} */
  const byStem = new Map();
  /** @type {Array<{ id: string, raw: object }>} */
  const parsed = [];

  for (const file of files) {
    let raw;
    try {
      raw = readJson(file);
    } catch {
      continue;
    }
    const id = path.basename(file, ".json");
    byStem.set(id, raw);
    parsed.push({ id, raw });
  }

  for (const { id, raw } of parsed) {
    const entry = extractBlockEntry(raw, byStem);
    if (entry) {
      catalog[id] = { ...(catalog[id] || {}), ...entry };
    }
  }
}

function ingestFluids(fluidsRoot, catalog) {
  if (!fs.existsSync(fluidsRoot)) {
    return;
  }
  for (const file of walkFiles(fluidsRoot, (f) => f.endsWith(".json"))) {
    let raw;
    try {
      raw = readJson(file);
    } catch {
      continue;
    }
    const id = path.basename(file, ".json");
    const entry = extractBlockEntry(raw, null);
    if (entry) {
      entry.kind = "fluid";
      catalog[id] = { ...(catalog[id] || {}), ...entry };
    }
  }
}

function ingestModels(modelsRoot, modelCatalog) {
  if (!fs.existsSync(modelsRoot)) {
    return;
  }
  for (const file of walkFiles(modelsRoot, (f) => f.endsWith(".json"))) {
    let raw;
    try {
      raw = readJson(file);
    } catch {
      continue;
    }
    const id = path.basename(file, ".json");
    const model = normalizeAssetPath(raw.Model);
    const texture = normalizeAssetPath(raw.Texture);
    if (!model) {
      continue;
    }
    trackCommonRef(model);
    if (texture) {
      trackCommonRef(texture);
    }
    modelCatalog[id] = {
      model,
      texture: texture || null,
      scale: Number(raw.MinScale) || 1,
    };
  }
}

function copyTree(srcRoot, destRoot, filterFn) {
  if (!fs.existsSync(srcRoot)) {
    return 0;
  }
  let count = 0;
  for (const file of walkFiles(srcRoot, filterFn)) {
    const rel = path.relative(srcRoot, file);
    copyFile(file, path.join(destRoot, rel));
    count += 1;
  }
  return count;
}

function resolveCommonFile(relPath) {
  const n = normalizeAssetPath(relPath);
  const candidates = [
    path.join(hytaleRoot, "Common", n),
    path.join(aetherhavenRoot, "Common", n),
    path.join(hytaleRoot, n),
    path.join(aetherhavenRoot, n),
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) {
      return c;
    }
  }
  return null;
}

function copyReferencedCommon() {
  let copied = 0;
  let missing = 0;
  for (const rel of referencedCommonPaths) {
    const n = normalizeAssetPath(rel);
    // Trees copied wholesale already.
    if (n.startsWith("BlockTextures/") || n.startsWith("Blocks/") || n.startsWith("Items/") || n.startsWith("NPC/")) {
      continue;
    }
    const dest = path.join(outDir, "Common", n);
    if (fs.existsSync(dest)) {
      continue;
    }
    const src = resolveCommonFile(rel);
    if (!src) {
      missing += 1;
      continue;
    }
    copyFile(src, dest);
    copied += 1;

    // Also copy sibling textures next to blockymodels when only the model was referenced.
    if (rel.endsWith(".blockymodel")) {
      const dir = path.dirname(src);
      try {
        for (const name of fs.readdirSync(dir)) {
          if (!/\.(png|jpg|jpeg|webp)$/i.test(name)) {
            continue;
          }
          const siblingRel = path.posix.join(path.posix.dirname(n), name);
          const siblingDest = path.join(outDir, "Common", siblingRel);
          if (fs.existsSync(siblingDest)) {
            continue;
          }
          copyFile(path.join(dir, name), siblingDest);
          copied += 1;
        }
      } catch {
        /* ignore */
      }
    }
  }
  return { copied, missing };
}

function main() {
  console.log("Hytale assets:", hytaleRoot);
  console.log("Aetherhaven assets:", aetherhavenRoot);
  console.log("Output:", outDir);

  if (!fs.existsSync(hytaleRoot)) {
    console.error("Hytale assets root not found. Set HYTALE_ASSETS_SRC.");
    process.exit(1);
  }

  ensureDir(outDir);
  // Clean previous sync (keep folder, replace contents)
  for (const name of fs.readdirSync(outDir)) {
    fs.rmSync(path.join(outDir, name), { recursive: true, force: true });
  }

  const blockTexturesCopied = copyTree(
    path.join(hytaleRoot, "Common", "BlockTextures"),
    path.join(outDir, "Common", "BlockTextures")
  );
  const aetherBlockTextures = copyTree(
    path.join(aetherhavenRoot, "Common", "BlockTextures"),
    path.join(outDir, "Common", "BlockTextures")
  );
  console.log(`BlockTextures: ${blockTexturesCopied + aetherBlockTextures} files`);

  // Copy full Blocks trees (furniture / roofs / stairs models + textures).
  const blocksCopied =
    copyTree(path.join(hytaleRoot, "Common", "Blocks"), path.join(outDir, "Common", "Blocks")) +
    copyTree(path.join(aetherhavenRoot, "Common", "Blocks"), path.join(outDir, "Common", "Blocks"));
  console.log(`Blocks: ${blocksCopied} files`);

  // Item-held models/textures (rubble skins, tools, etc. referenced as Items/...)
  const itemsCopied =
    copyTree(
      path.join(hytaleRoot, "Common", "Items"),
      path.join(outDir, "Common", "Items"),
      (f) => /\.(blockymodel|png|jpg|jpeg|webp)$/i.test(f)
    ) +
    copyTree(
      path.join(aetherhavenRoot, "Common", "Items"),
      path.join(outDir, "Common", "Items"),
      (f) => /\.(blockymodel|png|jpg|jpeg|webp)$/i.test(f)
    );
  console.log(`Items models/textures: ${itemsCopied} files`);

  /** @type {Record<string, any>} */
  const blockCatalog = {};
  ingestItemTree(path.join(hytaleRoot, "Server", "Item", "Items"), blockCatalog);
  ingestItemTree(path.join(aetherhavenRoot, "Server", "Item", "Items"), blockCatalog);
  ingestFluids(path.join(hytaleRoot, "Server", "Item", "Block", "Fluids"), blockCatalog);
  ingestFluids(path.join(aetherhavenRoot, "Server", "Item", "Block", "Fluids"), blockCatalog);
  resolveParents(blockCatalog);

  /** @type {Record<string, any>} */
  const modelCatalog = {};
  ingestModels(path.join(hytaleRoot, "Server", "Models"), modelCatalog);
  ingestModels(path.join(aetherhavenRoot, "Server", "Models"), modelCatalog);

  // Track model paths from catalogs already done; also pull NPC/Characters used by models.
  const refCopy = copyReferencedCommon();
  console.log(`Referenced Common files: ${refCopy.copied} copied, ${refCopy.missing} missing`);

  // Broaden NPC folder for entity models commonly referenced
  const npcCopied =
    copyTree(path.join(hytaleRoot, "Common", "NPC"), path.join(outDir, "Common", "NPC"), (f) =>
      /\.(blockymodel|png|jpg|jpeg|webp)$/i.test(f)
    ) +
    copyTree(path.join(aetherhavenRoot, "Common", "NPC"), path.join(outDir, "Common", "NPC"), (f) =>
      /\.(blockymodel|png|jpg|jpeg|webp)$/i.test(f)
    );
  console.log(`NPC models/textures: ${npcCopied} files`);

  const catalogDir = path.join(outDir, "catalog");
  ensureDir(catalogDir);
  fs.writeFileSync(path.join(catalogDir, "block_catalog.json"), JSON.stringify(blockCatalog));
  fs.writeFileSync(path.join(catalogDir, "model_catalog.json"), JSON.stringify(modelCatalog));
  fs.writeFileSync(
    path.join(catalogDir, "manifest.json"),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        blockCount: Object.keys(blockCatalog).length,
        modelCount: Object.keys(modelCatalog).length,
        sources: { hytaleRoot, aetherhavenRoot },
      },
      null,
      2
    )
  );

  console.log(
    `Catalogs: ${Object.keys(blockCatalog).length} blocks/items/fluids, ${Object.keys(modelCatalog).length} entity models`
  );
  console.log("Done.");
}

main();
