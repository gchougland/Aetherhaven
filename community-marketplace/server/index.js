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
  validateSubmissionBuilding,
} from "./validation.js";
import { createSubmissionRateLimit } from "./submissionRateLimit.js";
import { createVoteRateLimit } from "./voteRateLimit.js";
import { createDownloadRateLimit } from "./downloadRateLimit.js";
import { createVotes } from "./votes.js";
import { createDownloads } from "./downloads.js";

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
const dataDir = process.env.DATA_DIR || path.join(__dirname, "..", "data");
const storage = createStorage(dataDir);
const votes = createVotes(dataDir);
const downloads = createDownloads(dataDir);
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

function readBuildingDescription(buildingPath) {
  try {
    if (!fs.existsSync(buildingPath)) {
      return "";
    }
    const building = JSON.parse(fs.readFileSync(buildingPath, "utf8"));
    return typeof building.description === "string" ? building.description.trim() : "";
  } catch {
    return "";
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

function buildManifestEntry(id, meta, prefabBytes) {
  const entry = {
    id,
    displayName: meta.displayName,
    creatorUuid: meta.creatorUuid,
    creatorName: meta.creatorName,
    styleId: meta.styleId || "misc",
    tags: normalizeTags(meta.tags),
    blockIdVersion: meta.blockIdVersion,
    prefabBytes,
    version: meta.version || "1",
    approvedAt: meta.approvedAt,
  };
  const description = normalizeDescription(meta.description);
  if (description) {
    entry.description = description;
  }
  return entry;
}

function approveSubmission(submissionId, requestedId) {
  const meta = storage.loadSubmissionMeta(submissionId, "pending");
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  const pendingDir = storage.submissionDir(submissionId, "pending");
  const id = normalizeCommunityId(requestedId || meta.proposedId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }

  const approved = storage.approvedPaths(id);
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
  fs.writeFileSync(approved.building, JSON.stringify(building, null, 2));

  const buildingTags = normalizeTags(building.tags);
  const approvedMeta = {
    ...meta,
    id,
    description: normalizeDescription(building.description) || normalizeDescription(meta.description),
    tags: buildingTags.length ? buildingTags : normalizeTags(meta.tags),
    status: "approved",
    approvedAt: new Date().toISOString(),
    version: meta.version || "1",
  };
  fs.writeFileSync(approved.meta, JSON.stringify(approvedMeta, null, 2));

  const prefabBytes = fs.statSync(approved.prefab).size;
  const manifest = storage.readManifest();
  manifest.entries = manifest.entries.filter((e) => e.id !== id);
  manifest.entries.push(buildManifestEntry(id, approvedMeta, prefabBytes));
  manifest.version = (manifest.version || 0) + 1;
  storage.writeManifest(manifest);

  fs.rmSync(pendingDir, { recursive: true, force: true });
  storage.reassignScreenshotsToApproved(submissionId, id);
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
    .map((e) => ({
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
      iconUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/icon.png`,
      screenshots: enrichOwnerScreenshots("approved", e.id, true),
    }));

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
    };
  });
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

function enrichManifestEntries(manifest, clientBlockIdVersion = 0, userVotes = null) {
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
    const entryTags = normalizeTags(e.tags);
    const tags = entryTags.length ? entryTags : readBuildingTags(paths.building);
    const entry = {
      ...e,
      tags,
      prefabBytes,
      compatible,
      upvoteCount: voteCounts[e.id] || 0,
      downloadCount: downloadCounts[e.id] || 0,
      screenshotCount: storage.listScreenshotsForOwner("approved", e.id, "approved").length,
      iconUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/icon.png`,
      buildingUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/building.json`,
      prefabUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/prefab.json`,
    };
    if (description) {
      entry.description = description;
    } else {
      delete entry.description;
    }
    if (userVotes) {
      entry.userHasUpvoted = userVotes.has(e.id);
    }
    return entry;
  });
}

function sendManifest(req, res) {
  const clientBlockIdVersion = Number(req.query.blockIdVersion || 0);
  const manifest = storage.readManifest();
  const voterUuid = sessionProfileUuid(req);
  const userVotes = voterUuid ? votes.getUserVotes(voterUuid) : null;
  const entries = sortCatalogEntries(enrichManifestEntries(manifest, clientBlockIdVersion, userVotes));
  res.json({
    version: manifest.version,
    entries,
  });
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
      fs.writeFileSync(path.join(dir, "meta.json"), JSON.stringify(meta, null, 2));
      res.status(201).json({ submissionId, proposedId: id, status: "pending" });
    } catch (e) {
      res.status(400).json({ error: e.message || "submission_failed" });
    }
  }
);

app.get("/api/v1/submissions/pending", requireAdminApiKey, (_req, res) => {
  res.json({ submissions: storage.listPending() });
});

app.post("/api/v1/submissions/:submissionId/approve", requireAdminApiKey, (req, res) => {
  const result = approveSubmission(req.params.submissionId, req.body?.id);
  res.status(result.status).json(result.body);
});

app.post("/api/v1/submissions/:submissionId/reject", requireAdminApiKey, (req, res) => {
  const result = rejectSubmission(req.params.submissionId, req.body?.reason);
  res.status(result.status).json(result.body);
});

app.get("/api/v1/moderation/pending", requireModerator, (_req, res) => {
  res.json({ submissions: storage.listPending() });
});

app.get("/api/v1/moderation/submissions/:submissionId/prefab.json", requireModerator, (req, res) => {
  const file = pendingSubmissionFile(req.params.submissionId, "prefab.prefab.json");
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
  const result = approveSubmission(req.params.submissionId, req.body?.id);
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
    res.redirect("/dashboard.html");
  } catch (e) {
    res.status(400).send(`Login failed: ${e.message}`);
  }
});

app.get("/auth/logout", (req, res) => {
  req.session.destroy(() => res.redirect("/"));
});

app.get("/api/me", (req, res) => {
  res.json({ user: req.session?.user || null });
});

app.get("/api/my-submissions", requireWebUser, (req, res) => {
  const webUser = sessionWebUser(req);
  if (!webUser.profileUuid && !webUser.profileUsername) {
    res.status(400).json({ error: "profile_missing" });
    return;
  }
  res.json({ submissions: listSubmissionsForCreator(webUser) });
});

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
 */
function saveOwnerScreenshot(ownerKind, ownerId, ownerMeta, file, webUser) {
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
  const ext = screenshotExtForMime(file.mimetype);
  if (!ext) {
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

  const screenshotId = crypto.randomUUID();
  const paths = storage.screenshotPaths(screenshotId, ext);
  fs.mkdirSync(paths.dir, { recursive: true });
  fs.writeFileSync(paths.image, file.buffer);
  const meta = {
    screenshotId,
    ownerKind,
    ownerId,
    creatorUuid: ownerMeta.creatorUuid || webUser.profileUuid,
    creatorName: ownerMeta.creatorName || webUser.profileUsername || "Unknown",
    status: "pending",
    uploadedAt: new Date().toISOString(),
    mimeType: file.mimetype.toLowerCase(),
    ext,
    bytes: file.size,
  };
  storage.writeScreenshotMeta(meta);
  return {
    status: 201,
    body: {
      screenshotId,
      status: "pending",
      bytes: file.size,
      url: `/api/my-screenshots/${encodeURIComponent(screenshotId)}/image`,
    },
  };
}

function uploadPendingSubmissionScreenshot(submissionId, file, webUser) {
  const meta = storage.loadSubmissionMeta(submissionId, "pending");
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!isOwnedByWebUser(meta, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }
  return saveOwnerScreenshot("pending", submissionId, meta, file, webUser);
}

function uploadApprovedBuildingScreenshot(buildingId, file, webUser) {
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
  return saveOwnerScreenshot("approved", id, entry, file, webUser);
}

function deleteOwnScreenshot(screenshotId, webUser) {
  const meta = storage.loadScreenshotMeta(screenshotId);
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
  if (!isOwnedByWebUser(meta, webUser)) {
    return { status: 403, body: { error: "not_owner" } };
  }
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
  return { status: 200, body: { screenshotId, status: "approved" } };
}

function rejectScreenshot(screenshotId) {
  const meta = storage.loadScreenshotMeta(screenshotId);
  if (!meta) {
    return { status: 404, body: { error: "not_found" } };
  }
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
  };
}

function sendScreenshotImage(meta, res) {
  const file = storage.resolveScreenshotImagePath(meta);
  if (!file) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  res.type(meta.mimeType || "application/octet-stream").sendFile(file);
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
  (req, res) => {
    const result = uploadPendingSubmissionScreenshot(
      req.params.submissionId,
      req.file,
      sessionWebUser(req)
    );
    res.status(result.status).json(result.body);
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
  (req, res) => {
    const result = uploadApprovedBuildingScreenshot(
      req.params.buildingId,
      req.file,
      sessionWebUser(req)
    );
    res.status(result.status).json(result.body);
  }
);

app.delete("/api/my-screenshots/:screenshotId", requireWebUser, (req, res) => {
  const result = deleteOwnScreenshot(req.params.screenshotId, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

app.get("/api/my-screenshots/:screenshotId/image", requireWebUser, (req, res) => {
  const meta = storage.loadScreenshotMeta(req.params.screenshotId);
  if (!meta) {
    res.status(404).json({ error: "not_found" });
    return;
  }
  if (!isOwnedByWebUser(meta, sessionWebUser(req))) {
    res.status(403).json({ error: "not_owner" });
    return;
  }
  sendScreenshotImage(meta, res);
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
  sendScreenshotImage(meta, res);
});

app.post("/api/admin/screenshots/:screenshotId/approve", requireWebUser, requireAdmin, (req, res) => {
  const result = approveScreenshot(req.params.screenshotId);
  res.status(result.status).json(result.body);
});

app.post("/api/admin/screenshots/:screenshotId/reject", requireWebUser, requireAdmin, (req, res) => {
  const result = rejectScreenshot(req.params.screenshotId);
  res.status(result.status).json(result.body);
});

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
  const screenshots = storage.listScreenshotsForOwner("approved", id, "approved").map((meta) => ({
    screenshotId: meta.screenshotId,
    bytes: meta.bytes || 0,
    mimeType: meta.mimeType,
    uploadedAt: meta.uploadedAt,
    url: `/api/buildings/${encodeURIComponent(id)}/screenshots/${encodeURIComponent(meta.screenshotId)}`,
  }));
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
  sendScreenshotImage(meta, res);
});

app.get("/api/admin/pending", requireWebUser, requireAdmin, (_req, res) => {
  res.json({ submissions: listEnrichedPending() });
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
  const result = approveSubmission(req.params.submissionId, req.body?.id);
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

app.post("/api/v1/buildings/:id/download", downloadRateLimit, (req, res) => {
  const result = recordBuildingDownload(req.params.id);
  res.status(result.status).json(result.body);
});

app.post("/api/buildings/:id/upvote", requireWebUser, voteRateLimit, (req, res) => {
  const result = toggleBuildingUpvote(req.params.id, sessionWebUser(req));
  res.status(result.status).json(result.body);
});

app.use(express.static(path.join(__dirname, "..", "web")));

app.listen(PORT, () => {
  console.log(`Community marketplace listening on ${publicBaseUrl} (port ${PORT})`);
});
