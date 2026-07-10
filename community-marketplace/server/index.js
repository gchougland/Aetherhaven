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
  isBlockIdCompatible,
  MAX_BUILDING_JSON_BYTES,
  MAX_ICON_BYTES,
  MAX_PREFAB_BYTES,
  normalizeCommunityId,
  readPrefabBlockIdVersion,
  assignCommunityCatalogId,
  validateSubmissionBuilding,
} from "./validation.js";
import { createSubmissionRateLimit } from "./submissionRateLimit.js";

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

const submissionRateLimit = createSubmissionRateLimit({
  maxPerPlayer: Number(process.env.SUBMISSION_MAX_PER_PLAYER_PER_DAY || 10),
  maxPerIp: Number(process.env.SUBMISSION_MAX_PER_IP_PER_HOUR || 30),
});

function buildManifestEntry(id, meta, prefabBytes) {
  return {
    id,
    displayName: meta.displayName,
    creatorUuid: meta.creatorUuid,
    creatorName: meta.creatorName,
    styleId: meta.styleId || "misc",
    blockIdVersion: meta.blockIdVersion,
    prefabBytes,
    version: meta.version || "1",
    approvedAt: meta.approvedAt,
  };
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
  fs.writeFileSync(approved.building, JSON.stringify(building, null, 2));

  const approvedMeta = {
    ...meta,
    id,
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
  return { status: 200, body: { id, status: "approved" } };
}

function rejectSubmission(submissionId, reason) {
  const pendingDir = storage.submissionDir(submissionId, "pending");
  if (!fs.existsSync(pendingDir)) {
    return { status: 404, body: { error: "not_found" } };
  }
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
  manifest.entries = manifest.entries.filter((e) => e.id !== id);
  manifest.version = (manifest.version || 0) + 1;
  storage.writeManifest(manifest);
  return { status: 200, body: { id, status: "deleted" } };
}

// --- Public API (mod + downloads) ---

app.get("/api/v1/health", (_req, res) => {
  res.json({ ok: true });
});

function sendManifest(req, res) {
  const clientBlockIdVersion = Number(req.query.blockIdVersion || 0);
  const manifest = storage.readManifest();
  const entries = manifest.entries.map((e) => {
    const paths = storage.approvedPaths(e.id);
    let prefabBytes = e.prefabBytes || 0;
    if (!prefabBytes && fs.existsSync(paths.prefab)) {
      prefabBytes = fs.statSync(paths.prefab).size;
    }
    const compatible = isBlockIdCompatible(e.blockIdVersion, clientBlockIdVersion);
    return {
      ...e,
      prefabBytes,
      compatible,
      iconUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/icon.png`,
      buildingUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/building.json`,
      prefabUrl: `/api/v1/buildings/${encodeURIComponent(e.id)}/prefab.json`,
    };
  });
  res.json({ version: manifest.version, entries });
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
        creatorUuid,
        creatorName,
        styleId: building.styleId || "misc",
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

function requireWebUser(req, res, next) {
  if (!req.session?.user) {
    res.redirect("/?login=required");
    return;
  }
  next();
}

function requireAdmin(req, res, next) {
  const uuid = req.session?.user?.profile?.uuid?.toLowerCase();
  if (!uuid || !ADMIN_UUIDS.has(uuid)) {
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

app.get("/api/my-submissions", requireWebUser, (_req, res) => {
  res.json({ submissions: storage.listPending() });
});

app.get("/api/admin/pending", requireWebUser, requireAdmin, (_req, res) => {
  res.json({ submissions: storage.listPending() });
});

app.post("/api/admin/approve/:submissionId", requireWebUser, requireAdmin, (req, res) => {
  const result = approveSubmission(req.params.submissionId, req.body?.id);
  res.status(result.status).json(result.body);
});

app.post("/api/admin/reject/:submissionId", requireWebUser, requireAdmin, (req, res) => {
  const result = rejectSubmission(req.params.submissionId, req.body?.reason);
  res.status(result.status).json(result.body);
});

app.get("/api/admin/catalog", requireWebUser, requireAdmin, (_req, res) => {
  const manifest = storage.readManifest();
  res.json({ version: manifest.version, entries: manifest.entries });
});

app.post("/api/admin/delete/:buildingId", requireWebUser, requireAdmin, (req, res) => {
  const result = deleteApprovedBuilding(req.params.buildingId);
  res.status(result.status).json(result.body);
});

app.get("/api/catalog", sendManifest);

app.use(express.static(path.join(__dirname, "..", "web")));

app.listen(PORT, () => {
  console.log(`Community marketplace listening on ${publicBaseUrl} (port ${PORT})`);
});
