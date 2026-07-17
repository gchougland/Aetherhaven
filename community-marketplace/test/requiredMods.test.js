import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createStorage } from "../server/storage.js";
import { normalizeRequiredMods, validateSubmissionBuilding } from "../server/validation.js";

test("normalizes and deduplicates required mods", () => {
  assert.deepEqual(
    normalizeRequiredMods([
      { id: "dejan:ancient", name: "Ancient Furniture" },
      { id: "DEJAN:ANCIENT", name: "duplicate" },
    ]),
    [{ id: "dejan:ancient", name: "Ancient Furniture" }],
  );
});

test("submission accepts omitted or valid dependency metadata", () => {
  const base = {
    displayName: "Safe build",
    prefabPath: "safe.prefab.json",
    plotTokenItemId: "Aetherhaven:PlotToken",
  };
  assert.equal(validateSubmissionBuilding(base, 8), null);
  assert.equal(validateSubmissionBuilding({ ...base, requiredMods: null }, 8), "required_mods_invalid");
  assert.equal(validateSubmissionBuilding({ ...base, requiredMods: [{ name: "No id" }] }, 8), "required_mods_invalid");
  assert.equal(
    validateSubmissionBuilding(
      { ...base, requiredMods: [{ id: "dejan:ancient", name: "Ancient Furniture" }] },
      8,
    ),
    null,
  );
  assert.equal(validateSubmissionBuilding({ ...base, requiredMods: [] }, 8), null);
});

test("pending storage preserves required mods for moderation projection", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-marketplace-"));
  try {
    const storage = createStorage(root);
    const submissionId = "plot_community_safe_1";
    const dir = storage.submissionDir(submissionId, "pending");
    fs.mkdirSync(dir, { recursive: true });
    const requiredMods = [{ id: "dejan:ancient", name: "Ancient Furniture" }];
    fs.writeFileSync(
      path.join(dir, "meta.json"),
      JSON.stringify({ submissionId, status: "pending", requiredMods }),
    );

    assert.deepEqual(storage.listPending()[0].requiredMods, requiredMods);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
