import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** @param {string} dataDir */
export function createStorage(dataDir) {
  const root = path.resolve(dataDir);
  const dirs = {
    root,
    submissions: path.join(root, "submissions"),
    approved: path.join(root, "approved"),
    pending: path.join(root, "submissions", "pending"),
    rejected: path.join(root, "submissions", "rejected"),
    screenshots: path.join(root, "screenshots"),
  };
  for (const d of Object.values(dirs)) {
    fs.mkdirSync(d, { recursive: true });
  }

  const manifestPath = path.join(root, "manifest.json");

  function readManifest() {
    if (!fs.existsSync(manifestPath)) {
      return { version: 1, entries: [] };
    }
    return JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  }

  function writeManifest(manifest) {
    fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
  }

  function submissionDir(id, status = "pending") {
    const base = status === "approved" ? dirs.approved : status === "rejected" ? dirs.rejected : dirs.pending;
    return path.join(base, id);
  }

  function listPending() {
    if (!fs.existsSync(dirs.pending)) {
      return [];
    }
    return fs
      .readdirSync(dirs.pending, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => loadSubmissionMeta(e.name, "pending"))
      .filter(Boolean);
  }

  function listRejected() {
    if (!fs.existsSync(dirs.rejected)) {
      return [];
    }
    return fs
      .readdirSync(dirs.rejected, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => loadSubmissionMeta(e.name, "rejected"))
      .filter(Boolean);
  }

  /**
   * @param {string} id
   * @param {"pending"|"approved"|"rejected"} status
   */
  function loadSubmissionMeta(id, status = "pending") {
    const dir = submissionDir(id, status);
    const metaPath = path.join(dir, "meta.json");
    if (!fs.existsSync(metaPath)) {
      return null;
    }
    return JSON.parse(fs.readFileSync(metaPath, "utf8"));
  }

  /**
   * @param {string} id
   */
  function approvedDir(id) {
    return path.join(dirs.approved, id);
  }

  function approvedPaths(id) {
    const dir = approvedDir(id);
    return {
      dir,
      building: path.join(dir, "building.json"),
      prefab: path.join(dir, "prefab.prefab.json"),
      icon: path.join(dir, "icon.png"),
      meta: path.join(dir, "meta.json"),
    };
  }

  function screenshotDir(screenshotId) {
    return path.join(dirs.screenshots, screenshotId);
  }

  function screenshotPaths(screenshotId, ext) {
    const dir = screenshotDir(screenshotId);
    return {
      dir,
      meta: path.join(dir, "meta.json"),
      image: path.join(dir, `image.${ext}`),
      card: path.join(dir, "card.webp"),
    };
  }

  function loadScreenshotMeta(screenshotId) {
    const metaPath = path.join(screenshotDir(screenshotId), "meta.json");
    if (!fs.existsSync(metaPath)) {
      return null;
    }
    return JSON.parse(fs.readFileSync(metaPath, "utf8"));
  }

  function writeScreenshotMeta(meta) {
    const dir = screenshotDir(meta.screenshotId);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, "meta.json"), JSON.stringify(meta, null, 2));
  }

  /**
   * @param {{ screenshotId?: string, ext?: string }} meta
   * @param {"full"|"card"} [variant]
   */
  function resolveScreenshotImagePath(meta, variant = "full") {
    if (!meta?.screenshotId || !meta?.ext) {
      return null;
    }
    const paths = screenshotPaths(meta.screenshotId, meta.ext);
    if (variant === "card" && fs.existsSync(paths.card)) {
      return paths.card;
    }
    return fs.existsSync(paths.image) ? paths.image : null;
  }

  function listAllScreenshotMetas() {
    if (!fs.existsSync(dirs.screenshots)) {
      return [];
    }
    return fs
      .readdirSync(dirs.screenshots, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => loadScreenshotMeta(e.name))
      .filter(Boolean);
  }

  /**
   * @param {"pending"|"approved"} ownerKind
   * @param {string} ownerId
   * @param {"pending"|"approved"|null} [status]
   */
  function listScreenshotsForOwner(ownerKind, ownerId, status = null) {
    return listAllScreenshotMetas().filter((meta) => {
      if (meta.ownerKind !== ownerKind || meta.ownerId !== ownerId) {
        return false;
      }
      if (status && meta.status !== status) {
        return false;
      }
      return true;
    });
  }

  function listPendingScreenshots() {
    return listAllScreenshotMetas()
      .filter((meta) => meta.status === "pending")
      .sort((a, b) => String(b.uploadedAt || "").localeCompare(String(a.uploadedAt || "")));
  }

  function deleteScreenshot(screenshotId) {
    const dir = screenshotDir(screenshotId);
    if (fs.existsSync(dir)) {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  }

  /**
   * @param {"pending"|"approved"} ownerKind
   * @param {string} ownerId
   */
  function deleteScreenshotsForOwner(ownerKind, ownerId) {
    for (const meta of listScreenshotsForOwner(ownerKind, ownerId)) {
      deleteScreenshot(meta.screenshotId);
    }
  }

  /**
   * When a pending submission is approved, re-point its screenshots to the published building id.
   * @param {string} submissionId
   * @param {string} buildingId
   */
  function reassignScreenshotsToApproved(submissionId, buildingId) {
    for (const meta of listScreenshotsForOwner("pending", submissionId)) {
      meta.ownerKind = "approved";
      meta.ownerId = buildingId;
      writeScreenshotMeta(meta);
    }
  }

  /**
   * @param {"pending"|"approved"} ownerKind
   * @param {string} ownerId
   */
  function countScreenshotsForOwner(ownerKind, ownerId) {
    return listScreenshotsForOwner(ownerKind, ownerId).length;
  }

  return {
    dirs,
    readManifest,
    writeManifest,
    submissionDir,
    listPending,
    listRejected,
    loadSubmissionMeta,
    approvedDir,
    approvedPaths,
    screenshotDir,
    screenshotPaths,
    loadScreenshotMeta,
    writeScreenshotMeta,
    resolveScreenshotImagePath,
    listAllScreenshotMetas,
    listScreenshotsForOwner,
    listPendingScreenshots,
    deleteScreenshot,
    deleteScreenshotsForOwner,
    reassignScreenshotsToApproved,
    countScreenshotsForOwner,
  };
}
