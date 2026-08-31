/**
 * Offline checks for Update 6 block-entity scale/anchor handling in the prefab viewer.
 *
 * Usage: node scripts/assert-block-entity-anchor.mjs
 */

/**
 * @param {{ x: number, y: number, z: number }} position
 * @param {number} oldScale
 */
function applyAnchorShiftIdentity(position, oldScale) {
  position.y += oldScale / 4 - 0.5;
  position.y += 0.5;
}

function assertClose(label, actual, expected, eps = 1e-6) {
  if (Math.abs(actual - expected) > eps) {
    throw new Error(`${label}: expected ${expected}, got ${actual}`);
  }
}

function assert(label, cond) {
  if (!cond) {
    throw new Error(label);
  }
}

function checkAnchor(oldScale, startY, expectedY) {
  const position = { x: 10, y: startY, z: 20 };
  applyAnchorShiftIdentity(position, oldScale);
  assertClose(`scale ${oldScale} x`, position.x, 10);
  assertClose(`scale ${oldScale} y`, position.y, expectedY);
  assertClose(`scale ${oldScale} z`, position.z, 20);
}

/** Mirrors PrefabMeshBuilder.blockEntityHasUpdate6Version / prefabUsesOldBlockEntityScale. */
function blockEntityHasUpdate6Version(blockEntity) {
  const version = blockEntity?.Version ?? blockEntity?.version;
  return version != null && Number(version) >= 1;
}

function prefabUsesOldBlockEntityScale(entities) {
  for (const entity of entities || []) {
    const comps = entity?.Components || entity?.components || {};
    const blockEntity = comps.BlockEntity || comps.blockEntity;
    if (!blockEntity) {
      continue;
    }
    if (!blockEntityHasUpdate6Version(blockEntity)) {
      return true;
    }
  }
  return false;
}

function worldScale(raw, oldConventionPrefab) {
  return oldConventionPrefab ? raw / 2 : raw;
}

checkAnchor(2.0, 5.0, 5.5);
checkAnchor(1.2, 2.0, 2.3);

assert(
  "wagon-like (Version:1) is new",
  !prefabUsesOldBlockEntityScale([
    { Components: { BlockEntity: { Version: 1 }, EntityScale: { Scale: 1 } } },
    { Components: { BlockEntity: { Version: 1 }, EntityScale: { Scale: 0.5 } } },
  ])
);
assert(
  "cozy-like (no Version, scale 2) is old",
  prefabUsesOldBlockEntityScale([
    { Components: { BlockEntity: { BlockTypeKey: "Wood_Hardwood_Beam" }, EntityScale: { Scale: 2 } } },
  ])
);
assert(
  "blacksmith-like (no Version, scale 0.8) is old",
  prefabUsesOldBlockEntityScale([
    { Components: { BlockEntity: { BlockTypeKey: "X" }, EntityScale: { Scale: 0.8 } } },
  ])
);

assertClose("old scale 0.8 halves", worldScale(0.8, true), 0.4);
assertClose("old scale 2 halves", worldScale(2, true), 1);
assertClose("new scale 1 stays", worldScale(1, false), 1);
assertClose("new scale 0.5 stays", worldScale(0.5, false), 0.5);

console.log("assert-block-entity-anchor: ok");
