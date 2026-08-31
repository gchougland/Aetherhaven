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

/** Mirrors PrefabMeshBuilder.prefabUsesOldBlockEntityScale threshold. */
const OLD_THRESHOLD = 1.001;

function prefabUsesOldBlockEntityScale(scales) {
  return scales.some((s) => s > OLD_THRESHOLD);
}

function worldScale(raw, oldConventionPrefab) {
  return oldConventionPrefab ? raw / 2 : raw;
}

checkAnchor(2.0, 5.0, 5.5);
checkAnchor(1.2, 2.0, 2.3);

assert("wagon-like prefab is new", !prefabUsesOldBlockEntityScale([1, 0.8, 0.6, 0.5]));
assert("cozy-like prefab is old", prefabUsesOldBlockEntityScale([2, 2, 2]));
assert("mixed old prefab is old", prefabUsesOldBlockEntityScale([2, 1, 0.8]));

assertClose("mixed old scale 1 halves", worldScale(1, true), 0.5);
assertClose("mixed old scale 2 halves", worldScale(2, true), 1);
assertClose("new scale 1 stays", worldScale(1, false), 1);
assertClose("new scale 0.5 stays", worldScale(0.5, false), 0.5);

console.log("assert-block-entity-anchor: ok");
