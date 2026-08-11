/** Loads and resolves marketplace block / model catalogs for the prefab viewer. */

const DEFAULT_ASSET_BASE = "/hytale-assets";

/**
 * The catalog changes shape along with this module, and a browser will happily pair new
 * code with a catalog it cached weeks ago, so it is fetched with whatever cache busting
 * query the page imported this module with.
 */
const CACHE_BUST = new URL(import.meta.url).search;

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
    fetch(`${base}/catalog/block_catalog.json${CACHE_BUST}`),
    fetch(`${base}/catalog/model_catalog.json${CACHE_BUST}`),
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

const FACE_KEY = /^(All|Up|Down|Sides|UpDown|North|South|East|West|Left|Right)$/i;

/**
 * Older catalogs stored a state as a bare texture face map; newer ones store what the
 * state actually overrides.
 * @param {any} override
 */
function normalizeStateOverride(override) {
  if (!override || typeof override !== "object") {
    return null;
  }
  if (Object.keys(override).some((k) => FACE_KEY.test(k))) {
    return { textures: override };
  }
  return override;
}

/**
 * @param {Record<string, any>|null|undefined} states
 * @param {string} wanted
 */
function findState(states, wanted) {
  if (!states) {
    return null;
  }
  if (states[wanted]) {
    return states[wanted];
  }
  const lower = wanted.toLowerCase();
  for (const [name, override] of Object.entries(states)) {
    if (name.toLowerCase() === lower) {
      return override;
    }
  }
  return null;
}

/**
 * Merge one state over the block it belongs to. Block states and item variants are looked
 * up in whichever map the name came from, then the other, since a build should still draw
 * if an id ever mixes the two spellings.
 * @param {any} base
 * @param {string} stateName
 * @param {boolean} preferVariant
 */
function applyBlockState(base, stateName, preferVariant) {
  const maps = preferVariant ? [base.variants, base.states] : [base.states, base.variants];
  const override = normalizeStateOverride(findState(maps[0], stateName) || findState(maps[1], stateName));
  if (!override) {
    // Unknown state: draw the plain block rather than leaving a hole in the build.
    return base;
  }
  const merged = { ...base, ...override };
  // Only the state's own children are reachable from here, not its siblings.
  merged.states = override.states || null;
  merged.variants = override.variants || null;
  if (override.customModel) {
    merged.customModelTexture = override.customModelTexture || base.customModelTexture || null;
  } else if (override.textures) {
    // A state that just reskins the cube replaces the block's model, it does not wear it.
    merged.customModel = null;
    merged.customModelTexture = null;
  }
  return merged;
}

/**
 * Prefabs store a placed block state two ways: `*BlockId_State_Definitions_Name` for block
 * states (village wall segments, double slabs, roof corners, open doors, crop stages) and
 * `*BlockId_State_Name` for item variants (a filled bucket or watering can). Either may
 * reskin the block, swap its model, or both. State names contain underscores and a few
 * blocks nest a state inside a state, so peel one state off the end and resolve what is
 * left as a block in its own right.
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

  // Block states first: their separator contains the item variant one.
  const stateMatch =
    id.match(/^(\*?.+)_State_Definitions_(.+)$/) || id.match(/^(\*?.+)_State_(.+)$/);
  if (stateMatch) {
    const base = getBlockDef(stateMatch[1]);
    return base ? applyBlockState(base, stateMatch[2], !id.includes("_State_Definitions_")) : null;
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
