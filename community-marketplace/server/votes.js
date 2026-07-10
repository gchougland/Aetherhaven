import fs from "node:fs";
import path from "node:path";

/** @param {string} dataDir */
export function createVotes(dataDir) {
  const root = path.join(path.resolve(dataDir), "votes");
  const countsPath = path.join(root, "counts.json");
  const votersDir = path.join(root, "voters");

  fs.mkdirSync(votersDir, { recursive: true });

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

  function voterPath(voterUuid) {
    const safe = String(voterUuid || "")
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, "");
    if (!safe) {
      return null;
    }
    return path.join(votersDir, `${safe}.json`);
  }

  function readUserVotes(voterUuid) {
    const file = voterPath(voterUuid);
    if (!file || !fs.existsSync(file)) {
      return new Set();
    }
    const data = JSON.parse(fs.readFileSync(file, "utf8"));
    const ids = Array.isArray(data.buildingIds) ? data.buildingIds : [];
    return new Set(ids);
  }

  function writeUserVotes(voterUuid, buildingIds) {
    const file = voterPath(voterUuid);
    if (!file) {
      return;
    }
    if (buildingIds.size === 0) {
      if (fs.existsSync(file)) {
        fs.unlinkSync(file);
      }
      return;
    }
    fs.writeFileSync(file, JSON.stringify({ buildingIds: [...buildingIds] }, null, 2));
  }

  function getCounts() {
    return { ...readCounts().counts };
  }

  function getCount(buildingId) {
    const counts = readCounts().counts;
    return counts[buildingId] || 0;
  }

  function getUserVotes(voterUuid) {
    return readUserVotes(voterUuid);
  }

  /**
   * @param {string} buildingId
   * @param {string} voterUuid
   * @returns {{ upvoteCount: number, userHasUpvoted: boolean }}
   */
  function toggleVote(buildingId, voterUuid) {
    const countsData = readCounts();
    const userVotes = readUserVotes(voterUuid);
    const hadVote = userVotes.has(buildingId);
    let count = countsData.counts[buildingId] || 0;

    if (hadVote) {
      userVotes.delete(buildingId);
      count = Math.max(0, count - 1);
      if (count === 0) {
        delete countsData.counts[buildingId];
      } else {
        countsData.counts[buildingId] = count;
      }
    } else {
      userVotes.add(buildingId);
      count += 1;
      countsData.counts[buildingId] = count;
    }

    writeCounts(countsData);
    writeUserVotes(voterUuid, userVotes);

    return { upvoteCount: count, userHasUpvoted: !hadVote };
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
    getUserVotes,
    toggleVote,
    removeBuilding,
  };
}
