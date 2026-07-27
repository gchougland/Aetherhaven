import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const DEFAULT_RETENTION_DAYS = 30;

export function createSupportBundles(dataDir) {
  const rootDir = path.join(dataDir, "support-bundles");
  const retentionDays = Math.max(
    1,
    Number(process.env.SUPPORT_BUNDLE_RETENTION_DAYS || DEFAULT_RETENTION_DAYS),
  );

  function ensureRoot() {
    fs.mkdirSync(rootDir, { recursive: true });
  }

  function bundleDir(bundleId) {
    return path.join(rootDir, bundleId);
  }

  function metaPath(bundleId) {
    return path.join(bundleDir(bundleId), "meta.json");
  }

  function zipPath(bundleId) {
    return path.join(bundleDir(bundleId), "bundle.zip");
  }

  function pruneExpired() {
    ensureRoot();
    const cutoff = Date.now() - retentionDays * 24 * 60 * 60 * 1000;
    for (const entry of fs.readdirSync(rootDir, { withFileTypes: true })) {
      if (!entry.isDirectory()) {
        continue;
      }
      const id = entry.name;
      const metaFile = metaPath(id);
      if (!fs.existsSync(metaFile)) {
        continue;
      }
      try {
        const meta = JSON.parse(fs.readFileSync(metaFile, "utf8"));
        const uploadedAt = Date.parse(meta.uploadedAt || "");
        if (!Number.isFinite(uploadedAt) || uploadedAt < cutoff) {
          fs.rmSync(bundleDir(id), { recursive: true, force: true });
        }
      } catch {
        // Keep malformed entries for manual cleanup.
      }
    }
  }

  function saveUpload({ playerUuid, playerName, note, modVersion, serverUuid, worldNames, zipBuffer }) {
    ensureRoot();
    pruneExpired();

    const bundleId = crypto.randomUUID();
    const dir = bundleDir(bundleId);
    fs.mkdirSync(dir, { recursive: true });

    const uploadedAt = new Date().toISOString();
    const meta = {
      bundleId,
      playerUuid: String(playerUuid || "").trim().toLowerCase(),
      playerName: String(playerName || "Unknown").trim() || "Unknown",
      note: String(note || "").trim(),
      modVersion: String(modVersion || "").trim(),
      serverUuid: serverUuid ? String(serverUuid).trim() : "",
      worldNames: Array.isArray(worldNames) ? worldNames.map(String) : [],
      uploadedAt,
      sizeBytes: zipBuffer.length,
    };

    fs.writeFileSync(metaPath(bundleId), JSON.stringify(meta, null, 2));
    fs.writeFileSync(zipPath(bundleId), zipBuffer);
    return meta;
  }

  function listBundles() {
    ensureRoot();
    pruneExpired();
    const rows = [];
    for (const entry of fs.readdirSync(rootDir, { withFileTypes: true })) {
      if (!entry.isDirectory()) {
        continue;
      }
      const id = entry.name;
      const metaFile = metaPath(id);
      if (!fs.existsSync(metaFile)) {
        continue;
      }
      try {
        rows.push(JSON.parse(fs.readFileSync(metaFile, "utf8")));
      } catch {
        // skip
      }
    }
    rows.sort((a, b) => Date.parse(b.uploadedAt || 0) - Date.parse(a.uploadedAt || 0));
    return rows;
  }

  function getBundle(bundleId) {
    const id = String(bundleId || "").trim();
    if (!id || id.includes("..") || id.includes("/") || id.includes("\\")) {
      return null;
    }
    const metaFile = metaPath(id);
    const zipFile = zipPath(id);
    if (!fs.existsSync(metaFile) || !fs.existsSync(zipFile)) {
      return null;
    }
    try {
      const meta = JSON.parse(fs.readFileSync(metaFile, "utf8"));
      return { meta, zipFile };
    } catch {
      return null;
    }
  }

  function deleteBundle(bundleId) {
    const id = String(bundleId || "").trim();
    if (!id || id.includes("..") || id.includes("/") || id.includes("\\")) {
      return false;
    }
    const dir = bundleDir(id);
    if (!fs.existsSync(dir)) {
      return false;
    }
    fs.rmSync(dir, { recursive: true, force: true });
    return true;
  }

  return {
    saveUpload,
    listBundles,
    getBundle,
    deleteBundle,
    pruneExpired,
  };
}
