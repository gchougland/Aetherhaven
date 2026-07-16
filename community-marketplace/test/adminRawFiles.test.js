import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  AdminRawFileError,
  applyAdminRawMetadata,
  atomicWriteText,
  projectAdminRawMetadata,
  validateAdminRawFilePair,
} from "../server/adminRawFiles.js";
import { MAX_BUILDING_JSON_BYTES } from "../server/validation.js";

const PUBLISHED_ID = "plot_community_12345678_house";

function building(overrides = {}) {
  return {
    id: PUBLISHED_ID,
    displayName: "Raw House",
    description: "Edited directly",
    prefabPath: `${PUBLISHED_ID}.prefab.json`,
    plotTokenItemId: "Aetherhaven:PlotToken",
    requiredMods: [],
    styleId: "Coastal Ruins",
    tags: ["House", "house"],
    ...overrides,
  };
}

function prefab(overrides = {}) {
  return {
    blockIdVersion: 11,
    blocks: [],
    ...overrides,
  };
}

function expectRawError(fn, code) {
  assert.throws(fn, (err) => err instanceof AdminRawFileError && err.code === code);
}

test("validates a published raw building and prefab pair", () => {
  const result = validateAdminRawFilePair({
    buildingText: JSON.stringify(building()),
    prefabText: JSON.stringify(prefab()),
    publishedId: PUBLISHED_ID,
  });
  assert.equal(result.blockIdVersion, 11);
  assert.equal(result.building.displayName, "Raw House");
  assert.ok(result.prefabBytes > 0);
});

test("rejects malformed and oversized raw JSON", () => {
  expectRawError(
    () =>
      validateAdminRawFilePair({
        buildingText: "{not json",
        prefabText: JSON.stringify(prefab()),
      }),
    "building_json_invalid",
  );
  expectRawError(
    () =>
      validateAdminRawFilePair({
        buildingText: "x".repeat(MAX_BUILDING_JSON_BYTES + 1),
        prefabText: JSON.stringify(prefab()),
      }),
    "building_json_too_large",
  );
});

test("rejects prefab JSON without a positive block id version", () => {
  expectRawError(
    () =>
      validateAdminRawFilePair({
        buildingText: JSON.stringify(building()),
        prefabText: JSON.stringify(prefab({ blockIdVersion: 0 })),
      }),
    "block_id_version_missing",
  );
});

test("protects published id and canonical prefab path", () => {
  expectRawError(
    () =>
      validateAdminRawFilePair({
        buildingText: JSON.stringify(building({ id: "plot_community_87654321_other" })),
        prefabText: JSON.stringify(prefab()),
        publishedId: PUBLISHED_ID,
      }),
    "published_id_immutable",
  );
  expectRawError(
    () =>
      validateAdminRawFilePair({
        buildingText: JSON.stringify(building({ prefabPath: "other.prefab.json" })),
        prefabText: JSON.stringify(prefab()),
        publishedId: PUBLISHED_ID,
      }),
    "published_prefab_path_immutable",
  );
});

test("projects raw changes into catalog metadata", () => {
  const metadata = projectAdminRawMetadata(building(), 11, 2048);
  assert.deepEqual(metadata, {
    displayName: "Raw House",
    description: "Edited directly",
    styleId: "Coastal Ruins",
    tags: ["house"],
    requiredMods: [],
    blockIdVersion: 11,
    prefabBytes: 2048,
  });
  const manifestEntry = applyAdminRawMetadata(
    { id: PUBLISHED_ID, description: "Old", prefabBytes: 1 },
    metadata,
    { includePrefabBytes: true },
  );
  assert.equal(manifestEntry.id, PUBLISHED_ID);
  assert.equal(manifestEntry.displayName, "Raw House");
  assert.equal(manifestEntry.prefabBytes, 2048);
  assert.deepEqual(manifestEntry.tags, ["house"]);
});

test("atomically replaces file text and adds a final newline", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-raw-editor-"));
  try {
    const file = path.join(root, "building.json");
    fs.writeFileSync(file, "{}\n");
    atomicWriteText(file, '{"displayName":"Updated"}');
    assert.equal(fs.readFileSync(file, "utf8"), '{"displayName":"Updated"}\n');
    assert.deepEqual(
      fs.readdirSync(root).filter((name) => name.endsWith(".tmp")),
      [],
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
