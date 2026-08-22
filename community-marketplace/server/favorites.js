import fs from "node:fs";
import path from "node:path";
import { canonicalFavoriteUuid } from "./playerIdentity.js";

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

  /**
   * Merges alias favorite files into the canonical id and removes stale alias files.
   *
   * @param {string[]} userUuids
   * @returns {Set<string>}
   */
  function consolidateUserFavorites(userUuids) {
    const canonical = canonicalFavoriteUuid(userUuids);
    if (!canonical) {
      return new Set();
    }
    const merged = new Set();
    for (const id of userUuids) {
      for (const favoriteId of readUserFavorites(id)) {
        merged.add(favoriteId);
      }
    }
    writeUserFavorites(canonical, merged);
    for (const alias of userUuids) {
      if (alias === canonical) {
        continue;
      }
      const aliasFile = userPath(alias);
      if (aliasFile && fs.existsSync(aliasFile)) {
        fs.unlinkSync(aliasFile);
      }
    }
    return merged;
  }

  function getUserFavorites(userUuid) {
    return readUserFavorites(userUuid);
  }

  /**
   * @param {string[]} userUuids
   * @returns {Set<string>}
   */
  function getMergedUserFavorites(userUuids) {
    if (!userUuids.length) {
      return new Set();
    }
    return consolidateUserFavorites(userUuids);
  }

  function userHasFavorited(buildingId, userUuid) {
    return readUserFavorites(userUuid).has(buildingId);
  }

  /**
   * @param {string} buildingId
   * @param {string[]} userUuids
   * @returns {boolean}
   */
  function userHasFavoritedAny(buildingId, userUuids) {
    return getMergedUserFavorites(userUuids).has(buildingId);
  }

  /**
   * @param {string} buildingId
   * @param {string} userUuid
   * @returns {{ userHasFavorited: boolean, buildingIds: string[] }}
   */
  function toggleFavorite(buildingId, userUuid) {
    return toggleFavoriteForUserUuids(buildingId, userUuid ? [userUuid] : []);
  }

  /**
   * @param {string} buildingId
   * @param {string[]} userUuids
   * @returns {{ userHasFavorited: boolean, buildingIds: string[] }}
   */
  function toggleFavoriteForUserUuids(buildingId, userUuids) {
    const canonical = canonicalFavoriteUuid(userUuids);
    if (!canonical) {
      return { userHasFavorited: false, buildingIds: [] };
    }
    const favorites = consolidateUserFavorites(userUuids);
    const hadFavorite = favorites.has(buildingId);
    if (hadFavorite) {
      favorites.delete(buildingId);
    } else {
      favorites.add(buildingId);
    }
    writeUserFavorites(canonical, favorites);
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
    getMergedUserFavorites,
    userHasFavorited,
    userHasFavoritedAny,
    toggleFavorite,
    toggleFavoriteForUserUuids,
    removeBuilding,
  };
}
