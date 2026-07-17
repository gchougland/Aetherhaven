/** @typedef {{ id: string, displayName: string, description?: string, creatorUuid: string, creatorName: string, styleId?: string, tags?: string[], coverScreenshotId?: string, blockIdVersion: number, prefabBytes: number, version: string, approvedAt: string }} ApprovedEntry */
/** @typedef {ApprovedEntry & { upvoteCount?: number, downloadCount?: number, userHasUpvoted?: boolean, compatible?: boolean, coverScreenshotId?: string, usesCoverImage?: boolean, iconUrl?: string, coverImageUrl?: string, buildingUrl?: string, prefabUrl?: string }} CatalogEntry */

export const MAX_PREFAB_BYTES = 32 * 1024 * 1024;
export const MAX_BUILDING_JSON_BYTES = 512 * 1024;
export const MAX_ICON_BYTES = 2 * 1024 * 1024;
export const MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024;
export const MAX_SCREENSHOTS_PER_OWNER = 6;
export const ALLOWED_SCREENSHOT_MIME = Object.freeze(["image/jpeg", "image/png", "image/webp"]);
export const COMMUNITY_ID_PREFIX = "plot_community_";

const SCREENSHOT_EXT_BY_MIME = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

/**
 * @param {string} mimeType
 * @returns {string | null}
 */
export function screenshotExtForMime(mimeType) {
  return SCREENSHOT_EXT_BY_MIME[String(mimeType || "").toLowerCase()] || null;
}

/**
 * @param {string} mimeType
 */
export function isAllowedScreenshotMime(mimeType) {
  return ALLOWED_SCREENSHOT_MIME.includes(String(mimeType || "").toLowerCase());
}

/** Human-readable max size for client/server error messages. */
export function formatScreenshotMaxSizeLabel() {
  return "5 MB";
}

const ID_PATTERN = /^plot_community_[a-z0-9_]{8,80}$/;

/**
 * @param {string} raw
 */
export function normalizeCommunityId(raw) {
  const id = String(raw || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .replace(/_+/g, "_");
  if (!id.startsWith(COMMUNITY_ID_PREFIX)) {
    return null;
  }
  return ID_PATTERN.test(id) ? id : null;
}

/**
 * @param {string} creatorUuid
 * @param {string} slug
 */
export function proposeCommunityId(creatorUuid, slug) {
  const short = String(creatorUuid || "")
    .replace(/-/g, "")
    .slice(0, 8)
    .toLowerCase();
  const cleanSlug = String(slug || "building")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 40);
  const id = `${COMMUNITY_ID_PREFIX}${short}_${cleanSlug || "building"}`;
  return ID_PATTERN.test(id) ? id : `${COMMUNITY_ID_PREFIX}${short}_building`;
}

/**
 * Validates an uploaded building before a community catalog id is assigned.
 * Local plot creator ids (e.g. plot_my_house) are accepted here.
 *
 * @param {unknown} building
 * @param {number} prefabBlockIdVersion
 */
export function validateSubmissionBuilding(building, prefabBlockIdVersion) {
  if (!building || typeof building !== "object") {
    return "building_json_invalid";
  }
  const b = /** @type {Record<string, unknown>} */ (building);
  if (typeof b.displayName !== "string" || !b.displayName.trim()) {
    return "display_name_missing";
  }
  if (typeof b.prefabPath !== "string" || !b.prefabPath.trim()) {
    return "prefab_path_missing";
  }
  if (!b.plotTokenItemId) {
    return "plot_token_missing";
  }
  if (prefabBlockIdVersion < 1) {
    return "block_id_version_missing";
  }
  const requiredMods = b.requiredMods === undefined ? [] : b.requiredMods;
  if (!Array.isArray(requiredMods)) {
    return "required_mods_invalid";
  }
  if (requiredMods.length > MAX_REQUIRED_MODS) {
    return "required_mods_invalid";
  }
  const normalizedRequiredMods = normalizeRequiredMods(requiredMods);
  if (normalizedRequiredMods.length !== requiredMods.length) {
    return "required_mods_invalid";
  }
  return null;
}

/**
 * Validates a building that will be published under a community catalog id.
 *
 * @param {unknown} building
 * @param {number} prefabBlockIdVersion
 */
export function validateBuildingDefinition(building, prefabBlockIdVersion) {
  const submissionError = validateSubmissionBuilding(building, prefabBlockIdVersion);
  if (submissionError) {
    return submissionError;
  }
  const b = /** @type {Record<string, unknown>} */ (building);
  const id = typeof b.id === "string" ? b.id.trim() : "";
  if (!normalizeCommunityId(id)) {
    return "id_invalid";
  }
  return null;
}

/**
 * @param {Record<string, unknown>} building
 * @param {string} creatorUuid
 */
export function assignCommunityCatalogId(building, creatorUuid) {
  const existing = typeof building.id === "string" ? normalizeCommunityId(building.id) : null;
  if (existing) {
    building.id = existing;
    // Installed prefabs are always {id}.prefab.json — keep building JSON in sync.
    building.prefabPath = `${existing}.prefab.json`;
    return existing;
  }
  const displayName = typeof building.displayName === "string" ? building.displayName.trim() : "";
  const localId = typeof building.id === "string" ? building.id.trim() : "";
  const slugSource =
    displayName ||
    localId.replace(/^plot_/, "").replace(/[^a-z0-9]+/gi, "_").replace(/^_+|_+$/g, "") ||
    "building";
  const id = proposeCommunityId(creatorUuid, slugSource);
  building.id = id;
  building.prefabPath = `${id}.prefab.json`;
  return id;
}

/**
 * @param {Buffer} prefabBuf
 */
export function readPrefabBlockIdVersion(prefabBuf) {
  try {
    const text = prefabBuf.toString("utf8");
    const json = JSON.parse(text);
    const v = json?.blockIdVersion;
    return typeof v === "number" && Number.isFinite(v) ? v : 0;
  } catch {
    return 0;
  }
}

/**
 * @param {number} manifestVersion
 * @param {number} clientVersion
 */
export function isBlockIdCompatible(manifestVersion, clientVersion) {
  if (!manifestVersion || !clientVersion) {
    return true;
  }
  return manifestVersion === clientVersion;
}

/**
 * @param {number} bytes
 * @param {number} max
 * @param {string} label
 */
export function assertSize(bytes, max, label) {
  if (bytes > max) {
    throw new Error(`${label}_too_large`);
  }
}

export const MAX_REQUIRED_MODS = 32;
export const MAX_REQUIRED_MOD_ID_LENGTH = 128;
export const MAX_REQUIRED_MOD_NAME_LENGTH = 80;

/**
 * @param {unknown} value
 * @returns {Array<{ id: string, name: string }>}
 */
export function normalizeRequiredMods(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  const seen = new Set();
  /** @type {Array<{ id: string, name: string }>} */
  const mods = [];
  for (const raw of value) {
    if (!raw || typeof raw !== "object") {
      continue;
    }
    const row = /** @type {Record<string, unknown>} */ (raw);
    const id = typeof row.id === "string" ? row.id.trim().slice(0, MAX_REQUIRED_MOD_ID_LENGTH) : "";
    if (!id || seen.has(id.toLowerCase())) {
      continue;
    }
    seen.add(id.toLowerCase());
    const nameRaw = typeof row.name === "string" ? row.name.trim().slice(0, MAX_REQUIRED_MOD_NAME_LENGTH) : "";
    mods.push({ id, name: nameRaw || id });
    if (mods.length >= MAX_REQUIRED_MODS) {
      break;
    }
  }
  return mods;
}

export const MAX_DISPLAY_NAME_LENGTH = 80;
export const MAX_DESCRIPTION_LENGTH = 2000;
export const MAX_STYLE_ID_LENGTH = 64;
export const MAX_TAG_LENGTH = 32;
export const MAX_TAGS = 16;
export const MAX_MATERIALS = 64;
export const MAX_MATERIAL_ID_LENGTH = 128;

/**
 * @param {unknown} value
 * @returns {string}
 */
export function normalizeEditDescription(value) {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * @param {unknown} value
 * @returns {string[]}
 */
export function normalizeEditTags(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  const seen = new Set();
  const tags = [];
  for (const raw of value) {
    if (typeof raw !== "string") {
      continue;
    }
    const tag = raw.trim().toLowerCase().slice(0, MAX_TAG_LENGTH);
    if (!tag || seen.has(tag)) {
      continue;
    }
    seen.add(tag);
    tags.push(tag);
    if (tags.length >= MAX_TAGS) {
      break;
    }
  }
  return tags;
}

/**
 * Normalizes a style label for marketplace edits.
 * Preserves human-readable names (e.g. "Coastal Ruins"); only trims, collapses
 * whitespace, and strips characters outside letters/digits/spaces/hyphens/underscores.
 *
 * @param {unknown} value
 * @returns {string}
 */
export function normalizeEditStyleId(value) {
  const raw = typeof value === "string" ? value.trim() : "";
  if (!raw) {
    return "misc";
  }
  return (
    raw
      .replace(/\s+/g, " ")
      .replace(/[^a-zA-Z0-9 _-]+/g, "")
      .trim()
      .slice(0, MAX_STYLE_ID_LENGTH) || "misc"
  );
}

/**
 * Validates a website edit payload for name/description/gold/materials (and optional style/tags).
 *
 * @param {unknown} body
 * @param {{ allowStyleAndTags?: boolean }} [options]
 * @returns {{ ok: true, value: {
 *   displayName: string,
 *   description: string,
 *   treasuryGoldCoinCost: number,
 *   materials: Array<{ resourceTypeId?: string, itemId?: string, count: number }>,
 *   styleId?: string,
 *   tags?: string[],
 * } } | { ok: false, error: string, message?: string }}
 */
export function validateBuildingEditPayload(body, options = {}) {
  const allowStyleAndTags = Boolean(options.allowStyleAndTags);
  if (!body || typeof body !== "object") {
    return { ok: false, error: "invalid_body", message: "Request body must be a JSON object." };
  }
  const b = /** @type {Record<string, unknown>} */ (body);

  if (typeof b.displayName !== "string" || !b.displayName.trim()) {
    return { ok: false, error: "display_name_missing", message: "Display name is required." };
  }
  const displayName = b.displayName.trim().slice(0, MAX_DISPLAY_NAME_LENGTH);
  if (!displayName) {
    return { ok: false, error: "display_name_missing", message: "Display name is required." };
  }

  const description = normalizeEditDescription(b.description).slice(0, MAX_DESCRIPTION_LENGTH);

  let treasuryGoldCoinCost = 0;
  if (b.treasuryGoldCoinCost != null && b.treasuryGoldCoinCost !== "") {
    const gold = Number(b.treasuryGoldCoinCost);
    if (!Number.isFinite(gold) || gold < 0 || !Number.isInteger(gold)) {
      return {
        ok: false,
        error: "gold_invalid",
        message: "Gold cost must be a non-negative integer.",
      };
    }
    treasuryGoldCoinCost = gold;
  }

  if (!Array.isArray(b.materials)) {
    return { ok: false, error: "materials_invalid", message: "Materials must be an array." };
  }
  if (b.materials.length > MAX_MATERIALS) {
    return {
      ok: false,
      error: "materials_too_many",
      message: `At most ${MAX_MATERIALS} material rows allowed.`,
    };
  }
  /** @type {Array<{ resourceTypeId?: string, itemId?: string, count: number }>} */
  const materials = [];
  for (const row of b.materials) {
    if (!row || typeof row !== "object") {
      return { ok: false, error: "materials_invalid", message: "Each material row must be an object." };
    }
    const r = /** @type {Record<string, unknown>} */ (row);
    const resourceTypeId =
      typeof r.resourceTypeId === "string" ? r.resourceTypeId.trim().slice(0, MAX_MATERIAL_ID_LENGTH) : "";
    const itemId = typeof r.itemId === "string" ? r.itemId.trim().slice(0, MAX_MATERIAL_ID_LENGTH) : "";
    if ((resourceTypeId && itemId) || (!resourceTypeId && !itemId)) {
      return {
        ok: false,
        error: "materials_invalid",
        message: "Each material needs either resourceTypeId or itemId (not both).",
      };
    }
    const count = Number(r.count);
    if (!Number.isFinite(count) || !Number.isInteger(count) || count < 1) {
      return {
        ok: false,
        error: "materials_invalid",
        message: "Each material count must be an integer >= 1.",
      };
    }
    if (resourceTypeId) {
      materials.push({ resourceTypeId, count });
    } else {
      materials.push({ itemId, count });
    }
  }

  /** @type {{
   *   displayName: string,
   *   description: string,
   *   treasuryGoldCoinCost: number,
   *   materials: Array<{ resourceTypeId?: string, itemId?: string, count: number }>,
   *   styleId?: string,
   *   tags?: string[],
   * }} */
  const value = {
    displayName,
    description,
    treasuryGoldCoinCost,
    materials,
  };

  if (allowStyleAndTags) {
    value.styleId = normalizeEditStyleId(b.styleId);
    value.tags = normalizeEditTags(b.tags);
  }

  return { ok: true, value };
}
