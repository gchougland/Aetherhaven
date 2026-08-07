import "dotenv/config";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import cookieParser from "cookie-parser";
import express from "express";
import multer from "multer";
import { createOidc } from "./oauth.js";
import { createSessionMiddleware } from "./sessionStore.js";
import { createStorage } from "./storage.js";
import {
  assertSize,
  formatScreenshotMaxSizeLabel,
  isAllowedScreenshotMime,
  isBlockIdCompatible,
  MAX_BUILDING_JSON_BYTES,
  MAX_ICON_BYTES,
  MAX_PREFAB_BYTES,
  MAX_SCREENSHOT_BYTES,
  MAX_SCREENSHOTS_PER_OWNER,
  normalizeCommunityId,
  readPrefabBlockIdVersion,
  assignCommunityCatalogId,
  screenshotExtForMime,
  validateBuildingEditPayload,
  validateSubmissionBuilding,
  normalizeRequiredMods,
} from "./validation.js";
import { createSubmissionRateLimit } from "./submissionRateLimit.js";
import { createVoteRateLimit } from "./voteRateLimit.js";
import { createDownloadRateLimit } from "./downloadRateLimit.js";
import { createVotes } from "./votes.js";
import { createFavorites } from "./favorites.js";
import { createDownloads } from "./downloads.js";
import {
  applyCoverScreenshotIfUnset,
  autoSetCoverFromExistingApprovedScreenshots,
  isValidCoverScreenshot,
  readApprovedCoverScreenshotId,
} from "./coverScreenshots.js";
import { notifyBuildingApproved, notifyBuildingPending } from "./discordNotify.js";
import { processScreenshot, ScreenshotProcessingError } from "./imageProcessing.js";
import {
  AdminRawFileError,
  applyAdminRawMetadata,
  atomicWriteText,
  projectAdminRawMetadata,
  validateAdminRawFilePair,
} from "./adminRawFiles.js";
import {
  listSlimSubmissionsForCreator,
  resolveOwnerSubmissionFile,
  updateOwnedSubmission,
} from "./submissionUpdates.js";
import { createSupportBundles } from "./supportBundles.js";
import { createSupportBundleRateLimit } from "./supportBundleRateLimit.js";
import {
  buildRobotsTxt,
  buildSitemapXml,
  renderWikiTopic,
} from "./wikiRender.js";
// sharp is loaded lazily inside processScreenshot so native-lib failures
// do not crash startup before Railway's /api/v1/health check can succeed.

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 3847);
const IS_PRODUCTION = process.env.NODE_ENV === "production" || Boolean(process.env.RAILWAY_ENVIRONMENT);
const SESSION_SECRET = resolveRequiredSecret("SESSION_SECRET", IS_PRODUCTION);
/** Optional — only for machine-to-machine admin API; the mod does not use this. */
const ADMIN_API_KEY = resolveOptionalSecret("API_KEY");
const ADMIN_UUIDS = new Set(
  (process.env.ADMIN_HYTALE_UUIDS || "")
    .split(",")
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean)
);

const publicBaseUrl = resolvePublicBaseUrl();
const ADS_ENABLED = String(process.env.ADS_ENABLED || "")
  .trim()
  .toLowerCase() === "true";
const ADSENSE_CLIENT_ID = (process.env.ADSENSE_CLIENT_ID || "").trim();
const ADSENSE_SLOT_BROWSE = (process.env.ADSENSE_SLOT_BROWSE || "").trim();
const ADSENSE_SLOT_WIKI = (process.env.ADSENSE_SLOT_WIKI || "").trim();
const dataDir = process.env.DATA_DIR || path.join(__dirname, "..", "data");
const storage = createStorage(dataDir);
const votes = createVotes(dataDir);
const favorites = createFavorites(dataDir);
const downloads = createDownloads(dataDir);
const supportBundles = createSupportBundles(dataDir);
const supportBundleRateLimit = createSupportBundleRateLimit();
const oidc = createOidc({
  issuer: process.env.HYTALE_OIDC_ISSUER || "https://connect.accounts.hytale.com",
  clientId: process.env.HYTALE_OIDC_CLIENT_ID || "",
  clientSecret: process.env.HYTALE_OIDC_CLIENT_SECRET || "",
  redirectUri: process.env.HYTALE_OIDC_REDIRECT_URI || `${publicBaseUrl}/auth/callback`,
});

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_PREFAB_BYTES },
});

const screenshotUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_SCREENSHOT_BYTES },
});

const MAX_SUPPORT_BUNDLE_BYTES = Number(process.env.SUPPORT_BUNDLE_MAX_BYTES || 50 * 1024 * 1024);

const supportBundleUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_SUPPORT_BUNDLE_BYTES },
});

const adminRawJsonUpload = express.text({
  type: "text/plain",
  limit: MAX_PREFAB_BYTES,
});

const app = express();
app.set("trust proxy", 1);
app.use(cookieParser());
app.use(
  createSessionMiddleware({
    secret: SESSION_SECRET,
    isProduction: IS_PRODUCTION,
    dataDir,
  })
);
app.use(express.json({ limit: "1mb" }));
app.use(express.urlencoded({ extended: true }));

function resolvePublicBaseUrl() {
  if (process.env.PUBLIC_BASE_URL) {
    return process.env.PUBLIC_BASE_URL.replace(/\/$/, "");
  }
  if (process.env.RAILWAY_PUBLIC_DOMAIN) {
    return `https://${process.env.RAILWAY_PUBLIC_DOMAIN}`;
  }
  return `http://localhost:${PORT}`;
}

function resolveOptionalSecret(name) {
  const value = process.env[name];
  if (value && value.trim() && !isWeakPlaceholder(value)) {
    return value.trim();
  }
  return "";
}

function resolveRequiredSecret(name, required) {
  const value = process.env[name];
  if (value && value.trim() && !isWeakPlaceholder(value)) {
    return value.trim();
  }
  if (required) {
    console.error(`[startup] Missing or weak ${name}. Set it in Railway Variables (see docs/RailwayDeployment.md).`);
    process.exit(1);
  }
  return "dev-only-local-session-secret-not-for-production";
}

function isWeakPlaceholder(value) {
  const v = value.trim().toLowerCase();
  return (
    v === "change-me-mod-server-key" ||
    v === "change-me-long-random-session-secret" ||
    v.startsWith("dev-only-")
  );
}

function requireAdminApiKey(req, res, next) {
  if (!ADMIN_API_KEY) {
    res.status(503).json({ error: "admin_api_disabled" });
    return;
  }
  const key = req.get("X-Api-Key");
  if (!key || key !== ADMIN_API_KEY) {
    res.status(401).json({ error: "unauthorized" });
    return;
  }
  next();
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** In-game plot crafting moderation — trusted server-side mod sends the opening player's Hytale profile UUID. */
function requireModerator(req, res, next) {
  if (ADMIN_UUIDS.size === 0) {
    res.status(503).json({ error: "moderation_disabled" });
    return;
  }
  const uuid = String(req.get("X-Player-Uuid") || "")
    .trim()
    .toLowerCase();
  if (!UUID_RE.test(uuid) || !ADMIN_UUIDS.has(uuid)) {
    res.status(403).json({ error: "forbidden" });
    return;
  }
  next();
}

function pendingSubmissionFile(submissionId, fileName) {
  const dir = storage.submissionDir(submissionId, "pending");
  const file = path.join(dir, fileName);
  if (!fs.existsSync(dir) || !fs.existsSync(file)) {
    return null;
  }
  return file;
}

/** Enrich pending meta for website/admin review (icon URL + description). */
function enrichPendingSubmission(meta) {
  if (!meta || !meta.submissionId) {
    return meta;
  }
  const submissionId = meta.submissionId;
  const buildingPath = path.join(storage.submissionDir(submissionId, "pending"), "building.json");
  const description =
    normalizeDescription(meta.description) || readBuildingDescription(buildingPath);
  const iconFile = pendingSubmissionFile(submissionId, "icon.png");
  const enriched = { ...meta };
  const requiredModsFromMeta = normalizeRequiredMods(meta.requiredMods);
  enriched.requiredMods = requiredModsFromMeta.length
    ? requiredModsFromMeta
    : readBuildingRequiredMods(buildingPath);
  enriched.materials = readBuildingMaterials(buildingPath);
  enriched.treasuryGoldCoinCost = readBuildingGoldCost(buildingPath);
  if (description) {
    enriched.description = description;
  }
  if (iconFile) {
    enriched.iconUrl = `/api/admin/submissions/${encodeURIComponent(submissionId)}/icon.png`;
  }
  return enriched;
}

function listEnrichedPending() {
  return storage.listPending().map(enrichPendingSubmission);
}

const submissionRateLimit = createSubmissionRateLimit({
  maxPerPlayer: Number(process.env.SUBMISSION_MAX_PER_PLAYER_PER_DAY || 10),
  maxPerIp: Number(process.env.SUBMISSION_MAX_PER_IP_PER_HOUR || 30),
});

const voteRateLimit = createVoteRateLimit({
  maxPerUser: Number(process.env.VOTE_MAX_PER_USER_PER_HOUR || 60),
});

const downloadRateLimit = createDownloadRateLimit({
  maxPerIp: Number(process.env.DOWNLOAD_MAX_PER_IP_PER_HOUR || 120),
  maxPerBuildingIp: Number(process.env.DOWNLOAD_MAX_PER_BUILDING_IP_PER_HOUR || 5),
  maxPerPlayer: Number(process.env.DOWNLOAD_MAX_PER_PLAYER_PER_HOUR || 60),
});

function readBuildingJson(buildingPath) {
  try {
    if (!fs.existsSync(buildingPath)) {
      return null;
    }
    return JSON.parse(fs.readFileSync(buildingPath, "utf8"));
  } catch {
    return null;
  }
}

function readBuildingDescription(buildingPath) {
  const building = readBuildingJson(buildingPath);
  return typeof building?.description === "string" ? building.description.trim() : "";
}

/**
 * @param {string} buildingPath
 * @returns {number}
 */
function readBuildingGoldCost(buildingPath) {
  const building = readBuildingJson(buildingPath);
  const gold = Number(building?.treasuryGoldCoinCost);
  return Number.isFinite(gold) && gold > 0 ? Math.floor(gold) : 0;
}

/**
 * @param {string} buildingPath
 * @returns {Array<{ resourceTypeId?: string, itemId?: string, count: number }>}
 */
function readBuildingMaterials(buildingPath) {
  const building = readBuildingJson(buildingPath);
  if (!Array.isArray(building?.materials)) {
    return [];
  }
  /** @type {Array<{ resourceTypeId?: string, itemId?: string, count: number }>} */
  const materials = [];
  for (const row of building.materials) {
    if (!row || typeof row !== "object") {
      continue;
    }
    const count = Number(row.count);
    if (!Number.isFinite(count) || count < 1) {
      continue;
    }
    const resourceTypeId = typeof row.resourceTypeId === "string" ? row.resourceTypeId.trim() : "";
    const itemId = typeof row.itemId === "string" ? row.itemId.trim() : "";
    if (resourceTypeId && !itemId) {
      materials.push({ resourceTypeId, count: Math.floor(count) });
    } else if (itemId && !resourceTypeId) {
      materials.push({ itemId, count: Math.floor(count) });
    }
  }
  return materials;
}

/**
 * @param {string} buildingPath
 * @returns {Array<{ id: string, name: string }>}
 */
function readBuildingRequiredMods(buildingPath) {
  const building = readBuildingJson(buildingPath);
  return normalizeRequiredMods(building?.requiredMods);
}

/**
 * Apply editable fields onto an in-memory building.json object.
 * @param {Record<string, unknown>} building
 * @param {{
 *   displayName: string,
 *   description: string,
 *   treasuryGoldCoinCost: number,
 *   materials: Array<{ resourceTypeId?: string, itemId?: string, count: number }>,
 *   styleId?: string,
 *   tags?: string[],
 * }} edit
 * @param {{ allowStyleAndTags?: boolean }} [options]
 */
function applyBuildingEditFields(building, edit, options = {}) {
  building.displayName = edit.displayName;
  if (edit.description) {
    building.description = edit.description;
  } else {
    delete building.description;
  }
  if (edit.treasuryGoldCoinCost > 0) {
    building.treasuryGoldCoinCost = edit.treasuryGoldCoinCost;
  } else {
    delete building.treasuryGoldCoinCost;
  }
  building.materials = edit.materials;
  if (options.allowStyleAndTags) {
    if (edit.styleId) {
      building.styleId = edit.styleId;
    }
    if (edit.tags && edit.tags.length) {
      building.tags = edit.tags;
    } else {
      delete building.tags;
    }
  }
}

function normalizeDescription(value) {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * @param {unknown} value
 * @returns {string[]}
 */
function normalizeTags(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  const seen = new Set();
  const tags = [];
  for (const raw of value) {
    if (typeof raw !== "string") {
      continue;
    }
    const tag = raw.trim().toLowerCase();
    if (!tag || seen.has(tag)) {
      continue;
    }
    seen.add(tag);
    tags.push(tag);
  }
  return tags;
}

/**
 * @param {string} buildingPath
 * @returns {string[]}
 */
function readBuildingTags(buildingPath) {
  try {
    if (!fs.existsSync(buildingPath)) {
      return [];
    }
    const building = JSON.parse(fs.readFileSync(buildingPath, "utf8"));
    return normalizeTags(building.tags);
  } catch {
    return [];
  }
}

/**
 * @param {unknown} value
 * @returns {string|string[]|undefined}
 */
function normalizeCountsAsConstructionId(value) {
  if (typeof value === "string") {
    const id = value.trim();
    return id ? id : undefined;
  }
  if (!Array.isArray(value)) {
    return undefined;
  }
  const ids = [];
  const seen = new Set();
  for (const raw of value) {
    if (typeof raw !== "string") {
      continue;
    }
    const id = raw.trim();
    if (!id || seen.has(id.toLowerCase())) {
      continue;
    }
    seen.add(id.toLowerCase());
    ids.push(id);
  }
  if (!ids.length) {
    return undefined;
  }
  return ids.length === 1 ? ids[0] : ids;
}

/**
 * @param {string} buildingPath
 * @returns {{ decorationPlot: boolean, countsAsConstructionId: string|string[]|undefined }}
 */
function readBuildingTypeMeta(buildingPath) {
  try {
    if (!fs.existsSync(buildingPath)) {
      return { decorationPlot: false, countsAsConstructionId: undefined };
    }
    const building = JSON.parse(fs.readFileSync(buildingPath, "utf8"));
    const id = String(building.id || "").trim();
    const decorationPlot =
      Boolean(building.decorationPlot) || id.toLowerCase().startsWith("plot_decoration");
    const countsAsConstructionId = normalizeCountsAsConstructionId(building.countsAsConstructionId);
    return { decorationPlot, countsAsConstructionId };
  } catch {
    return { decorationPlot: false, countsAsConstructionId: undefined };
  }
}

function buildManifestEntry(id, meta, prefabBytes) {
  const entry = {
    id,
    displayName: meta.displayName,
    creatorUuid: meta.creatorUuid,
    creatorName: meta.creatorName,
    styleId: meta.styleId || "misc",
    tags: normalizeTags(meta.tags),
    decorationPlot: Boolean(meta.decorationPlot),
    blockIdVersion: meta.blockIdVersion,
    prefabBytes,
    version: meta.version || "1",
    approvedAt: meta.approvedAt,
  };
  const countsAsConstructionId = normalizeCountsAsConstructionId(meta.countsAsConstructionId);
  if (countsAsConstructionId) {
    entry.countsAsConstructionId = countsAsConstructionId;
  }
  const description = normalizeDescription(meta.description);
  if (description) {
    entry.description = description;
  }
  const coverScreenshotId = String(meta.coverScreenshotId || "").trim();
  if (coverScreenshotId) {
    entry.coverScreenshotId = coverScreenshotId;
  }
  const requiredMods = normalizeRequiredMods(meta.requiredMods);
  if (requiredMods.length) {
    entry.requiredMods = requiredMods;
  }
  return entry;
}

/** Keeps the marketplace card cover when approving an update to an already published building. */
function resolvePreservedCoverScreenshotId(buildingId) {
  const candidates = [];
  const fromMeta = readApprovedCoverScreenshotId(storage, buildingId);
  if (fromMeta) {
    candidates.push(fromMeta);
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((candidate) => candidate.id === buildingId);
  const fromManifest = String(entry?.coverScreenshotId || "").trim();
  if (fromManifest) {
    candidates.push(fromManifest);
  }
  for (const coverScreenshotId of candidates) {
    if (isValidCoverScreenshot(storage, buildingId, coverScreenshotId)) {
      return coverScreenshotId;
    }
  }
  return "";
}

/**
 * @param {string} buildingId
 * @param {string} [coverScreenshotId]
 * @returns {{ iconUrl: string, coverImageUrl: string, coverScreenshotId: string, usesCoverImage: boolean }}
 */
function resolveCardImage(buildingId, coverScreenshotId) {
  const iconUrl = `/api/v1/buildings/${encodeURIComponent(buildingId)}/icon.png`;
  const coverId = String(coverScreenshotId || "").trim() || readApprovedCoverScreenshotId(storage, buildingId);
  if (!isValidCoverScreenshot(storage, buildingId, coverId)) {
    return { iconUrl, coverImageUrl: "", coverScreenshotId: "", usesCoverImage: false };
  }
  return {
    // In-game craft bench always uses the token icon; cover is website-only.
    iconUrl,
    coverImageUrl: withScreenshotVariant(
      `/api/buildings/${encodeURIComponent(buildingId)}/screenshots/${encodeURIComponent(coverId)}`,
      "card"
    ),
    coverScreenshotId: coverId,
    usesCoverImage: true,
  };
}

/**
 * @param {string} buildingId
 * @param {string} coverScreenshotId empty string clears
 * @param {{ profileUuid: string, profileUsername: string }} webUser
 * @param {{ asAdmin?: boolean }} [options]
 */
function setBuildingCoverScreenshot(buildingId, coverScreenshotId, webUser, options = {}) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const paths = storage.approvedPaths(id);
  if (!fs.existsSync(paths.meta)) {
    return { status: 404, body: { error: "not_found" } };
  }
  const approvedMeta = JSON.parse(fs.readFileSync(paths.meta, "utf8"));
  if (!options.asAdmin && !isOwnedByWebUser(approvedMeta, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }

  const nextCover = String(coverScreenshotId || "").trim();
  if (nextCover && !isValidCoverScreenshot(storage, id, nextCover)) {
    return {
      status: 400,
      body: { error: "invalid_cover", message: "Cover must be an approved screenshot for this build." },
    };
  }

  if (nextCover) {
    approvedMeta.coverScreenshotId = nextCover;
  } else {
    delete approvedMeta.coverScreenshotId;
  }
  fs.writeFileSync(paths.meta, JSON.stringify(approvedMeta, null, 2));

  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (entry) {
    if (nextCover) {
      entry.coverScreenshotId = nextCover;
    } else {
      delete entry.coverScreenshotId;
    }
    storage.writeManifest(manifest);
  }

  return {
    status: 200,
    body: {
      id,
      coverScreenshotId: nextCover || null,
      ...resolveCardImage(id, nextCover),
    },
  };
}

/**
 * Clear cover references if the deleted/rejected screenshot was the cover.
 * @param {object} shotMeta
 */
function clearCoverIfScreenshotRemoved(shotMeta) {
  if (!shotMeta || shotMeta.ownerKind !== "approved" || !shotMeta.ownerId) {
    return;
  }
  const buildingId = shotMeta.ownerId;
  const paths = storage.approvedPaths(buildingId);
  if (!fs.existsSync(paths.meta)) {
    return;
  }
  const approvedMeta = JSON.parse(fs.readFileSync(paths.meta, "utf8"));
  if (approvedMeta.coverScreenshotId !== shotMeta.screenshotId) {
    return;
  }
  delete approvedMeta.coverScreenshotId;
  fs.writeFileSync(paths.meta, JSON.stringify(approvedMeta, null, 2));
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === buildingId);
  if (entry?.coverScreenshotId === shotMeta.screenshotId) {
    delete entry.coverScreenshotId;
    storage.writeManifest(manifest);
  }
}

function approveSubmission(submissionId, requestedId, requiredModsOverride) {
  const meta = storage.loadSubmissionMeta(submissionId, "pending");
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  const pendingDir = storage.submissionDir(submissionId, "pending");
  const id = normalizeCommunityId(requestedId || meta.proposedId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  let normalizedRequiredModsOverride = null;
  if (requiredModsOverride !== undefined) {
    normalizedRequiredModsOverride = normalizeRequiredMods(requiredModsOverride);
    if (!Array.isArray(requiredModsOverride) || normalizedRequiredModsOverride.length !== requiredModsOverride.length) {
      return { status: 400, body: { error: "required_mods_invalid" } };
    }
  }

  const approved = storage.approvedPaths(id);
  const wasAlreadyPublished =
    fs.existsSync(approved.meta) ||
    (storage.readManifest().entries || []).some((entry) => entry.id === id);
  const preservedCoverScreenshotId = resolvePreservedCoverScreenshotId(id);
  fs.mkdirSync(approved.dir, { recursive: true });
  fs.copyFileSync(path.join(pendingDir, "building.json"), approved.building);
  fs.copyFileSync(path.join(pendingDir, "prefab.prefab.json"), approved.prefab);
  const pendingIcon = path.join(pendingDir, "icon.png");
  if (fs.existsSync(pendingIcon)) {
    fs.copyFileSync(pendingIcon, approved.icon);
  }

  const building = JSON.parse(fs.readFileSync(approved.building, "utf8"));
  building.id = id;
  // Match client install layout: Community/.../Prefabs/{id}.prefab.json
  building.prefabPath = `${id}.prefab.json`;
  if (normalizedRequiredModsOverride !== null) {
    building.requiredMods = normalizedRequiredModsOverride;
  }
  fs.writeFileSync(approved.building, JSON.stringify(building, null, 2));

  const buildingTags = normalizeTags(building.tags);
  const buildingRequiredMods = normalizeRequiredMods(building.requiredMods);
  const typeMeta = readBuildingTypeMeta(approved.building);
  const approvedMeta = {
    ...meta,
    id,
    description: normalizeDescription(building.description) || normalizeDescription(meta.description),
    tags: buildingTags.length ? buildingTags : normalizeTags(meta.tags),
    decorationPlot: typeMeta.decorationPlot,
    requiredMods: buildingRequiredMods.length
      ? buildingRequiredMods
      : normalizeRequiredMods(meta.requiredMods),
    status: "approved",
    approvedAt: new Date().toISOString(),
    version: meta.version || "1",
  };
  approvedMeta.requiredMods = normalizeRequiredMods(approvedMeta.requiredMods);
  if (typeMeta.countsAsConstructionId) {
    approvedMeta.countsAsConstructionId = typeMeta.countsAsConstructionId;
  } else {
    delete approvedMeta.countsAsConstructionId;
  }
  if (preservedCoverScreenshotId) {
    approvedMeta.coverScreenshotId = preservedCoverScreenshotId;
  }
  fs.writeFileSync(approved.meta, JSON.stringify(approvedMeta, null, 2));

  const prefabBytes = fs.statSync(approved.prefab).size;
  const manifest = storage.readManifest();
  manifest.entries = manifest.entries.filter((e) => e.id !== id);
  manifest.entries.push(buildManifestEntry(id, approvedMeta, prefabBytes));
  manifest.version = (manifest.version || 0) + 1;
  storage.writeManifest(manifest);

  fs.rmSync(pendingDir, { recursive: true, force: true });
  storage.reassignScreenshotsToApproved(submissionId, id);
  autoSetCoverFromExistingApprovedScreenshots(storage, id);
  if (!wasAlreadyPublished) {
    notifyBuildingApproved({
      publicBaseUrl,
      id,
      displayName: approvedMeta.displayName,
      description: approvedMeta.description,
      creatorName: approvedMeta.creatorName,
      tags: approvedMeta.tags,
      styleId: approvedMeta.styleId,
      approvedAt: approvedMeta.approvedAt,
      hasIcon: fs.existsSync(approved.icon),
    }).catch(() => {});
  }
  return { status: 200, body: { id, status: "approved" } };
}

function rejectSubmission(submissionId, reason) {
  const pendingDir = storage.submissionDir(submissionId, "pending");
  if (!fs.existsSync(pendingDir)) {
    return { status: 404, body: { error: "not_found" } };
  }
  storage.deleteScreenshotsForOwner("pending", submissionId);
  const rejectedDir = storage.submissionDir(submissionId, "rejected");
  fs.mkdirSync(path.dirname(rejectedDir), { recursive: true });
  fs.renameSync(pendingDir, rejectedDir);
  const meta = storage.loadSubmissionMeta(submissionId, "rejected") || {};
  meta.status = "rejected";
  meta.rejectedAt = new Date().toISOString();
  meta.reason = reason || "rejected";
  fs.writeFileSync(path.join(rejectedDir, "meta.json"), JSON.stringify(meta, null, 2));
  return { status: 200, body: { submissionId, status: "rejected" } };
}

function deleteApprovedBuilding(buildingId) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const manifest = storage.readManifest();
  const hadEntry = manifest.entries.some((e) => e.id === id);
  if (!hadEntry) {
    return { status: 404, body: { error: "not_found" } };
  }
  const approvedDir = storage.approvedPaths(id).dir;
  if (fs.existsSync(approvedDir)) {
    fs.rmSync(approvedDir, { recursive: true, force: true });
  }
  storage.deleteScreenshotsForOwner("approved", id);
  manifest.entries = manifest.entries.filter((e) => e.id !== id);
  manifest.version = (manifest.version || 0) + 1;
  storage.writeManifest(manifest);
  votes.removeBuilding(id);
  favorites.removeBuilding(id);
  downloads.removeBuilding(id);
  return { status: 200, body: { id, status: "deleted" } };
}

function sessionProfileUuid(req) {
  return String(req.session?.user?.profile?.uuid || "")
    .trim()
    .toLowerCase();
}

function sessionProfileUsername(req) {
  return String(req.session?.user?.profile?.username || req.session?.user?.sub || "")
    .trim()
    .toLowerCase();
}

function sessionWebUser(req) {
  return {
    profileUuid: sessionProfileUuid(req),
    profileUsername: sessionProfileUsername(req),
  };
}

function inGamePlayerUser(req) {
  return {
    profileUuid: String(req.get("X-Player-Uuid") || "")
      .trim()
      .toLowerCase(),
    profileUsername: String(req.get("X-Player-Name") || "")
      .trim()
      .toLowerCase(),
  };
}

function requireInGamePlayer(req, res, next) {
  const uuid = inGamePlayerUser(req).profileUuid;
  if (!uuid || !UUID_RE.test(uuid)) {
    res.status(400).json({ error: "player_uuid_required" });
    return;
  }
  next();
}

function isOwnedByProfile(metaOrEntry, profileUuid) {
  if (!profileUuid) {
    return false;
  }
  return (
    String(metaOrEntry?.creatorUuid || "")
      .trim()
      .toLowerCase() === profileUuid
  );
}

/** Matches OAuth profile UUID or Hytale username (in-game submissions use the game UUID). */
function isOwnedByWebUser(metaOrEntry, webUser) {
  if (isOwnedByProfile(metaOrEntry, webUser.profileUuid)) {
    return true;
  }
  const creatorName = String(metaOrEntry?.creatorName || "")
    .trim()
    .toLowerCase();
  return (
    webUser.profileUsername.length > 0 &&
    creatorName.length > 0 &&
    creatorName === webUser.profileUsername
  );
}

function listSubmissionsForCreator(webUser) {
  const pending = storage
    .listPending()
    .filter((s) => isOwnedByWebUser(s, webUser))
    .map((s) => ({
      kind: "pending",
      ...s,
      screenshots: enrichOwnerScreenshots("pending", s.submissionId, true),
    }));

  const rejected = storage
    .listRejected()
    .filter((s) => isOwnedByWebUser(s, webUser))
    .map((s) => ({ kind: "rejected", ...s }));

  const manifest = storage.readManifest();
  const approved = (manifest.entries || [])
    .filter((e) => isOwnedByWebUser(e, webUser))
    .map((e) => {
      const card = resolveCardImage(e.id, e.coverScreenshotId);
      const paths = storage.approvedPaths(e.id);
      const goldCost = readBuildingGoldCost(paths.building);
      const row = {
        kind: "approved",
        id: e.id,
        displayName: e.displayName,
        status: "approved",
        approvedAt: e.approvedAt,
        version: e.version || "1",
        prefabBytes: e.prefabBytes || 0,
        upvoteCount: votes.getCount(e.id),
        downloadCount: downloads.getCount(e.id),
        creatorUuid: e.creatorUuid,
        coverScreenshotId: card.coverScreenshotId || null,
        usesCoverImage: card.usesCoverImage,
        iconUrl: card.iconUrl,
        coverImageUrl: card.coverImageUrl || null,
        screenshots: enrichOwnerScreenshots("approved", e.id, true),
      };
      if (goldCost > 0) {
        row.treasuryGoldCoinCost = goldCost;
      }
      return row;
    });

  return [...pending, ...approved, ...rejected].sort((a, b) => {
    const dateA = a.submittedAt || a.approvedAt || a.rejectedAt || "";
    const dateB = b.submittedAt || b.approvedAt || b.rejectedAt || "";
    return dateB.localeCompare(dateA);
  });
}

/**
 * @param {"pending"|"approved"} ownerKind
 * @param {string} ownerId
 * @param {boolean} forOwner
 */
function enrichOwnerScreenshots(ownerKind, ownerId, forOwner) {
  return storage.listScreenshotsForOwner(ownerKind, ownerId).map((meta) => {
    const url =
      forOwner || meta.status === "approved"
        ? screenshotImageUrl(meta, forOwner)
        : null;
    return {
      screenshotId: meta.screenshotId,
      status: meta.status,
      bytes: meta.bytes || 0,
      mimeType: meta.mimeType,
      uploadedAt: meta.uploadedAt,
      url,
      cardUrl: url ? withScreenshotVariant(url, "card") : null,
    };
  });
}

/**
 * @param {string} url
 * @param {"full"|"card"} variant
 */
function withScreenshotVariant(url, variant) {
  if (!url || variant !== "card") {
    return url;
  }
  return `${url}${url.includes("?") ? "&" : "?"}variant=card`;
}

/**
 * @param {import("express").Request} req
 * @returns {"full"|"card"}
 */
function parseScreenshotVariant(req) {
  return String(req.query?.variant || "")
    .trim()
    .toLowerCase() === "card"
    ? "card"
    : "full";
}

/**
 * @param {object} meta
 * @param {boolean} forOwner
 */
function screenshotImageUrl(meta, forOwner) {
  if (forOwner && meta.status !== "approved") {
    return `/api/my-screenshots/${encodeURIComponent(meta.screenshotId)}/image`;
  }
  if (meta.ownerKind === "approved") {
    return `/api/buildings/${encodeURIComponent(meta.ownerId)}/screenshots/${encodeURIComponent(meta.screenshotId)}`;
  }
  return `/api/my-screenshots/${encodeURIComponent(meta.screenshotId)}/image`;
}

function withdrawPendingSubmission(submissionId, webUser) {
  const meta = storage.loadSubmissionMeta(submissionId, "pending");
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!isOwnedByWebUser(meta, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }
  storage.deleteScreenshotsForOwner("pending", submissionId);
  const dir = storage.submissionDir(submissionId, "pending");
  if (fs.existsSync(dir)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
  return { status: 200, body: { submissionId, status: "withdrawn" } };
}

function dismissRejectedSubmission(submissionId, webUser) {
  const meta = storage.loadSubmissionMeta(submissionId, "rejected");
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!isOwnedByWebUser(meta, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }
  const dir = storage.submissionDir(submissionId, "rejected");
  if (fs.existsSync(dir)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
  return { status: 200, body: { submissionId, status: "dismissed" } };
}

function removeOwnApprovedBuilding(buildingId, webUser) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (!entry) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!isOwnedByWebUser(entry, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }
  return deleteApprovedBuilding(id);
}

// --- Public API (mod + downloads) ---

app.get("/api/v1/health", (_req, res) => {
  res.json({ ok: true });
});

function sortCatalogEntries(entries) {
  return [...entries].sort((a, b) => {
    const votesA = a.upvoteCount || 0;
    const votesB = b.upvoteCount || 0;
    if (votesB !== votesA) {
      return votesB - votesA;
    }
    const nameA = String(a.displayName || "").toLowerCase();
    const nameB = String(b.displayName || "").toLowerCase();
    const byName = nameA.localeCompare(nameB);
    if (byName !== 0) {
      return byName;
    }
    return String(a.approvedAt || "").localeCompare(String(b.approvedAt || ""));
  });
}

function enrichManifestEntries(manifest, clientBlockIdVersion = 0, userVotes = null, userFavorites = null) {
  const voteCounts = votes.getCounts();
  const downloadCounts = downloads.getCounts();
  return (manifest.entries || []).map((e) => {
    const paths = storage.approvedPaths(e.id);
    let prefabBytes = e.prefabBytes || 0;
    if (!prefabBytes && fs.existsSync(paths.prefab)) {
      prefabBytes = fs.statSync(paths.prefab).size;
    }
    const compatible = isBlockIdCompatible(e.blockIdVersion, clientBlockIdVersion);
    const description = normalizeDescription(e.description) || readBuildingDescription(paths.building);
    const goldCost = readBuildingGoldCost(paths.building);
    const materials = readBuildingMaterials(paths.building);
    const entryTags = normalizeTags(e.tags);
    const tags = entryTags.length ? entryTags : readBuildingTags(paths.building);
    const typeMeta = readBuildingTypeMeta(paths.building);
    const decorationPlot =
      typeof e.decorationPlot === "boolean" ? e.decorationPlot : typeMeta.decorationPlot;
    const countsAsConstructionId =
      normalizeCountsAsConstructionId(e.countsAsConstructionId) ?? typeMeta.countsAsConstructionId;
    const card = resolveCardImage(e.id, e.coverScreenshotId);
    const entry = {
      ...e,
      tags,
      decorationPlot,
      prefabBytes,
      compatible,
      upvoteCount: voteCounts[e.id] || 0,
      downloadCount: downloadCounts[e.id] || 0,
      screenshotCount: storage.listScreenshotsForOwner("approved", e.id, "approved").length,
      coverScreenshotId: card.coverScreenshotId || undefined,
      usesCoverImage: card.usesCoverImage,
      iconUrl: card.iconUrl,
      coverImageUrl: card.coverImageUrl || undefined,
      buildingUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/building.json`,
      prefabUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/prefab.json`,
      materials,
    };
    if (countsAsConstructionId) {
      entry.countsAsConstructionId = countsAsConstructionId;
    } else {
      delete entry.countsAsConstructionId;
    }
    if (goldCost > 0) {
      entry.treasuryGoldCoinCost = goldCost;
    } else {
      delete entry.treasuryGoldCoinCost;
    }
    const requiredModsFromEntry = normalizeRequiredMods(e.requiredMods);
    const requiredMods = requiredModsFromEntry.length
      ? requiredModsFromEntry
      : readBuildingRequiredMods(paths.building);
    entry.requiredMods = requiredMods;
    if (!entry.coverScreenshotId) {
      delete entry.coverScreenshotId;
    }
    if (!entry.coverImageUrl) {
      delete entry.coverImageUrl;
    }
    if (description) {
      entry.description = description;
    } else {
      delete entry.description;
    }
    if (userVotes) {
      entry.userHasUpvoted = userVotes.has(e.id);
    }
    if (userFavorites) {
      entry.userHasFavorited = userFavorites.has(e.id);
    }
    return entry;
  });
}

function profileUuidForManifest(req) {
  const session = sessionProfileUuid(req);
  if (session) {
    return session;
  }
  const inGame = String(req.get("X-Player-Uuid") || "")
    .trim()
    .toLowerCase();
  return UUID_RE.test(inGame) ? inGame : "";
}

function sendManifest(req, res) {
  const clientBlockIdVersion = Number(req.query.blockIdVersion || 0);
  const manifest = storage.readManifest();
  const voterUuid = sessionProfileUuid(req);
  const userVotes = voterUuid ? votes.getUserVotes(voterUuid) : null;
  const profileUuid = profileUuidForManifest(req);
  const userFavorites = profileUuid ? favorites.getUserFavorites(profileUuid) : null;
  const entries = sortCatalogEntries(
    enrichManifestEntries(manifest, clientBlockIdVersion, userVotes, userFavorites)
  );
  res.json({
    version: manifest.version,
    entries,
  });
}

function toggleBuildingFavorite(buildingId, profileUuid) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (!entry) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!profileUuid) {
    return { status: 400, body: { error: "profile_missing" } };
  }
  const result = favorites.toggleFavorite(id, profileUuid);
  return { status: 200, body: result };
}

function toggleBuildingUpvote(buildingId, webUser) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (!entry) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (isOwnedByWebUser(entry, webUser)) {
    return { status: 403, body: { error: "self_upvote_not_allowed" } };
  }
  if (!webUser.profileUuid) {
    return { status: 400, body: { error: "profile_missing" } };
  }
  const result = votes.toggleVote(id, webUser.profileUuid);
  return { status: 200, body: result };
}

function recordBuildingDownload(buildingId) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (!entry) {
    return { status: 404, body: { error: "not_found" } };
  }
  return { status: 200, body: downloads.increment(id) };
}

app.get("/api/v1/manifest", sendManifest);

app.get("/api/v1/buildings/:id/building.json", (req, res) => {
  const id = normalizeCommunityId(req.params.id);
  if (!id) {
    res.status(400).json({ error: "invalid_id" });
    return;
  }
  const file = storage.approvedPaths(id).building;
  if (!fs.existsSync(file)) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.type("application/json").sendFile(file);
});

app.get("/api/v1/buildings/:id/prefab.json", (req, res) => {
  const id = normalizeCommunityId(req.params.id);
  if (!id) {
    res.status(400).json({ error: "invalid_id" });
    return;
  }
  const file = storage.approvedPaths(id).prefab;
  if (!fs.existsSync(file)) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.type("application/json").sendFile(file);
});

app.get("/api/v1/buildings/:id/icon.png", (req, res) => {
  const id = normalizeCommunityId(req.params.id);
  if (!id) {
    res.status(400).json({ error: "invalid_id" });
    return;
  }
  const file = storage.approvedPaths(id).icon;
  if (!fs.existsSync(file)) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.type("image/png").sendFile(file);
});

app.post(
  "/api/v1/submissions",
  submissionRateLimit,
  upload.fields([
    { name: "building", maxCount: 1 },
    { name: "prefab", maxCount: 1 },
    { name: "icon", maxCount: 1 },
  ]),
  (req, res) => {
    try {
      const creatorUuid = String(req.get("X-Player-Uuid") || "").trim().toLowerCase();
      const creatorName = String(req.get("X-Player-Name") || "Unknown").trim();
      if (!creatorUuid || !UUID_RE.test(creatorUuid)) {
        res.status(400).json({ error: "player_uuid_required" });
        return;
      }
      const buildingFile = req.files?.building?.[0];
      const prefabFile = req.files?.prefab?.[0];
      const iconFile = req.files?.icon?.[0];
      if (!buildingFile || !prefabFile) {
        res.status(400).json({ error: "building_and_prefab_required" });
        return;
      }
      assertSize(buildingFile.size, MAX_BUILDING_JSON_BYTES, "building");
      assertSize(prefabFile.size, MAX_PREFAB_BYTES, "prefab");
      if (iconFile) {
        assertSize(iconFile.size, MAX_ICON_BYTES, "icon");
      }

      const building = JSON.parse(buildingFile.buffer.toString("utf8"));
      const blockIdVersion = readPrefabBlockIdVersion(prefabFile.buffer);
      const validationError = validateSubmissionBuilding(building, blockIdVersion);
      if (validationError) {
        res.status(400).json({ error: validationError });
        return;
      }

      const id = assignCommunityCatalogId(building, creatorUuid);
      const requiredMods = normalizeRequiredMods(building.requiredMods);
      building.requiredMods = requiredMods;

      const submissionId = `${id}_${Date.now()}`;
      const dir = storage.submissionDir(submissionId, "pending");
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, "building.json"), JSON.stringify(building, null, 2));
      fs.writeFileSync(path.join(dir, "prefab.prefab.json"), prefabFile.buffer);
      if (iconFile) {
        fs.writeFileSync(path.join(dir, "icon.png"), iconFile.buffer);
      }

      const meta = {
        submissionId,
        proposedId: id,
        displayName: building.displayName,
        description: normalizeDescription(building.description),
        creatorUuid,
        creatorName,
        styleId: building.styleId || "misc",
        tags: normalizeTags(building.tags),
        blockIdVersion,
        status: "pending",
        submittedAt: new Date().toISOString(),
        version: "1",
      };
      if (requiredMods.length) {
        meta.requiredMods = requiredMods;
      }
      fs.writeFileSync(path.join(dir, "meta.json"), JSON.stringify(meta, null, 2));
      notifyBuildingPending({
        publicBaseUrl,
        submissionId,
        proposedId: id,
        displayName: meta.displayName,
        description: meta.description,
        creatorName: meta.creatorName,
        submittedAt: meta.submittedAt,
      }).catch(() => {});
      res.status(201).json({ submissionId, proposedId: id, status: "pending" });
    } catch (e) {
      res.status(400).json({ error: e.message || "submission_failed" });
    }
  }
);

app.get("/api/v1/my-submissions", requireInGamePlayer, (req, res) => {
  const webUser = inGamePlayerUser(req);
  res.json({ submissions: listSlimSubmissionsForCreator(storage, webUser, isOwnedByWebUser) });
});

app.get("/api/v1/my-submissions/:submissionId/building.json", requireInGamePlayer, (req, res) => {
  const file = resolveOwnerSubmissionFile(
    storage,
    req.params.submissionId,
    "building.json",
    inGamePlayerUser(req),
    isOwnedByWebUser,
  );
  if (!file) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.type("application/json").send(fs.readFileSync(file));
});

app.get("/api/v1/my-submissions/:submissionId/prefab.json", requireInGamePlayer, (req, res) => {
  const file = resolveOwnerSubmissionFile(
    storage,
    req.params.submissionId,
    "prefab.prefab.json",
    inGamePlayerUser(req),
    isOwnedByWebUser,
  );
  if (!file) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.type("application/json").send(fs.readFileSync(file));
});

app.get("/api/v1/my-submissions/:submissionId/icon.png", requireInGamePlayer, (req, res) => {
  const file = resolveOwnerSubmissionFile(
    storage,
    req.params.submissionId,
    "icon.png",
    inGamePlayerUser(req),
    isOwnedByWebUser,
  );
  if (!file) {
    res.status(404).end();
    return;
  }
  res.type("image/png").send(fs.readFileSync(file));
});

app.put(
  "/api/v1/submissions/:buildingId",
  submissionRateLimit,
  requireInGamePlayer,
  upload.fields([
    { name: "building", maxCount: 1 },
    { name: "prefab", maxCount: 1 },
    { name: "icon", maxCount: 1 },
  ]),
  (req, res) => {
    const buildingFile = req.files?.building?.[0];
    const prefabFile = req.files?.prefab?.[0];
    const iconFile = req.files?.icon?.[0];
    if (!buildingFile || !prefabFile) {
      res.status(400).json({ error: "building_and_prefab_required" });
      return;
    }
    const player = inGamePlayerUser(req);
    const result = updateOwnedSubmission({
      storage,
      buildingId: req.params.buildingId,
      creatorUuid: player.profileUuid,
      creatorName: String(req.get("X-Player-Name") || "Unknown").trim(),
      buildingFile,
      prefabFile,
      iconFile,
      isOwnedByProfile,
      normalizeDescription,
      normalizeTags,
    });
    if (result.status === 201 && result.body?.action === "created_pending" && !result.body?.isBuildingUpdate) {
      notifyBuildingPending({
        publicBaseUrl,
        submissionId: result.body.submissionId,
        proposedId: result.body.proposedId,
        displayName: result.body.displayName || result.body.proposedId,
        description: result.body.description || "",
        creatorName: String(req.get("X-Player-Name") || "Unknown").trim(),
        submittedAt: new Date().toISOString(),
      }).catch(() => {});
    }
    res.status(result.status).json(result.body);
  },
);

app.get("/api/v1/submissions/pending", requireAdminApiKey, (_req, res) => {
  res.json({ submissions: listEnrichedPending() });
});

app.post("/api/v1/submissions/:submissionId/approve", requireAdminApiKey, (req, res) => {
  const result = approveSubmission(req.params.submissionId, req.body?.id, req.body?.requiredMods);
  res.status(result.status).json(result.body);
});

app.post("/api/v1/submissions/:submissionId/reject", requireAdminApiKey, (req, res) => {
  const result = rejectSubmission(req.params.submissionId, req.body?.reason);
  res.status(result.status).json(result.body);
});

app.post(
  "/api/v1/support-bundles",
  supportBundleRateLimit,
  supportBundleUpload.fields([{ name: "bundle", maxCount: 1 }]),
  (req, res) => {
    const playerUuid = String(req.get("X-Player-Uuid") || "").trim().toLowerCase();
    if (!UUID_RE.test(playerUuid)) {
      res.status(400).json({ error: "missing_player_uuid" });
      return;
    }
    const zipFile = req.files?.bundle?.[0];
    if (!zipFile?.buffer?.length) {
      res.status(400).json({ error: "missing_bundle" });
      return;
    }
    if (zipFile.buffer.length > MAX_SUPPORT_BUNDLE_BYTES) {
      res.status(413).json({ error: "too_large" });
      return;
    }
    let meta = {};
    try {
      const raw = req.body?.meta;
      meta = raw ? JSON.parse(String(raw)) : {};
    } catch {
      res.status(400).json({ error: "invalid_meta" });
      return;
    }
    const headerNote = String(req.get("X-Support-Note") || "").trim();
    const saved = supportBundles.saveUpload({
      playerUuid,
      playerName: String(req.get("X-Player-Name") || "Unknown"),
      note: headerNote || meta.note || "",
      modVersion: meta.modVersion || "",
      serverUuid: meta.serverUuid || "",
      worldNames: meta.worldNames,
      zipBuffer: zipFile.buffer,
    });
    res.status(201).json({ bundleId: saved.bundleId, uploadedAt: saved.uploadedAt });
  },
);

app.get("/api/v1/moderation/pending", requireModerator, (_req, res) => {
  res.json({ submissions: listEnrichedPending() });
});

app.get("/api/v1/moderation/submissions/:submissionId/prefab.json", requireModerator, (req, res) => {
  const file = pendingSubmissionFile(req.params.submissionId, "prefab.prefab.json");
  if (!file) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.type("application/json").send(fs.readFileSync(file));
});

app.get("/api/v1/moderation/submissions/:submissionId/building.json", requireModerator, (req, res) => {
  const file = pendingSubmissionFile(req.params.submissionId, "building.json");
  if (!file) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.type("application/json").send(fs.readFileSync(file));
});

app.get("/api/v1/moderation/submissions/:submissionId/icon.png", requireModerator, (req, res) => {
  const file = pendingSubmissionFile(req.params.submissionId, "icon.png");
  if (!file) {
    res.status(404).end();
    return;
  }
  res.type("image/png").send(fs.readFileSync(file));
});

app.post("/api/v1/moderation/approve/:submissionId", requireModerator, (req, res) => {
  const result = approveSubmission(req.params.submissionId, req.body?.id, req.body?.requiredMods);
  res.status(result.status).json(result.body);
});

app.post("/api/v1/moderation/reject/:submissionId", requireModerator, (req, res) => {
  const result = rejectSubmission(req.params.submissionId, req.body?.reason);
  res.status(result.status).json(result.body);
});

// --- Website (same process / port — required for Railway) ---

function isApiRequest(req) {
  return req.originalUrl.startsWith("/api/");
}

function requireWebUser(req, res, next) {
  if (!req.session?.user) {
    if (isApiRequest(req)) {
      res.status(401).json({ error: "login_required" });
      return;
    }
    res.redirect("/?login=required");
    return;
  }
  next();
}

function requireAdmin(req, res, next) {
  const uuid = req.session?.user?.profile?.uuid?.toLowerCase();
  if (!uuid || !ADMIN_UUIDS.has(uuid)) {
    if (isApiRequest(req)) {
      res.status(403).json({ error: "admin_required" });
      return;
    }
    res.status(403).send("Admin access required.");
    return;
  }
  next();
}

app.get("/auth/login", async (req, res) => {
  if (!oidc.enabled) {
    res.status(503).send("OIDC not configured. See docs/RailwayDeployment.md");
    return;
  }
  const state = crypto.randomUUID();
  const { verifier, challenge } = oidc.createPkce();
  req.session.oauthState = state;
  req.session.pkceVerifier = verifier;
  const url = await oidc.authorizationUrl(state, challenge);
  res.redirect(url);
});

app.get("/auth/callback", async (req, res) => {
  try {
    if (req.query.error) {
      throw new Error(String(req.query.error_description || req.query.error));
    }
    if (req.query.state !== req.session.oauthState) {
      throw new Error("state_mismatch");
    }
    const code = String(req.query.code || "");
    const tokens = await oidc.exchangeCode(code, req.session.pkceVerifier);
    const profile = await oidc.userInfo(tokens.access_token);
    req.session.user = profile;
    delete req.session.oauthState;
    delete req.session.pkceVerifier;
    res.redirect("/");
  } catch (e) {
    res.status(400).send(`Login failed: ${e.message}`);
  }
});

app.get("/auth/logout", (req, res) => {
  req.session.destroy(() => res.redirect("/"));
});

app.get("/api/me", (req, res) => {
  const user = req.session?.user || null;
  const profileUuid = String(user?.profile?.uuid || "")
    .trim()
    .toLowerCase();
  res.json({
    user,
    isAdmin: Boolean(profileUuid && ADMIN_UUIDS.has(profileUuid)),
  });
});

/**
 * @param {string} buildingId
 * @param {{ asAdmin?: boolean }} [options]
 * @param {{ profileUuid: string, profileUsername: string } | null} [webUser]
 */
function getPublishedBuildingEditPayload(buildingId, webUser, options = {}) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (!entry) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!options.asAdmin && (!webUser || !isOwnedByWebUser(entry, webUser))) {
    return { status: 403, body: { error: "not_owner" } };
  }
  const paths = storage.approvedPaths(id);
  if (!fs.existsSync(paths.building)) {
    return { status: 404, body: { error: "not_found" } };
  }
  const building = readBuildingJson(paths.building) || {};
  const card = resolveCardImage(id, entry.coverScreenshotId);
  const description =
    normalizeDescription(entry.description) ||
    normalizeDescription(building.description) ||
    "";
  const materials = readBuildingMaterials(paths.building);
  const goldCost = readBuildingGoldCost(paths.building);
  const styleId =
    String(entry.styleId || building.styleId || "misc").trim() || "misc";
  const tags = normalizeTags(entry.tags).length
    ? normalizeTags(entry.tags)
    : normalizeTags(building.tags);
  return {
    status: 200,
    body: {
      kind: "approved",
      id,
      displayName: String(entry.displayName || building.displayName || "").trim(),
      description,
      treasuryGoldCoinCost: goldCost,
      materials,
      styleId,
      tags,
      version: entry.version || "1",
      creatorUuid: entry.creatorUuid,
      creatorName: entry.creatorName,
      coverScreenshotId: card.coverScreenshotId || null,
      usesCoverImage: card.usesCoverImage,
      iconUrl: card.iconUrl,
      coverImageUrl: card.coverImageUrl || null,
      screenshots: enrichOwnerScreenshots("approved", id, true),
    },
  };
}

/**
 * @param {string} buildingId
 * @param {unknown} body
 * @param {{ asAdmin?: boolean }} [options]
 * @param {{ profileUuid: string, profileUsername: string } | null} [webUser]
 */
function patchPublishedBuilding(buildingId, body, webUser, options = {}) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (!entry) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!options.asAdmin && (!webUser || !isOwnedByWebUser(entry, webUser))) {
    return { status: 403, body: { error: "not_owner" } };
  }
  const allowStyleAndTags = Boolean(options.asAdmin);
  const validated = validateBuildingEditPayload(body, { allowStyleAndTags });
  if (!validated.ok) {
    return { status: 400, body: { error: validated.error, message: validated.message } };
  }
  const paths = storage.approvedPaths(id);
  if (!fs.existsSync(paths.building)) {
    return { status: 404, body: { error: "not_found" } };
  }
  const building = readBuildingJson(paths.building);
  if (!building || typeof building !== "object") {
    return { status: 500, body: { error: "building_corrupt" } };
  }
  applyBuildingEditFields(building, validated.value, { allowStyleAndTags });
  fs.writeFileSync(paths.building, JSON.stringify(building, null, 2));

  entry.displayName = validated.value.displayName;
  if (validated.value.description) {
    entry.description = validated.value.description;
  } else {
    delete entry.description;
  }
  if (allowStyleAndTags) {
    entry.styleId = validated.value.styleId || "misc";
    entry.tags = validated.value.tags || [];
  }
  storage.writeManifest(manifest);

  if (fs.existsSync(paths.meta)) {
    try {
      const approvedMeta = JSON.parse(fs.readFileSync(paths.meta, "utf8"));
      approvedMeta.displayName = validated.value.displayName;
      if (validated.value.description) {
        approvedMeta.description = validated.value.description;
      } else {
        delete approvedMeta.description;
      }
      if (allowStyleAndTags) {
        approvedMeta.styleId = validated.value.styleId || "misc";
        approvedMeta.tags = validated.value.tags || [];
      }
      fs.writeFileSync(paths.meta, JSON.stringify(approvedMeta, null, 2));
    } catch {
      // meta is best-effort sync
    }
  }

  return getPublishedBuildingEditPayload(id, webUser, options);
}

/**
 * @param {string} submissionId
 */
function getPendingSubmissionEditPayload(submissionId) {
  const meta = storage.loadSubmissionMeta(submissionId, "pending");
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  const buildingPath = path.join(storage.submissionDir(submissionId, "pending"), "building.json");
  const building = readBuildingJson(buildingPath) || {};
  const description =
    normalizeDescription(meta.description) ||
    normalizeDescription(building.description) ||
    "";
  const materials = readBuildingMaterials(buildingPath);
  const goldCost = readBuildingGoldCost(buildingPath);
  const styleId =
    String(meta.styleId || building.styleId || "misc").trim() || "misc";
  const tags = normalizeTags(meta.tags).length
    ? normalizeTags(meta.tags)
    : normalizeTags(building.tags);
  const iconFile = pendingSubmissionFile(submissionId, "icon.png");
  return {
    status: 200,
    body: {
      kind: "pending",
      submissionId,
      proposedId: meta.proposedId || null,
      displayName: String(meta.displayName || building.displayName || "").trim(),
      description,
      treasuryGoldCoinCost: goldCost,
      materials,
      styleId,
      tags,
      version: meta.version || "1",
      creatorUuid: meta.creatorUuid,
      creatorName: meta.creatorName,
      iconUrl: iconFile
        ? `/api/admin/submissions/${encodeURIComponent(submissionId)}/icon.png`
        : null,
      coverScreenshotId: null,
      usesCoverImage: false,
      coverImageUrl: null,
      screenshots: enrichOwnerScreenshots("pending", submissionId, true),
    },
  };
}

/**
 * @param {string} submissionId
 * @param {unknown} body
 */
function patchPendingSubmission(submissionId, body) {
  const meta = storage.loadSubmissionMeta(submissionId, "pending");
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  const validated = validateBuildingEditPayload(body, { allowStyleAndTags: true });
  if (!validated.ok) {
    return { status: 400, body: { error: validated.error, message: validated.message } };
  }
  const dir = storage.submissionDir(submissionId, "pending");
  const buildingPath = path.join(dir, "building.json");
  const building = readBuildingJson(buildingPath);
  if (!building || typeof building !== "object") {
    return { status: 500, body: { error: "building_corrupt" } };
  }
  applyBuildingEditFields(building, validated.value, { allowStyleAndTags: true });
  fs.writeFileSync(buildingPath, JSON.stringify(building, null, 2));

  meta.displayName = validated.value.displayName;
  if (validated.value.description) {
    meta.description = validated.value.description;
  } else {
    delete meta.description;
  }
  meta.styleId = validated.value.styleId || "misc";
  meta.tags = validated.value.tags || [];
  fs.writeFileSync(path.join(dir, "meta.json"), JSON.stringify(meta, null, 2));

  return getPendingSubmissionEditPayload(submissionId);
}

function parseAdminRawJson(req, res, next) {
  adminRawJsonUpload(req, res, (err) => {
    if (!err) {
      next();
      return;
    }
    if (err.type === "entity.too.large") {
      res.status(413).json({
        error: "prefab_json_too_large",
        message: "Raw file exceeds the 32 MB prefab limit.",
      });
      return;
    }
    res.status(400).json({ error: "raw_file_invalid", message: err.message || "Invalid raw file." });
  });
}

function resolveAdminRawFiles(ownerKind, rawOwnerId) {
  if (ownerKind === "approved") {
    const id = normalizeCommunityId(rawOwnerId);
    if (!id) return null;
    const manifest = storage.readManifest();
    const entry = (manifest.entries || []).find((candidate) => candidate.id === id);
    if (!entry) return null;
    const paths = storage.approvedPaths(id);
    if (!fs.existsSync(paths.building) || !fs.existsSync(paths.prefab)) return null;
    return { ownerKind, ownerId: id, paths, entry, manifest };
  }

  const ownerId = String(rawOwnerId || "");
  const meta = storage.listPending().find((candidate) => candidate.submissionId === ownerId);
  if (!meta) return null;
  const dir = storage.submissionDir(ownerId, "pending");
  const paths = {
    dir,
    building: path.join(dir, "building.json"),
    prefab: path.join(dir, "prefab.prefab.json"),
    meta: path.join(dir, "meta.json"),
  };
  if (!fs.existsSync(paths.building) || !fs.existsSync(paths.prefab)) return null;
  return { ownerKind, ownerId, paths, meta };
}

function readAdminRawFile(ownerKind, ownerId, fileKind) {
  if (fileKind !== "building" && fileKind !== "prefab") {
    return { status: 400, body: { error: "raw_file_kind_invalid" } };
  }
  const resolved = resolveAdminRawFiles(ownerKind, ownerId);
  if (!resolved) {
    return { status: 404, body: { error: "not_found" } };
  }
  const file = fileKind === "building" ? resolved.paths.building : resolved.paths.prefab;
  return { status: 200, text: fs.readFileSync(file, "utf8") };
}

function writeAdminRawFile(ownerKind, ownerId, fileKind, replacementText) {
  if (fileKind !== "building" && fileKind !== "prefab") {
    return { status: 400, body: { error: "raw_file_kind_invalid" } };
  }
  const resolved = resolveAdminRawFiles(ownerKind, ownerId);
  if (!resolved) {
    return { status: 404, body: { error: "not_found" } };
  }
  const buildingText =
    fileKind === "building"
      ? replacementText
      : fs.readFileSync(resolved.paths.building, "utf8");
  const prefabText =
    fileKind === "prefab"
      ? replacementText
      : fs.readFileSync(resolved.paths.prefab, "utf8");

  try {
    const validated = validateAdminRawFilePair({
      buildingText,
      prefabText,
      publishedId: ownerKind === "approved" ? resolved.ownerId : "",
    });
    const metadata = projectAdminRawMetadata(
      validated.building,
      validated.blockIdVersion,
      validated.prefabBytes,
    );
    const target = fileKind === "building" ? resolved.paths.building : resolved.paths.prefab;
    atomicWriteText(target, replacementText);

    if (ownerKind === "pending") {
      const meta = applyAdminRawMetadata({ ...resolved.meta }, metadata);
      atomicWriteText(resolved.paths.meta, JSON.stringify(meta, null, 2));
    } else {
      const entry = resolved.entry;
      applyAdminRawMetadata(entry, metadata, { includePrefabBytes: true });
      resolved.manifest.version = (resolved.manifest.version || 0) + 1;
      storage.writeManifest(resolved.manifest);

      let approvedMeta = {};
      if (fs.existsSync(resolved.paths.meta)) {
        approvedMeta = JSON.parse(fs.readFileSync(resolved.paths.meta, "utf8"));
      }
      applyAdminRawMetadata(approvedMeta, metadata);
      atomicWriteText(resolved.paths.meta, JSON.stringify(approvedMeta, null, 2));
    }

    return {
      status: 200,
      body: {
        ok: true,
        fileKind,
        bytes: fileKind === "building" ? validated.buildingBytes : validated.prefabBytes,
        blockIdVersion: validated.blockIdVersion,
      },
    };
  } catch (err) {
    if (err instanceof AdminRawFileError) {
      return { status: 400, body: { error: err.code, message: err.message } };
    }
    throw err;
  }
}

function sendAdminRawFile(result, res) {
  if (result.status !== 200) {
    res.status(result.status).json(result.body);
    return;
  }
  res.status(200).type("text/plain").send(result.text);
}

app.get("/api/my-submissions", requireWebUser, (req, res) => {
  const webUser = sessionWebUser(req);
  if (!webUser.profileUuid && !webUser.profileUsername) {
    res.status(400).json({ error: "profile_missing" });
    return;
  }
  res.json({ submissions: listSubmissionsForCreator(webUser) });
});

app.get("/api/my-buildings/:buildingId", requireWebUser, (req, res) => {
  const result = getPublishedBuildingEditPayload(req.params.buildingId, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

app.patch("/api/my-buildings/:buildingId", requireWebUser, (req, res) => {
  const result = patchPublishedBuilding(req.params.buildingId, req.body, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

app.get("/api/admin/buildings/:buildingId", requireWebUser, requireAdmin, (req, res) => {
  const result = getPublishedBuildingEditPayload(req.params.buildingId, sessionWebUser(req), {
    asAdmin: true,
  });
  res.status(result.status).json(result.body);
});

app.patch("/api/admin/buildings/:buildingId", requireWebUser, requireAdmin, (req, res) => {
  const result = patchPublishedBuilding(req.params.buildingId, req.body, sessionWebUser(req), {
    asAdmin: true,
  });
  res.status(result.status).json(result.body);
});

app.get("/api/admin/submissions/:submissionId", requireWebUser, requireAdmin, (req, res) => {
  const result = getPendingSubmissionEditPayload(req.params.submissionId);
  res.status(result.status).json(result.body);
});

app.patch("/api/admin/submissions/:submissionId", requireWebUser, requireAdmin, (req, res) => {
  const result = patchPendingSubmission(req.params.submissionId, req.body);
  res.status(result.status).json(result.body);
});

app.get(
  "/api/admin/submissions/:submissionId/files/:fileKind",
  requireWebUser,
  requireAdmin,
  (req, res) => {
    sendAdminRawFile(
      readAdminRawFile("pending", req.params.submissionId, req.params.fileKind),
      res,
    );
  },
);

app.put(
  "/api/admin/submissions/:submissionId/files/:fileKind",
  requireWebUser,
  requireAdmin,
  parseAdminRawJson,
  (req, res, next) => {
    try {
      const result = writeAdminRawFile(
        "pending",
        req.params.submissionId,
        req.params.fileKind,
        req.body,
      );
      res.status(result.status).json(result.body);
    } catch (err) {
      next(err);
    }
  },
);

app.get(
  "/api/admin/buildings/:buildingId/files/:fileKind",
  requireWebUser,
  requireAdmin,
  (req, res) => {
    sendAdminRawFile(
      readAdminRawFile("approved", req.params.buildingId, req.params.fileKind),
      res,
    );
  },
);

app.put(
  "/api/admin/buildings/:buildingId/files/:fileKind",
  requireWebUser,
  requireAdmin,
  parseAdminRawJson,
  (req, res, next) => {
    try {
      const result = writeAdminRawFile(
        "approved",
        req.params.buildingId,
        req.params.fileKind,
        req.body,
      );
      res.status(result.status).json(result.body);
    } catch (err) {
      next(err);
    }
  },
);

app.post("/api/my-submissions/:submissionId/withdraw", requireWebUser, (req, res) => {
  const result = withdrawPendingSubmission(req.params.submissionId, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

app.post("/api/my-submissions/:submissionId/dismiss", requireWebUser, (req, res) => {
  const result = dismissRejectedSubmission(req.params.submissionId, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

app.post("/api/my-buildings/:buildingId/remove", requireWebUser, (req, res) => {
  const result = removeOwnApprovedBuilding(req.params.buildingId, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

function multerScreenshotError(err, res) {
  if (err instanceof multer.MulterError && err.code === "LIMIT_FILE_SIZE") {
    res.status(400).json({
      error: "screenshot_too_large",
      message: `Screenshot too large (max ${formatScreenshotMaxSizeLabel()})`,
      maxBytes: MAX_SCREENSHOT_BYTES,
    });
    return true;
  }
  return false;
}

/**
 * @param {"pending"|"approved"} ownerKind
 * @param {string} ownerId
 * @param {object} ownerMeta
 * @param {Express.Multer.File} file
 * @param {{ profileUuid: string, profileUsername: string }} webUser
 * @param {{ autoApprove?: boolean, uploadedByAdmin?: boolean }} [options]
 */
async function saveOwnerScreenshot(ownerKind, ownerId, ownerMeta, file, webUser, options = {}) {
  if (!file) {
    return { status: 400, body: { error: "screenshot_required", message: "Choose an image file to upload." } };
  }
  if (!isAllowedScreenshotMime(file.mimetype)) {
    return {
      status: 400,
      body: {
        error: "screenshot_type_invalid",
        message: "Screenshots must be JPEG, PNG, or WebP.",
      },
    };
  }
  if (!screenshotExtForMime(file.mimetype)) {
    return {
      status: 400,
      body: {
        error: "screenshot_type_invalid",
        message: "Screenshots must be JPEG, PNG, or WebP.",
      },
    };
  }
  try {
    assertSize(file.size, MAX_SCREENSHOT_BYTES, "screenshot");
  } catch {
    return {
      status: 400,
      body: {
        error: "screenshot_too_large",
        message: `Screenshot too large (max ${formatScreenshotMaxSizeLabel()})`,
        maxBytes: MAX_SCREENSHOT_BYTES,
      },
    };
  }
  const existingCount = storage.countScreenshotsForOwner(ownerKind, ownerId);
  if (existingCount >= MAX_SCREENSHOTS_PER_OWNER) {
    return {
      status: 400,
      body: {
        error: "screenshot_limit_reached",
        message: `You can upload up to ${MAX_SCREENSHOTS_PER_OWNER} screenshots per build.`,
        maxCount: MAX_SCREENSHOTS_PER_OWNER,
      },
    };
  }

  let processed;
  try {
    processed = await processScreenshot(file.buffer);
  } catch (err) {
    if (err instanceof ScreenshotProcessingError) {
      return { status: 400, body: { error: err.code, message: err.message } };
    }
    throw err;
  }

  const screenshotId = crypto.randomUUID();
  const paths = storage.screenshotPaths(screenshotId, processed.ext);
  fs.mkdirSync(paths.dir, { recursive: true });
  try {
    fs.writeFileSync(paths.image, processed.fullBuffer);
    fs.writeFileSync(paths.card, processed.cardBuffer);
  } catch (err) {
    storage.deleteScreenshot(screenshotId);
    throw err;
  }
  const autoApprove = Boolean(options.autoApprove);
  const meta = {
    screenshotId,
    ownerKind,
    ownerId,
    creatorUuid: ownerMeta.creatorUuid || webUser.profileUuid,
    creatorName: ownerMeta.creatorName || webUser.profileUsername || "Unknown",
    status: autoApprove ? "approved" : "pending",
    uploadedAt: new Date().toISOString(),
    mimeType: processed.mimeType,
    ext: processed.ext,
    bytes: processed.fullBuffer.length,
    cardBytes: processed.cardBuffer.length,
  };
  if (autoApprove) {
    meta.approvedAt = new Date().toISOString();
  }
  if (options.uploadedByAdmin) {
    meta.uploadedByAdmin = true;
  }
  try {
    storage.writeScreenshotMeta(meta);
  } catch (err) {
    storage.deleteScreenshot(screenshotId);
    throw err;
  }
  if (autoApprove && ownerKind === "approved") {
    applyCoverScreenshotIfUnset(storage, ownerId, screenshotId);
  }
  let url = `/api/my-screenshots/${encodeURIComponent(screenshotId)}/image`;
  if (autoApprove && ownerKind === "approved") {
    url = `/api/buildings/${encodeURIComponent(ownerId)}/screenshots/${encodeURIComponent(screenshotId)}`;
  } else if (options.uploadedByAdmin) {
    url = `/api/admin/screenshots/${encodeURIComponent(screenshotId)}/image`;
  }
  return {
    status: 201,
    body: {
      screenshotId,
      status: meta.status,
      bytes: meta.bytes,
      url,
      cardUrl: withScreenshotVariant(url, "card"),
    },
  };
}

async function uploadPendingSubmissionScreenshot(submissionId, file, webUser, options = {}) {
  const meta = storage.loadSubmissionMeta(submissionId, "pending");
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!options.asAdmin && !isOwnedByWebUser(meta, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }
  return saveOwnerScreenshot("pending", submissionId, meta, file, webUser, {
    autoApprove: Boolean(options.asAdmin),
    uploadedByAdmin: Boolean(options.asAdmin),
  });
}

async function uploadApprovedBuildingScreenshot(buildingId, file, webUser, options = {}) {
  const id = normalizeCommunityId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (!entry) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!options.asAdmin && !isOwnedByWebUser(entry, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }
  return saveOwnerScreenshot("approved", id, entry, file, webUser, {
    autoApprove: Boolean(options.asAdmin),
    uploadedByAdmin: Boolean(options.asAdmin),
  });
}

function deleteOwnScreenshot(screenshotId, webUser, options = {}) {
  const meta = storage.loadScreenshotMeta(screenshotId);
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!options.asAdmin && !isOwnedByWebUser(meta, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }
  clearCoverIfScreenshotRemoved(meta);
  storage.deleteScreenshot(screenshotId);
  return { status: 200, body: { screenshotId, status: "deleted" } };
}

function approveScreenshot(screenshotId) {
  const meta = storage.loadScreenshotMeta(screenshotId);
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (meta.status !== "pending") {
    return { status: 400, body: { error: "not_pending" } };
  }
  meta.status = "approved";
  meta.approvedAt = new Date().toISOString();
  storage.writeScreenshotMeta(meta);
  if (meta.ownerKind === "approved" && meta.ownerId) {
    applyCoverScreenshotIfUnset(storage, meta.ownerId, screenshotId);
  }
  return { status: 200, body: { screenshotId, status: "approved" } };
}

function rejectScreenshot(screenshotId) {
  const meta = storage.loadScreenshotMeta(screenshotId);
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  clearCoverIfScreenshotRemoved(meta);
  storage.deleteScreenshot(screenshotId);
  return { status: 200, body: { screenshotId, status: "rejected" } };
}

function enrichPendingScreenshotForAdmin(meta) {
  let displayName = meta.ownerId;
  let ownerLabel = meta.ownerId;
  if (meta.ownerKind === "pending") {
    const submission = storage.loadSubmissionMeta(meta.ownerId, "pending");
    displayName = submission?.displayName || meta.ownerId;
    ownerLabel = meta.ownerId;
  } else {
    const manifest = storage.readManifest();
    const entry = (manifest.entries || []).find((e) => e.id === meta.ownerId);
    displayName = entry?.displayName || meta.ownerId;
    ownerLabel = meta.ownerId;
  }
  return {
    ...meta,
    displayName,
    ownerLabel,
    imageUrl: `/api/admin/screenshots/${encodeURIComponent(meta.screenshotId)}/image`,
    cardUrl: withScreenshotVariant(
      `/api/admin/screenshots/${encodeURIComponent(meta.screenshotId)}/image`,
      "card"
    ),
  };
}

/**
 * @param {object} meta
 * @param {import("express").Response} res
 * @param {"full"|"card"} [variant]
 * @param {{ cachePublic?: boolean }} [options]
 */
function sendScreenshotImage(meta, res, variant = "full", options = {}) {
  const file = storage.resolveScreenshotImagePath(meta, variant);
  if (!file) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  const isCardFile = path.basename(file) === "card.webp";
  if (options.cachePublic) {
    res.set("Cache-Control", "public, max-age=3600");
  }
  res.type(isCardFile ? "image/webp" : meta.mimeType || "application/octet-stream").sendFile(file);
}

app.post(
  "/api/my-submissions/:submissionId/screenshots",
  requireWebUser,
  (req, res, next) => {
    screenshotUpload.single("screenshot")(req, res, (err) => {
      if (err) {
        if (multerScreenshotError(err, res)) {
          return;
        }
        res.status(400).json({ error: err.message || "upload_failed" });
        return;
      }
      next();
    });
  },
  async (req, res, next) => {
    try {
      const result = await uploadPendingSubmissionScreenshot(
        req.params.submissionId,
        req.file,
        sessionWebUser(req)
      );
      res.status(result.status).json(result.body);
    } catch (err) {
      next(err);
    }
  }
);

app.post(
  "/api/my-buildings/:buildingId/screenshots",
  requireWebUser,
  (req, res, next) => {
    screenshotUpload.single("screenshot")(req, res, (err) => {
      if (err) {
        if (multerScreenshotError(err, res)) {
          return;
        }
        res.status(400).json({ error: err.message || "upload_failed" });
        return;
      }
      next();
    });
  },
  async (req, res, next) => {
    try {
      const result = await uploadApprovedBuildingScreenshot(
        req.params.buildingId,
        req.file,
        sessionWebUser(req)
      );
      res.status(result.status).json(result.body);
    } catch (err) {
      next(err);
    }
  }
);

app.delete("/api/my-screenshots/:screenshotId", requireWebUser, (req, res) => {
  const result = deleteOwnScreenshot(req.params.screenshotId, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

app.post("/api/my-buildings/:buildingId/cover", requireWebUser, (req, res) => {
  const screenshotId = req.body?.screenshotId;
  const result = setBuildingCoverScreenshot(
    req.params.buildingId,
    screenshotId == null ? "" : String(screenshotId),
    sessionWebUser(req)
  );
  res.status(result.status).json(result.body);
});

app.get("/api/my-screenshots/:screenshotId/image", requireWebUser, (req, res) => {
  const meta = storage.loadScreenshotMeta(req.params.screenshotId);
  if (!meta) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  const webUser = sessionWebUser(req);
  const profileUuid = webUser.profileUuid;
  const isAdmin = Boolean(profileUuid && ADMIN_UUIDS.has(profileUuid));
  if (!isAdmin && !isOwnedByWebUser(meta, webUser)) {
    res.status(403).json({ error: "not_owner" });
    return;
  }
  sendScreenshotImage(meta, res, parseScreenshotVariant(req));
});

app.get("/api/admin/screenshots/pending", requireWebUser, requireAdmin, (_req, res) => {
  res.json({
    screenshots: storage.listPendingScreenshots().map(enrichPendingScreenshotForAdmin),
  });
});

app.get("/api/admin/screenshots/:screenshotId/image", requireWebUser, requireAdmin, (req, res) => {
  const meta = storage.loadScreenshotMeta(req.params.screenshotId);
  if (!meta) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  sendScreenshotImage(meta, res, parseScreenshotVariant(req));
});

app.post("/api/admin/screenshots/:screenshotId/approve", requireWebUser, requireAdmin, (req, res) => {
  const result = approveScreenshot(req.params.screenshotId);
  res.status(result.status).json(result.body);
});

app.post("/api/admin/screenshots/:screenshotId/reject", requireWebUser, requireAdmin, (req, res) => {
  const result = rejectScreenshot(req.params.screenshotId);
  res.status(result.status).json(result.body);
});

app.delete("/api/admin/screenshots/:screenshotId", requireWebUser, requireAdmin, (req, res) => {
  const result = deleteOwnScreenshot(req.params.screenshotId, sessionWebUser(req), { asAdmin: true });
  res.status(result.status).json(result.body);
});

app.post("/api/admin/buildings/:buildingId/cover", requireWebUser, requireAdmin, (req, res) => {
  const screenshotId = req.body?.screenshotId;
  const result = setBuildingCoverScreenshot(
    req.params.buildingId,
    screenshotId == null ? "" : String(screenshotId),
    sessionWebUser(req),
    { asAdmin: true }
  );
  res.status(result.status).json(result.body);
});

function handleScreenshotUploadMiddleware(req, res, next) {
  screenshotUpload.single("screenshot")(req, res, (err) => {
    if (err) {
      if (multerScreenshotError(err, res)) {
        return;
      }
      res.status(400).json({ error: err.message || "upload_failed" });
      return;
    }
    next();
  });
}

app.post(
  "/api/admin/submissions/:submissionId/screenshots",
  requireWebUser,
  requireAdmin,
  handleScreenshotUploadMiddleware,
  async (req, res, next) => {
    try {
      const result = await uploadPendingSubmissionScreenshot(
        req.params.submissionId,
        req.file,
        sessionWebUser(req),
        { asAdmin: true }
      );
      res.status(result.status).json(result.body);
    } catch (err) {
      next(err);
    }
  }
);

app.post(
  "/api/admin/buildings/:buildingId/screenshots",
  requireWebUser,
  requireAdmin,
  handleScreenshotUploadMiddleware,
  async (req, res, next) => {
    try {
      const result = await uploadApprovedBuildingScreenshot(
        req.params.buildingId,
        req.file,
        sessionWebUser(req),
        { asAdmin: true }
      );
      res.status(result.status).json(result.body);
    } catch (err) {
      next(err);
    }
  }
);

app.get("/api/buildings/:id/screenshots", (req, res) => {
  const id = normalizeCommunityId(req.params.id);
  if (!id) {
    res.status(400).json({ error: "invalid_id" });
    return;
  }
  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((e) => e.id === id);
  if (!entry) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  const screenshots = storage.listScreenshotsForOwner("approved", id, "approved").map((meta) => {
    const url = `/api/buildings/${encodeURIComponent(id)}/screenshots/${encodeURIComponent(meta.screenshotId)}`;
    return {
      screenshotId: meta.screenshotId,
      bytes: meta.bytes || 0,
      mimeType: meta.mimeType,
      uploadedAt: meta.uploadedAt,
      url,
      cardUrl: withScreenshotVariant(url, "card"),
    };
  });
  res.json({ screenshots });
});

app.get("/api/buildings/:id/screenshots/:screenshotId", (req, res) => {
  const id = normalizeCommunityId(req.params.id);
  if (!id) {
    res.status(400).json({ error: "invalid_id" });
    return;
  }
  const meta = storage.loadScreenshotMeta(req.params.screenshotId);
  if (
    !meta ||
    meta.ownerKind !== "approved" ||
    meta.ownerId !== id ||
    meta.status !== "approved"
  ) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  sendScreenshotImage(meta, res, parseScreenshotVariant(req), { cachePublic: true });
});

app.get("/api/admin/pending", requireWebUser, requireAdmin, (_req, res) => {
  res.json({ submissions: listEnrichedPending() });
});

app.get("/api/admin/support-bundles", requireWebUser, requireAdmin, (_req, res) => {
  res.json({ bundles: supportBundles.listBundles() });
});

app.get("/api/admin/support-bundles/:bundleId/download", requireWebUser, requireAdmin, (req, res) => {
  const bundle = supportBundles.getBundle(req.params.bundleId);
  if (!bundle) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.download(bundle.zipFile, `aetherhaven-support-${req.params.bundleId}.zip`);
});

app.delete("/api/admin/support-bundles/:bundleId", requireWebUser, requireAdmin, (req, res) => {
  if (!supportBundles.deleteBundle(req.params.bundleId)) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.json({ ok: true });
});

app.get("/api/admin/submissions/:submissionId/icon.png", requireWebUser, requireAdmin, (req, res) => {
  const file = pendingSubmissionFile(req.params.submissionId, "icon.png");
  if (!file) {
    res.status(404).end();
    return;
  }
  res.type("image/png").send(fs.readFileSync(file));
});

app.post("/api/admin/approve/:submissionId", requireWebUser, requireAdmin, (req, res) => {
  const result = approveSubmission(req.params.submissionId, req.body?.id, req.body?.requiredMods);
  res.status(result.status).json(result.body);
});

app.post("/api/admin/reject/:submissionId", requireWebUser, requireAdmin, (req, res) => {
  const result = rejectSubmission(req.params.submissionId, req.body?.reason);
  res.status(result.status).json(result.body);
});

app.get("/api/admin/catalog", requireWebUser, requireAdmin, (req, res) => {
  const manifest = storage.readManifest();
  const voterUuid = sessionProfileUuid(req);
  const userVotes = voterUuid ? votes.getUserVotes(voterUuid) : null;
  res.json({
    version: manifest.version,
    entries: sortCatalogEntries(enrichManifestEntries(manifest, 0, userVotes)),
  });
});

app.post("/api/admin/delete/:buildingId", requireWebUser, requireAdmin, (req, res) => {
  const result = deleteApprovedBuilding(req.params.buildingId);
  res.status(result.status).json(result.body);
});

app.get("/api/catalog", sendManifest);

app.get("/api/site-config", (_req, res) => {
  const slots = {};
  if (ADSENSE_SLOT_BROWSE) {
    slots.browseHeader = ADSENSE_SLOT_BROWSE;
  }
  if (ADSENSE_SLOT_WIKI) {
    slots.wikiHeader = ADSENSE_SLOT_WIKI;
  }
  const enabled = ADS_ENABLED && Boolean(ADSENSE_CLIENT_ID);
  res.json({
    ads: {
      enabled,
      provider: "adsense",
      clientId: ADSENSE_CLIENT_ID || null,
      slots,
    },
  });
});

app.get("/ads.txt", (_req, res) => {
  if (!ADSENSE_CLIENT_ID) {
    res.status(404).type("text/plain").send("Not found\n");
    return;
  }
  const publisherId = ADSENSE_CLIENT_ID.replace(/^ca-/, "");
  res
    .type("text/plain")
    .send(`google.com, ${publisherId}, DIRECT, f08c47fec0942fa0\n`);
});

app.post("/api/v1/buildings/:id/download", downloadRateLimit, (req, res) => {
  const result = recordBuildingDownload(req.params.id);
  res.status(result.status).json(result.body);
});

app.post("/api/buildings/:id/upvote", requireWebUser, voteRateLimit, (req, res) => {
  const result = toggleBuildingUpvote(req.params.id, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

app.get("/api/me/favorites", (req, res) => {
  const profileUuid = profileUuidForManifest(req);
  if (!profileUuid) {
    res.status(401).json({ error: "auth_required" });
    return;
  }
  const buildingIds = [...favorites.getUserFavorites(profileUuid)];
  res.json({ buildingIds });
});

app.post("/api/buildings/:id/favorite", requireWebUser, (req, res) => {
  const result = toggleBuildingFavorite(req.params.id, sessionProfileUuid(req));
  res.status(result.status).json(result.body);
});

app.post("/api/v1/buildings/:id/favorite", requireInGamePlayer, (req, res) => {
  const result = toggleBuildingFavorite(req.params.id, inGamePlayerUser(req).profileUuid);
  res.status(result.status).json(result.body);
});

const webRoot = path.join(__dirname, "..", "web");

/** Inject AdSense site-verification script into HTML head when publisher ID is configured. */
function sendHtmlWithAdSense(relativePath) {
  return (_req, res) => {
    sendHtmlFile(relativePath, _req, res);
  };
}

function sendHtmlFile(relativePath, req, res, options = {}) {
  const filePath = path.join(webRoot, relativePath);
  let html;
  try {
    html = fs.readFileSync(filePath, "utf8");
  } catch {
    res.status(404).send("Not found");
    return;
  }
  if (options.wikiTopic) {
    const rendered = renderWikiTopic(options.wikiTopic);
    if (rendered) {
      html = html.replace(
        /<!-- SSR_WIKI_ARTICLE -->[\s\S]*?(?=<\/article>)/,
        rendered.html
      );
      html = html.replace(/<title>[^<]*<\/title>/i, `<title>${escapeHtmlAttr(rendered.title)} | Aetherhaven Wiki</title>`);
      const desc = escapeHtmlAttr(rendered.description);
      if (html.includes('name="description"')) {
        html = html.replace(
          /<meta\s+name="description"\s+content="[^"]*"\s*\/?>/i,
          `<meta name="description" content="${desc}" />`
        );
      } else {
        html = html.replace(
          /<meta\s+name="viewport"[^>]*>/i,
          `$&\n    <meta name="description" content="${desc}" />`
        );
      }
      const canonical = `${publicBaseUrl}/wiki.html?topic=${encodeURIComponent(options.wikiTopic)}`;
      if (!html.includes('rel="canonical"')) {
        html = html.replace(
          /<link rel="canonical"[^>]*>/i,
          `<link rel="canonical" href="${escapeHtmlAttr(canonical)}" />`
        );
      } else {
        html = html.replace(
          /<link rel="canonical" href="[^"]*"\s*\/?>/i,
          `<link rel="canonical" href="${escapeHtmlAttr(canonical)}" />`
        );
      }
    }
  }
  if (ADSENSE_CLIENT_ID && !html.includes("pagead2.googlesyndication.com/pagead/js/adsbygoogle.js")) {
    const snippet =
      `<script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE_CLIENT_ID}" ` +
      `crossorigin="anonymous"></script>\n`;
    html = html.replace(/<\/head>/i, `${snippet}</head>`);
  }
  res.type("html").send(html);
}

function escapeHtmlAttr(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function sendWikiHtml(req, res) {
  const topic = String(req.query.topic || "welcome").trim() || "welcome";
  sendHtmlFile("wiki.html", req, res, { wikiTopic: topic });
}

app.get("/admin.html", requireWebUser, requireAdmin, sendHtmlWithAdSense("admin.html"));

app.get("/sitemap.xml", (_req, res) => {
  res.type("application/xml").send(buildSitemapXml(publicBaseUrl));
});

app.get("/robots.txt", (_req, res) => {
  res.type("text/plain").send(buildRobotsTxt(publicBaseUrl));
});

if (ADSENSE_CLIENT_ID) {
  app.get("/", sendHtmlWithAdSense("index.html"));
  app.get("/index.html", sendHtmlWithAdSense("index.html"));
  app.get("/wiki.html", sendWikiHtml);
  app.get("/privacy.html", sendHtmlWithAdSense("privacy.html"));
  app.get("/about.html", sendHtmlWithAdSense("about.html"));
  app.get("/terms.html", sendHtmlWithAdSense("terms.html"));
  app.get("/account.html", sendHtmlWithAdSense("account.html"));
  app.get("/submissions.html", sendHtmlWithAdSense("submissions.html"));
  app.get("/edit.html", sendHtmlWithAdSense("edit.html"));
  app.get("/dashboard.html", sendHtmlWithAdSense("dashboard.html"));
} else {
  app.get("/wiki.html", sendWikiHtml);
}

app.use(express.static(webRoot));

const server = app.listen(PORT, () => {
  console.log(`Community marketplace listening on ${publicBaseUrl} (port ${PORT})`);
});

function shutdown(signal) {
  console.log(`Received ${signal}, shutting down gracefully…`);
  server.close((err) => {
    if (err) {
      console.error("Error during shutdown:", err);
      process.exit(1);
      return;
    }
    process.exit(0);
  });
  // Don't hang forever if open connections keep the server open.
  setTimeout(() => {
    console.warn("Forced shutdown after timeout");
    process.exit(0);
  }, 8_000).unref();
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
