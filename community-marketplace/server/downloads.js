import fs from "node:fs";
import path from "node:path";

const INSTANCE_ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** @param {unknown} raw */
export function normalizeInstallInstanceId(raw) {
  const id = String(raw || "")
    .trim()
    .toLowerCase();
  return INSTANCE_ID_RE.test(id) ? id : "";
}

/** Install counters for approved community buildings, unique per save/server instance. */
export function createDownloads(dataDir) {
  const root = path.join(path.resolve(dataDir), "downloads");
  const countsPath = path.join(root, "counts.json");
  const seenDir = path.join(root, "seen");

  fs.mkdirSync(root, { recursive: true });
  fs.mkdirSync(seenDir, { recursive: true });

  function readCounts() {
    if (!fs.existsSync(countsPath)) {
      return { version: 1, counts: {} };
    }
    const data = JSON.parse(fs.readFileSync(countsPath, "utf8"));
    return {
      version: data.version ?? 1,
      counts: data.counts && typeof data.counts === "object" ? data.counts : {},
    };
  }

  function writeCounts(data) {
    fs.writeFileSync(countsPath, JSON.stringify(data, null, 2));
  }

  function seenFile(buildingId) {
    const safe = String(buildingId || "")
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9_-]/g, "");
    if (!safe) {
      return null;
    }
    return path.join(seenDir, `${safe}.json`);
  }

  function readSeen(buildingId) {
    const file = seenFile(buildingId);
    if (!file || !fs.existsSync(file)) {
      return new Set();
    }
    const data = JSON.parse(fs.readFileSync(file, "utf8"));
    const keys = Array.isArray(data.keys) ? data.keys : [];
    return new Set(keys.map((key) => String(key).trim().toLowerCase()).filter(Boolean));
  }

  function writeSeen(buildingId, keys) {
    const file = seenFile(buildingId);
    if (!file) {
      return;
    }
    if (keys.size === 0) {
      if (fs.existsSync(file)) {
        fs.unlinkSync(file);
      }
      return;
    }
    fs.writeFileSync(file, JSON.stringify({ keys: [...keys] }, null, 2));
  }

  function getCounts() {
    return { ...readCounts().counts };
  }

  function getCount(buildingId) {
    const counts = readCounts().counts;
    return counts[buildingId] || 0;
  }

  /**
   * @param {string} buildingId
   * @param {string} [instanceId]
   * @returns {{ downloadCount: number }}
   */
  function increment(buildingId, instanceId) {
    const countsData = readCounts();
    const current = countsData.counts[buildingId] || 0;
    const instance = normalizeInstallInstanceId(instanceId);
    if (!instance) {
      return { downloadCount: current };
    }
    const seen = readSeen(buildingId);
    if (seen.has(instance)) {
      return { downloadCount: current };
    }
    seen.add(instance);
    const count = current + 1;
    countsData.counts[buildingId] = count;
    writeCounts(countsData);
    writeSeen(buildingId, seen);
    return { downloadCount: count };
  }

  function removeBuilding(buildingId) {
    const countsData = readCounts();
    if (buildingId in countsData.counts) {
      delete countsData.counts[buildingId];
      writeCounts(countsData);
    }
    const file = seenFile(buildingId);
    if (file && fs.existsSync(file)) {
      fs.unlinkSync(file);
    }
  }

  return {
    getCounts,
    getCount,
    increment,
    removeBuilding,
  };
}
