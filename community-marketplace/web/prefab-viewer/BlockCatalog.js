/** Loads and resolves marketplace block / model catalogs for the prefab viewer. */

const DEFAULT_ASSET_BASE = "/hytale-assets";

/** @type {{ blocks: Record<string, any>, models: Record<string, any>, assetBase: string } | null} */
let cached = null;

/**
 * @param {string} [assetBase]
 */
export async function loadCatalogs(assetBase = DEFAULT_ASSET_BASE) {
  if (cached && cached.assetBase === assetBase) {
    return cached;
  }
  const base = assetBase.replace(/\/$/, "");
  const [blocksRes, modelsRes] = await Promise.all([
    fetch(`${base}/catalog/block_catalog.json`),
    fetch(`${base}/catalog/model_catalog.json`),
  ]);
  if (!blocksRes.ok) {
    throw new Error(`Block catalog unavailable (${blocksRes.status})`);
  }
  if (!modelsRes.ok) {
    throw new Error(`Model catalog unavailable (${modelsRes.status})`);
  }
  cached = {
    blocks: await blocksRes.json(),
    models: await modelsRes.json(),
    assetBase: base,
  };
  return cached;
}

/**
 * Connected wall/blocks are stored in prefabs as e.g.
 * `*Wood_Village_Wall_Yellow_Full_State_Definitions_Middle`.
 * Resolve those to the base block + state textures when present.
 * @param {string} name
 */
export function getBlockDef(name) {
  if (!cached) {
    return null;
  }
  const id = String(name || "");
  if (!id) {
    return null;
  }
  const direct = cached.blocks[id];
  if (direct) {
    return direct;
  }

  // Connected / merged states: Bottom|Middle|Top walls, "Block" double-slabs, etc.
  const stateMatch = id.match(/^\*?(.+)_State_Definitions_([A-Za-z0-9]+)$/i);
  if (stateMatch) {
    const baseId = stateMatch[1];
    const stateRaw = stateMatch[2];
    const stateName =
      stateRaw.charAt(0).toUpperCase() + stateRaw.slice(1).toLowerCase();
    const base = cached.blocks[baseId];
    if (!base) {
      return null;
    }
    const stateTextures =
      base.states?.[stateName] ||
      base.states?.[stateRaw] ||
      base.states?.[stateRaw.charAt(0).toUpperCase() + stateRaw.slice(1)];
    if (stateTextures) {
      return {
        ...base,
        drawType: "Cube",
        textures: stateTextures,
        // State variants are cube skins of the connected block, not the custom model.
        customModel: null,
        customModelTexture: null,
      };
    }
    // Fallback: at least show the full/base wall block instead of skipping.
    return base;
  }

  if (id.startsWith("*")) {
    return cached.blocks[id.slice(1)] || null;
  }
  return null;
}

/**
 * @param {string} modelId
 */
export function getModelDef(modelId) {
  if (!cached) {
    return null;
  }
  return cached.models[modelId] || null;
}

export function getAssetBase() {
  return cached?.assetBase || DEFAULT_ASSET_BASE;
}

/**
 * Resolve a catalog texture/model path to a fetchable URL under /hytale-assets.
 * @param {string} assetPath
 */
export function assetUrl(assetPath) {
  const base = getAssetBase();
  let p = String(assetPath || "").replace(/\\/g, "/").replace(/^\/+/, "");
  if (p.startsWith("BlockTextures/")) {
    return `${base}/Common/${p}`;
  }
  if (p.startsWith("Common/")) {
    return `${base}/${p}`;
  }
  if (p.startsWith("Blocks/") || p.startsWith("NPC/") || p.startsWith("Characters/") || p.startsWith("Items/")) {
    return `${base}/Common/${p}`;
  }
  return `${base}/Common/${p}`;
}

/**
 * Resolve cube face texture paths for a block def.
 * @param {any} def
 * @returns {{ up: string|null, down: string|null, north: string|null, south: string|null, east: string|null, west: string|null }}
 */
export function resolveCubeFaces(def) {
  const t = def?.textures || {};
  const all = t.All || null;
  const upDown = t.UpDown || null;
  const sides = t.Sides || null;
  const up = t.Up || upDown || all;
  const down = t.Down || upDown || all;
  const north = t.North || t.back || sides || all;
  const south = t.South || t.front || sides || all;
  const east = t.East || t.Right || sides || all;
  const west = t.West || t.Left || sides || all;
  return { up, down, north, south, east, west };
}
