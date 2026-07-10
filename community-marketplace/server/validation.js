/** @typedef {{ id: string, displayName: string, creatorUuid: string, creatorName: string, styleId?: string, blockIdVersion: number, prefabBytes: number, version: string, approvedAt: string }} ApprovedEntry */

export const MAX_PREFAB_BYTES = 32 * 1024 * 1024;
export const MAX_BUILDING_JSON_BYTES = 512 * 1024;
export const MAX_ICON_BYTES = 2 * 1024 * 1024;
export const COMMUNITY_ID_PREFIX = "plot_community_";

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
