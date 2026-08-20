#!/usr/bin/env node
/**
 * Sync Hytale + Aetherhaven assets used by the marketplace prefab viewer.
 * Models and textures stay out of git — run locally (or onto a Railway volume) before
 * serving. The small catalog JSON is committed instead, so viewer code and the block
 * data it depends on always deploy together.
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

/** Every tree laid out like a mod: Hytale itself, Aetherhaven, and each subplugin pack. */
const assetRoots = [hytaleRoot, aetherhavenRoot, ...subpluginRoots()];

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
 * The drawable part of one block state. A state can reskin the cube, swap the model, or
 * both — roof corners and open doors are whole models, double slabs and coloured banners
 * are textures. Everything else a state carries (hitboxes, loot, sounds) is not drawn.
 * @param {any} stateDef
 * @returns {any|null}
 */
function extractStateOverride(stateDef) {
  if (!stateDef || typeof stateDef !== "object") {
    return null;
  }
  /** @type {any} */
  const out = {};
  if (stateDef.DrawType) {
    out.drawType = String(stateDef.DrawType);
  }
  if (Array.isArray(stateDef.Textures) && stateDef.Textures.length) {
    const faces = pickTextures(stateDef.Textures[0]);
    if (faces) {
      out.textures = faces;
    }
  }
  if (stateDef.CustomModel) {
    out.customModel = normalizeAssetPath(stateDef.CustomModel);
    trackCommonRef(out.customModel);
  }
  if (Array.isArray(stateDef.CustomModelTexture) && stateDef.CustomModelTexture[0]?.Texture) {
    out.customModelTexture = normalizeAssetPath(stateDef.CustomModelTexture[0].Texture);
    trackCommonRef(out.customModelTexture);
  }
  if (stateDef.CustomModelScale != null && Number.isFinite(Number(stateDef.CustomModelScale))) {
    out.customModelScale = Number(stateDef.CustomModelScale);
  }
  if (stateDef.Opacity) {
    out.opacity = String(stateDef.Opacity);
  }
  if (Array.isArray(stateDef.Tint) && stateDef.Tint[0]) {
    out.tint = stateDef.Tint[0];
  }
  if (Array.isArray(stateDef.TintUp) && stateDef.TintUp[0]) {
    out.tintUp = stateDef.TintUp[0];
  }
  // Open trapdoors / doors keep the same model and apply a hold-last animation pose.
  if (stateDef.CustomModelAnimation) {
    out.customModelAnimation = normalizeAssetPath(stateDef.CustomModelAnimation);
    trackCommonRef(out.customModelAnimation);
  }
  // Furnaces and a few benches nest a state inside a state.
  const nested = collectStates(stateDef.State?.Definitions);
  if (nested) {
    out.states = nested;
  }
  return Object.keys(out).length ? out : null;
}

/**
 * Item variants are the other kind of state, written `*Id_State_Name` in prefabs rather
 * than `*Id_State_Definitions_Name`. A filled bucket or watering can is the same item
 * carrying its own block model.
 * @param {any} state the item's top level State
 * @returns {Record<string, any>|null}
 */
function collectVariants(state) {
  if (!state || typeof state !== "object") {
    return null;
  }
  /** @type {Record<string, any>} */
  const variants = {};
  for (const [name, variant] of Object.entries(state)) {
    if (!variant || typeof variant !== "object") {
      continue;
    }
    const override = extractStateOverride(variant.BlockType) || {};
    if (variant.Model) {
      override.itemModel = normalizeAssetPath(variant.Model);
      trackCommonRef(override.itemModel);
    }
    if (variant.Texture) {
      override.itemTexture = normalizeAssetPath(variant.Texture);
      trackCommonRef(override.itemTexture);
    }
    if (Object.keys(override).length) {
      variants[name] = override;
    }
  }
  return Object.keys(variants).length ? variants : null;
}

/**
 * @param {any} definitions BlockType.State.Definitions
 * @returns {Record<string, any>|null}
 */
function collectStates(definitions) {
  if (!definitions || typeof definitions !== "object") {
    return null;
  }
  /** @type {Record<string, any>} */
  const states = {};
  for (const [stateName, stateDef] of Object.entries(definitions)) {
    const override = extractStateOverride(stateDef);
    if (override) {
      states[stateName] = override;
    }
  }
  return Object.keys(states).length ? states : null;
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
    if (block.CustomModelScale != null && Number.isFinite(Number(block.CustomModelScale))) {
      entry.customModelScale = Number(block.CustomModelScale);
    }
    if (Array.isArray(block.TintUp) && block.TintUp[0]) {
      entry.tintUp = block.TintUp[0];
    }
    if (Array.isArray(block.Tint) && block.Tint[0]) {
      entry.tint = block.Tint[0];
    }
    const states = collectStates(block.State?.Definitions);
    if (states) {
      entry.states = states;
    }
  }

  const variants = collectVariants(raw.State);
  if (variants) {
    entry.variants = variants;
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
        "customModelScale",
        "maxFluidLevel",
        "itemModel",
        "itemTexture",
        "opacity",
        "tintUp",
        "tint",
        "states",
        "variants",
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
      // Prefabs name states as *BlockId_State_Definitions_Name; the viewer resolves those
      // against the block's own states rather than storing a copy per state.
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
  for (const root of assetRoots) {
    for (const candidate of [path.join(root, "Common", n), path.join(root, n)]) {
      if (fs.existsSync(candidate)) {
        return candidate;
      }
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

/**
 * Aetherhaven ships its shop, quest and portal blocks from subplugin packs, each laid out
 * like a mod of its own, so every pack is another asset root.
 * @returns {string[]}
 */
function subpluginRoots() {
  const packs = path.join(repoRoot, "subplugin-assets");
  if (!fs.existsSync(packs)) {
    return [];
  }
  return fs
    .readdirSync(packs, { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .map((e) => path.join(packs, e.name));
}

function main() {
  console.log("Hytale assets:", hytaleRoot);
  console.log("Aetherhaven assets:", aetherhavenRoot);
  console.log(`Subplugin packs: ${assetRoots.length - 2}`);
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

  const sumOverRoots = (fn) => assetRoots.reduce((total, root) => total + fn(root), 0);
  const isModelOrTexture = (f) => /\.(blockymodel|blockyanim|png|jpg|jpeg|webp)$/i.test(f);

  const blockTexturesCopied = sumOverRoots((root) =>
    copyTree(path.join(root, "Common", "BlockTextures"), path.join(outDir, "Common", "BlockTextures"))
  );
  console.log(`BlockTextures: ${blockTexturesCopied} files`);

  // Copy full Blocks trees (furniture / roofs / stairs models + textures).
  const blocksCopied = sumOverRoots((root) =>
    copyTree(path.join(root, "Common", "Blocks"), path.join(outDir, "Common", "Blocks"))
  );
  console.log(`Blocks: ${blocksCopied} files`);

  // Door / trapdoor open poses live under Blocks/Animations.
  const animCopied = sumOverRoots((root) =>
    copyTree(
      path.join(root, "Common", "Blocks", "Animations"),
      path.join(outDir, "Common", "Blocks", "Animations"),
      (f) => /\.blockyanim$/i.test(f)
    )
  );
  console.log(`Block animations: ${animCopied} files`);

  // Item-held models/textures (rubble skins, tools, etc. referenced as Items/...)
  const itemsCopied = sumOverRoots((root) =>
    copyTree(path.join(root, "Common", "Items"), path.join(outDir, "Common", "Items"), isModelOrTexture)
  );
  console.log(`Items models/textures: ${itemsCopied} files`);

  /** @type {Record<string, any>} */
  const blockCatalog = {};
  for (const root of assetRoots) {
    ingestItemTree(path.join(root, "Server", "Item", "Items"), blockCatalog);
    ingestFluids(path.join(root, "Server", "Item", "Block", "Fluids"), blockCatalog);
  }
  resolveParents(blockCatalog);

  /** @type {Record<string, any>} */
  const modelCatalog = {};
  for (const root of assetRoots) {
    ingestModels(path.join(root, "Server", "Models"), modelCatalog);
  }

  // Track model paths from catalogs already done; also pull NPC/Characters used by models.
  const refCopy = copyReferencedCommon();
  console.log(`Referenced Common files: ${refCopy.copied} copied, ${refCopy.missing} missing`);

  // Broaden NPC folder for entity models commonly referenced
  const npcCopied = sumOverRoots((root) =>
    copyTree(path.join(root, "Common", "NPC"), path.join(outDir, "Common", "NPC"), isModelOrTexture)
  );
  console.log(`NPC models/textures: ${npcCopied} files`);

  const catalogFiles = {
    "block_catalog.json": JSON.stringify(blockCatalog),
    "model_catalog.json": JSON.stringify(modelCatalog),
    "manifest.json": JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        blockCount: Object.keys(blockCatalog).length,
        modelCount: Object.keys(modelCatalog).length,
        sources: { hytaleRoot, aetherhavenRoot },
      },
      null,
      2
    ),
  };

  // The committed copy is what production serves, so refresh it even when OUT_DIR sends
  // the bulky assets somewhere else for upload.
  const repoCatalogDir = path.join(marketplaceRoot, "web", "hytale-assets", "catalog");
  for (const dir of new Set([path.join(outDir, "catalog"), repoCatalogDir])) {
    ensureDir(dir);
    for (const [name, contents] of Object.entries(catalogFiles)) {
      fs.writeFileSync(path.join(dir, name), contents);
    }
  }

  console.log(
    `Catalogs: ${Object.keys(blockCatalog).length} blocks/items/fluids, ${Object.keys(modelCatalog).length} entity models`
  );
  console.log("Done.");
}

main();
