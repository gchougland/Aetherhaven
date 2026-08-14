import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createDownloads, normalizeInstallInstanceId } from "../server/downloads.js";

const INSTANCE_A = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
const INSTANCE_B = "11111111-2222-3333-4444-555555555555";
const BUILDING_A = "plot_community_aaaabbbb_cozy_cottage";

function tempDataDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-downloads-"));
}

test("normalizeInstallInstanceId accepts uuids only", () => {
  assert.equal(normalizeInstallInstanceId(INSTANCE_A), INSTANCE_A);
  assert.equal(normalizeInstallInstanceId(INSTANCE_A.toUpperCase()), INSTANCE_A);
  assert.equal(normalizeInstallInstanceId(""), "");
  assert.equal(normalizeInstallInstanceId("not-a-uuid"), "");
  assert.equal(normalizeInstallInstanceId("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee-extra"), "");
});

test("first increment records a download", () => {
  const downloads = createDownloads(tempDataDir());

  const result = downloads.increment(BUILDING_A, INSTANCE_A);
  assert.equal(result.downloadCount, 1);
  assert.equal(downloads.getCount(BUILDING_A), 1);
});

test("same instance does not increment again", () => {
  const downloads = createDownloads(tempDataDir());

  downloads.increment(BUILDING_A, INSTANCE_A);
  const again = downloads.increment(BUILDING_A, INSTANCE_A);
  assert.equal(again.downloadCount, 1);
  assert.equal(downloads.getCount(BUILDING_A), 1);
});

test("different instance increments", () => {
  const downloads = createDownloads(tempDataDir());

  downloads.increment(BUILDING_A, INSTANCE_A);
  const second = downloads.increment(BUILDING_A, INSTANCE_B);
  assert.equal(second.downloadCount, 2);
  assert.equal(downloads.getCount(BUILDING_A), 2);
});

test("missing instance id does not increment", () => {
  const downloads = createDownloads(tempDataDir());

  const missing = downloads.increment(BUILDING_A, "");
  assert.equal(missing.downloadCount, 0);
  const invalid = downloads.increment(BUILDING_A, "nope");
  assert.equal(invalid.downloadCount, 0);
  assert.equal(downloads.getCount(BUILDING_A), 0);
});

test("removeBuilding clears count and seen keys", () => {
  const downloads = createDownloads(tempDataDir());

  downloads.increment(BUILDING_A, INSTANCE_A);
  downloads.removeBuilding(BUILDING_A);
  assert.equal(downloads.getCount(BUILDING_A), 0);

  const after = downloads.increment(BUILDING_A, INSTANCE_A);
  assert.equal(after.downloadCount, 1);
});
