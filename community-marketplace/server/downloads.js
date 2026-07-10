import fs from "node:fs";
import path from "node:path";

/** Monotonic install counters for approved community buildings. */
export function createDownloads(dataDir) {
  const root = path.join(path.resolve(dataDir), "downloads");
  const countsPath = path.join(root, "counts.json");

  fs.mkdirSync(root, { recursive: true });

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

  function getCounts() {
    return { ...readCounts().counts };
  }

  function getCount(buildingId) {
    const counts = readCounts().counts;
    return counts[buildingId] || 0;
  }

  /**
   * @param {string} buildingId
   * @returns {{ downloadCount: number }}
   */
  function increment(buildingId) {
    const countsData = readCounts();
    const count = (countsData.counts[buildingId] || 0) + 1;
    countsData.counts[buildingId] = count;
    writeCounts(countsData);
    return { downloadCount: count };
  }

  function removeBuilding(buildingId) {
    const countsData = readCounts();
    if (buildingId in countsData.counts) {
      delete countsData.counts[buildingId];
      writeCounts(countsData);
    }
  }

  return {
    getCounts,
    getCount,
    increment,
    removeBuilding,
  };
}
