import assert from "node:assert/strict";
import test from "node:test";
import { requestFavoriteIdentityUuids, sessionIdentityUuids } from "../server/playerIdentity.js";

const PROFILE_UUID = "11111111-2222-3333-4444-555555555555";
const SUB_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

test("sessionIdentityUuids prefers profile uuid then sub", () => {
  const uuids = sessionIdentityUuids({
    sub: SUB_UUID,
    profile: { uuid: PROFILE_UUID, username: "Player" },
  });
  assert.deepEqual(uuids, [PROFILE_UUID, SUB_UUID]);
});

test("requestFavoriteIdentityUuids uses session ids before header", () => {
  const uuids = requestFavoriteIdentityUuids({
    session: {
      user: {
        sub: SUB_UUID,
        profile: { uuid: PROFILE_UUID },
      },
    },
    get(header) {
      return header === "X-Player-Uuid" ? "ffffffff-ffff-ffff-ffff-ffffffffffff" : "";
    },
  });
  assert.deepEqual(uuids, [PROFILE_UUID, SUB_UUID]);
});

test("requestFavoriteIdentityUuids falls back to in game header", () => {
  const uuids = requestFavoriteIdentityUuids({
    session: {},
    get(header) {
      return header === "X-Player-Uuid" ? SUB_UUID : "";
    },
  });
  assert.deepEqual(uuids, [SUB_UUID]);
});
