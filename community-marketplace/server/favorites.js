import fs from "node:fs";
import path from "node:path";

/** @param {string} dataDir */
export function createFavorites(dataDir) {
  const usersDir = path.join(path.resolve(dataDir), "favorites", "users");
  fs.mkdirSync(usersDir, { recursive: true });

  function userPath(userUuid) {
    const safe = String(userUuid || "")
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, "");
    if (!safe) {
      return null;
    }
    return path.join(usersDir, `${safe}.json`);
  }

  function readUserFavorites(userUuid) {
    const file = userPath(userUuid);
    if (!file || !fs.existsSync(file)) {
      return new Set();
    }
    const data = JSON.parse(fs.readFileSync(file, "utf8"));
    const ids = Array.isArray(data.buildingIds) ? data.buildingIds : [];
    return new Set(ids);
  }

  function writeUserFavorites(userUuid, buildingIds) {
    const file = userPath(userUuid);
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

  function getUserFavorites(userUuid) {
    return readUserFavorites(userUuid);
  }

  function userHasFavorited(buildingId, userUuid) {
    return readUserFavorites(userUuid).has(buildingId);
  }

  /**
   * @param {string} buildingId
   * @param {string} userUuid
   * @returns {{ userHasFavorited: boolean, buildingIds: string[] }}
   */
  function toggleFavorite(buildingId, userUuid) {
    const favorites = readUserFavorites(userUuid);
    const hadFavorite = favorites.has(buildingId);
    if (hadFavorite) {
      favorites.delete(buildingId);
    } else {
      favorites.add(buildingId);
    }
    writeUserFavorites(userUuid, favorites);
    return { userHasFavorited: !hadFavorite, buildingIds: [...favorites] };
  }

  function removeBuilding(buildingId) {
    if (!fs.existsSync(usersDir)) {
      return;
    }
    for (const name of fs.readdirSync(usersDir)) {
      if (!name.endsWith(".json")) {
        continue;
      }
      const file = path.join(usersDir, name);
      const data = JSON.parse(fs.readFileSync(file, "utf8"));
      const ids = new Set(Array.isArray(data.buildingIds) ? data.buildingIds : []);
      if (!ids.has(buildingId)) {
        continue;
      }
      ids.delete(buildingId);
      if (ids.size === 0) {
        fs.unlinkSync(file);
      } else {
        fs.writeFileSync(file, JSON.stringify({ buildingIds: [...ids] }, null, 2));
      }
    }
  }

  return {
    getUserFavorites,
    userHasFavorited,
    toggleFavorite,
    removeBuilding,
  };
}
