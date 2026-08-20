import assert from "node:assert/strict";
import test from "node:test";
import {
  assignCommunityPropId,
  detectSubmissionContentType,
  isPropCatalogId,
  normalizeCatalogId,
  proposeCommunityPropId,
  resolveManifestContentType,
  validateSubmissionBuilding,
  validateSubmissionProp,
} from "../server/validation.js";

test("prop ids follow prop_community_ pattern", () => {
  const id = proposeCommunityPropId("12345678-1234-1234-1234-123456789abc", "Red Bench");
  assert.match(id, /^prop_community_[a-z0-9_]{8,80}$/);
  assert.equal(isPropCatalogId(id), true);
  assert.equal(normalizeCatalogId(id), id);
});

test("validateSubmissionProp requires displayName and prefabPath only", () => {
  const base = {
    displayName: "Red Bench",
    prefabPath: "bench.prefab.json",
  };
  assert.equal(validateSubmissionProp(base, 8), null);
  assert.equal(validateSubmissionBuilding(base, 8), "plot_token_missing");
  assert.equal(validateSubmissionProp({ prefabPath: "x.prefab.json" }, 8), "display_name_missing");
  assert.equal(validateSubmissionProp({ displayName: "Bench" }, 8), "prefab_path_missing");
});

test("assignCommunityPropId sets prefabPath from catalog id", () => {
  const prop = { displayName: "Lantern Post" };
  const id = assignCommunityPropId(prop, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  assert.ok(isPropCatalogId(id));
  assert.equal(prop.id, id);
  assert.equal(prop.prefabPath, `${id}.prefab.json`);
});

test("detectSubmissionContentType prefers prop multipart and id prefix", () => {
  assert.equal(
    detectSubmissionContentType({
      propField: true,
      definition: { displayName: "A", prefabPath: "a.prefab.json" },
    }),
    "prop",
  );
  assert.equal(
    detectSubmissionContentType({
      definition: { id: "prop_community_abcd1234_lantern", displayName: "A", prefabPath: "a.prefab.json" },
    }),
    "prop",
  );
  assert.equal(
    detectSubmissionContentType({
      definition: { wallSegment: true, displayName: "Wall", prefabPath: "a.prefab.json", plotTokenItemId: "x" },
    }),
    "wall",
  );
});

test("resolveManifestContentType infers wall from wallSegment", () => {
  assert.equal(resolveManifestContentType(undefined, { wallSegment: true }), "wall");
  assert.equal(resolveManifestContentType(undefined, { id: "prop_community_abcd1234_x" }), "prop");
  assert.equal(resolveManifestContentType(undefined, { id: "plot_community_abcd1234_x" }), "building");
});
