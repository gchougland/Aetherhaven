import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createStorage } from "../server/storage.js";
import {
  nextVersionForBuilding,
  parseVersionNumber,
  updateOwnedSubmission,
  withdrawPendingSubmissionInternal,
} from "../server/submissionUpdates.js";
import { validateSubmissionBuilding } from "../server/validation.js";

const CREATOR_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
const OTHER_UUID = "11111111-2222-3333-4444-555555555555";
const BUILDING_ID = "plot_community_aaaabbbb_cozy_cottage";

function normalizeDescription(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeTags(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((tag) => String(tag || "").trim()).filter(Boolean);
}

function isOwnedByProfile(metaOrEntry, profileUuid) {
  return (
    String(metaOrEntry?.creatorUuid || "")
      .trim()
      .toLowerCase() === profileUuid
  );
}

function sampleBuilding(id = BUILDING_ID) {
  return {
    id,
    displayName: "Cozy Cottage",
    prefabPath: `${id}.prefab.json`,
    plotTokenItemId: "Aetherhaven:PlotToken",
    tags: ["home"],
  };
}

function samplePrefabBuffer() {
  return Buffer.from(JSON.stringify({ version: 8, blockIdVersion: 8, blocks: [] }));
}

function multerFile(name, buffer) {
  return { fieldname: name, buffer, size: buffer.length };
}

function writeApprovedBuilding(storage, id, creatorUuid, version = "2") {
  const paths = storage.approvedPaths(id);
  fs.mkdirSync(paths.dir, { recursive: true });
  fs.writeFileSync(paths.building, JSON.stringify(sampleBuilding(id), null, 2));
  fs.writeFileSync(paths.prefab, samplePrefabBuffer());
  fs.writeFileSync(
    paths.meta,
    JSON.stringify({
      id,
      creatorUuid,
      creatorName: "Creator",
      displayName: "Cozy Cottage",
      version,
      status: "approved",
      approvedAt: new Date().toISOString(),
      blockIdVersion: 8,
    }),
  );
  const manifest = storage.readManifest();
  manifest.entries = manifest.entries.filter((entry) => entry.id !== id);
  manifest.entries.push({
    id,
    displayName: "Cozy Cottage",
    creatorUuid,
    creatorName: "Creator",
    version,
    blockIdVersion: 8,
    prefabBytes: samplePrefabBuffer().length,
    approvedAt: new Date().toISOString(),
  });
  storage.writeManifest(manifest);
}

function writePendingSubmission(storage, submissionId, proposedId, creatorUuid, version = "1") {
  const dir = storage.submissionDir(submissionId, "pending");
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, "building.json"), JSON.stringify(sampleBuilding(proposedId), null, 2));
  fs.writeFileSync(path.join(dir, "prefab.prefab.json"), samplePrefabBuffer());
  fs.writeFileSync(
    path.join(dir, "meta.json"),
    JSON.stringify({
      submissionId,
      proposedId,
      creatorUuid,
      creatorName: "Creator",
      displayName: "Cozy Cottage",
      version,
      status: "pending",
      submittedAt: "2026-01-01T00:00:00.000Z",
      blockIdVersion: 8,
    }),
  );
}

test("parseVersionNumber handles numeric strings", () => {
  assert.equal(parseVersionNumber("1"), 1);
  assert.equal(parseVersionNumber("12"), 12);
  assert.equal(parseVersionNumber(undefined), 0);
});

test("nextVersionForBuilding picks the highest existing version", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-marketplace-"));
  try {
    const storage = createStorage(root);
    writeApprovedBuilding(storage, BUILDING_ID, CREATOR_UUID, "2");
    writePendingSubmission(storage, `${BUILDING_ID}_pending`, BUILDING_ID, CREATOR_UUID, "3");
    assert.equal(nextVersionForBuilding(storage, BUILDING_ID), "4");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("updateOwnedSubmission replaces pending files and increments version", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-marketplace-"));
  try {
    const storage = createStorage(root);
    const submissionId = `${BUILDING_ID}_1000`;
    writePendingSubmission(storage, submissionId, BUILDING_ID, CREATOR_UUID, "1");
    const building = sampleBuilding();
    building.displayName = "Updated Cottage";
    const buildingBuffer = Buffer.from(JSON.stringify(building));
    const result = updateOwnedSubmission({
      storage,
      buildingId: BUILDING_ID,
      creatorUuid: CREATOR_UUID,
      creatorName: "Creator",
      buildingFile: multerFile("building", buildingBuffer),
      prefabFile: multerFile("prefab", samplePrefabBuffer()),
      isOwnedByProfile,
      normalizeDescription,
      normalizeTags,
    });
    assert.equal(result.status, 200);
    assert.equal(result.body.action, "replaced_pending");
    assert.equal(result.body.version, "2");
    assert.equal(result.body.submissionId, submissionId);
    const meta = JSON.parse(
      fs.readFileSync(path.join(storage.submissionDir(submissionId, "pending"), "meta.json"), "utf8"),
    );
    assert.equal(meta.version, "2");
    assert.equal(meta.displayName, "Updated Cottage");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("updateOwnedSubmission creates new pending for approved buildings", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-marketplace-"));
  try {
    const storage = createStorage(root);
    writeApprovedBuilding(storage, BUILDING_ID, CREATOR_UUID, "2");
    writePendingSubmission(storage, `${BUILDING_ID}_old`, BUILDING_ID, CREATOR_UUID, "3");
    const buildingBuffer = Buffer.from(JSON.stringify(sampleBuilding()));
    const result = updateOwnedSubmission({
      storage,
      buildingId: BUILDING_ID,
      creatorUuid: CREATOR_UUID,
      creatorName: "Creator",
      buildingFile: multerFile("building", buildingBuffer),
      prefabFile: multerFile("prefab", samplePrefabBuffer()),
      isOwnedByProfile,
      normalizeDescription,
      normalizeTags,
    });
    assert.equal(result.status, 201);
    assert.equal(result.body.action, "created_pending");
    assert.equal(result.body.isBuildingUpdate, true);
    assert.equal(result.body.version, "4");
    assert.equal(storage.listPending().length, 1);
    assert.notEqual(storage.listPending()[0].submissionId, `${BUILDING_ID}_old`);
    const approvedMeta = JSON.parse(
      fs.readFileSync(storage.approvedPaths(BUILDING_ID).meta, "utf8"),
    );
    assert.equal(approvedMeta.version, "2");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("updateOwnedSubmission rejects non owners", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-marketplace-"));
  try {
    const storage = createStorage(root);
    writePendingSubmission(storage, `${BUILDING_ID}_1000`, BUILDING_ID, CREATOR_UUID, "1");
    const buildingBuffer = Buffer.from(JSON.stringify(sampleBuilding()));
    const result = updateOwnedSubmission({
      storage,
      buildingId: BUILDING_ID,
      creatorUuid: OTHER_UUID,
      creatorName: "Other",
      buildingFile: multerFile("building", buildingBuffer),
      prefabFile: multerFile("prefab", samplePrefabBuffer()),
      isOwnedByProfile,
      normalizeDescription,
      normalizeTags,
    });
    assert.equal(result.status, 403);
    assert.equal(result.body.error, "not_owner");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("withdrawPendingSubmissionInternal removes pending directory", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-marketplace-"));
  try {
    const storage = createStorage(root);
    const submissionId = `${BUILDING_ID}_1000`;
    writePendingSubmission(storage, submissionId, BUILDING_ID, CREATOR_UUID, "1");
    withdrawPendingSubmissionInternal(storage, submissionId);
    assert.equal(storage.listPending().length, 0);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("sample building validates for submission updates", () => {
  assert.equal(validateSubmissionBuilding(sampleBuilding(), 8), null);
});
