import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  applyCoverScreenshotIfUnset,
  autoSetCoverFromExistingApprovedScreenshots,
  readApprovedCoverScreenshotId,
} from "../server/coverScreenshots.js";
import { createStorage } from "../server/storage.js";

const BUILDING_ID = "plot_community_aaaabbbb_cozy_cottage";
const SHOT_A = "11111111-1111-1111-1111-111111111111";
const SHOT_B = "22222222-2222-2222-2222-222222222222";

function tempDataDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-cover-"));
}

function writeApprovedBuilding(storage, id) {
  const paths = storage.approvedPaths(id);
  fs.mkdirSync(paths.dir, { recursive: true });
  fs.writeFileSync(paths.building, JSON.stringify({ id, displayName: "Cottage" }, null, 2));
  fs.writeFileSync(paths.prefab, Buffer.from("{}"));
  fs.writeFileSync(
    paths.meta,
    JSON.stringify({
      id,
      displayName: "Cottage",
      creatorUuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
      creatorName: "Creator",
      status: "approved",
      approvedAt: new Date().toISOString(),
    }),
  );
  const manifest = storage.readManifest();
  manifest.entries = manifest.entries.filter((entry) => entry.id !== id);
  manifest.entries.push({
    id,
    displayName: "Cottage",
    creatorUuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    creatorName: "Creator",
    approvedAt: new Date().toISOString(),
    prefabBytes: 2,
    blockIdVersion: 8,
    version: "1",
  });
  storage.writeManifest(manifest);
}

function writeApprovedScreenshot(storage, buildingId, screenshotId, uploadedAt, approvedAt) {
  const paths = storage.screenshotPaths(screenshotId, "webp");
  fs.mkdirSync(paths.dir, { recursive: true });
  fs.writeFileSync(paths.image, Buffer.from("full"));
  fs.writeFileSync(paths.card, Buffer.from("card"));
  storage.writeScreenshotMeta({
    screenshotId,
    ownerKind: "approved",
    ownerId: buildingId,
    creatorUuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    creatorName: "Creator",
    status: "approved",
    uploadedAt,
    approvedAt,
    mimeType: "image/webp",
    ext: "webp",
    bytes: 4,
    cardBytes: 4,
  });
}

test("applyCoverScreenshotIfUnset sets the first approved screenshot as card image", () => {
  const storage = createStorage(tempDataDir());
  writeApprovedBuilding(storage, BUILDING_ID);
  writeApprovedScreenshot(storage, BUILDING_ID, SHOT_A, "2026-01-01T00:00:00.000Z", "2026-01-01T00:01:00.000Z");

  const applied = applyCoverScreenshotIfUnset(storage, BUILDING_ID, SHOT_A);
  assert.equal(applied, true);
  assert.equal(readApprovedCoverScreenshotId(storage, BUILDING_ID), SHOT_A);

  const manifest = storage.readManifest();
  const entry = manifest.entries.find((candidate) => candidate.id === BUILDING_ID);
  assert.equal(entry.coverScreenshotId, SHOT_A);
});

test("applyCoverScreenshotIfUnset does not replace an existing valid cover", () => {
  const storage = createStorage(tempDataDir());
  writeApprovedBuilding(storage, BUILDING_ID);
  writeApprovedScreenshot(storage, BUILDING_ID, SHOT_A, "2026-01-01T00:00:00.000Z", "2026-01-01T00:01:00.000Z");
  writeApprovedScreenshot(storage, BUILDING_ID, SHOT_B, "2026-01-02T00:00:00.000Z", "2026-01-02T00:01:00.000Z");

  assert.equal(applyCoverScreenshotIfUnset(storage, BUILDING_ID, SHOT_A), true);
  assert.equal(applyCoverScreenshotIfUnset(storage, BUILDING_ID, SHOT_B), false);
  assert.equal(readApprovedCoverScreenshotId(storage, BUILDING_ID), SHOT_A);
});

test("autoSetCoverFromExistingApprovedScreenshots uses the earliest approved screenshot", () => {
  const storage = createStorage(tempDataDir());
  writeApprovedBuilding(storage, BUILDING_ID);
  writeApprovedScreenshot(storage, BUILDING_ID, SHOT_B, "2026-01-02T00:00:00.000Z", "2026-01-02T00:01:00.000Z");
  writeApprovedScreenshot(storage, BUILDING_ID, SHOT_A, "2026-01-01T00:00:00.000Z", "2026-01-01T00:01:00.000Z");

  const applied = autoSetCoverFromExistingApprovedScreenshots(storage, BUILDING_ID);
  assert.equal(applied, true);
  assert.equal(readApprovedCoverScreenshotId(storage, BUILDING_ID), SHOT_A);
});
