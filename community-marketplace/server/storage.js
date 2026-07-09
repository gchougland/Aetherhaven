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

  return {
    dirs,
    readManifest,
    writeManifest,
    submissionDir,
    listPending,
    loadSubmissionMeta,
    approvedDir,
    approvedPaths,
  };
}
