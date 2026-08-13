import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createStorage } from "../server/storage.js";
import { resolvePreviewScreenshotTarget } from "../server/previewScreenshot.js";

const SUBMISSION_ID = "11111111-1111-4111-8111-111111111111";
const BUILDING_ID = "plot_community_aaaabbbb_cozy_cottage";
const PORT = 3847;

function tempDataDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-preview-"));
}

function writePendingSubmission(storage, submissionId) {
  const dir = storage.submissionDir(submissionId, "pending");
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, "prefab.prefab.json"), "{}");
  fs.writeFileSync(
    path.join(dir, "meta.json"),
    JSON.stringify({
      submissionId,
      proposedId: BUILDING_ID,
      displayName: "Cottage",
      creatorUuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
      creatorName: "Creator",
      status: "pending",
    }),
  );
}

function writeApprovedBuilding(storage, buildingId, submissionId) {
  const paths = storage.approvedPaths(buildingId);
  fs.mkdirSync(paths.dir, { recursive: true });
  fs.writeFileSync(paths.prefab, Buffer.from("{}"));
  fs.writeFileSync(paths.building, JSON.stringify({ id: buildingId, displayName: "Cottage" }, null, 2));
  fs.writeFileSync(
    paths.meta,
    JSON.stringify({
      submissionId,
      id: buildingId,
      displayName: "Cottage",
      creatorUuid: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
      creatorName: "Creator",
      status: "approved",
    }),
  );
}

test("resolvePreviewScreenshotTarget uses pending prefab when the submission still exists", () => {
  const storage = createStorage(tempDataDir());
  writePendingSubmission(storage, SUBMISSION_ID);

  const target = resolvePreviewScreenshotTarget(storage, PORT, "pending", SUBMISSION_ID);
  assert.equal(target?.ownerKind, "pending");
  assert.equal(target?.ownerId, SUBMISSION_ID);
  assert.equal(
    target?.prefabUrl,
    `http://127.0.0.1:${PORT}/internal/pending-prefab/${SUBMISSION_ID}.json`,
  );
});

test("resolvePreviewScreenshotTarget follows a pending job to the approved building after approve", () => {
  const storage = createStorage(tempDataDir());
  writeApprovedBuilding(storage, BUILDING_ID, SUBMISSION_ID);

  const target = resolvePreviewScreenshotTarget(storage, PORT, "pending", SUBMISSION_ID);
  assert.equal(target?.ownerKind, "approved");
  assert.equal(target?.ownerId, BUILDING_ID);
  assert.equal(
    target?.prefabUrl,
    `http://127.0.0.1:${PORT}/internal/approved-prefab/${BUILDING_ID}.json`,
  );
});

test("resolvePreviewScreenshotTarget returns null when pending is gone and no approved building matches", () => {
  const storage = createStorage(tempDataDir());
  const target = resolvePreviewScreenshotTarget(storage, PORT, "pending", SUBMISSION_ID);
  assert.equal(target, null);
});

test("resolvePreviewScreenshotTarget uses the approved prefab for published buildings", () => {
  const storage = createStorage(tempDataDir());
  writeApprovedBuilding(storage, BUILDING_ID, SUBMISSION_ID);

  const target = resolvePreviewScreenshotTarget(storage, PORT, "approved", BUILDING_ID);
  assert.equal(target?.ownerKind, "approved");
  assert.equal(target?.ownerId, BUILDING_ID);
  assert.equal(target?.meta?.submissionId, SUBMISSION_ID);
});

test("findApprovedBySubmissionId returns the published building meta", () => {
  const storage = createStorage(tempDataDir());
  writeApprovedBuilding(storage, BUILDING_ID, SUBMISSION_ID);

  const found = storage.findApprovedBySubmissionId(SUBMISSION_ID);
  assert.equal(found?.id, BUILDING_ID);
  assert.equal(storage.findApprovedBySubmissionId("missing"), null);
});
