import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createFavorites } from "../server/favorites.js";

const USER_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
const BUILDING_A = "plot_community_aaaabbbb_cozy_cottage";
const BUILDING_B = "plot_community_ccccdddd_tall_tower";

function tempDataDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-favorites-"));
}

test("toggleFavorite adds and removes building ids", () => {
  const dataDir = tempDataDir();
  const favorites = createFavorites(dataDir);

  const first = favorites.toggleFavorite(BUILDING_A, USER_UUID);
  assert.equal(first.userHasFavorited, true);
  assert.deepEqual(first.buildingIds, [BUILDING_A]);
  assert.equal(favorites.userHasFavorited(BUILDING_A, USER_UUID), true);

  const second = favorites.toggleFavorite(BUILDING_A, USER_UUID);
  assert.equal(second.userHasFavorited, false);
  assert.deepEqual(second.buildingIds, []);
  assert.equal(favorites.userHasFavorited(BUILDING_A, USER_UUID), false);
});

test("getUserFavorites returns persisted ids", () => {
  const dataDir = tempDataDir();
  const favorites = createFavorites(dataDir);

  favorites.toggleFavorite(BUILDING_A, USER_UUID);
  favorites.toggleFavorite(BUILDING_B, USER_UUID);

  const set = favorites.getUserFavorites(USER_UUID);
  assert.equal(set.size, 2);
  assert.equal(set.has(BUILDING_A), true);
  assert.equal(set.has(BUILDING_B), true);
});

test("removeBuilding clears id from all users", () => {
  const dataDir = tempDataDir();
  const favorites = createFavorites(dataDir);

  favorites.toggleFavorite(BUILDING_A, USER_UUID);
  favorites.removeBuilding(BUILDING_A);

  assert.equal(favorites.userHasFavorited(BUILDING_A, USER_UUID), false);
  assert.deepEqual([...favorites.getUserFavorites(USER_UUID)], []);
});
